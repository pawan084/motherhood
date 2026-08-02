# Aira — Architecture

Design and the reasoning behind it. Written at P0 to steer P1–P3; sections are
filled in as each phase lands rather than after the fact.

---

## 1. Shape

```
  web client (React 19)
        │  HTTPS/JSON + SSE
        ▼
  FastAPI  ──► safety gate ──┬─► urgent help  (deterministic + LLM, fails closed)
        │                    └─► Gemini ──► reply + typed action cards
        │                                      │
        │                                 ElevenLabs TTS (P9)
        ▼
  SQLite (dev) / Postgres (prod)
```

One backend, many clients. The web client is first and becomes the reference
implementation a native client ports from — the same order sayli used for its
iOS→Android port, which worked.

## 2. Why FastAPI and not the prototype's Cloudflare/D1 stack

`ref/aira-react-web` ships a Vinext + Cloudflare Workers + D1 scaffold, but its
`db/schema.ts` is empty and `page.tsx` makes zero network calls — there is no
backend to keep, only a build setup. Meanwhile `ref/sayli/backend` is ~11.9k
lines of Python that already solves this product's hard parts: guest device
tokens, account merge, a DB-backed prompt registry, per-turn memory enrichment,
streaming replies, an admin API, and a SQLite→Postgres path. Porting Python is
cheaper than rewriting those patterns in TypeScript, so the web app stays a
client and the API is Python.

## 3. The safety gate

The single most important component. `ref/diagram.png` places it on every input,
before any reply is shown.

**Layered, and the layers are independent:**

1. **Deterministic rules** — a reviewable red-flag taxonomy held as *data*, not
   scattered conditionals, so a clinician can audit it without reading Python.
   Covers bleeding, severe headache and vision changes (preeclampsia), reduced
   fetal movement, fever, and PPD/suicidal ideation.
2. **LLM classifier** — catches phrasing the rules miss, in English, Hindi, and
   Hinglish.

**Fails closed.** A provider timeout or error routes to Urgent Help. It never
passes the message through to chat. This is the opposite of the usual default and
it is deliberate: the failure mode of over-escalating is an unnecessary phone
call, and the failure mode of under-escalating is a missed obstetric emergency.

**Audited.** Every input, decision, and rationale is written to an audit table in
the same transaction as the decision, so a reviewer can reconstruct why any given
turn was or was not escalated.

**On the critical path.** The classifier runs on a smaller, faster model than
chat, with an explicit timeout budget (`SAFETY_LLM_TIMEOUT_MS`).

The "Safety checked" chip in the mockup is backed by a real gate result. It is
never cosmetic.

## 4. Care context

The state everything else reads: journey stage (trying to conceive / pregnant /
postpartum), week index, and accumulated profile facts. The chat prompt, the
Journey tab's week content, and the Today tab's single next action are all
derived from it, so it gets its own module rather than living inside the chat
handler.

## 5. Data and privacy

The database holds health data. That drives several choices that would otherwise
look like over-engineering:

- SQLite is a **dev-only** default; the production guard refuses to boot without
  `DATABASE_URL`.
- `*.db` and `backend/uploads/` are gitignored — committing either is a
  disclosure, not a style nit.
- "What Aira remembers" is **readable and deletable** by the user, which means
  memory rows carry enough provenance to be individually removed.
- The mockup promises health data is never used for advertising. That promise
  constrains the analytics schema: event rows carry no free-text health content.

## 6. Single-sourced catalogs

`/config` serves journey stages, languages, and the disclaimer. Clients do not
hardcode mirrors. This is a direct response to a sayli bug: the same catalog
lived in three codebases and drifted the day a new language was added.

## 7. Conventions

- Modules import flat (`import config`, `import db`) — matches sayli, keeps SQL
  and call sites portable between the two codebases.
- All SQL uses `?` placeholders and `ON CONFLICT … DO UPDATE/NOTHING` so it runs
  unchanged on both SQLite and Postgres.
- Handlers run in threads; each thread gets its own DB connection
  (`db._ThreadLocalConn`). Never share a cursor across threads.
- Multi-statement writes that must not half-apply use `conn.transaction()`.

---

## Filled in as phases land

- **P1** — auth model, schema, care context tables
- **P2** — the red-flag taxonomy and the gate's decision contract
- **P3** — chat turn pipeline, streaming, action-card tool schema
- **P9** — voice loop
