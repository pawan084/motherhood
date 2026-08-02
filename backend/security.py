"""Lightweight, dependency-free hardening: shared-secret auth, per-IP rate
limiting, an upload-size cap, and client-history sanitization.

Ported from ref/sayli/backend/security.py, which shipped these after a real
security review. Two decisions worth keeping verbatim:

- X-Forwarded-For is honoured ONLY behind a declared trusted proxy
  (TRUST_FORWARDED_FOR=1) — otherwise it's client-supplied and spoofable.
- `sanitize_history` drops any `system` role from client-supplied history so a
  client can never inject a system prompt.

All knobs are env-driven and degrade to safe no-ops when unset, so local/dev
runs need zero configuration while production can lock everything down:

    APP_SHARED_SECRET   if set, protected routes require `X-App-Token: <secret>`
                        (constant-time compared). Unset -> disabled (dev).
    MAX_UPLOAD_BYTES    reject bodies larger than this (default 10 MB).
    RATE_LIMIT_PER_MIN  per-IP request budget per 60s window (default 120).
                        0 or negative disables rate limiting.
    MAX_HISTORY_TURNS   cap on client-supplied chat history (default 20).
    MAX_HISTORY_CHARS   per-message content cap (default 2000).
"""
import hmac
import logging
import os
import threading
import time

from fastapi import Header, HTTPException, Request, UploadFile

log = logging.getLogger("aira.security")

APP_SHARED_SECRET = os.environ.get("APP_SHARED_SECRET", "").strip()
MAX_UPLOAD_BYTES = int(os.environ.get("MAX_UPLOAD_BYTES", str(10 * 1024 * 1024)))
RATE_LIMIT_PER_MIN = int(os.environ.get("RATE_LIMIT_PER_MIN", "120"))
# Only honor X-Forwarded-For when we sit behind a trusted proxy that sets it.
# Off by default so a direct client can't spoof its IP to dodge the limiter.
TRUST_FORWARDED_FOR = os.environ.get("TRUST_FORWARDED_FOR", "").strip().lower() in ("1", "true", "yes")
# NOTE: counts MESSAGES, not exchanges — 20 messages is ~10 user↔Aira turns.
# The web client bounds its own history to match; raise both together.
MAX_HISTORY_TURNS = int(os.environ.get("MAX_HISTORY_TURNS", "20"))
MAX_HISTORY_CHARS = int(os.environ.get("MAX_HISTORY_CHARS", "2000"))

# Routes that never require auth / rate limiting (health probes + public legal
# pages browsers must reach freely).
_OPEN_PATHS = {"/health", "/docs", "/openapi.json", "/redoc",
               "/privacy", "/terms", "/legal"}


# --- Auth ------------------------------------------------------------------

def require_app_token(x_app_token: str | None = Header(default=None)) -> None:
    """FastAPI dependency: enforce the shared secret when one is configured.

    No-op when APP_SHARED_SECRET is unset so local development keeps working.
    """
    if not APP_SHARED_SECRET:
        return
    if not x_app_token or not hmac.compare_digest(x_app_token, APP_SHARED_SECRET):
        raise HTTPException(status_code=401, detail="unauthorized")


# --- Upload cap ------------------------------------------------------------

async def read_capped(upload: UploadFile) -> bytes:
    """Read an UploadFile fully but abort past MAX_UPLOAD_BYTES instead of
    loading an unbounded body into memory."""
    chunks: list[bytes] = []
    total = 0
    while True:
        chunk = await upload.read(1 << 20)  # 1 MB at a time
        if not chunk:
            break
        total += len(chunk)
        if total > MAX_UPLOAD_BYTES:
            raise HTTPException(status_code=413, detail="upload too large")
        chunks.append(chunk)
    return b"".join(chunks)


# --- Per-IP rate limiting (fixed window) -----------------------------------

_rl_lock = threading.Lock()
_rl_state: dict[str, tuple[int, int]] = {}  # ip -> (window_start_minute, count)

