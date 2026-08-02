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
- [x] Red-flag taxonomy as **data** (`safety_taxonomy.py`) — reviewable without
      reading Python; en / hi / Hinglish terms; `denial_terms` class for
      negation-carrying phrasings.
- [x] Deterministic rules run first; self-harm escalates with no network on the
      path (< 50 ms, tested).
- [x] LLM classifier (Gemini, JSON decision, temperature 0) for what rules miss.
- [x] **Fail-closed** in every branch: rule hits + LLM down → urgent; clean
      input + LLM down → `error` (no reply may be generated).
- [x] Audit table `safety_audit`: every input, decision, rule hits, rationale,
      latency.
- [x] Adversarial tests (28): negation incl. Hindi postfix, absence-as-symptom
      terms, floors, timeout, invalid model output, stage scoping, audit trail.
- [x] Latency budget: `SAFETY_LLM_TIMEOUT_MS` (default 4000), enforced by a
      worker-thread timeout.
- [ ] **Live-classifier evals** with recorded Gemini fixtures (reported speech,
      past tense, typos — the nuance the mocked suite can't pin). Needs a
      GEMINI_API_KEY; build the eval harness when it exists.
- [ ] Tune `GEMINI_SAFETY_MODEL` + measure real p95 gate latency once a key
      exists.

### P8 — Web client
- [x] Vite + React SPA wired to the backend (device bootstrap, onboarding-in-
      chat, SSE chat with gate routing, offline emergency profile).
- [x] Node integration smoke over the real bundled `api.ts` against the live
      backend — 13/13.
- [ ] **In-browser click-through pending**: the Claude-in-Chrome extension
      can't reach localhost (site permission not granted for local addresses),
      so the UI was verified by typecheck/build + the API-layer smoke, not by
      driving the rendered app. Either grant the extension localhost access or
      click through manually: `npm run dev -- --host` in `web/` + backend on
      :8001.
- [ ] Vite binds IPv6-only by default on this machine — `--host` is needed for
      IPv4 loopback (Chrome resolves 127.0.0.1 first).
- [ ] CORS allowlist for the production origin before deploy (`CORS_ORIGINS`).

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
