# Aira — AI Maternal-Wellness Companion

A private, chat-first companion for people who are **trying to conceive, pregnant,
or postpartum**. The product's defining promise is **safety**: every message
passes a safety gate before Aira replies, and anything urgent is routed to the
user's care team rather than answered by an AI.

This monorepo holds all four surfaces plus one shared backend that owns the
safety gate — so the web and Android clients behave identically instead of each
implementing half of it (which is exactly what the original prototypes did).

```
motherhood/
├── apps/
│   ├── web/        React 19 + Vite + Cloudflare Worker — the consumer web app
│   ├── admin/      Next.js 14 — operational console (safety, content, users)
│   └── android/    Kotlin + Jetpack Compose — the native app
├── backend/        FastAPI + Google Gemini — API, safety gate, auth, data
└── ref/            Original prototypes (kept intact for reference)
```

> **Reference stack:** the structure and conventions (FastAPI + Gemini backend,
> Next.js admin, per-service `.env.example`, fail-closed prod checks) follow the
> `sayli` project's layout.

## The safety flow (why the backend exists)

```
message ─▶ [ SAFETY GATE ] ─▶ red  ─▶ urgent-care handoff  (real care-team number, no AI reply)
                          └─▶ green/amber ─▶ AI reply + trust label + ONE action card
```

The gate (`backend/safety.py`) combines a deterministic **keyword floor** (works
even when the LLM is down) with a **Gemini classifier**, always erring toward
caution. Every amber/red screen is logged for review in the admin console. This
single server-side implementation fixes the two biggest gaps found in review:

- Web shipped a keyword gate but an **inert "Call care team" button**.
- Android shipped a **real dialer but no input gate**.

Both now call `POST /v1/chat/turn`; a red result returns `urgent: true` with a
real care-team number sourced from the user's emergency profile.

## Quick start

**Backend** (needs Python 3.12+):
```bash
cd backend
pip install -r requirements.txt
uvicorn app:app --reload          # http://127.0.0.1:8000  (/docs, /health)
pytest -q                          # 18 tests, fully offline
```
Runs with zero config in dev. Set `GEMINI_API_KEY` to enable real replies + the
LLM classifier (without it, the deterministic keyword gate still protects users).

**Admin** (needs Node 18+):
```bash
cd apps/admin
npm install
cp .env.example .env.local        # NEXT_PUBLIC_API_URL=http://127.0.0.1:8000
npm run dev                        # http://127.0.0.1:3001
```
Bootstrap an admin by setting `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD`
in the backend env before first run.

**Web:**
```bash
cd apps/web
npm install
cp .env.example .env               # VITE_API_URL=http://127.0.0.1:8000
npm run dev
```

**Android:** open `apps/android` in Android Studio. The backend URL is
`BuildConfig.AIRA_API_BASE` (defaults to `http://10.0.2.2:8000`, the host as seen
from the emulator); override with `-PairaApiBase=` or `gradle.properties`.

## Docs

- [`ARCHITECTURE.md`](ARCHITECTURE.md) — system design, data flow, auth model
- [`DEPLOY.md`](DEPLOY.md) — deploying the backend (Fly / Render) and admin
- [`backend/README.md`](backend/README.md) — backend module map and endpoints

## Safety boundary

Aira is **wellness support, not diagnosis or emergency care**. The safety gate,
trust labels, and urgent handoff are product safeguards, not a substitute for
clinical judgement. The classifier prompt and keyword lists must be reviewed by a
clinician before any real-world use.
