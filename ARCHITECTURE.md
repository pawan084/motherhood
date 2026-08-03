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

## 8. Identity (P1)

Two credential kinds, deliberately disjoint:

- **Device token** (guest): minted once by `POST /device/register`, an HMAC
  envelope over a server-issued random id. Health data is never addressable by
  a client-named device string — that was a real IDOR in sayli's history, and
  the fix is ported wholesale.
- **Session token** (signed in): HMAC envelope with `sub`/`exp` claims. The two
  claim shapes never cross-validate even under a shared key.

Resolution order for learner routes (`device_auth.resolve_learner`): session →
device token → 401. Bans are enforced on every path, not just `/account/*`.

On sign-in, `link_and_merge` folds the *presented* (proven) device token's data
into the account — never a client-named id — and care-context merge is
freshest-`updated`-wins. Account deletion cascades through every learner-keyed
table for the account id *and* all linked device ids: delete means delete.

One DB rule learned by test: `db.connect()` returns a **shared handle** per
database. Two modules holding separate SQLite connections deadlock the moment a
cross-module transaction spans them (the deletion cascade did exactly that).

## 9. Care context (P1)

`care_context` stores stage + anchor dates (`due_date` when pregnant,
`birth_date` when postpartum) and computes the week index **at read time** — a
stored week goes stale silently, and a wrong gestational week would poison the
Journey content, the chat grounding, and the P2 red-flag context (bleeding at
week 6 ≠ bleeding at week 36). Week 40 falls on the due date, clamped 1..42;
postpartum week 1 is the first seven days. Anchor dates are sanity-bounded at
write time (a due date 5 years out is a typo, not a pregnancy). Changing stage
clears the other stage's anchor so no stale date survives a transition.

## 10. The safety gate, as built (P2)

`safety_taxonomy.py` (data, clinician-reviewable) + `safety.py` (engine).

**Decision vocabulary** — the gate's public contract, consumed by P3 and the
client:

| decision | meaning |
|---|---|
| `urgent` | route to Urgent Help; no AI reply |
| `caution` | reply proceeds, but leads with support + surfaces the care team |
| `ok` | reply proceeds normally |
| `error` | gate could not verify a clean input; **no reply may be generated** — client shows a safe error with the ever-present Urgent Help affordance |

`error` exists so a Gemini outage doesn't force a false choice between crying
wolf (full urgent framing on "which fruits are good?") and silently replying
unscreened. During an outage the reply generation is down anyway; the gate's
job is to make sure nothing pretends otherwise.

**Deterministic floors** (hold no matter what the model says):

- Self-harm terms → `urgent` from the rules alone; the LLM is not consulted and
  nothing on the path touches the network (tested < 50 ms).
- Any non-negated urgent-rule hit → at least `caution`. "What should I do if I
  have bleeding?" may legitimately be a question — the LLM adjudicates urgent
  vs. caution — but it can never render as a plain reply.
- Any caution-rule hit → at least `caution`.
- LLM unavailable: rule hits → `urgent`; caution-only hits → `caution`; clean
  input → `error`. Fail closed in every branch.

**Negation model.** Markers within a ±4-token window route a hit to UNCERTAIN
(LLM adjudicates — never cleared by rules). Two deliberate subtleties, both
found by test: Hindi negates after the noun ("khoon *nahi* aa raha"), so the
window looks both ways; and negation words *inside* an authored term are part
of the danger sign itself ("no kicks", "can't feel the baby" — absence is the
symptom), so the match span is not scanned. Denial phrasings that carry their
own negation ("khoon nahi") are a separate `denial_terms` class in the
taxonomy, routed straight to UNCERTAIN.

**Stage scoping.** Rules declare the journey stages they apply to; an unknown
or missing stage applies every rule — missing context widens the net, never
narrows it.

**Audit.** Every `screen()` writes input, context, decision, rule hits, source,
and latency to `safety_audit`. An audit-write failure never blocks the decision
but logs as an incident-level error.

**The live classifier's judgment** is pinned separately by
`evals/safety_eval.py` — a labeled adversarial set (typos, Hinglish, Hindi
postfix negation, LLM-only catches with no rule hit, third-party reports,
past-resolved symptoms, idioms) run against the REAL rules + REAL Gemini.
First full run (2026-08-03, gemini-2.5-flash-lite): **20/20, zero critical
failures, p95 latency 1.7s** against the 4s budget; results recorded in
`evals/results-*.json`. It bills the key, so it runs on demand, never in the
suite. The taxonomy still requires clinician review before any real user
sees the product.

