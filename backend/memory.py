"""What Aira remembers: per-learner care memory.

Modeled on sayli's learner memory, reshaped for a health product's obligations:

- Items are SMALL STRUCTURED FACTS extracted from the learner's own messages
  ("sleeping badly this week", "prefers brief answers"), never transcripts.
  The chat history itself is not persisted server-side.
- Every item is individually visible AND deletable by the learner
  (GET /memory, DELETE /memory/{id}, DELETE /memory) — the mockup's "What
  Aira remembers" screen is a real control surface, not a display.
- Extraction runs only on turns Aira actually replied to (ok/caution). Urgent
  and error turns store nothing here — urgent inputs are already recorded in
  the safety audit, which serves a different purpose under different rules.
- Deletion is deletion: rows are removed, and the next turn's prompt no
  longer contains the fact.

Kinds are a closed catalog (KINDS) so the You screen can render them sanely
and the extractor can't invent categories.
"""
import secrets
import time

from fastapi import APIRouter, Depends, HTTPException

import db
from device_auth import resolve_learner

router = APIRouter(tags=["memory"])

_conn = None

KINDS = ("fact", "concern", "symptom", "preference")

# Bound what one learner can accumulate (oldest evicted) and what one prompt
# carries. Memory should sharpen the conversation, not become a dossier.
MAX_ITEMS_PER_LEARNER = 60
RECALL_LIMIT = 8


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS memory_items ("
              " id TEXT PRIMARY KEY,"
              " learner_id TEXT NOT NULL,"
              " kind TEXT NOT NULL,"
              " content TEXT NOT NULL,"
              " source TEXT DEFAULT 'chat',"
              " created REAL, updated REAL)")
    c.execute("CREATE INDEX IF NOT EXISTS idx_memory_learner"
              " ON memory_items (learner_id, updated)")
    c.commit()
    _conn = c


# --- storage -----------------------------------------------------------------

def remember(learner_id: str, items: list[dict]) -> int:
    """Store extracted items. Near-duplicates (same kind + case-folded content)
    refresh their timestamp instead of duplicating. Returns how many rows were
    written or refreshed."""
    now = time.time()
    stored = 0
    for item in items:
        kind = item.get("kind")
        content = str(item.get("content", "")).strip()[:200]
        if kind not in KINDS or not content:
            continue
        row = _conn.execute(
            "SELECT id FROM memory_items WHERE learner_id=? AND kind=?"
            " AND LOWER(content)=LOWER(?)",
            (learner_id, kind, content)).fetchone()
        if row:
            _conn.execute("UPDATE memory_items SET updated=? WHERE id=?", (now, row[0]))
        else:
            _conn.execute(
                "INSERT INTO memory_items (id, learner_id, kind, content, created, updated)"
                " VALUES (?,?,?,?,?,?)",
                ("mem_" + secrets.token_hex(8), learner_id, kind, content, now, now))
        stored += 1
    # Evict beyond the cap, oldest-updated first.
    _conn.execute(
        "DELETE FROM memory_items WHERE learner_id=? AND id NOT IN ("
        " SELECT id FROM memory_items WHERE learner_id=?"
        " ORDER BY updated DESC LIMIT ?)",
        (learner_id, learner_id, MAX_ITEMS_PER_LEARNER))
    _conn.commit()
    return stored


def recall(learner_id: str, limit: int = RECALL_LIMIT) -> list[dict]:
    """The freshest items, for prompt grounding."""
    rows = _conn.execute(
        "SELECT kind, content FROM memory_items WHERE learner_id=?"
        " ORDER BY updated DESC LIMIT ?", (learner_id, limit)).fetchall()
    return [{"kind": r[0], "content": r[1]} for r in rows]


def merge(did: str, uid: str) -> None:
    """On sign-in, move the device's memories to the account. Reassignment, not
    copy — the device id stops accumulating a parallel record."""
    _conn.execute("UPDATE memory_items SET learner_id=? WHERE learner_id=?", (uid, did))
    _conn.commit()


def erase(learner_id: str) -> None:
    """Account-deletion cascade hook. Delete means delete."""
    _conn.execute("DELETE FROM memory_items WHERE learner_id=?", (learner_id,))


# --- routes ------------------------------------------------------------------

@router.get("/memory")
def list_memory(learner_id: str = Depends(resolve_learner)):
    rows = _conn.execute(
        "SELECT id, kind, content, updated FROM memory_items WHERE learner_id=?"
        " ORDER BY updated DESC", (learner_id,)).fetchall()
    return {"items": [
        {"id": r[0], "kind": r[1], "content": r[2], "updated": r[3]} for r in rows]}


@router.delete("/memory/{item_id}")
def forget_item(item_id: str, learner_id: str = Depends(resolve_learner)):
    """Scoped to the caller: someone else's item id 404s rather than deleting."""
    cur = _conn.execute("DELETE FROM memory_items WHERE id=? AND learner_id=?",
                        (item_id, learner_id))
    _conn.commit()
    if cur.rowcount == 0:
        raise HTTPException(status_code=404, detail="not found")
    return {"ok": True}


@router.delete("/memory")
def forget_all(learner_id: str = Depends(resolve_learner)):
    _conn.execute("DELETE FROM memory_items WHERE learner_id=?", (learner_id,))
    _conn.commit()
    return {"ok": True}
