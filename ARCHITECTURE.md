# Aira — Architecture

## Overview

Four clients, one backend. The backend is the source of truth for identity,
care data, and — critically — the **safety gate**. Clients are thin: they render
state and call the API; they never make the safety decision themselves.

```
┌──────────┐   ┌──────────┐   ┌───────────┐
│ web (Vite)│   │ android  │   │  admin    │
└────┬─────┘   └────┬─────┘   │ (Next 14) │
     │ Bearer app   │ Bearer  └────┬──────┘
     │ session      │ session      │ cookie + CSRF
     └──────┬───────┴──────────────┘
            ▼
     ┌─────────────────────────────────────────┐
     │  FastAPI backend                         │
     │  security (rate-limit, app-token, caps)  │
     │  accounts · care · chat · memory ·       │
     │  consent · feedback · content · admin    │
     │  safety ──▶ services ──▶ Google Gemini   │
     │  db (SQLite ⟷ Postgres)                  │
     └─────────────────────────────────────────┘
```

## Auth model

Two independent layers, both env-driven:

1. **Coarse edge gate** — `X-App-Token` shared secret (`security.require_app_token`).
   No-op in dev; required in production. Rejects traffic that isn't from our apps.
2. **Identity** — a per-user HMAC-signed **app-session token** (`accounts.py`):
   - First run → `POST /device/register` mints an anonymous user + token.
   - `POST /account/google` verifies a Google ID token (JWKS signature, `aud`,
     `iss`, `exp`) and upserts an account, merging the device's care data.
   - `current_user` verifies the token's HMAC **and** the user's `token_version`
     (so a server-side revoke invalidates outstanding tokens).

The **admin console** uses a separate, hardened scheme (`admin.py`): PBKDF2
(600k iters) passwords, HMAC-signed httpOnly session cookie, CSRF double-submit,
account-keyed login throttling, sliding idle + absolute session caps, RBAC
(viewer < support < owner), and an audit log.

## The safety gate (`safety.py`)

The one piece the prototypes each half-built. `screen(message, history)`:

1. **Keyword floor** — deterministic phrase lists (EN + Hindi/Hinglish) →
   green/amber/red. Runs with no network, so it protects users even if the LLM
   is misconfigured or down (fail-safe).
2. **Gemini classifier** — the admin-editable `aira.safety_classifier` prompt.
3. **Combine toward caution** — final level is the *more urgent* of the two.

`chat.py` calls the gate first on every turn. **Red** short-circuits: no reply is
generated; the client gets `urgent_help` with a real care-team number from the
emergency profile. **Green/amber** produce a journey-grounded reply carrying a
trust label (`wellness`/`watchful`) and at most one action card. Amber/red
screens are persisted to `safety_flags` for admin review, with a `degraded` flag
when only the keyword floor ran (a silent classifier outage becomes visible).

## Journey-awareness

Content is keyed by journey (`content.py`), and pregnancy copy is week-banded.
Onboarding persists the user's journey/priorities; `Today`/`Journey` assemble
from journey + state. A postpartum user never sees pregnancy-week cards — fixing
the prototypes' hardcoded "everyone is 24-weeks-pregnant" persona at the source.

## Consent & memory

- **Consent** (`consent.py`) is an append-only ledger; `require_consent(feature)`
  is a dependency that 403s before any processing — so consent-gated features
  (e.g. the future-baby story) can't collect data before consent.
- **Memory** (`memory.py`) only feeds *approved* items into the reply prompt, so
  the "AI personalisation" toggle and "forget" are real, not cosmetic.

## Storage

`db.py` is a portable connection helper: SQLite by default (WAL, per-thread
connections), Postgres when `DATABASE_URL` is set. All module SQL uses `?`
placeholders and `ON CONFLICT` so it runs unchanged on either backend.

## Data flow: one chat turn

```
client POST /v1/chat/turn {message, history}
  └─ require_app_token (coarse) + current_user (identity)
  └─ safety.screen ──▶ record flag (amber/red)
       ├─ red   ─▶ care.emergency_profile ─▶ urgent_help payload   (no LLM)
       └─ other ─▶ prompts.fill(system, journey, trust_label)
                    + memory.context_summary (approved only)
                    ─▶ services.gemini_json ─▶ {reply, action_card}
  └─ persist chat_turns ─▶ response
```
