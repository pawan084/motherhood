"""Identity: anonymous device users, Google Sign-In accounts, and the app-session
token that every data route is scoped by.

Auth model (mirrors the client flows in the diagram's "Welcome / Sign in"):
  - A first-run client calls POST /device/register and gets a long-lived,
    HMAC-signed session token bound to a fresh anonymous user row. No PII.
  - Signing in with Google (POST /account/google) verifies the Google ID token,
    upserts an account user keyed by the Google `sub`, optionally MERGES the
    anonymous device user's data into it, and returns a new token.
  - `current_user` reads `Authorization: Bearer <token>`, verifies the HMAC and
    the user's token_version (so a server-side revoke invalidates old tokens),
    and yields the user id. This is IDENTITY; the coarse `X-App-Token` shared
    secret (security.require_app_token) is a separate, optional edge gate.

Tokens are stdlib HMAC (no dependency), like the admin console's sessions.
"""
import base64
import hashlib
import hmac
import json
import logging
import os
import secrets
import time
import uuid

from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel

import db
import security

log = logging.getLogger("aira.accounts")

APP_SESSION_SECRET = os.environ.get("APP_SESSION_SECRET", "dev-app-session-secret")
GOOGLE_CLIENT_ID = os.environ.get("GOOGLE_CLIENT_ID", "").strip()
ANDROID_PACKAGE_NAME = os.environ.get("ANDROID_PACKAGE_NAME", "com.aira.companion").strip()
_GOOGLE_ISSUERS = {"accounts.google.com", "https://accounts.google.com"}
_GOOGLE_CERTS = "https://www.googleapis.com/oauth2/v3/certs"

VALID_JOURNEYS = {"trying", "pregnant", "postpartum", "exploring"}

router = APIRouter(tags=["accounts"])
_conn = None
_jwk_client = None


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS users ("
              " id TEXT PRIMARY KEY, kind TEXT NOT NULL, email TEXT, google_sub TEXT,"
              " name TEXT DEFAULT '', journey TEXT DEFAULT '', language TEXT DEFAULT 'English',"
              " onboarded INTEGER DEFAULT 0, token_version INTEGER DEFAULT 0,"
              " created REAL, last_seen REAL)")
    c.execute("CREATE UNIQUE INDEX IF NOT EXISTS users_google_sub ON users(google_sub)")
    c.commit()
    _conn = c


# ── token mint / verify (stdlib HMAC) ────────────────────────────────────────

def _b64(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def _unb64(s: str) -> bytes:
    return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))


def mint_token(uid: str, token_version: int) -> str:
    payload = _b64(json.dumps({"uid": uid, "ver": token_version,
                               "iat": int(time.time())}).encode())
    sig = _b64(hmac.new(APP_SESSION_SECRET.encode(), payload.encode(), hashlib.sha256).digest())
    return f"v1.{payload}.{sig}"


def _verify_token(token: str) -> str | None:
    """Return the uid for a valid, non-revoked token, else None."""
    try:
        ver, payload, sig = token.split(".", 2)
        if ver != "v1":
            return None
        expect = _b64(hmac.new(APP_SESSION_SECRET.encode(), payload.encode(), hashlib.sha256).digest())
        if not hmac.compare_digest(sig, expect):
            return None
        data = json.loads(_unb64(payload))
    except (ValueError, KeyError, json.JSONDecodeError):
        return None
    uid = data.get("uid")
    if not uid:
        return None
    init()
    row = _conn.execute("SELECT token_version FROM users WHERE id=?", (uid,)).fetchone()
    if row is None or int(row[0]) != int(data.get("ver", -1)):
        return None
    return uid


# ── current_user dependency ──────────────────────────────────────────────────

def current_user(authorization: str | None = Header(default=None)) -> str:
    """FastAPI dependency: the authenticated user id, from the Bearer token.
    401 when missing/invalid/revoked."""
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="missing bearer token")
    uid = _verify_token(authorization[7:].strip())
    if not uid:
        raise HTTPException(status_code=401, detail="invalid or expired token")
    init()
    _conn.execute("UPDATE users SET last_seen=? WHERE id=?", (time.time(), uid))
    _conn.commit()
    return uid


