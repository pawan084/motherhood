"""Per-user product preferences: settings that are neither identity nor care data.

Voice preferences live here rather than as new columns on the `users` row,
because they are UI settings clients will keep adding to, and widening the
accounts schema for each one would mean a migration per setting. One JSON blob
per user instead, validated on write against a known key set so a client typo
cannot silently create a preference nothing ever reads.

Conversation LANGUAGE is deliberately not stored here — it already lives on the
user profile (`PATCH /account/profile`) and is used to shape replies. Keeping a
second copy would give two sources of truth that drift.

Spoken replies do not exist in this build. The voice preference is still stored
for real — it round-trips, exports and deletes like any other user data — and
the clients say plainly that it takes effect once speech is wired up. That is
the honest version of a setting whose consumer hasn't shipped: persist it, and
don't imply it does something it doesn't.
"""
import json
import logging
import time

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from app.core import db
from app.core import security
from app.domains.accounts import current_user

log = logging.getLogger("aira.prefs")
router = APIRouter(prefix="/v1", tags=["prefs"],
                   dependencies=[Depends(security.require_app_token)])
_conn = None

# Allowed values, mirrored by the clients' choice lists. An unknown value is a
# 400 rather than a stored string nothing can render.
VOICES = ("Aira warm", "Aira gentle", "Text only")

DEFAULTS = {
    "voice": "Aira warm",
    "spoken_replies": False,   # no TTS in this build; see module docstring
}


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS user_prefs ("
              " user_id TEXT PRIMARY KEY, data TEXT, updated REAL)")
    c.commit()
    _conn = c


def get_prefs(uid: str) -> dict:
    init()
    row = _conn.execute("SELECT data FROM user_prefs WHERE user_id=?", (uid,)).fetchone()
    stored = json.loads(row[0]) if row and row[0] else {}
    return {**DEFAULTS, **stored}


# ── per-user data (privacy.py: export / delete) ──────────────────────────────

def export_user(uid: str) -> dict:
    return get_prefs(uid)


def delete_user(uid: str) -> int:
    init()
    cur = _conn.execute("DELETE FROM user_prefs WHERE user_id=?", (uid,))
    _conn.commit()
    return getattr(cur, "rowcount", 0) or 0


# ── routes ───────────────────────────────────────────────────────────────────

class PrefsIn(BaseModel):
    voice: str | None = None
    spoken_replies: bool | None = None


@router.get("/prefs")
def read_prefs(uid: str = Depends(current_user)):
    return get_prefs(uid)


@router.put("/prefs")
def write_prefs(body: PrefsIn, uid: str = Depends(current_user)):
    """Partial update: only the fields present in the body change, so a client
    that knows about one preference can't blank out one it hasn't heard of."""
    current = get_prefs(uid)
    patch = {k: v for k, v in body.model_dump().items() if v is not None}
    if "voice" in patch and patch["voice"] not in VOICES:
        raise HTTPException(status_code=400,
                            detail=f"voice must be one of: {', '.join(VOICES)}")
    merged = {**current, **patch}
    init()
    _conn.execute("INSERT INTO user_prefs (user_id, data, updated) VALUES (?,?,?) "
                  "ON CONFLICT(user_id) DO UPDATE SET data=excluded.data, "
                  "updated=excluded.updated",
                  (uid, json.dumps(merged), time.time()))
    _conn.commit()
    return merged
