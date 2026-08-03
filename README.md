# Aira

An AI maternal wellness companion for people who are trying to conceive,
pregnant, or postpartum. Chat-first, with a hard clinical safety gate on every
input and visible consent and privacy controls.

| Doc | Covers |
|---|---|
| **README.md** (this) | what it is, how to run it, current state |
| [PLAN.md](./PLAN.md) | locked decisions, module map, phase plan |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | design and the reasoning behind it |
| [TODO.md](./TODO.md) | known gaps, and the human-gated items with lead times |

---

## Layout

```
aira/
├── backend/     FastAPI API + safety gate + SQLite/Postgres   (Python 3.12)
├── web/         React 19 client                               (TypeScript)
└── ref/         reference material — see below
```

`ref/` is the spec and the architecture donor, not shipped code:

**Tracked in git** (they define the product):

- `mockup.png` — the 5-tab product (Today · Aira · Journey · Care · You) + Urgent Help
- `diagram.png` — the full flow; note that the safety gate screens **every** input

**Local only, not in git** — three separate codebases with their own histories,
539M in total. A fresh clone will not have them; they are read, never shipped:

- `aira-react-web/` — a front-end-only React prototype (no backend, no AI)
- `AiraAndroid/` — a front-end-only Compose prototype
- `sayli/` — a shipped full-stack product whose backend patterns Aira ports

---

## Run the backend

Requires Python 3.12+ (macOS system Python is 3.9 — use `uv`, which is already
installed at `~/.local/bin/uv`).

```bash
cd backend
uv venv --python 3.12 .venv
uv pip install -r requirements.txt --python .venv/bin/python
cp .env.example .env          # fill GEMINI_API_KEY and ELEVENLABS_API_KEY
.venv/bin/python -m uvicorn app:app --reload --port 8001
```

Sanity check:

```bash
curl http://127.0.0.1:8001/health    # {"ok":true}
curl http://127.0.0.1:8001/config    # shared catalogs
```

Tests:

```bash
cd backend && .venv/bin/python -m pytest -q
```

## Run the web client

```bash
cd web
npm install
npm run dev -- --host      # http://localhost:5173 (backend must be on :8001)
```

