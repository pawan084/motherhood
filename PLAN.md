# Aira — Build Plan

**What we're building:** Aira, an AI maternal wellness companion for people who are
trying to conceive, pregnant, or postpartum. Chat-first, with a hard clinical safety
gate on every input and visible consent/privacy controls.

**Decisions locked (2026-08-02):**

| Decision | Choice | Why |
|---|---|---|
| First client | **Web** | A React prototype already exists; no device/toolchain friction; becomes the reference implementation native clients port from. |

> *As built:* Web landed first (P8) and remains the reference implementation. During
> the post-P10 home sprint the **Android** client (Me · Chat · Videos) became the
> lead surface — it is the one verified on real hardware — while the web SPA keeps
> the full five-tab layout and the admin console.
| Scope | **Production-track** | Real auth, consent records, safety audit log, Postgres path, admin console, deploy. Nothing built to be thrown away. |
| Backend | **FastAPI (Python 3.12)** | Ports ~7.2k lines of proven modules from `ref/sayli/backend`. |
| LLM / voice | **Gemini** (LLM + multimodal/STT), **ElevenLabs** (TTS) | Same split as sayli, which shipped it end to end. |

---

## Source material

| Asset | What it is | How we use it |
|---|---|---|
| `ref/mockup.png` | 5-tab product: Today · Aira · Journey · Care · You, plus Urgent Help | The UI spec |
| `ref/diagram.png` | Full flow; **safety gate screens every input** as a first-class node | The behaviour spec |
| `ref/aira-react-web` | 804-line single-file `page.tsx`, **zero `fetch()` calls**, empty Drizzle schema | UI donor for the web client (P8) |
| `ref/AiraAndroid` | 18 Kotlin files, one ViewModel, all local state | Reference for a later native port; source of the en/hi/hinglish language set |
| `ref/sayli` | ~11.9k-line FastAPI backend + SwiftUI + Compose + Next.js admin | **Architecture donor** — see the module map below |

Neither Aira prototype has a backend, AI, or persistence. Both are clickable shells.
All substance comes from sayli's patterns.

---

## Module map

Planned at P0; below is the map **as built** (it drifted from the initial sketch —
there is no `analytics_store.py`/`legal.py`/`documents.py`, and prompts stayed in
code rather than a DB registry).

```
backend/
  app.py             routes + /health + /config      ← port (sayli app.py)
  config.py          env config + prod startup guard ← NEW
  db.py              SQLite -> Postgres adapter       ← port
  security.py        shared-secret auth, rate limit   ← port
  device_auth.py     guest device tokens             ← port
  accounts.py        sign-in, HMAC sessions           ← port
  care_context.py    stage, week index, facts         ← NEW
  safety.py          clinical red-flag triage gate    ← NEW
  safety_taxonomy.py the red-flag taxonomy as data    ← NEW
  chat.py            /respond (+ SSE) + voice turns   ← NEW
  prompts.py         prompt constants (code, not DB)  ← NEW
  services.py        Gemini glue (reply + cards)      ← port
  memory.py          "what Aira remembers"            ← port (sayli learner memory)
  journey.py         week-indexed content             ← NEW
  care.py            medicines, docs, appts, plan     ← NEW
  wellness.py        moods, reminders, videos, report ← NEW
  today.py           the proactive Me / home feed     ← NEW
  privacy.py         consents, export, deletion       ← NEW
  admin.py           admin API + safety review queue  ← port
  demo.py            demo personas (dev only)         ← NEW
  seed_*.py          journey / tips / videos seeds    ← NEW

web/                 React 19 SPA (reference client)  ← from ref/aira-react-web
android/             Kotlin/Compose app (lead client) ← from ref/AiraAndroid
```

---

## Phases

### P0 — Skeleton + docs
Repo, `git init`, toolchain (`uv` + Python 3.12; system Python is 3.9), `.env.example`,
and the doc set. Sayli's doc discipline (HANDOFF / ARCHITECTURE / TODO kept in sync in
the same commit as the change) is why sayli was resumable and this folder was not.

### P1 — Backend core
Health + config, device-token guest auth, accounts, schema, and the **care context**
model: journey stage (TTC / pregnant / postpartum), week index, profile facts.
Everything downstream reads care context.

### P2 — The safety gate  ⚠️ blocks P3
The diagram makes this a first-class node, and it is the one component where being
wrong hurts a real person.

- Deterministic red-flag rules: bleeding, severe headache / vision change
  (preeclampsia), reduced fetal movement, fever, PPD / suicidal ideation.
- Plus an LLM classifier for what rules miss.
- **Fails closed** to Urgent Help — an LLM error escalates, it does not pass through.
- Every gate decision written to an audit table.
- The adversarial test suite is the deliverable, not an afterthought.

**No chat endpoint ships before this does.**

### P3 — Aira chat
`/respond` + SSE streaming (port `stream_reply_sentences`). Care-context-grounded
prompts. The "Safety checked" trust label is backed by a real gate result, never
cosmetic. Dynamic action cards as typed tool-call outputs: check-in, reminder,
Appointment Copilot, upload, wellness session, mood/sleep/symptom log, partner task.

### P4 — Memory + care context
Persist facts, symptoms, meds, appointments. "What Aira remembers" must support
**read and delete**, not read alone.

### P5 — Journey
Week-indexed content per stage (body this week / baby this week / prepare for visit),
CMS-seeded and admin-authorable.

### P6 — Care
Medicines, reminders, documents (upload → **queued** extraction), care plan, and an
**offline** emergency profile.

### P7 — You
Privacy and consent records, voice & language (en / hi / hinglish), partner access,
data export and delete.

### P8 — Web client
Refactor the prototype's single 804-line `page.tsx` into components, replace mock
state with real API calls, implement Urgent Help end to end.

### P9 — Voice loop
Port push-to-talk + Gemini multimodal + ElevenLabs TTS. Avatar is optional and last.

### P10 — Admin + deploy
Content CMS, a **safety-gate review queue**, Postgres, `REDIS_URL`, job queue,
secrets guard, smoke test.

---

## Traps sayli already paid for

Inherit the fixes, not the bugs. Each of these cost sayli a debugging session.

1. **SQLite is ephemeral and single-instance.** Postgres from the start for
   production-track; `analytics.db` is gitignored so every machine starts empty.
2. **Prompt-registry DB rows shadow code constants.** Changing a prompt in code does
   nothing on an existing install until the row is updated or deleted. Decide the
   override direction deliberately and document it.
3. **Hardcoded catalog mirrors drift.** Sayli's admin `languages.ts` drifted the day
   Italian was added. Single-source Aira's catalogs (stages, languages, red-flag
   taxonomy) from one backend endpoint on day one.
4. **No job queue.** Sayli ran 15-second course generation inline in the request
   handler. Aira's document extraction will hit this immediately — queue it from the
   start.
5. **Rate limiting and caches are per-worker** unless `REDIS_URL` is set.
6. **Human-gated items discovered late** cost sayli the most. Surface them in TODO.md
   at P0, not P9 — and for a health app that adds privacy policy, data-processing
   review, and app-store health-claims compliance.

---

## Non-negotiables

- The safety gate runs on **every** user input, including voice, before any reply is
  shown.
- The gate fails **closed**. Provider errors escalate to Urgent Help.
- Health data is never used for advertising (stated in the mockup — hold to it).
- "What Aira remembers" is readable and deletable by the user.
- Aira gives no diagnosis. Prompts enforce escalate-don't-diagnose.
