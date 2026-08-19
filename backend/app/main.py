"""FastAPI entry for the Aira backend — `uvicorn app.main:app`.

Wires the safety-gated chat, care/consent/memory data routes, the identity
layer, and the admin console. Hardening (auth / rate-limit / upload cap) lives
in `core/security.py`; every setting it reads lives in `config.py`, so dev runs
need zero configuration. The startup block fails CLOSED in production: it
refuses to boot if a signing secret is still the committed dev default or the
app-token gate is unset.

Importing `config` first is deliberate — it is what loads `.env`, so it must run
before any module snapshots a setting.
"""
import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app import admin, analytics_store, config, legal, privacy, prompts, safety
from app.core import db, llm, security
from app.domains import (
    accounts, care, chat, consent, content, feedback,
    memory, partner, prefs, videos,
)

logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger("aira")

# Error tracking — a no-op until SENTRY_DSN is set.
if config.SENTRY_DSN:
    import sentry_sdk
    sentry_sdk.init(
        dsn=config.SENTRY_DSN,
        environment=config.ENV or "development",
        traces_sample_rate=config.SENTRY_TRACES_RATE,
        send_default_pii=False,   # sensitive health content stays out of error reports
    )
    log.info("Sentry error tracking enabled")

app = FastAPI(title="Aira Backend")
app.add_middleware(security.RateLimitMiddleware)

# CORS for the two browser clients (native mobile apps don't need it):
#   ADMIN_ORIGINS — the admin console (credentialed: httpOnly session + CSRF cookie)
#   WEB_ORIGINS   — the consumer web app (Bearer token, no cookies)
# Both are served from an explicit allow-list (no "*") because the middleware runs
# with allow_credentials=True — which the admin cookie flow requires, and which is
# harmless for the web app's non-credentialed requests. Set these to your deployed
# URL(s) in production; the defaults cover local dev (admin :3001, Vite :5173).
_cors_origins = sorted(set(config.ADMIN_ORIGINS + config.WEB_ORIGINS))
app.add_middleware(CORSMiddleware, allow_origins=_cors_origins,
                   allow_methods=["*"], allow_headers=["*"], allow_credentials=True)

app.include_router(admin.router)
app.include_router(accounts.router)
app.include_router(care.router)
app.include_router(chat.router)
app.include_router(memory.router)
app.include_router(consent.router)
app.include_router(feedback.router)
app.include_router(prefs.router)
app.include_router(partner.router)
app.include_router(videos.router)
app.include_router(privacy.router)
app.include_router(legal.router)


@app.get("/health")
def health():
    return {"ok": True, "service": "aira", "llm_configured": llm.configured()}


@app.on_event("startup")
def _startup():
    analytics_store.init()
    accounts.init()
    admin.init()
    content.init()
    memory.init()
    consent.init()
    safety.init()
    feedback.init()
    care.init()
    chat.init()
    prefs.init()
    partner.init()
    videos.init()
    prompts.init()
    prompts.seed_defaults()

    # Enforce the safety-flag retention window on boot, so the policy holds
    # without depending on an external cron being wired up.
    safety.purge_expired()

    # Fail closed in production: signing secrets must not be committed defaults,
    # and the coarse app-token gate must be on (else every data route is open).
    # The check itself lives in config.py, beside the values it is checking.
    if config.is_production():
        bad = config.insecure_production_config()
        if bad:
            raise RuntimeError("Refusing to start in production with insecure/missing "
                               "config: " + ", ".join(bad))
        # Warn (don't block) on integrations that fail closed at runtime anyway.
        if not config.GOOGLE_CLIENT_ID:
            log.warning("GOOGLE_CLIENT_ID unset — Google Sign-In will 503 until configured.")
    if not config.GEMINI_API_KEY:
        log.warning("GEMINI_API_KEY unset — chat and the safety classifier will run "
                    "keyword-only until it is configured.")
    log.info("Aira backend ready (env=%s, db=%s)",
             "production" if security.is_production() else "development",
             "postgres" if db.IS_POSTGRES else "sqlite")