`VITE_API_URL` overrides the backend base URL. The app registers a guest
device token on first load, runs onboarding inside chat, and unlocks the five
tabs once a care context exists. With no Gemini key configured the chat
demonstrates the fail-closed posture honestly (benign text → "Safety check
unavailable", danger signs → the Urgent Help takeover, offline).

First run creates `backend/aira.db` (SQLite, gitignored). **That file will hold
health data** — it is excluded from git deliberately, along with
`backend/uploads/`.

---

## Current state

**All build phases (P0–P10) complete; live-verified with a real Gemini key**
(2026-08-03):

- **Safety evals: 20/20, zero critical failures** on the first run
  (`backend/evals/safety_eval.py`) — typos, Hinglish, Hindi negation,
  LLM-only catches, third-party reports, idioms. p95 gate latency 1.7s.
- **The full product loop verified live**: caution turn → empathy-first
  reply → typed cards → memory extraction; SSE streaming in Hinglish with
  Hinglish cards. One threading bug found only live (per-call genai clients
  dying) — fixed with a shared client.
- Still keyless: ElevenLabs (voice quality checks in TODO.md).

**P9 complete** — the voice loop, built and wired, live verification pending
API keys:

- `POST /respond_voice`: audio → Gemini transcript → **the same gated turn
  spine as text**. Transcription is deliberately a separate call from reply
  generation — the gate screens the transcript before any reply exists, so a
  spoken danger sign escalates exactly like a typed one (tested). Silence →
  `empty` (no gate, no crying wolf); STT failure → honest `error`.
- `POST /speak`: ElevenLabs MP3, LRU-cached; TTS failure never costs the
  text reply (502, client degrades silently).
- Client: tap-to-toggle mic (MediaRecorder), transcript renders as your
  bubble, replies auto-play. **139 backend tests.** Keyless fail-closed
  paths live-verified; real STT/TTS quality checks listed in TODO.md.

**P10 complete** (2026-08-03). Admin + deploy hardening:

- `admin.py` — owner/support/content roles, PBKDF2 passwords, HMAC admin
  tokens (disjoint from learner tokens, tested), bootstrap owner from env.
- **Safety review queue**: filterable, paginated, reviews in a separate
  table (the gate's record stays append-only), `unreviewed_urgent` on the
  overview, every admin action logged. Journey CMS edits reach learners
  immediately.
- Admin console at `/admin.html` (second Vite entry).
- `Dockerfile` + `smoke_test.sh`; full urgent→queue→review loop
  live-verified. **128 backend tests.**
- Remaining in P9-P10: the voice loop (needs GEMINI/ELEVENLABS keys to
  verify) and the production human-gated list in TODO.md.

**P7 complete** (2026-08-03). Privacy controls are real:

- `privacy.py` — the `ai_personalisation` consent with real effect (off = no
  memories in prompts, no extraction; existing memories retained until
  explicitly deleted), full JSON export (including the learner's own
  safety-audit entries), and `DELETE /learner-data` so guests can delete
  everything without an account.
- You tab: working toggle, "Download my data", and a confirmed
  delete-everything that returns the app to onboarding.
- Deferred deliberately: partner access (needs a second-party consent flow).
  Open legal question flagged: audit-row retention after deletion
  (ARCHITECTURE §15, TODO.md). **116 backend tests.**

**P6 complete** (2026-08-03). The Care tab is real:

- `care.py` — medicines (+ idempotent taken-today), documents
  (upload/download/delete; server-generated paths, extension whitelist, size
  cap), appointments, care plan. All owner-scoped; sign-in merge moves files
  between directories; account deletion removes files from disk too.
- Care tab UI: appointments with add form, medicines with taken-dots, the
  Care Vault with real upload/download, care-plan checklist — plus the
  offline emergency profile.
- Deferred honestly: reminder delivery (web push infra) and AI document
  extraction (queued work + needs the key). **110 backend tests.**

**P5 complete** (2026-08-03). Journey has real content:

- `seed_journey.py` — 16 week-banded content units (pregnant 1–42, postpartum
  1–52+, TTC) covering the mockup's three sections: your body / your baby /
  prepare for your visit. **General-knowledge copy, human-gated: clinician
  review required before real users** (TODO.md).
- `journey.py` — `GET /journey` derives the week from the care context;
  `?week=` pages without modifying it; seed-once so admin edits stick.
- Journey tab renders the sections with week paging and the disclaimer.
- **97 backend tests**; band coverage for every week is pinned by test.

**P4 complete** (2026-08-03). Memory — "What Aira remembers" — is live:

- `memory.py` — structured facts (fact/concern/symptom/preference) extracted
  per chat turn by the small model, never transcripts. Readable AND deletable
  (`GET /memory`, `DELETE /memory/{id}`, `DELETE /memory`); a deletion is out
  of the prompt by the very next turn. Urgent/error turns store nothing.
  Sign-in reassigns device memories to the account; account deletion erases
  them.
- You tab renders the real memory list with per-item forget + forget-all.
- **87 backend tests**; client smoke 15/15 against the live backend.

**Next: P5** (Journey week content), **P6** (Care), **P7** (You: export,
partner access).

**P8 complete** (2026-08-02). The web client is wired to the backend:

- `web/` — Vite + React 19 SPA (the prototype's vinext/Cloudflare scaffolding
  was dropped; the backend is FastAPI, the client is a static SPA). Visual
  system rewritten from the prototype's palette.
- Real flows only: guest device bootstrap → onboarding-in-chat →
  `PUT /care-context` → tabs unlock. Chat streams over SSE with the gate
  event routing urgent turns to the full-screen Urgent Help takeover and
  error turns to an honest inline notice. Action cards render from the
  backend's typed payloads.
- The **emergency profile is localStorage-only and works offline** by design.
  Features without a backend yet (medicines, Care Vault, memory review) are
  labelled "coming soon" — nothing fake is presented as real.
- Verified by a Node integration smoke running the REAL bundled `api.ts`
  against the live backend: 13/13 checks (device bootstrap, week-24 context,
  SSE gate-error + urgent event ordering, honest labels). In-browser
  click-through is pending — see TODO.

**P3 complete** (2026-08-02). Chat is live behind the gate:

- `prompts.py` — care-context-grounded system prompt (stage, week, name,
  en/hi/Hinglish), escalate-don't-diagnose rules, the caution addendum, and
  the typed `CARD_TYPES` catalog (served via `/config`).
- `services.py` — all Gemini calls live here or in `safety.py`, nowhere else.
  `suggest_cards` never raises; cards are garnish, the reply is not.
- `chat.py` — `POST /respond` + `POST /respond_stream` (SSE). Gate on every
  turn; urgent/error turns provably never call the model; the SSE gate event
  precedes any token; the trust label is honest ("Safety check unavailable"
  on error turns, never "Safety checked").
- **77 tests passing.** Live-verified without a Gemini key: benign text →
  honest `error` with no reply; self-harm → full Urgent Help payload, offline.

**Next: P4-P7** (memory + the tabs), or **P8** (the web client) — the chat
contract the client needs is now stable.

**P2 complete** (2026-08-02). The safety gate exists and is tested:

- `safety_taxonomy.py` — the red-flag taxonomy as clinician-reviewable data
  (bleeding, preeclampsia signs, fetal movement, fever, breathing/chest,
  waters, seizure/fainting, self-harm; en/hi/Hinglish). **Not yet reviewed by
  a clinician — human-gated in TODO.md.**
- `safety.py` — deterministic rules + Gemini classifier, fail-closed in every
  branch, with hard floors the model cannot override (self-harm → urgent with
  no network on the path; any symptom hit → at least caution). Every decision
  audited to `safety_audit`.
- 28 adversarial tests over the deterministic guarantees (LLM mocked); live
  classifier evals await a GEMINI_API_KEY.
- **57 tests passing** repo-wide.

**Next: P3** — the chat endpoint, now unblocked.

**P1 complete** (2026-08-02). The backend core is up:

- `config.py` — environment config plus a production startup guard that refuses
  to boot on placeholder secrets, a missing Gemini key, or a missing
  `DATABASE_URL`.
- `db.py` — SQLite/Postgres adapter ported from sayli; one shared handle per
  database so cross-module transactions (the account-deletion cascade) work.
- `security.py` — shared-secret auth, per-IP rate limiting (Redis-aware),
  upload cap, client-history sanitization. Ported from sayli.
- `device_auth.py` — HMAC guest device tokens; identity resolution is session →
  device token → 401, and never a client-named id (the IDOR fix, kept).
- `accounts.py` — HMAC sessions, Google Sign-In (fails closed 503 until
  `GOOGLE_CLIENT_ID` is configured), dev login (blocked in production), and an
  account deletion that cascades through linked devices' data too.
- `care_context.py` — journey stage + anchor dates; the week index is computed
  at read time, never stored. Sign-in merges guest context (freshest wins).
- `app.py` — routers wired; CORS outermost so even 429s are readable by the
  browser.
- **29 tests passing**, including tampered-token, cross-device IDOR,
  token-kind-confusion, and delete-cascade cases; full guest → onboard →
  sign-in → merged-context flow verified against a running server.

**Next: P2**, the clinical safety gate, which blocks all chat work.

## What is deliberately not built yet

There is no chat endpoint, and there will not be one until the safety gate in P2
lands and its adversarial test suite passes. The gate is on the critical path of
every turn, fails closed to Urgent Help, and audits every decision. Shipping chat
first and adding safety later is the one sequencing mistake this project cannot
make.
