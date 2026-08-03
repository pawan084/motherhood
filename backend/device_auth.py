"""Per-device credentials for the anonymous (pre-account) surface.

Ported from ref/sayli/backend/device_auth.py essentially verbatim — the design
is the fix for a real IDOR sayli shipped and then closed: a learner's data must
NOT be addressable by a raw, client-supplied device string, or anyone holding
the shared app token can read another person's health data by guessing an id.

Instead, on first launch the client calls `POST /device/register` once and
persists the returned **device token** (web: localStorage; native: Keychain).
The token is an HMAC-signed envelope around a high-entropy, server-issued id
(`did`) that is never exposed elsewhere, so it can't be forged (no signing
secret) and can't be guessed (random `did`).

Identity for learner-scoped routes is resolved by `resolve_learner`:
  1. `Authorization: Bearer <session>`  -> the account id (signed in), else
  2. `X-Device-Token: <device token>`   -> the verified `did`, else
  3. 401.

The token shares the session-secret HMAC scheme by default but uses a disjoint
claim shape (`kind="device"`, no `sub`/`exp`), so device tokens and account
sessions never cross-validate even under a shared key.
"""
import base64
import hashlib
import hmac
import json
import secrets

from fastapi import APIRouter, Depends, Header, HTTPException

import accounts
import config
import security

router = APIRouter(tags=["device"])


def _sign(did: str) -> str:
    payload = {"did": did, "kind": "device"}
    body = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    sig = hmac.new(config.DEVICE_TOKEN_SECRET.encode(), body.encode(), hashlib.sha256).hexdigest()
    return f"{body}.{sig}"


def verify_device_token(token: str | None) -> str | None:
    """Return the device id (`did`) for a valid token, else None."""
    if not token:
        return None
    try:
        body, sig = token.split(".", 1)
        expect = hmac.new(config.DEVICE_TOKEN_SECRET.encode(), body.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(sig, expect):
            return None
        pad = "=" * (-len(body) % 4)
        payload = json.loads(base64.urlsafe_b64decode(body + pad))
        if not isinstance(payload, dict) or payload.get("kind") != "device" or "did" not in payload:
            return None
        return payload["did"]
    except Exception:
        return None


def new_device_id() -> str:
    return "dev_" + secrets.token_urlsafe(24)


@router.post("/device/register", dependencies=[Depends(security.require_app_token)])
def register_device():
    """Mint a fresh device credential. Idempotency is the client's job: call
    once on first launch and persist the token."""
    return {"device_token": _sign(new_device_id())}


def resolve_learner(authorization: str = Header(default=""),
                    x_device_token: str | None = Header(default=None)) -> str:
    """FastAPI dependency: the authenticated learner id for the request. Prefers
    a signed account session, then a registered device token. Never trusts a
    client-named `device` field. Raises 401 when neither credential is valid."""
    token = authorization.removeprefix("Bearer ").strip()
    if token:
        payload = accounts._verify_session(token)
        if payload:
            # A banned account keeps a valid session token but must not act on
            # the learner surface either — mirror require_account.
            if accounts.account_disabled(payload["sub"]):
                raise HTTPException(status_code=403, detail="account disabled")
            return payload["sub"]
    did = verify_device_token(x_device_token)
    if did:
        return did
    raise HTTPException(status_code=401, detail="unauthorized")


def link_and_merge(uid: str, x_device_token: str | None) -> None:
    """On sign-in, fold the caller's OWN device data into their account. The
    device is taken from the presented (proven) token — never a client-named
    id — so a caller can't merge a victim's device into their account."""
    did = verify_device_token(x_device_token)
    if not did:
        return
    accounts.link_device(uid, did)
    import care  # local imports to avoid a startup cycle
    import care_context
    import memory
    import privacy
    import wellness
    care_context.merge(did, uid)
    memory.merge(did, uid)
    care.merge(did, uid)
    privacy.merge(did, uid)
    wellness.merge(did, uid)
