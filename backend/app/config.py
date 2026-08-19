"""Every environment variable the backend reads, in one place.

Before this existed, 35 `os.environ` calls were spread across ten modules and
all of them ran at IMPORT time. Two things followed from that, both bad:

  - `tests/conftest.py` opens with "Must be set BEFORE app/db import", because
    importing anything froze that module's configuration. Get the order wrong
    and the failure is a test reading the developer's real database.
  - the fail-closed production check in `main.py` had to reach into three other
    modules' globals to find out what they had decided, a hundred lines from
    where the values were read.

Reading them here does not make configuration dynamic — these are still
snapshotted at import. It makes the snapshot happen ONCE, somewhere a person can
read the whole of it, and it gives the production check the values directly.

Every value degrades to a safe development default, so `uvicorn app.main:app`
runs with zero configuration. `ENV=production` turns on the checks in
`insecure_production_config()`, which `main.py` refuses to boot past.
"""
import os
import pathlib

from dotenv import load_dotenv

# ── where things are ─────────────────────────────────────────────────────────
#
# `backend/` — the directory holding this package, `.env`, `data/`, and (by
# default) the SQLite file and uploaded documents. Derived from this file's own
# location rather than the CWD, so `uvicorn app.main:app` works from the repo
# root, from `backend/`, or from inside a container, and so a tool run out of
# `tools/` resolves the same paths as the server does.
#
# This is not decoration: moving the modules into a package silently repointed
# three `__file__`-relative paths into the package directory. The video
# catalogue stopped loading and uploaded documents would have been written
# somewhere nobody looks. One anchor, computed once, is the fix.
BASE_DIR = pathlib.Path(__file__).resolve().parent.parent

# An already-set variable always wins over the file (override=False), so the
# platform's environment beats a stray local .env.
load_dotenv(BASE_DIR / ".env")


def _int(name: str, default: int) -> int:
    raw = os.environ.get(name, "").strip()
    return int(raw) if raw else default


def _flag(name: str) -> bool:
    return os.environ.get(name, "").strip().lower() in ("1", "true", "yes")


def _csv(name: str, default: str) -> list[str]:
    return [o.strip() for o in os.environ.get(name, default).split(",") if o.strip()]


# ── environment ──────────────────────────────────────────────────────────────

ENV = os.environ.get("ENV", "").strip().lower()


def is_production() -> bool:
    """Single source of truth for the prod check. Stripped and lowered so a
    stray space or casing in `ENV` (e.g. "prod ") can't silently re-enable dev
    conveniences / default secrets."""
    return ENV in ("production", "prod")


# ── storage ──────────────────────────────────────────────────────────────────

DATABASE_URL = os.environ.get("DATABASE_URL", "")
IS_POSTGRES = DATABASE_URL.startswith(("postgres://", "postgresql://"))
SQLITE_PATH = os.environ.get("SQLITE_PATH") or str(BASE_DIR / "aira.db")

# Care Vault document bytes. NOT inside the package, and on a deployment NOT
# inside the image layer — set this to the same mounted volume as SQLITE_PATH or
# every uploaded scan is deleted on the next deploy.
FILES_DIR = os.environ.get("AIRA_FILES_DIR") or str(BASE_DIR / "files")

VIDEO_CATALOG_PATH = str(BASE_DIR / "data" / "video_catalog.json")

# A stand-in video so the playback path can be exercised before anything is
# filmed. Must stay off by default: a placeholder served as though it were the
# real thing is the kind of claim this codebase spends its time removing.
DEMO_MEDIA = _flag("AIRA_DEMO_MEDIA")

# ── LLM ──────────────────────────────────────────────────────────────────────

GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
GEMINI_KEY_NAME = "GEMINI_API_KEY"  # the one name the admin "is an LLM configured?" guard reads
GEMINI_MODEL = os.environ.get("AIRA_GEMINI_MODEL", "gemini-2.5-flash")
SAFETY_MODEL = os.environ.get("AIRA_SAFETY_MODEL", GEMINI_MODEL)
GEMINI_MAX_TOKENS = _int("AIRA_GEMINI_MAX_TOKENS", 640)

# ── app auth (the coarse edge gate + identity) ───────────────────────────────

APP_SHARED_SECRET = os.environ.get("APP_SHARED_SECRET", "").strip()

