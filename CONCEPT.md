# Aira — Product & Design Concept

> **Status:** Draft concept, iterating. Concept not yet finalized.
> **Last updated:** 2026-07-30
> **One-line concept:** *"Aira thinks ahead so you don't have to — but you're always the one who decides."*

Aira is a global, English-first **proactive AI companion** for the maternal journey (trying to conceive → pregnancy → postpartum). This document defines the product & design concept: what "proactive companion" means, the interaction principles, the trust/safety posture, and the signature experiences that should make Aira feel world-class.

It is grounded in two fact-checked research passes (2023–2026 sources). Findings across all four dimensions were adversarially verified (3-vote); remaining gaps (EU MDR, post-Dobbs state law, certifications) are listed under [Open questions](#open-questions--gaps-to-close).

---

## 0. The one decision that anchors everything: autonomy model

**Aira is a proactive companion but its autonomy is deliberately `suggest-only, confirm-to-act`.**

Aira drafts/proposes the next step (create a reminder, prep an appointment, log a check-in) and **nothing is written or executed until the user taps to confirm.** No silent auto-scheduling, auto-logging, or acting on the user's behalf without a tap.

**Why:** For a maternal-health product the user must stay in control of anything touching their care data. Suggest-then-confirm keeps a human in the loop while still letting Aira do the "thinking ahead." It is the conservative, trust-preserving end of the autonomy spectrum vs. "act-then-notify." The research reinforces this: LLM guardrails govern *content*, not *actions* — so governing what Aira *does* requires a separate control, and confirm-to-act is exactly that control.

---

## 1. Agentic AI product design *(verified)*

### Build a bounded agentic *system*, not a maximally-autonomous agent
Anthropic distinguishes *workflows* (LLMs + tools on predefined paths) from *agents* (LLMs directing their own process) and advises "finding the simplest solution possible, and only increasing complexity when needed" — autonomy "trades latency and cost" and risks "compounding errors." In a health context that argues against full autonomy. Compose patterns (routing, orchestrator-workers, evaluator-optimizer) rather than running a free agent.
→ *Anthropic, "Building Effective Agents"*

### The server-side safety gate is a first-class pattern — but it only governs content
The gate maps to the input/output **guardrail** pattern (a cheap/fast model screens every message before the expensive model runs). **Caveat:** guardrails govern *content*, not *actions*. Governing Aira's actions (reminders, appointment edits, partner sharing) needs the separate `confirm-to-act` control above.
→ *OpenAI Agents SDK docs*

### Proactivity is the defining trait — and a design *tension*, not a free win
A proactive agent takes initiative: it anticipates and plans before the user asks. But poorly-designed initiative reads as intrusive. Design against the human-centered PCA taxonomy:
- **Intelligence** — anticipation, initiative, planning
- **Adaptivity** — adjust to the person and moment
- **Civility** — boundary-respect, trust, "right to be forgotten" of nudges
→ *Deng et al., SIGIR 2024 (arXiv:2404.12670)*

### Model the proactive engine as a JITAI (Just-in-Time Adaptive Intervention)
The evidence-based scaffold for check-ins/nudges. A JITAI adapts *type, timing, intensity* of support to the person's changing state, delivered when they need it most **and are most likely to be receptive.** Four buildable components:
1. **Decision points** — when to consider intervening
2. **Intervention options** — what could be offered
3. **Tailoring variables** — the signals that drive the choice
4. **Decision rules** — how signals map to an option
→ *Nahum-Shani et al. 2018, Annals of Behavioral Medicine*

### Receptivity is a distinct construct — model it separately from need
"The individual's transient ability and/or willingness to receive, process, and utilize just-in-time support." Firing a nudge when a tired/postpartum user *can't act on it* causes intervention fatigue and disengagement. **Need ≠ receptivity.**

---

## 2. Proactive-nudge evidence & UX *(verified; honestly mixed)*

### Maternal evidence is real but modest — do not overclaim
- JITAI perinatal intervention (Mothers & Babies-Personalized, 2025, n=100): beat usual care on **postpartum depression (d=0.43)** and **stress (d=0.46)**; **no effect on anxiety.**
- Non-adaptive reminder exercise app (MomZing, 2025, n=99): **no effect** on depression.
- **Adaptivity and contextual relevance — not notification frequency — is the lever.** Just-in-time messages tied to a real moment of need are followed more and rated more relevant; frequent *irrelevant* messages hurt the target behavior. (All four claims the verifiers killed argued the opposite — strengthening this conclusion.)
- Absolute behavior-change efficacy of nudges is **not** established (underpowered field). Proactivity is a well-designed bet, not a proven cure.

### Design for reduced cognitive bandwidth
Anxiety + postpartum sleep deprivation shrink working memory and decision capacity. Therefore:
- **Surface a single next step** (not a feed)
- Progressive disclosure, forgiving defaults
- One-handed use, night-friendly, WCAG AA contrast

### Heavy individual personalization may matter *less* than assumed
One 2024 study found postpartum users valued **stage-appropriate content + human coach feedback** over deep individual personalization (contested, but useful). Implication: **journey-stage content + an expert-review layer can earn trust with less personal data** — which also lightens the consent/privacy burden.

---

## 3. Competitive landscape *(verified — 3-vote adversarial checks)*

The femtech incumbents that failed publicly failed on **data-sharing/consent**; the AI-health companions that struggled failed on the **wellness↔medical-advice boundary**. Both map directly onto Aira's design bets.

- **Flo (100M+ users) — the femtech privacy cautionary tale.** The FTC *alleged* (settled 2021, no admission of liability) that Flo shared sensitive health data — *including the fact of a user's pregnancy* — with Facebook, Google, AppsFlyer and Flurry as "app events" despite privacy promises. The settlement now compels **affirmative consent before sharing health data, an independent privacy review, user notification, and instructing third parties to destroy the data**. (A separate $56M Google/Flo class action settled Sept 2025.) → *Never route health data to ad/analytics SDKs. Pregnancy status is maximally sensitive.* [FTC 2021]
- **Ovia — B2B2C data-flow risk.** Ovia's employer/health-plan model shares personal data with employer health plans, their business associates and benefits vendors; it withholds *health* data from the employer only on **express opt-in**, and lets Facebook collect device/engagement data whether or not you use Facebook login. → *Aira's partner sharing must stay user-initiated, granular, and never silently route health data to employers or ad platforms.* [Ovia policy; Mozilla]
- **Ada — consumer symptom checkers don't match clinicians.** In an ED study, Ada matched physicians' top diagnosis in 30% of cases (top-3: 63%) vs. physicians' 47%/69% — point estimates below clinicians (*not* statistically significant at n=30, so directional only). → *Reinforces Aira's non-diagnostic, suggest-only posture; avoid "what condition do I have" outputs.* [Fraser et al., JMIR 2023]
- **Triage is a *separate* capability from correctness — and it fails dangerously.** In the same study Ada gave an **unsafe under-triage** recommendation in 14% of cases and **ChatGPT-3.5 in 41%** — sometimes correctly identifying a serious condition yet *recommending against* urgent care. → **The single most transferable finding: Aira's safety gate and "next step" suggestions must treat safe triage/escalation as an independent, rigorously tested capability. An under-urgent suggestion in an obstetric emergency is the worst-case failure.** [Fraser et al., JMIR 2023]
- **WHOOP — disclaimers don't immunize a diagnostic function.** The FDA warned WHOOP (July 2025) that its Blood Pressure Insights feature is a medical device requiring 510(k), because BP measurement is "inherently associated with the diagnosis of hypo- and hypertension" *regardless of any disclaimers* (WHOOP disputed; closed out June 2026 after labeling changes). → *The FDA judges function/claim, not fine print. Avoid features whose purpose is to measure/interpret a vital sign toward a diagnosis.* [FDA warning letter]
- **Woebot — even a validated chatbot can be sunk by the device pathway.** Woebot shut down its therapy chatbot (July 2025) — ~1.5M users, 14 RCTs, an FDA Breakthrough Device designation for postpartum depression — with its CEO attributing the shutdown largely to the cost/difficulty of FDA marketing authorization. → *Aira's non-diagnostic, wellness-only, suggest-only positioning is precisely what stays out of the pathway that killed Woebot — but it must be maintained rigorously.* [STAT 2025]
- **Wysa — credibility signals must be scoped precisely.** Wysa's FDA Breakthrough Device Designation is narrow (adults 18+ with chronic musculoskeletal pain + depression/anxiety) and is *expedited review, not clearance or blanket approval*. → *Any regulatory/clinical claim Aira makes must be narrow, accurate, and honest about its limits.* [Wysa; Healio 2022]

## 4. Trust, safety & regulation *(verified — 3-vote adversarial checks)*

- **US: sharing health data without consent is now a *reportable federal breach* — even absent a hack.** The FTC's April 2024 Health Breach Notification Rule (effective July 2024) explicitly covers non-HIPAA health apps and defines a "breach of security" to include unauthorized *disclosures* (e.g., to ad/analytics platforms), not just hacking. → *Data minimization + refusing to feed health data to analytics/ad SDKs is both a trust signal and a compliance necessity.* [FTC 2024] *(Rule passed 3-2; applies to apps qualifying as a "personal health record.")*
- **Crisis handling: the APA (Nov 2025) calls GenAI chatbots "limited and unpredictable" in crises and *mandates* tested escalation.** The advisory documents chatbots encouraging self-harm/suicide, substance use, eating disorders and delusional thinking with vulnerable users, and requires **robust crisis-response protocols and rigorously tested escalation pathways** (988, clickable links, human handoff) triggered when risk is detected. → *Aira's server-side gate on every message is exactly the recommended architecture — it must include tested escalation routing to 988/local crisis lines and human/emergency services, tuned for both self-harm AND obstetric emergencies, and must never try to "manage" a crisis conversationally on its own.* [APA 2025]
- **Disclaimers & positioning: adjunct, never replacement.** The APA requires **clear, prominent, persistent disclaimers that the user is talking to an AI (not a person) that cannot replace a qualified professional**, says there is no scientific consensus these tools match a trained human, and recommends making it illegal for a chatbot to misrepresent itself as a licensed professional. → *Aira's disclaimer pattern: prominent + persistent ("you're talking to Aira, an AI wellness companion — not a doctor or therapist; this isn't medical advice"), never role-play a clinician, and consistently route toward real providers.* [APA 2025]

**Failed verification — do NOT cite (refuted 0-3):** Wysa "more effective than standard orthopedic care / comparable to in-person counseling"; Wysa "31% improvement"; Ovia "preserves data for law enforcement despite deletion"; WHOOP "'medical-grade' language triggered the FDA action"; and the tidy "collecting vs. interpreting data" rule for what counts as a medical device.

---

## Recommended Concept Direction

**The proactive-companion model:** a lightweight JITAI loop wrapped in `suggest-only, confirm-to-act` — not a chatty autonomous agent.

- **Decision points:** journey milestones, logged check-ins, appointment proximity, quiet/receptive windows
- **Tailoring variables:** journey stage, recent mood/symptom check-ins, what's overdue, time of day
- **Intervention options:** a *drafted* next step — never an executed one
- **Decision rules:** fire only when both *need* and *receptivity* are plausibly present; otherwise stay silent (silence is a feature)
- **Architecture:** router + orchestrator-worker workflow; safety gate as input/output guardrail; every state-changing tool gated behind explicit confirmation

### Interaction principles — the "draft" convention
1. **Every proactive suggestion is a visible draft** with **Confirm · Edit · Not now.** Nothing touches care data until Confirm.
2. **One decision per screen.** Today shows a single suggested next step, not a feed.
3. **Every nudge says *why it fired*** ("You logged low sleep 3 days running — want a gentle wind-down reminder?"). This is *Civility* made concrete.
4. **Right-to-be-forgotten for nudges.** One tap to dial a nudge type down or off, permanently. Frequency is opt-*down*; relevance is the default.
5. **Built for depleted users:** forgiving flows, large tap targets, night-friendly (WCAG AA), one-handed reach.

### Trust & safety posture
- Stay **general-wellness, not medical**; keep disclaimers; never diagnose.
- **Safety gate stays server-side** → RED escalates to human/emergency, no AI reply.
- **Privacy as the headline differentiator:** consent-first, export/delete, data-minimizing. The femtech privacy scandals make this valuable — market it.
- **Clinical credibility:** stage content that's expert-reviewed and cited, plus a named advisory board — cheaper-to-trust than deep personalization.

### Signature "agentic" experiences (all suggest → confirm)
1. **The One Next Step** — each day Aira composes a *single* drafted next step from stage + overdue care items + recent check-ins. Confirm / Edit / Not now.
2. **Receptivity-timed check-in** — a gentle mood/energy check fired at a *good moment*, not a fixed alarm. If it signals elevated stress, Aira *proposes* (never auto-creates) a supportive action.
3. **Appointment co-pilot** — ahead of a visit, Aira drafts a question list from logged symptoms + stage; afterward offers to log outcomes. Every item is a draft you approve.
4. **Your Week with Aira (video)** — each new gestational week, Aira surfaces that week's short educational video (from the video catalog) as a suggest-only moment, with a check-in and appointment-prep as optional follow-ons. See *Educational Video Library* below.

### What the evidence says *not* to do
- Don't promise anxiety relief (no trial support).
- Don't lean on notification *frequency*.
- Don't position Aira as therapy.
- Don't over-collect data to personalize when stage-appropriate content earns trust more cheaply.

---

## Educational Video Library (planned feature)

**Source:** an external topic catalog at `C:\Users\pawan\Desktop\Aira-Video-Topic-Catalog` — 100 clinician-review-gated video topics (catalog v1.0.0), each a structured record validated by `video-topic.schema.json`. It's a natural, high-value extension of the proactive-companion model.

**Catalog shape (verified):** 100 topics — 37 pregnancy week-by-week, 12 symptoms, 10 nutrition, 8 movement/wellness, 8 tests/appointments, 10 labour/delivery, 8 postpartum, 7 newborn. Safety: 85 `clinical`, **8 `urgent`**, 7 `standard`. Formats: `weekly_update` (37), `explainer` (47), `safety_explainer` (8), `guided_session` (8). Timing: 37 `gestational_week`, 63 `on_demand`. All bilingual (en/hi). Every record ships `clinical_review: pending` with named specialties and `status: planned`.

**Why it fits Aira (near 1:1):**
- **Week timing ↔ Journey.** `timing.gestational_week` maps directly to Aira's live week countdown (`care.py:current_weeks`). "Your Week N with Aira" anchors the Journey screen.
- **Content model already exists.** `content.py` is journey-keyed, week-banded, draft/published with `reviewed_by`, and *only serves published*. Videos extend this exact paradigm — a `video_topics` store with the same review lifecycle.
- **Personalization inputs ↔ consent-gated context.** `journey_stage`, `gestational_week`, `recent_check_ins`, `care_plan` select which video to surface, using the same consent gate as memory. Stage/week selection needs little personal data (the research's "stage-appropriate beats deep personalization" finding).
- **In-app actions ↔ existing surfaces.** `save_video`, `add_to_care_plan`, `weekly_check_in`, `prepare_appointment`, `ask_aira`, `contact_care_team`, `open_emergency_profile` map onto Aira's tools, chat action cards, and the urgent handoff.

**Where videos surface (all suggest-only, per §0 autonomy model):**
1. **Journey** — the library's home; each stage/week shows its relevant video(s), with the week-by-week video as the centerpiece.
2. **Today** — a timely video can *be* the one next step ("Your Week 24 video is ready" → Watch / Not now), reusing the draft card.
3. **Chat (Aira)** — when a user asks about a topic, Aira offers the matching *approved* video as an action card — never in place of the safety gate.
4. **Learn** — on-demand browse/search of the 63 explainers + saved videos.

**Safety & clinical posture (load-bearing):**
- **The 8 urgent topics** (bleeding, reduced fetal movement, breathlessness, swelling, severe headache, water breaking, postpartum bleeding, newborn danger signs) are `safety_explainer`s that must **route to care, not reassure**: their CTA is `contact_care_team` / `open_emergency_profile` (Aira's existing urgent handoff). If a chat message trips the server-side red gate, the handoff may *also* surface the relevant urgent explainer — but the routing decision stays server-side and the video never replaces the "contact your care team" action. This is exactly the Ada/ChatGPT triage lesson: safe escalation is a capability separate from content.
- **Only published, clinician-approved videos are ever served** — mirroring `content.py`. The review lifecycle (`planned → script_draft → clinical_review → approved → produced → published`) is tracked per topic with named specialties; the admin console gains a "Videos" surface for it, alongside content/prompts.
- **Medical-visual policy:** generative AI may animate *approved* visuals but "must not invent clinical anatomy." Asset generation is gated behind clinical approval — keeping Aira firmly non-diagnostic and out of the regulated-device pathway.

**Data model & API (sketch):**
- `video_topics` table seeded from the catalog (all schema fields + produced asset URLs, captions, and translations once made).
- `GET /v1/videos?journey=&week=&category=` (published only) · `GET /v1/videos/{id}` · `POST /v1/videos/{id}/save` · `GET /v1/videos/saved`. The Today/Journey builders select the timely video.
- **Journey-stage mapping:** catalog `trying_to_conceive | pregnancy | postpartum` → Aira `trying | pregnant | postpartum`; `exploring` users see the on-demand library only.
- i18n by user language (en/hi today); clients cache video *metadata*, stream media, and store saved ids offline.

---

## Open questions & gaps to close
1. **EU MDR / Rule 11 tipping point** — exactly when a maternal wellness app becomes Class I/IIa medical-device software, and where symptom/mood check-ins + "next step" suggestions sit, is still unverified.
2. **Post-Dobbs reproductive-data legal exposure** — specifics of law-enforcement access and US state health-privacy laws (e.g., Washington My Health My Data Act) beyond the general FTC coverage remain unverified.
3. **Which credibility certifications are realistically pursuable** for a non-diagnostic wellness app (ORCHA, NHS DTAC, ISO 27001, IEC 62304) vs. those gated to regulated-device status — only Wysa's FDA designation was verified.
4. **JITAI evidence is thin** (small pilots) — enough to justify the design bet, not to make efficacy claims in marketing. Whether adaptivity adds value over good non-adaptive support, and whether nudging moves anxiety (the trial showed no effect), is unanswerable on current evidence.

---

## Sources (verified findings, Dimensions 1–2)
- Anthropic — *Building Effective Agents* — https://www.anthropic.com/engineering/building-effective-agents
- OpenAI Agents SDK — *Guardrails* — https://openai.github.io/openai-agents-python/guardrails/
- Deng et al., SIGIR 2024 — *Towards Human-centered Proactive Conversational Agents* — https://arxiv.org/pdf/2404.12670
- Nahum-Shani et al. 2018, *Annals of Behavioral Medicine* — JITAI canonical — https://academic.oup.com/abm/article/52/6/446/4733473
- Mothers & Babies-Personalized RCT, 2025 — https://pmc.ncbi.nlm.nih.gov/articles/PMC12702803/
- MomZing RCT, 2025 — https://pmc.ncbi.nlm.nih.gov/articles/PMC12121695/
- Hardeman et al. 2019, JITAI systematic review — https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6448257/
- JMIR Formative Research 2024;e56319 — postpartum UCD/usability — https://formative.jmir.org/2024/1/e56319

## Sources (verified findings, Dimensions 3–4)
- FTC, 2021 — Flo Health fertility-app data-sharing settlement — https://www.ftc.gov/news-events/news/press-releases/2021/01/developer-popular-womens-fertility-tracking-app-settles-ftc-allegations-it-misled-consumers-about
- FTC, 2021 — Flo order finalized — https://www.ftc.gov/news-events/news/press-releases/2021/06/ftc-finalizes-order-flo-health-fertility-tracking-app-shared-sensitive-health-data-facebook-google
- FTC, Apr 2024 — updated Health Breach Notification Rule (covers non-HIPAA health apps) — https://www.ftc.gov/business-guidance/blog/2024/04/updated-ftc-health-breach-notification-rule-puts-new-provisions-place-protect-users-health-apps
- APA, Nov 2025 — health advisory on AI chatbots & wellness apps — https://www.apa.org/topics/artificial-intelligence-machine-learning/health-advisory-chatbots-wellness-apps
- Fraser et al., JMIR mHealth 2023 — Ada / ChatGPT diagnosis & triage accuracy — https://mhealth.jmir.org/2023/1/e49995/
- STAT, Jul 2025 — Woebot therapy chatbot shutdown — https://www.statnews.com/2025/07/02/woebot-therapy-chatbot-shuts-down-founder-says-ai-moving-faster-than-regulators/
- FDA warning letter, Jul 2025 — WHOOP Blood Pressure Insights (closed June 2026) — https://www.fda.gov/inspections-compliance-enforcement-and-criminal-investigations/warning-letters/whoop-inc-709755-07142025
- Wysa — clinical evidence / FDA Breakthrough Device Designation — https://www.wysa.com/clinical-evidence
- Mozilla *Privacy Not Included* — Ovia Pregnancy — https://www.mozillafoundation.org/en/privacynotincluded/ovia-pregnancy/