---

## 11. The chat turn (P3)

Every turn — `/respond` and `/respond_stream` alike — runs one spine:
resolve learner → load care context → **gate** → branch → reply → cards.

- **urgent / error** turns generate NO model reply; the generator is provably
  never called (tested). Urgent Help copy is static and pre-translated
  (en/hi/Hinglish) — that screen never depends on a model call. Self-harm gets
  supportive copy.
- **caution** flips a caution addendum into the system prompt: lead with
  empathy, surface the care team.
- The **trust label** travels with every response and is honest: `error` turns
  say "Safety check unavailable", never "Safety checked". On SSE the gate event
  is emitted FIRST, so no token can render ahead of the safety status.
- **Action cards** are typed against the `CARD_TYPES` catalog (served in
  `/config`), suggested by the small model in a separate call after the reply.
  Contract: `suggest_cards` never raises; a card failure costs the cards, never
  the reply. Unknown types are dropped server-side.
- **Prompts are code, not a DB registry** — sayli's registry rows silently
  shadowed code constants; the override direction gets decided when the P10
  admin needs editable prompts, not implicitly before.
- Reply generation failing after a passed gate surfaces as the same honest
  `error` posture — there is no retry into an unscreened path, and no fallback
  provider by design.

SSE event order: `gate` → `delta`* → `cards`? → `done`; a mid-stream provider
failure emits `error` then `done`.

## 12. Memory (P4)

`memory.py` — small structured facts extracted from the learner's own
messages, never transcripts (chat history is not persisted server-side).

- **A control surface, not a display.** `GET /memory`, `DELETE /memory/{id}`,
  `DELETE /memory`. Deletion is immediate: the next turn's system prompt no
  longer contains the fact (tested end to end through the chat route).
- **Extraction runs only on turns Aira replied to** (ok/caution). Urgent and
  error turns store nothing here — urgent inputs live in the safety audit,
  a different record under different rules.
- Kinds are a closed catalog (fact / concern / symptom / preference),
  enforced twice: in the extractor's validation and again in the store
  (defense in depth against a misbehaving model).
- Near-duplicates refresh their timestamp instead of duplicating; a per-
  learner cap (60) evicts oldest-updated; prompts carry only the freshest 8.
  Memory sharpens the conversation — it must not become a dossier.
- On sign-in, device memories are **reassigned** to the account (not copied);
  account deletion cascades through them.
- On the streaming path, extraction runs after `done` is delivered so it can
  never delay a turn; on both paths `extract_memory` never raises.

## 13. Journey content (P5)

`journey.py` + `seed_journey.py`. Content is **banded** (week ranges), served
per-week: `GET /journey` derives the week from the caller's care context and
resolves it to its band; `?week=` pages without touching the stored context.

Why bands and not 50 per-week rows: each band is a substantial, reviewable
unit — a clinician reviews 16 units instead of 54 thin ones, and the API
shape doesn't change if per-week admin overrides land later. Coverage is
pinned by test: every pregnant week 1–42 resolves; postpartum weeks past the
last band clamp to it instead of erroring.

Seeding is **seed-once** (INSERT ... ON CONFLICT DO NOTHING keyed on band
id) so admin edits and deletions survive restarts — sayli's scenarios lesson,
inherited and tested.

The seed copy is general-knowledge material written for development. It is
flagged HUMAN-GATED like the safety taxonomy: clinician review before real
users. Every band's "prepare" section points worry back to the care team.

## 14. Care (P6)

`care.py` — four owner-scoped resources sharing one module: medicines (+
idempotent taken-today log), documents, appointments, care plan.

- **Documents are PHI.** Files live at `UPLOAD_DIR/{learner}/{doc_id}.{ext}`
  — every path component server-generated, extension whitelist (pdf/jpg/png),
  the shared size cap. A hostile client filename influences only the display
  name (pinned by a traversal test). Downloads are authenticated and go
  through fetch→blob in the client (a bare <a href> would arrive without
  credentials). Local disk is the dev stand-in; production needs object
  storage.
- **The identity flows extend to files**: sign-in merge physically moves
  each document into the account's directory; the deletion cascade removes
  files from disk, not just rows — deleting the row but orphaning the PDF
  would make "delete my account" a lie. Both are tested.
