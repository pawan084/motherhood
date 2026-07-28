"""What Aira remembers — an explicit, user-reviewable care-memory store.

Only items the user has approved shape future answers (the "AI personalisation"
toggle). `context_summary` is what chat.py folds into the reply prompt, and it
includes ONLY approved items — so "forget" and the personalisation switch are
real, not cosmetic.
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

log = logging.getLogger("aira.memory")
router = APIRouter(prefix="/v1", tags=["memory"],
                   dependencies=[Depends(security.require_app_token)])
_conn = None


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS memory_items ("
              " id TEXT PRIMARY KEY, user_id TEXT, label TEXT, value TEXT,"
              " approved INTEGER DEFAULT 1, source TEXT DEFAULT 'user', created REAL)")
    c.execute("CREATE INDEX IF NOT EXISTS memory_user ON memory_items(user_id)")
    c.commit()
    _conn = c


def _list(uid: str) -> list[dict]:
    init()
    rows = _conn.execute("SELECT id, label, value, approved, source, created "
                         "FROM memory_items WHERE user_id=? ORDER BY created DESC", (uid,)).fetchall()
    return [{"id": r[0], "label": r[1], "value": r[2], "approved": bool(r[3]),
             "source": r[4], "created": r[5]} for r in rows]


def context_summary(uid: str) -> str:
    """Approved memory as a compact string for the reply prompt. Empty when the
    user has approved nothing — so personalisation genuinely off means off."""
    init()
    rows = _conn.execute("SELECT label, value FROM memory_items "
                         "WHERE user_id=? AND approved=1 ORDER BY created DESC LIMIT 20",
                         (uid,)).fetchall()
    return "; ".join(f"{lbl}: {val}" for lbl, val in rows if val)


@router.get("/memory")
def get_memory(uid: str = Depends(current_user)):
    return {"items": _list(uid)}


class MemoryIn(BaseModel):
    label: str
    value: str
    approved: bool = True
    source: str = "user"


@router.post("/memory")
def add_memory(body: MemoryIn, uid: str = Depends(current_user)):
    init()
    mid = "mem_" + uuid.uuid4().hex[:12]
    _conn.execute("INSERT INTO memory_items (id, user_id, label, value, approved, source, created) "
                  "VALUES (?,?,?,?,?,?,?)",
                  (mid, uid, body.label[:120], body.value[:500],
                   1 if body.approved else 0, body.source[:40], time.time()))
    _conn.commit()
    return {"id": mid, **body.model_dump()}


class ApproveIn(BaseModel):
    approved: bool


@router.patch("/memory/{item_id}")
def set_approval(item_id: str, body: ApproveIn, uid: str = Depends(current_user)):
    init()
    cur = _conn.execute("UPDATE memory_items SET approved=? WHERE id=? AND user_id=?",
                        (1 if body.approved else 0, item_id, uid))
    _conn.commit()
    if getattr(cur, "rowcount", 0) == 0:
        raise HTTPException(status_code=404, detail="not found")
    return {"ok": True, "approved": body.approved}


@router.delete("/memory/{item_id}")
def forget(item_id: str, uid: str = Depends(current_user)):
    init()
    cur = _conn.execute("DELETE FROM memory_items WHERE id=? AND user_id=?", (item_id, uid))
    _conn.commit()
    if getattr(cur, "rowcount", 0) == 0:
        raise HTTPException(status_code=404, detail="not found")
    return {"ok": True}