DEV_APP_SESSION_SECRET = "dev-app-session-secret"
APP_SESSION_SECRET = os.environ.get("APP_SESSION_SECRET", DEV_APP_SESSION_SECRET)

GOOGLE_CLIENT_ID = os.environ.get("GOOGLE_CLIENT_ID", "").strip()

# ── admin console ────────────────────────────────────────────────────────────

DEV_ADMIN_SECRET = "dev-admin-secret-change-me"
ADMIN_SECRET = os.environ.get("ADMIN_JWT_SECRET", DEV_ADMIN_SECRET)
ADMIN_SESSION_IDLE_TTL = _int("ADMIN_SESSION_IDLE_TTL", 2 * 3600)
ADMIN_SESSION_ABSOLUTE_TTL = _int("ADMIN_SESSION_ABSOLUTE_TTL", 12 * 3600)
ADMIN_COOKIE_SAMESITE = os.environ.get("ADMIN_COOKIE_SAMESITE", "strict").strip().lower()
ADMIN_COOKIE_DOMAIN = os.environ.get("ADMIN_COOKIE_DOMAIN", "").strip() or None
ADMIN_BOOTSTRAP_EMAIL = os.environ.get("ADMIN_BOOTSTRAP_EMAIL", "").strip().lower()
ADMIN_BOOTSTRAP_PASSWORD = os.environ.get("ADMIN_BOOTSTRAP_PASSWORD", "")

# ── hardening ────────────────────────────────────────────────────────────────

MAX_UPLOAD_BYTES = _int("MAX_UPLOAD_BYTES", 20 * 1024 * 1024)
RATE_LIMIT_PER_MIN = _int("RATE_LIMIT_PER_MIN", 120)
# Only honor X-Forwarded-For behind a trusted proxy that sets it (Render/Fly).
# Off by default so a direct client can't spoof its IP to dodge the limiter.
TRUST_FORWARDED_FOR = _flag("TRUST_FORWARDED_FOR")
MAX_HISTORY_TURNS = _int("MAX_HISTORY_TURNS", 20)
MAX_HISTORY_CHARS = _int("MAX_HISTORY_CHARS", 2000)
REDIS_URL = os.environ.get("REDIS_URL", "").strip()

LOGIN_MAX_FAILS = _int("LOGIN_MAX_FAILS", 8)
LOGIN_LOCK_SECONDS = _int("LOGIN_LOCK_SECONDS", 900)

# ── retention ────────────────────────────────────────────────────────────────

# How long a flagged message is kept. The row holds the user's own words about
# their health, so it is the most sensitive text in the system. 0 disables the
# startup purge (not advisable in production).
SAFETY_FLAG_RETENTION_DAYS = _int("SAFETY_FLAG_RETENTION_DAYS", 90)

# ── CORS ─────────────────────────────────────────────────────────────────────

ADMIN_ORIGINS = _csv("ADMIN_ORIGINS", "http://localhost:3001,http://127.0.0.1:3001")
WEB_ORIGINS = _csv("WEB_ORIGINS", "http://localhost:5173,http://127.0.0.1:5173")

# ── error tracking ───────────────────────────────────────────────────────────

SENTRY_DSN = os.environ.get("SENTRY_DSN", "")
SENTRY_TRACES_RATE = float(os.environ.get("SENTRY_TRACES_RATE", "0") or 0)


# ── the production gate ──────────────────────────────────────────────────────

def insecure_production_config() -> list[str]:
    """Names of settings that must not hold their development value in
    production. Empty means safe to boot; `main.py` refuses otherwise.

    This lives beside the values instead of reaching across three modules'
    globals to find them. A new secret is guarded by adding one line HERE, next
    to where its default is written — which is the only place somebody adding a
    secret is guaranteed to be looking.
    """
    bad = []
    if ADMIN_SECRET == DEV_ADMIN_SECRET:
        bad.append("ADMIN_JWT_SECRET")
    if APP_SESSION_SECRET == DEV_APP_SESSION_SECRET:
        bad.append("APP_SESSION_SECRET")
    # Without this the coarse gate is a no-op and every data route is open.
    if not APP_SHARED_SECRET:
        bad.append("APP_SHARED_SECRET")
    return bad