# ── user helpers ─────────────────────────────────────────────────────────────

_COLS = ("id", "kind", "email", "google_sub", "name", "journey", "language",
         "onboarded", "token_version", "created", "last_seen")


def get_user(uid: str) -> dict | None:
    init()
    row = _conn.execute(f"SELECT {','.join(_COLS)} FROM users WHERE id=?", (uid,)).fetchone()
    if not row:
        return None
    u = dict(zip(_COLS, row))
    u["onboarded"] = bool(u["onboarded"])
    return u


def public_user(u: dict) -> dict:
    return {"id": u["id"], "kind": u["kind"], "email": u["email"], "name": u["name"],
            "journey": u["journey"], "language": u["language"], "onboarded": u["onboarded"]}


def _create_device_user() -> str:
    init()
    uid = "usr_" + uuid.uuid4().hex
    now = time.time()
    _conn.execute("INSERT INTO users (id, kind, created, last_seen) VALUES (?,?,?,?)",
                  (uid, "device", now, now))
    _conn.commit()
    return uid


def export_user(uid: str) -> dict | None:
    """The user's own profile row, for their data export (privacy.py)."""
    return get_user(uid)


def delete_user(uid: str) -> int:
    """Delete the account row. Must run LAST in a deletion sequence: removing it
    also invalidates every outstanding token (`_verify_token` finds no row), so
    a failure earlier in the sequence leaves the user able to retry rather than
    locked out of data that still exists."""
    init()
    cur = _conn.execute("DELETE FROM users WHERE id=?", (uid,))
    _conn.commit()
    return getattr(cur, "rowcount", 0) or 0


def bump_token_version(uid: str) -> None:
    """Invalidate every existing token for a user (log-out-everywhere / on a
    security event)."""
    init()
    _conn.execute("UPDATE users SET token_version = token_version + 1 WHERE id=?", (uid,))
    _conn.commit()


def update_profile(uid: str, *, name=None, journey=None, language=None,
                   onboarded=None) -> dict:
    init()
    sets, params = [], []
    if name is not None:
        sets.append("name=?"); params.append(str(name)[:120])
    if journey is not None:
        j = str(journey).strip().lower()
        if j and j not in VALID_JOURNEYS:
            raise HTTPException(status_code=400, detail="invalid journey")
        sets.append("journey=?"); params.append(j)
    if language is not None:
        sets.append("language=?"); params.append(str(language)[:40] or "English")
    if onboarded is not None:
        sets.append("onboarded=?"); params.append(1 if onboarded else 0)
    if sets:
        params.append(uid)
        _conn.execute(f"UPDATE users SET {','.join(sets)} WHERE id=?", tuple(params))
        _conn.commit()
    return get_user(uid)


# ── Google Sign-In verification ──────────────────────────────────────────────

def _verify_google_id_token(id_token: str) -> dict:
    """Verify a Google ID token's signature (against Google's JWKS) and claims.
    Returns the decoded claims, or raises HTTPException. Fails CLOSED: an unset
    GOOGLE_CLIENT_ID in production is a 503, not a skipped audience check."""
    global _jwk_client
    if not GOOGLE_CLIENT_ID:
        if security.is_production():
            raise HTTPException(status_code=503, detail="Google Sign-In not configured")
        # Dev convenience only: without a client id we cannot check `aud`. Reject
        # rather than trust an unverified token.
        raise HTTPException(status_code=503, detail="GOOGLE_CLIENT_ID unset (dev)")
    import jwt  # lazy: pyjwt only needed on the Google path
    from jwt import PyJWKClient
    if _jwk_client is None:
        _jwk_client = PyJWKClient(_GOOGLE_CERTS)
    # Accept both the web/OAuth client id and the Android package name as valid
    # audiences (Android ID tokens carry the app's client id; the package name
    # is checked defensively where configured).
    audiences = [a for a in (GOOGLE_CLIENT_ID, ANDROID_PACKAGE_NAME) if a]
    try:
        signing_key = _jwk_client.get_signing_key_from_jwt(id_token)
        claims = jwt.decode(id_token, signing_key.key, algorithms=["RS256"],
                            audience=audiences, options={"require": ["exp", "iat", "sub"]})
    except Exception as e:  # noqa: BLE001 — any verification failure is a 401
        raise HTTPException(status_code=401, detail=f"invalid Google token: {str(e)[:120]}")
    if claims.get("iss") not in _GOOGLE_ISSUERS:
        raise HTTPException(status_code=401, detail="bad token issuer")
    return claims


