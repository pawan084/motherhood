# Aira — TODO / Known Gaps

Living list, split by who can do it. Started **2026-08-02** at P0.

Sayli's most expensive lesson was discovering human-gated items at the end of the
build. They are listed **first** here, at P0, so they can be started in parallel with
the code.

---

## Human-gated — start these now, they have lead times

### Regulatory / legal (a health app; these gate launch, not polish)
- [ ] **Privacy policy + terms** drafted and reviewed, covering health data
      specifically. The mockup promises "Your health data is never used for
      advertising" — that promise has to appear in the policy and hold in the code.
- [ ] **Data-processing review**: what we store, where, for how long, and who can
      reach it. Care context, chat transcripts, uploaded prescriptions/reports, and
      safety-gate audit rows are all health data.
- [ ] **Jurisdiction call**: India (DPDP Act) and/or US (HIPAA applies only with a
      covered entity relationship, but state health-privacy laws may still bite) —
      decide the target market before the schema hardens, because it changes
      retention and residency.
- [ ] **Clinical review of the red-flag taxonomy** (P2) by someone qualified. The
      rules can be written from published obstetric guidance, but shipping them to
      real pregnant users without a clinician signing off is not acceptable.
- [ ] **Emergency-contact policy**: what "Call care team" does when no care team is
      configured, and the fallback for each supported country.
- [ ] App-store **health-claims compliance** review (both stores restrict medical
      claims; Aira must present as wellness + escalation, not diagnosis).

### Accounts / infrastructure
- [ ] Gemini + ElevenLabs API keys, with **hard monthly spend caps** set in both
      dashboards. A leaked key spends real money.
- [ ] Production Postgres provisioned (`DATABASE_URL`); uncomment `psycopg[binary]`
      in `backend/requirements.txt` when it exists.
- [ ] Redis provisioned (`REDIS_URL`) — without it, rate limiting and caches are
      per-worker.
- [ ] Object storage for uploaded documents (local disk is a P0 stand-in only).
- [ ] Sentry project + `SENTRY_DSN`.
- [ ] Domain + TLS for the API and the web client.

---

## Open by phase

### P0 — Skeleton
- [x] `git init`, directory layout, `.gitignore` (health data + uploads excluded)
- [x] PLAN.md with locked decisions and the module map
- [ ] backend skeleton (`uv` + Python 3.12 — system Python is 3.9.6)
- [ ] `.env.example`
- [ ] ARCHITECTURE.md, HANDOFF.md, README.md

### P2 — Safety gate (the one that matters)
- [ ] Red-flag taxonomy as **data**, not scattered `if` statements, so a clinician
      can review it without reading Python.
- [ ] Deterministic rules run first and independently of the LLM.
- [ ] LLM classifier for what the rules miss.
- [ ] **Fail-closed** on any provider error → Urgent Help.
- [ ] Audit table: every input, gate decision, and rationale.
- [ ] Adversarial tests: negation ("no bleeding"), reported speech ("my friend had
      bleeding"), past tense ("I bled last month"), hypotheticals, non-English and
      Hinglish phrasing, and typos. False negatives are the dangerous direction.
- [ ] Decide the **latency budget** — the gate is on the critical path of every turn.

### Carried from sayli's TODO (do not repeat these)
- [ ] Single-source shared catalogs (stages, languages, red-flag taxonomy) from one
      backend endpoint. Sayli kept three hardcoded mirrors and they drifted.
- [ ] Queue long-running work (document extraction) from the start — sayli ran a
      15-second generation inline in the request handler and never fixed it.
- [ ] Document the prompt-registry override direction. In sayli, DB rows silently
      shadow code constants, so editing a prompt in code does nothing on an existing
      install.
- [ ] Decide dark mode early. Sayli forced `.light` app-wide and never revisited it.

---

## Docs upkeep

When behaviour changes — especially the safety gate, consent, or memory — update the
matching doc section **in the same commit**. Sayli's docs stayed useful because of
this rule; this folder sat unresumable for a week because nothing was written down.
