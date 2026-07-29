"""Consent ledger — append-only record of what the user has explicitly agreed to.

Each grant/revoke is a new row (never an update), so the full history is
auditable and exportable (a "consent history" the privacy centre surfaces).
`require_consent` is a dependency the consent-gated features use — e.g. the
future-baby story preview — so the backend enforces consent BEFORE any
processing, fixing the clients' "collect the partner photo, then ask" ordering.
"""
import json
import logging
import time

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

import db
import security
from accounts import current_user

log = logging.getLogger("aira.consent")
router = APIRouter(prefix="/v1", tags=["consent"],
                   dependencies=[Depends(security.require_app_token)])
_conn = None

# The consent-gated features.
#
# Every entry MUST declare exactly one of three things, and `test_consent_is_not
# _decorative` fails the build if one doesn't:
#
#   enforced_by  where the backend actually checks this consent before acting.
#                Name the call sites; a claim here is a claim the test verifies
#                is not empty.
#   available    False when the feature does not exist in this build. The
#                consent cannot be granted, and both clients grey the row out
#                and say why.
#   locked       True for a permanent denial that is a statement of policy
#                rather than a control the user operates.
#
# This registry exists because `partner_access` shipped as a real, working
# feature whose consent nothing read: a user could switch partner access off and
# still create invites while accepted partners kept reading her care. A toggle
# that changes nothing is worse than no toggle, so the rule is now mechanical —
# a new feature cannot reach the privacy centre without declaring how its
# consent is honoured.
FEATURES = {
    "personalization": {
        "label": "AI personalisation", "default": True,
        "enforced_by": "memory.context_summary",
    },
    "partner_access": {
        "label": "Partner access (tasks only)", "default": False,
        "enforced_by": "partner.create_invite, partner.accept_invite, partner.shared",
    },
    # Not built. Each needs a service this project has no integration for —
    # speech synthesis and lip-sync, image generation, audio capture — so there
    # is no code path that could honour a grant. Rather than record consent for
    # something that cannot happen, the grant is refused and the UI says so.
    "avatar": {"label": "Talking avatar", "default": False, "available": False},
    "future_baby_story": {"label": "Future-baby story", "default": False, "available": False},
    "store_raw_audio": {"label": "Store raw voice audio", "default": False, "available": False},
    # Listed so the ledger records a permanent denial — Aira never uses health
    # data for advertising, and there is no code path that could.
    "data_for_ads": {"label": "Health data for ads", "default": False, "locked": True},
}


def is_available(feature: str) -> bool:
    return FEATURES.get(feature, {}).get("available", True)


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS consent_ledger ("
              f" id {db.AUTOINC_PK},"
              " user_id TEXT, feature TEXT, granted INTEGER, ts REAL, note TEXT DEFAULT '')")
    c.execute("CREATE INDEX IF NOT EXISTS consent_user ON consent_ledger(user_id, feature)")
    c.commit()
    _conn = c


def _current(uid: str) -> dict:
    """Latest state per feature, starting from the defaults."""
    init()
    state = {k: v["default"] for k, v in FEATURES.items()}
    rows = _conn.execute(
        "SELECT feature, granted FROM consent_ledger WHERE user_id=? "
        "AND id IN (SELECT MAX(id) FROM consent_ledger WHERE user_id=? GROUP BY feature)",
        (uid, uid)).fetchall()
    for feature, granted in rows:
        if feature in state:
            state[feature] = bool(granted)
    # Locked features can never read as granted regardless of any stray row.
    # Nor can unavailable ones: a grant written by an older build (when these
    # were freely grantable) must not keep reading as consent for a feature
    # that still cannot happen.
    for k, meta in FEATURES.items():
        if meta.get("locked") or not meta.get("available", True):
            state[k] = False
    return state


def is_granted(uid: str, feature: str) -> bool:
    return bool(_current(uid).get(feature))


def _record(uid: str, feature: str, granted: bool, note: str = "") -> None:
    init()
    _conn.execute("INSERT INTO consent_ledger (user_id, feature, granted, ts, note) "
                  "VALUES (?,?,?,?,?)", (uid, feature, 1 if granted else 0, time.time(), note[:200]))
    _conn.commit()


def require_consent(feature: str):
    """Build a dependency that 403s unless `feature` is currently granted."""
    def _dep(uid: str = Depends(current_user)) -> str:
        if not is_granted(uid, feature):
            raise HTTPException(status_code=403, detail=f"consent required: {feature}")
        return uid
    return _dep


def export_user(uid: str) -> dict:
    init()
    rows = _conn.execute("SELECT feature, granted, ts, note FROM consent_ledger "
                         "WHERE user_id=? ORDER BY id", (uid,)).fetchall()
    return {"current": _current(uid),
            "history": [{"feature": r[0], "granted": bool(r[1]), "ts": r[2], "note": r[3]}
                        for r in rows]}


def delete_user(uid: str) -> int:
    """Erase this user's consent history. The ledger is append-only *during* an
    account's life so grants/revokes stay auditable — but it is still the user's
    own record, so an account deletion removes it rather than keeping a
    permanent trace of someone who asked to be forgotten."""
    init()
    cur = _conn.execute("DELETE FROM consent_ledger WHERE user_id=?", (uid,))
    _conn.commit()
    return getattr(cur, "rowcount", 0) or 0


@router.get("/consent")
def get_consent(uid: str = Depends(current_user)):
    state = _current(uid)
    return {"features": [{"key": k, "label": FEATURES[k]["label"],
                          "granted": state[k],
                          "locked": FEATURES[k].get("locked", False),
                          # Clients disable an unavailable row and explain it,
                          # instead of offering a switch that governs nothing.
                          "available": FEATURES[k].get("available", True)}
                         for k in FEATURES]}


class ConsentIn(BaseModel):
    feature: str
    granted: bool
    note: str | None = None


@router.post("/consent")
def set_consent(body: ConsentIn, uid: str = Depends(current_user)):
    if body.feature not in FEATURES:
        raise HTTPException(status_code=400, detail="unknown feature")
    if FEATURES[body.feature].get("locked"):
        raise HTTPException(status_code=400, detail="this consent cannot be granted")
    # Granting consent for a feature that does not exist would put a row in the
    # ledger saying the user agreed to something that can never happen — a
    # record that reads like permission and governs nothing. Revoking stays
    # allowed, so a grant recorded by an older build can always be withdrawn.
    if body.granted and not is_available(body.feature):
        raise HTTPException(
            status_code=400,
            detail=f"{body.feature} is not available in this build")
    _record(uid, body.feature, body.granted, body.note or "")
    return {"ok": True, "features": get_consent(uid)["features"]}


@router.get("/consent/history")
def consent_history(uid: str = Depends(current_user)):
    init()
    rows = _conn.execute("SELECT feature, granted, ts, note FROM consent_ledger "
                         "WHERE user_id=? ORDER BY id DESC LIMIT 500", (uid,)).fetchall()
    return {"history": [{"feature": r[0], "granted": bool(r[1]), "ts": r[2], "note": r[3]}
                        for r in rows]}
