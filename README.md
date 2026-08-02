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

First run creates `backend/aira.db` (SQLite, gitignored). **That file will hold
health data** — it is excluded from git deliberately, along with
`backend/uploads/`.

---

## Current state

**P0 complete** (2026-08-02). The repo, docs, and backend skeleton exist:

- `config.py` — environment config plus a production startup guard that refuses
  to boot on placeholder secrets, a missing Gemini key, or a missing
  `DATABASE_URL`.
- `db.py` — SQLite/Postgres adapter ported from sayli, thread-safe per handler.
- `app.py` — `/health` and `/config`. `/config` is the single source of truth for
  shared catalogs (journey stages, languages) so clients never hardcode a mirror.
- 6 tests passing; both endpoints verified against a running server.

**Next: P1** (backend core — device auth, accounts, care context), then **P2**,
the clinical safety gate, which blocks all chat work.

## What is deliberately not built yet

There is no chat endpoint, and there will not be one until the safety gate in P2
lands and its adversarial test suite passes. The gate is on the critical path of
every turn, fails closed to Urgent Help, and audits every decision. Shipping chat
first and adding safety later is the one sequencing mistake this project cannot
make.
