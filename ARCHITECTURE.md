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

**Not verified by these tests:** the live classifier's judgment. The suite pins
the deterministic guarantees with the LLM mocked; recorded-fixture evals
against real Gemini output are tracked in TODO.md, and the taxonomy still
requires clinician review before any real user sees the product.

---

## Filled in as phases land
- **P3** — chat turn pipeline, streaming, action-card tool schema
- **P9** — voice loop
