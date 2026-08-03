"""Environment configuration + the production startup guard.

The guard exists because sayli shipped a build where a committed default secret
would have reached production. In `ENV=production` this module refuses to boot on
any placeholder value rather than logging a warning nobody reads.
"""
import os

ENV = os.environ.get("ENV", "development").lower()
IS_PRODUCTION = ENV == "production"

# ── Providers ────────────────────────────────────────────────────────────────
# Gemini does every LLM call (chat, the safety classifier, multimodal audio
# turns). ElevenLabs does TTS. There is no fallback provider by design: a silent
# degradation in a health context is worse than a visible error.
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
ELEVENLABS_API_KEY = os.environ.get("ELEVENLABS_API_KEY", "")

# Pinned deliberately. Sayli's lesson: a fresh key's project can have zero free
# quota on an entire model generation, so probe before pinning a new one.
GEMINI_MODEL = os.environ.get("GEMINI_MODEL", "gemini-2.5-flash")
# The safety classifier is on the critical path of every turn and must be fast.
# It may be a smaller model than the chat model, never a larger one.
GEMINI_SAFETY_MODEL = os.environ.get("GEMINI_SAFETY_MODEL", "gemini-2.5-flash-lite")

# ── Secrets ──────────────────────────────────────────────────────────────────
_DEV_PLACEHOLDER = "dev-insecure-change-me"
APP_SESSION_SECRET = os.environ.get("APP_SESSION_SECRET", _DEV_PLACEHOLDER)
DEVICE_TOKEN_SECRET = os.environ.get("DEVICE_TOKEN_SECRET") or APP_SESSION_SECRET
ADMIN_JWT_SECRET = os.environ.get("ADMIN_JWT_SECRET", _DEV_PLACEHOLDER)

# ── Storage ──────────────────────────────────────────────────────────────────
DATABASE_URL = os.environ.get("DATABASE_URL", "")
REDIS_URL = os.environ.get("REDIS_URL", "")
UPLOAD_DIR = os.environ.get("UPLOAD_DIR") or os.path.join(os.path.dirname(__file__), "uploads")

# ── Safety ───────────────────────────────────────────────────────────────────
# Milliseconds the LLM classifier gets before the gate gives up and escalates.
# The gate FAILS CLOSED: a timeout routes to Urgent Help, it does not pass the
# message through to chat.
SAFETY_LLM_TIMEOUT_MS = int(os.environ.get("SAFETY_LLM_TIMEOUT_MS", "4000"))

# Milliseconds a voice transcription gets. Found live: on silence Gemini can
# spin for minutes generating a hallucination loop — the request must be
# bounded, and a timeout surfaces as the honest transcription error.
TRANSCRIBE_TIMEOUT_MS = int(os.environ.get("TRANSCRIBE_TIMEOUT_MS", "20000"))

# ── Observability ────────────────────────────────────────────────────────────
SENTRY_DSN = os.environ.get("SENTRY_DSN", "")

SUPPORTED_LANGUAGES = ("en", "hi", "hi-Latn")  # English, Hindi, Hinglish
JOURNEY_STAGES = ("trying_to_conceive", "pregnant", "postpartum")


class ConfigError(RuntimeError):
    """Raised at import/startup when production config is unsafe."""


def verify_production_config() -> None:
    """Refuse to boot a production instance on insecure or missing config.

    Called from app startup. Raising here is intentional — a health app running
    with a known-public session secret is worse than a health app that is down.
    """
    if not IS_PRODUCTION:
        return

    problems: list[str] = []

    for name, value in (
        ("APP_SESSION_SECRET", APP_SESSION_SECRET),
        ("DEVICE_TOKEN_SECRET", DEVICE_TOKEN_SECRET),
        ("ADMIN_JWT_SECRET", ADMIN_JWT_SECRET),
    ):
        if not value or value == _DEV_PLACEHOLDER:
            problems.append(f"{name} is unset or still the committed dev placeholder")

    if not GEMINI_API_KEY:
        problems.append("GEMINI_API_KEY is unset — the safety gate cannot run")

    if not DATABASE_URL:
        problems.append(
            "DATABASE_URL is unset — production must not run on SQLite "
            "(single-instance, and the file is ephemeral on most hosts)"
        )

    if problems:
        raise ConfigError(
            "Refusing to start in ENV=production:\n  - " + "\n  - ".join(problems)
        )
