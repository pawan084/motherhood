"""FastAPI entry for the Aira backend.

Wires the safety-gated chat, care/consent/memory data routes, the identity
layer, and the admin console. Hardening (auth / rate-limit / upload cap) lives
in `security.py` and is env-driven, so dev runs need zero configuration. The
startup block fails CLOSED in production: it refuses to boot if a signing secret
is still the committed dev default or the app-token gate is unset.
"""
import logging
import os

from dotenv import load_dotenv
# Load this backend's own .env regardless of the process CWD, so `uvicorn app:app`
# run from the repo root (with --app-dir backend) still picks up backend/.env.
# An already-set environment variable always wins over the file (override=False).
load_dotenv(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env"))

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

import accounts
import admin
import analytics_store
import care
import chat
import consent
import content
import feedback
import legal
import memory
import prompts
import safety
import security

logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger("aira")

# Error tracking — a no-op until SENTRY_DSN is set.
if os.environ.get("SENTRY_DSN"):
    import sentry_sdk
    sentry_sdk.init(
        dsn=os.environ["SENTRY_DSN"],
        environment=os.environ.get("ENV", "development"),
        traces_sample_rate=float(os.environ.get("SENTRY_TRACES_RATE", "0")),
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
def _origins(env_var: str, default: str) -> list[str]:
    return [o.strip() for o in os.environ.get(env_var, default).split(",") if o.strip()]

_admin_origins = _origins("ADMIN_ORIGINS", "http://localhost:3001,http://127.0.0.1:3001")
_web_origins = _origins("WEB_ORIGINS", "http://localhost:5173,http://127.0.0.1:5173")
_cors_origins = sorted(set(_admin_origins + _web_origins))
app.add_middleware(CORSMiddleware, allow_origins=_cors_origins,
                   allow_methods=["*"], allow_headers=["*"], allow_credentials=True)

app.include_router(admin.router)
app.include_router(accounts.router)
app.include_router(care.router)
app.include_router(chat.router)
app.include_router(memory.router)
app.include_router(consent.router)
app.include_router(feedback.router)
app.include_router(legal.router)


@app.get("/health")
def health():
    return {"ok": True, "service": "aira", "llm_configured": services_configured()}


def services_configured() -> bool:
    import services
    return services.configured()


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
    prompts.init()
    prompts.seed_defaults()

    # Fail closed in production: signing secrets must not be committed defaults,
    # and the coarse app-token gate must be on (else every data route is open).
    if security.is_production():
        bad = []
        if admin.ADMIN_SECRET == "dev-admin-secret-change-me":
            bad.append("ADMIN_JWT_SECRET")
        if accounts.APP_SESSION_SECRET == "dev-app-session-secret":
            bad.append("APP_SESSION_SECRET")
        if not security.APP_SHARED_SECRET:
            bad.append("APP_SHARED_SECRET")
        if bad:
            raise RuntimeError("Refusing to start in production with insecure/missing "
                               "config: " + ", ".join(bad))
        # Warn (don't block) on integrations that fail closed at runtime anyway.
        if not accounts.GOOGLE_CLIENT_ID:
            log.warning("GOOGLE_CLIENT_ID unset — Google Sign-In will 503 until configured.")
    if not os.environ.get("GEMINI_API_KEY"):
        log.warning("GEMINI_API_KEY unset — chat and the safety classifier will run "
                    "keyword-only until it is configured.")
    log.info("Aira backend ready (env=%s, db=%s)",
             "production" if security.is_production() else "development",
             "postgres" if __import__("db").IS_POSTGRES else "sqlite")
