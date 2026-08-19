"""Password hashing, verification, and login throttling — shared by the admin
console and consumer accounts.

Lifted wholesale out of `admin.py`, which had the only implementation in the
project. Consumer email/password sign-in needs exactly the same primitives, and
a second copy of password crypto is the kind of duplication that quietly
diverges: one side gets an iteration bump or a timing fix and the other doesn't.

Stdlib only (hashlib/hmac/secrets), matching the rest of the auth layer.

The stored format is `iterations$salt$hash`, which carries its own cost factor so
a future increase re-verifies old hashes correctly and lets callers upgrade them
in place on next login. `_LEGACY_ITERATIONS` supports the two-part `salt$hash`
rows written before the format was versioned.
"""
import hashlib
import hmac
import secrets
import threading
import time

from app import config

PBKDF2_ITERATIONS = 600_000
_LEGACY_ITERATIONS = 200_000


def hash_pw(pw: str, salt: str | None = None, iterations: int = PBKDF2_ITERATIONS) -> str:
    salt = salt or secrets.token_hex(16)
    dk = hashlib.pbkdf2_hmac("sha256", pw.encode(), bytes.fromhex(salt), iterations)
    return f"{iterations}${salt}${dk.hex()}"


def verify_pw(pw: str, stored: str) -> bool:
    parts = stored.split("$")
    try:
        if len(parts) == 3:
            iterations, salt, h = int(parts[0]), parts[1], parts[2]
        elif len(parts) == 2:
            iterations, (salt, h) = _LEGACY_ITERATIONS, parts
        else:
            return False
        dk = hashlib.pbkdf2_hmac("sha256", pw.encode(), bytes.fromhex(salt), iterations)
    except (ValueError, TypeError):
        return False
    return hmac.compare_digest(dk.hex(), h)


# Verified against when an account does not exist, so a missing email and a wrong
# password cost the same time. Without it, response latency tells an attacker
# which addresses are registered — on a maternal-health app, that alone leaks
# something about a person.
DUMMY_PW_HASH = hash_pw("aira-timing-oracle-dummy")


def needs_rehash(stored: str) -> bool:
    """True when a stored hash used fewer iterations than we now require, so the
    caller can upgrade it while it holds the plaintext at login."""
    parts = stored.split("$")
    if len(parts) != 3:
        return True                      # legacy two-part format
    try:
        return int(parts[0]) < PBKDF2_ITERATIONS
    except ValueError:
        return True


# ── account-keyed login throttle ─────────────────────────────────────────────
#
# Defence in depth alongside `security.RateLimitMiddleware`, which is per-IP and
# so does nothing against credential stuffing spread across many addresses.
# Keyed by identity instead. In-memory and therefore per-process: a multi-worker
# deployment gets proportionally more attempts before locking, which is why this
# supplements the per-IP limiter rather than replacing it.

_MAX_FAILS = config.LOGIN_MAX_FAILS
_LOCK_SECONDS = config.LOGIN_LOCK_SECONDS
_fails: dict[str, tuple[int, float]] = {}
_lock = threading.Lock()


def throttle_locked(key: str) -> bool:
    with _lock:
        _, until = _fails.get(key, (0, 0.0))
    return until > time.time()


def throttle_fail(key: str) -> None:
    with _lock:
        count, _ = _fails.get(key, (0, 0.0))
        count += 1
        until = time.time() + _LOCK_SECONDS if count >= _MAX_FAILS else 0.0
        _fails[key] = (count, until)


def throttle_reset(key: str) -> None:
    with _lock:
        _fails.pop(key, None)