- Deliberately deferred: reminder *delivery* (web push needs a service
  worker + VAPID) and AI document extraction (queued work by design, and it
  needs the Gemini key). Uploads store what the USER says the document is —
  nothing pretends to have read it.

## 15. Privacy controls (P7)

`privacy.py` — consents, export, guest deletion. Three rules shaped it:

1. **A consent must DO something.** `ai_personalisation` off means the chat
   prompt carries no memories AND extraction is skipped (pinned by a test
   that toggles it off mid-conversation and inspects the prompts). The care
   context itself still grounds replies — stage/week is the product, not
   personalisation. Existing memories are retained, not erased, when the
   toggle goes off — collecting and keeping are separate consents; deletion
   remains an explicit act on the memory screen.
2. **Export is complete and own-data-only**: care context, memories, care
   data, consents, and the learner's safety-audit entries (their inputs and
   the decisions made about them — a record they have a right to see).
3. **Guests can delete too**: `DELETE /learner-data` erases everything keyed
   to the resolved learner id, no account required.

Consent merge on sign-in: existing account rows win — an explicit choice is
never overwritten by a merge.

**Open legal question (TODO.md):** safety-audit rows are currently RETAINED
through both deletion paths, on an accountability rationale (they record
escalation decisions). Whether retention is defensible post-deletion, and for
how long, is a data-protection question for the legal review — not something
this codebase should decide silently. It is decided *visibly* here.

Partner access is deferred: it needs a second-party consent flow designed
properly (invitation, scope, revocation), not bolted on.

## 16. Admin + deploy (P10)

`admin.py` — roles are a linear hierarchy (owner > support > content) behind
`require_admin(min_role)`. Passwords are PBKDF2-HMAC-SHA256 (stdlib, per-admin
salt, 600k iterations); tokens are the product's HMAC envelope with a disjoint
`kind="admin"` claim shape — learner sessions can never pass admin auth
(tested). Login is constant-shape: unknown emails hash a dummy attempt so
timing doesn't leak which addresses exist. Bootstrap owner via
`ADMIN_BOOTSTRAP_EMAIL/_PASSWORD` on an empty admins table.

**The safety review queue is why this phase exists**: an escalation system
nobody reviews is theater. `GET /admin/safety-audit` filters by decision +
unreviewed, paginates; reviews live in a separate `safety_reviews` table so
the gate's own record stays append-only; `unreviewed_urgent` is on the
overview as the number that must be zero. Every mutating admin action lands
in `admin_audit` — the reviewers are subject to review too.

The Journey CMS closes P5's loop: `PUT /admin/journey/{band}` edits reach
learners immediately (tested end to end), and seed-once means those edits
survive restarts.

The admin console is a second Vite entry (`/admin.html`) sharing the visual
system; the token lives in sessionStorage. Same origin in dev — production
should serve it from a separate host (TODO.md).

Deploy: `Dockerfile` (slim, libpq, secrets never baked in, `ENV=production`
so the startup guard is armed by default) + `smoke_test.sh` (read-only:
liveness, catalogs, auth posture on learner AND admin routes).

## 17. Voice (P9)

`POST /respond_voice` (audio → transcript → **the same `_text_turn` spine as
/respond**) + `POST /speak` (text → ElevenLabs MP3, LRU-cached per worker).

The one decision that matters: **transcription is a separate Gemini call from
reply generation** — sayli's fused audio→reply optimization is not available
to Aira, because the gate must screen the transcript before any reply exists.
A spoken danger sign escalates exactly like a typed one (tested: same
generator-never-called guarantee).

Silence/noise returns `decision: "empty"` — an invitation to try again, not
an error and not an urgent escalation; the gate is not consulted on an empty
transcript. Transcription failure is the honest `error` posture.

Client: tap-to-toggle mic (MediaRecorder → webm/opus), transcript renders as
the user's bubble, and the reply auto-plays via `/speak` — TTS failure never
costs the text reply.

**Not yet live-verified** (no GEMINI/ELEVENLABS keys): real STT accuracy for
hi/Hinglish, whether Gemini accepts Chrome's webm/opus container (may need a
transcode step — TODO.md), and real TTS quality/voice choice. The wiring
guarantees are pinned by 11 mocked tests, and both fail-closed paths were
live-verified keyless (STT down → honest error; TTS down → 502).

---

## Filled in as phases land
*(all phases landed — P0-P10)*
