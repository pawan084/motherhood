"""Accounts and sessions.

Slimmed from ref/sayli/backend/accounts.py for a web-first product. What stays
and why:

- Stateless HMAC session tokens (stdlib, no JWT dependency for our own tokens).
- The disabled/ban column checked on EVERY auth path, not just /account/* —
  a banned account holding a still-valid token must not act anywhere.
- Google Sign-In verified server-side against GOOGLE_CLIENT_ID (web client uses
  Google Identity Services). Fails closed with 503 while the client id is
  unconfigured — it's a human-gated item in TODO.md.
- Dev login (ALLOW_DEV_LOGIN=1) so local dev and tests never need real OAuth.
  Hard-blocked in production regardless of the env var.

Deferred, deliberately: Apple Sign-In (needs the native app), IAP/entitlements
(no paywall in Aira yet), synced state blobs (care context is structured, not a
blob).

Account deletion cascades through every table that keys on the learner id —
this is a health app, so "delete my account" has to actually mean delete.
"""
import base64
import hashlib
import hmac
import json
import os
import secrets
import time

import jwt
from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel

import config
import db

router = APIRouter(prefix="/account", tags=["account"])

_conn = None
_google_jwk_client = jwt.PyJWKClient("https://www.googleapis.com/oauth2/v3/certs", timeout=5)

# A Google OAuth client id is generated per-app in Google Cloud Console — no
# safe default exists, so it's blank until configured (human-gated, TODO.md).
GOOGLE_CLIENT_ID = os.environ.get("GOOGLE_CLIENT_ID", "").strip()
ALLOW_DEV_LOGIN = os.environ.get("ALLOW_DEV_LOGIN", "").lower() in ("1", "true", "yes")


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS accounts ("
              " id TEXT PRIMARY KEY, google_sub TEXT, email TEXT, name TEXT,"
              " created REAL, last_seen REAL, disabled INTEGER DEFAULT 0)")
    # SQLite's ALTER TABLE forbids adding a UNIQUE column directly, so
    # uniqueness lives in a separate index — portable, and NULLs don't collide.
    c.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_accounts_google_sub ON accounts (google_sub)")
    c.execute("CREATE TABLE IF NOT EXISTS account_devices ("
              " user_id TEXT, device TEXT, PRIMARY KEY (user_id, device))")
    c.commit()
    _conn = c


# --- session token (HMAC, stdlib) -------------------------------------------

def _make_session(user_id: str, ttl: int = 30 * 86_400) -> str:
    payload = {"sub": user_id, "exp": int(time.time()) + ttl}
    body = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    sig = hmac.new(config.APP_SESSION_SECRET.encode(), body.encode(), hashlib.sha256).hexdigest()
    return f"{body}.{sig}"