# Optional shared backend: set REDIS_URL to make the limiter correct across
# multiple workers/instances (the in-memory dict is per-process). Lazy-imported
# so single-process/dev runs need neither Redis nor the `redis` package.
REDIS_URL = os.environ.get("REDIS_URL", "").strip()
_redis = None
_redis_ready = False


def _redis_client():
    global _redis, _redis_ready
    if _redis_ready:
        return _redis
    _redis_ready = True
    if REDIS_URL:  # pragma: no cover
        try:
            import redis  # lazy: only needed when REDIS_URL is configured
            _redis = redis.from_url(REDIS_URL)
            _redis.ping()
        except Exception as e:  # noqa: BLE001 — fall back to in-memory, don't crash
            log.warning("REDIS_URL set but Redis unavailable (%s); using in-memory rate limiter", e)
            _redis = None
    return _redis


def _rate_allowed(ip: str, minute: int) -> bool:
    """Count this request in the IP's current-minute window; return False if over
    budget. Uses Redis when configured (multi-worker safe), else the in-memory dict."""
    r = _redis_client()
    if r is not None:  # pragma: no cover
        try:
            key = f"rl:{ip}:{minute}"
            count = r.incr(key)
            if count == 1:
                r.expire(key, 120)  # self-expiring window; no manual eviction needed
            return count <= RATE_LIMIT_PER_MIN
        except Exception as e:  # noqa: BLE001 — degrade to in-memory on a Redis blip
            log.warning("Redis rate-limit error (%s); falling back to in-memory", e)
    with _rl_lock:
        win, count = _rl_state.get(ip, (minute, 0))
        if win != minute:
            win, count = minute, 0
        count += 1
        _rl_state[ip] = (win, count)
        # Bounded growth: evict only stale (previous-window) entries rather than
        # clearing everything (which would briefly reset every IP's budget).
        if len(_rl_state) > 10_000:
            for k in [k for k, (w, _c) in _rl_state.items() if w != minute]:
                _rl_state.pop(k, None)
        return count <= RATE_LIMIT_PER_MIN


def _client_ip(request: Request) -> str:
    if TRUST_FORWARDED_FOR:
        fwd = request.headers.get("x-forwarded-for")
        if fwd:
            return fwd.split(",")[0].strip()
    return request.client.host if request.client else "unknown"


class RateLimitMiddleware:
    """ASGI middleware: at most RATE_LIMIT_PER_MIN requests per IP per minute.

    In-memory by default (per-process); set REDIS_URL to share the counter
    across workers/instances. See `_rate_allowed`.
    """

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or RATE_LIMIT_PER_MIN <= 0:
            await self.app(scope, receive, send)
            return
        path = scope.get("path", "")
        # Never rate-limit CORS preflights — they carry no auth, fire before the
        # real request, and rejecting them strips the CORS headers the browser needs.
        if path in _OPEN_PATHS or scope.get("method") == "OPTIONS":
            await self.app(scope, receive, send)
            return
        request = Request(scope, receive=receive)
        ip = _client_ip(request)
        minute = int(time.time()) // 60
        if not _rate_allowed(ip, minute):
            await self._reject(send)
            return
        await self.app(scope, receive, send)

    @staticmethod
    async def _reject(send):
        await send({
            "type": "http.response.start",
            "status": 429,
            "headers": [(b"content-type", b"application/json"),
                        (b"retry-after", b"60")],
        })
        await send({"type": "http.response.body", "body": b'{"error":"rate limited"}'})


# --- Client history sanitization -------------------------------------------

def sanitize_history(raw) -> list[dict]:
    """Coerce client-supplied chat history into a safe, bounded list of
    {role, content} dicts. Drops anything malformed and any `system` role
    (clients must not inject system prompts), and caps both turn count and
    per-message length to bound cost and block prompt-injection."""
    if not isinstance(raw, list):
        return []
    clean: list[dict] = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        role = item.get("role")
        content = item.get("content")
        if role not in ("user", "assistant") or not isinstance(content, str):
            continue
        clean.append({"role": role, "content": content[:MAX_HISTORY_CHARS]})
    return clean[-MAX_HISTORY_TURNS:]
