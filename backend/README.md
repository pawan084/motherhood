# Aira Backend

FastAPI service for Aira — the maternal-wellness companion. It owns the one thing
the web and native prototypes each half-implemented: **the safety gate**. Every
inbound message is screened before Aira replies; a red result routes the user to
their care team instead of an AI answer.

## Stack

- **FastAPI** + uvicorn
- **Google Gemini** (`google-genai`) — the only LLM provider, running both the
  safety classifier and the reply. No second provider to fall back to.
- **SQLite** by default, **Postgres** when `DATABASE_URL` is set (same SQL).
- Stdlib auth: PBKDF2 password hashing + HMAC-signed sessions (admin) and
  HMAC app-session tokens (mobile/web). Google Sign-In via `pyjwt`.

## Run locally

```bash
cd backend
python -m venv .venv && source .venv/bin/activate   # or .venv\Scripts\activate on Windows
pip install -r requirements.txt
cp .env.example .env        # optional; runs with zero config in dev
uvicorn app.main:app --reload    # http://127.0.0.1:8000  (docs at /docs)
```

Without `GEMINI_API_KEY` the server still runs: the safety gate falls back to its
deterministic **keyword floor** and chat returns a calm non-AI reply. Set the key
to enable the classifier and real replies.

## Test

```bash
pytest -q          # 322 tests, fully offline (no API key needed)
```

## Layout

```
backend/
├── app/                   the package — `uvicorn app.main:app`
│   ├── main.py            routers, CORS, middleware, fail-closed boot
│   ├── config.py          every env read, once
│   ├── core/              infrastructure, no domain knowledge
│   ├── safety/            the gate: gate.py (decision) + flags.py (record)
│   ├── domains/           one module per domain, each owning its own tables
│   ├── admin.py  privacy.py  prompts.py  analytics_store.py  legal.py
├── data/                  video catalogue
├── tools/                 code generators (client offline keyword list)
└── tests/                 322 tests, fully offline
```

Arranged by **domain, not by layer**. Each module in `domains/` owns its router,
its tables, its SQL, and its `export_user`/`delete_user` pair, so a new table is
covered where it is defined and `privacy.py` erases an account by walking modules
rather than a hand-kept list of tables.

| Module | Responsibility |
|---|---|
| `main.py` | Entry: routers, CORS, rate-limit middleware, fail-closed prod checks |
| `config.py` | Every environment variable + `insecure_production_config()` |
| `core/security.py` | App-token gate, per-IP rate limit, upload cap, history sanitization |
| `core/db.py` | Portable connection helper (SQLite ⟷ Postgres) |
| `core/passwords.py` | PBKDF2 hashing, timing dummy, account-keyed login throttle |
| `core/llm.py` | Gemini access (`gemini_json`/`gemini_stream` + health probe) |
| `safety/gate.py` | **The decision** — keyword floor ⊕ LLM classifier → green/amber/red. No database |
| `safety/flags.py` | **The record** — amber/red persistence, retention, admin review |
| `prompts.py` | Admin-editable prompt registry (system + safety classifier), in-code defaults |
| `domains/accounts.py` | Device tokens, Google Sign-In, app sessions, `current_user` |
| `domains/care.py` | Onboarding, journey-aware Today/Journey/Care, tools, emergency profile |
| `domains/content.py` | Journey content with version + review metadata; published rows override the in-code seed |
| `domains/memory.py` | Reviewable care memory; gated by the `personalization` consent **and** per-item approval |
| `domains/consent.py` | Append-only consent ledger + the `require_consent` dependency |
| `domains/chat.py` | The safety-gated turn + `/safety/screen` + history |
| `domains/feedback.py` | Feedback + "report an AI answer" (clinical/safety) |
| `domains/videos.py` · `prefs.py` · `partner.py` | Video library, voice prefs, partner invites |
| `privacy.py` | Data export + account deletion, fanned out over each module's `export_user`/`delete_user` |
| `analytics_store.py` | Events + dashboard rollups |
| `legal.py` | Public privacy/terms/faq pages |
| `admin.py` | RBAC auth (PBKDF2/HMAC/CSRF) + dashboard/users/safety/feedback/content/prompts/system |

## Key endpoints

Mobile/web (Bearer app-session token; coarse `X-App-Token` gate optional):

- `POST /device/register` → anonymous session token
- `POST /account/google` → sign in, merges the device's care data
- `POST /v1/onboarding` · `GET /v1/today` · `GET /v1/journey` · `GET /v1/care`
- `POST /v1/chat/turn` → **safety-gated** reply / urgent handoff
- `POST /v1/safety/screen` → standalone screen
- `GET/POST /v1/memory` · `GET/POST /v1/consent` · `GET/PUT /v1/emergency-profile`
- `POST /v1/feedback` · `POST /v1/feedback/report`
- `GET /v1/account/export` → everything held for this user, as JSON
- `POST /v1/account/delete` → irreversible erase (body: `{"confirm": "DELETE MY DATA"}`)

Admin (httpOnly cookie session + CSRF), all under `/admin/*`:
`login`, `overview`, `users`, `safety/flags`, `feedback`, `content`, `prompts`,
`admins`, `system`.

## Production notes

`ENV=production` makes startup **fail closed** unless `ADMIN_JWT_SECRET`,
`APP_SESSION_SECRET`, and `APP_SHARED_SECRET` are set to real values. Deploy with
`Dockerfile` (Fly: `fly.toml`, Render: `render.yaml`).

Integration points still to wire for real clinical use: encrypted document byte
storage + OCR-approval flow (Care Vault currently stores metadata only),
retention/redaction policy for `safety_flags` message text (sensitive health
content), notification scheduling, and avatar/voice providers. The safety
classifier and keyword lists should be reviewed by a clinician before launch.