def _verify_session(token: str) -> dict | None:
    try:
        body, sig = token.split(".", 1)
        expect = hmac.new(config.APP_SESSION_SECRET.encode(), body.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(sig, expect):
            return None
        pad = "=" * (-len(body) % 4)
        payload = json.loads(base64.urlsafe_b64decode(body + pad))
        # Require well-formed claims rather than KeyError-ing into a 500.
        if not isinstance(payload, dict) or "sub" not in payload or "exp" not in payload:
            return None
        return None if payload["exp"] < time.time() else payload
    except Exception:
        return None


def account_disabled(user_id: str) -> bool:
    """True when the account has been banned by an admin. Shared by every auth
    path so a ban is enforced uniformly. A device-only id is never disabled."""
    row = _conn.execute("SELECT disabled FROM accounts WHERE id=?", (user_id,)).fetchone()
    return bool(row and row[0])


def require_account(authorization: str = Header(default="")) -> str:
    token = authorization.removeprefix("Bearer ").strip()
    payload = _verify_session(token)
    if not payload:
        raise HTTPException(status_code=401, detail="unauthorized")
    if account_disabled(payload["sub"]):
        raise HTTPException(status_code=403, detail="account disabled")
    return payload["sub"]


# --- users -------------------------------------------------------------------

def upsert_google_user(google_sub: str, email: str = "", name: str = "") -> str:
    now = time.time()
    row = _conn.execute("SELECT id FROM accounts WHERE google_sub=?", (google_sub,)).fetchone()
    if row:
        _conn.execute("UPDATE accounts SET last_seen=?, email=COALESCE(NULLIF(?,''), email),"
                      " name=COALESCE(NULLIF(?,''), name) WHERE id=?",
                      (now, email, name, row[0]))
        _conn.commit()
        return row[0]
    uid = "acc_" + secrets.token_urlsafe(16)
    _conn.execute("INSERT INTO accounts (id, google_sub, email, name, created, last_seen)"
                  " VALUES (?,?,?,?,?,?)", (uid, google_sub, email, name, now, now))
    _conn.commit()
    return uid


def verify_google_token(identity_token: str) -> dict:
    """Verify a Google ID token's signature and audience. Raises on any failure."""
    if not GOOGLE_CLIENT_ID:
        # Human-gated config; fail closed rather than skipping the audience check.
        raise HTTPException(status_code=503, detail="google sign-in not configured")
    try:
        signing_key = _google_jwk_client.get_signing_key_from_jwt(identity_token)
        return jwt.decode(identity_token, signing_key.key, algorithms=["RS256"],
                          audience=GOOGLE_CLIENT_ID,
                          issuer=["https://accounts.google.com", "accounts.google.com"])
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=401, detail="invalid google token")


def link_device(user_id: str, device: str) -> None:
    _conn.execute("INSERT INTO account_devices (user_id, device) VALUES (?,?)"
                  " ON CONFLICT (user_id, device) DO NOTHING", (user_id, device))
    _conn.commit()


# --- routes ------------------------------------------------------------------

class GoogleIn(BaseModel):
    identity_token: str


class DevLoginIn(BaseModel):
    email: str
    name: str = ""


@router.post("/google")
def sign_in_google(body: GoogleIn, x_device_token: str | None = Header(default=None)):
    claims = verify_google_token(body.identity_token)
    uid = upsert_google_user(claims["sub"], claims.get("email", ""), claims.get("name", ""))
    if account_disabled(uid):
        raise HTTPException(status_code=403, detail="account disabled")
    import device_auth  # local import to avoid a startup cycle
    device_auth.link_and_merge(uid, x_device_token)
    return {"session": _make_session(uid), "user_id": uid}


@router.post("/dev-login")
def dev_login(body: DevLoginIn, x_device_token: str | None = Header(default=None)):
    """Dev/QA only: a session without real OAuth. Never honoured in production,
    regardless of the env var."""
    if not ALLOW_DEV_LOGIN or config.IS_PRODUCTION:
        raise HTTPException(status_code=404, detail="not found")
    uid = upsert_google_user("dev:" + body.email, body.email, body.name)
    import device_auth
    device_auth.link_and_merge(uid, x_device_token)
    return {"session": _make_session(uid), "user_id": uid}


@router.get("/me")
def me(user_id: str = Depends(require_account)):
    row = _conn.execute("SELECT id, email, name, created FROM accounts WHERE id=?",
                        (user_id,)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="not found")
    return {"user_id": row[0], "email": row[1] or "", "name": row[2] or "", "created": row[3]}


def _erase_user(user_id: str) -> None:
    """Cascade a deletion through every learner-keyed table, including the ids
    of devices linked to the account. Health data: delete means delete."""
    devices = [r[0] for r in _conn.execute(
        "SELECT device FROM account_devices WHERE user_id=?", (user_id,)).fetchall()]
    ids = [user_id, *devices]
    import care  # local imports to avoid a startup cycle
    import care_context
    import memory
    import privacy
    with _conn.transaction():
        for lid in ids:
            care_context.erase(lid)
            memory.erase(lid)
            care.erase(lid)
            privacy.erase(lid)
        _conn.execute("DELETE FROM account_devices WHERE user_id=?", (user_id,))
        _conn.execute("DELETE FROM accounts WHERE id=?", (user_id,))


@router.delete("")
def delete_account(user_id: str = Depends(require_account)):
    _erase_user(user_id)
    return {"ok": True}
