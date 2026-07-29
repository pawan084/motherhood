"""User feedback and — importantly for a health app — "report an AI answer".

A report can flag a clinical, safety, or technical concern about something Aira
said, optionally referencing the chat turn. Reports surface in the admin console
next to the safety flags so a human can review AI output that worried a user.
"""
import json
import logging
import time
import uuid

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

import db
import security
from accounts import current_user

log = logging.getLogger("aira.feedback")
router = APIRouter(prefix="/v1", tags=["feedback"],
                   dependencies=[Depends(security.require_app_token)])
# Admin reads/writes for feedback live in admin.py (which owns require_admin);
# this module exposes the query/mutate helpers it calls.
_conn = None

_KINDS = {"general", "bug", "clinical", "safety", "technical", "praise"}


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS feedback ("
              " id TEXT PRIMARY KEY, user_id TEXT, kind TEXT, message TEXT, ref TEXT DEFAULT '',"
              " ts REAL, status TEXT DEFAULT 'open', handled_by TEXT DEFAULT '')")
    c.commit()
    _conn = c


class FeedbackIn(BaseModel):
    kind: str = "general"
    message: str
    ref: str | None = None  # optional chat-turn id or screen name


@router.post("/feedback")
def submit_feedback(body: FeedbackIn, uid: str = Depends(current_user)):
    init()
    kind = body.kind if body.kind in _KINDS else "general"
    fid = "fb_" + uuid.uuid4().hex[:12]
    _conn.execute("INSERT INTO feedback (id, user_id, kind, message, ref, ts) "
                  "VALUES (?,?,?,?,?,?)",
                  (fid, uid, kind, (body.message or "")[:4000], (body.ref or "")[:80], time.time()))
    _conn.commit()
    return {"ok": True, "id": fid}


@router.post("/feedback/report")
def report_answer(body: FeedbackIn, uid: str = Depends(current_user)):
    """Report an AI answer (clinical/safety/technical). Forces a review-worthy
    kind so it can't be filed as generic feedback."""
    if body.kind not in ("clinical", "safety", "technical"):
        body.kind = "clinical"
    return submit_feedback(body, uid)


# ── per-user data (privacy.py: export / delete) ──────────────────────────────

def export_user(uid: str) -> list[dict]:
    init()
    rows = _conn.execute("SELECT id, kind, message, ref, ts, status FROM feedback "
                         "WHERE user_id=? ORDER BY ts", (uid,)).fetchall()
    cols = ("id", "kind", "message", "ref", "ts", "status")
    return [dict(zip(cols, r)) for r in rows]


def delete_user(uid: str) -> int:
    init()
    cur = _conn.execute("DELETE FROM feedback WHERE user_id=?", (uid,))
    _conn.commit()
    return getattr(cur, "rowcount", 0) or 0


# ── admin surface (auth applied where mounted) ───────────────────────────────

def list_feedback(kind: str | None = None, status: str | None = None,
                  limit: int = 200) -> list[dict]:
    init()
    q = "SELECT id, user_id, kind, message, ref, ts, status, handled_by FROM feedback"
    where, params = [], []
    if kind in _KINDS:
        where.append("kind=?"); params.append(kind)
    if status in ("open", "resolved"):
        where.append("status=?"); params.append(status)
    if where:
        q += " WHERE " + " AND ".join(where)
    q += " ORDER BY ts DESC LIMIT ?"
    params.append(min(int(limit), 500))
    cols = ("id", "user_id", "kind", "message", "ref", "ts", "status", "handled_by")
    return [dict(zip(cols, r)) for r in _conn.execute(q, tuple(params)).fetchall()]


def resolve_feedback(fid: str, actor: str) -> bool:
    init()
    cur = _conn.execute("UPDATE feedback SET status='resolved', handled_by=? WHERE id=?",
                        (actor, fid))
    _conn.commit()
    return getattr(cur, "rowcount", 0) != 0


def stats() -> dict:
    init()
    def _c(sql):
        return _conn.execute(sql).fetchone()[0]
    return {"total": _c("SELECT COUNT(*) FROM feedback"),
            "open": _c("SELECT COUNT(*) FROM feedback WHERE status='open'"),
            "reports": _c("SELECT COUNT(*) FROM feedback WHERE kind IN ('clinical','safety','technical')")}