# ── routes ───────────────────────────────────────────────────────────────────

class DeviceRegisterOut(BaseModel):
    user_id: str
    token: str
    kind: str


@router.post("/device/register", response_model=DeviceRegisterOut,
             dependencies=[Depends(security.require_app_token)])
def device_register():
    """First-run anonymous identity. Returns a long-lived session token. The
    client stores it and sends it as `Authorization: Bearer <token>`."""
    uid = _create_device_user()
    u = get_user(uid)
    return {"user_id": uid, "token": mint_token(uid, u["token_version"]), "kind": "device"}


class GoogleIn(BaseModel):
    id_token: str
    device_token: str | None = None  # optional: merge this anon device into the account


@router.post("/account/google", dependencies=[Depends(security.require_app_token)])
def account_google(body: GoogleIn):
    """Verify a Google ID token and return an account session. If `device_token`
    names an existing anonymous user, its onboarding/care data is carried over."""
    claims = _verify_google_id_token(body.id_token)
    sub = claims.get("sub")
    email = (claims.get("email") or "").lower() or None
    name = claims.get("name") or ""
    init()
    row = _conn.execute("SELECT id FROM users WHERE google_sub=?", (sub,)).fetchone()
    if row:
        uid = row[0]
        _conn.execute("UPDATE users SET email=?, name=COALESCE(NULLIF(name,''),?), last_seen=? WHERE id=?",
                      (email, name, time.time(), uid))
        _conn.commit()
    else:
        # Promote the anonymous device user in place when we can (keeps its care
        # data), else create a fresh account user.
        prior = _verify_token(body.device_token or "") if body.device_token else None
        now = time.time()
        if prior and get_user(prior) and get_user(prior)["kind"] == "device":
            uid = prior
            _conn.execute("UPDATE users SET kind='account', email=?, google_sub=?, "
                          "name=COALESCE(NULLIF(name,''),?), last_seen=? WHERE id=?",
                          (email, sub, name, now, uid))
        else:
            uid = "usr_" + uuid.uuid4().hex
            _conn.execute("INSERT INTO users (id, kind, email, google_sub, name, created, last_seen) "
                          "VALUES (?,?,?,?,?,?,?)", (uid, "account", email, sub, name, now, now))
        _conn.commit()
    u = get_user(uid)
    return {"user_id": uid, "token": mint_token(uid, u["token_version"]),
            "user": public_user(u)}


@router.get("/account/me")
def account_me(uid: str = Depends(current_user)):
    return {"user": public_user(get_user(uid))}


class ProfileIn(BaseModel):
    name: str | None = None
    journey: str | None = None
    language: str | None = None


@router.patch("/account/profile")
def account_profile(body: ProfileIn, uid: str = Depends(current_user)):
    u = update_profile(uid, name=body.name, journey=body.journey, language=body.language)
    return {"user": public_user(u)}


@router.post("/account/logout")
def account_logout(uid: str = Depends(current_user)):
    """Log out everywhere: bumps token_version so every issued token stops
    verifying. The client discards its copy and re-registers/sign-in."""
    bump_token_version(uid)
    return {"ok": True}
