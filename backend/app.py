"""Aira API — application entry point.

Route modules are mounted here as they land (see PLAN.md for the phase order).
P0 ships the two endpoints everything else depends on: `/health` for liveness and
`/config` for the shared catalogs.

`/config` exists to solve a specific problem sayli hit: it kept the same catalog
hardcoded in three codebases, and they drifted the day a new language was added.
Every client reads its catalogs from here. Nothing is duplicated client-side
except as a documented offline fallback.
"""
import logging
import os
from contextlib import asynccontextmanager

from dotenv import load_dotenv

load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))

# Imported after load_dotenv so module-level config reads the .env values.
import config  # noqa: E402
import accounts  # noqa: E402
import care  # noqa: E402
import care_context  # noqa: E402
import chat  # noqa: E402
import device_auth  # noqa: E402
import journey  # noqa: E402
import memory  # noqa: E402
import prompts  # noqa: E402
import safety  # noqa: E402
import security  # noqa: E402
from fastapi import FastAPI  # noqa: E402
from fastapi.middleware.cors import CORSMiddleware  # noqa: E402

log = logging.getLogger("aira")


@asynccontextmanager
async def lifespan(_app: FastAPI):
    # Raises in ENV=production on placeholder secrets, a missing Gemini key, or
    # a missing DATABASE_URL. Failing to boot is the intended outcome.
    config.verify_production_config()
    accounts.init()
    care.init()
    care_context.init()
    journey.init()
    memory.init()
    safety.init()
    log.info("Aira API starting (env=%s, postgres=%s)", config.ENV, bool(config.DATABASE_URL))
    yield


app = FastAPI(title="Aira API", version="0.1.0", lifespan=lifespan)

# Middleware order: `add_middleware` wraps, so LAST added runs OUTERMOST.
# Rate limiting first, CORS second → CORS is outermost, so even a 429 carries
# the CORS headers a browser needs to surface the error to the web client.
app.add_middleware(security.RateLimitMiddleware)
# The web client is served from a different origin in dev (Vite) and may be in
# production too. Tightened to an allowlist before launch — see TODO.md.
app.add_middleware(
    CORSMiddleware,
    allow_origins=os.environ.get("CORS_ORIGINS", "http://localhost:5173").split(","),
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(device_auth.router)
app.include_router(accounts.router)
app.include_router(care.router)
app.include_router(care_context.router)
app.include_router(chat.router)
app.include_router(journey.router)
app.include_router(memory.router)


@app.get("/health")
def health() -> dict:
    return {"ok": True}


@app.get("/config")
def get_config() -> dict:
    """Shared catalogs. The single source of truth for every client.

    `safety_gate_enabled` is reported so a client can refuse to render chat if
    the gate is somehow off, rather than silently offering ungated AI replies.
    """
    return {
        "journey_stages": list(config.JOURNEY_STAGES),
        "languages": list(config.SUPPORTED_LANGUAGES),
        "card_types": list(prompts.CARD_TYPES),
        "safety_gate_enabled": True,
        # Aira gives no diagnosis. Clients surface this next to AI replies.
        "ai_disclaimer": (
            "Aira offers general information and support, not medical advice. "
            "Contact your care team about anything that worries you."
        ),
    }
