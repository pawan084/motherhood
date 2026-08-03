"""Privacy controls: consents, data export, and learner-data deletion.

Three rules shaped this module:

1. **A consent must DO something.** `ai_personalisation` is not a cosmetic
   toggle: when off, the chat prompt carries no memories and extraction is
   skipped entirely (enforced in chat.py, pinned by test). A switch that
   changes nothing would be worse than no switch.
2. **Export is the learner's own data, complete.** Care context, memories,
   care data, consents, and their safety-audit entries — the audit rows are
   their inputs and the decisions made about them, which is exactly the kind
   of record a person has a right to see. Document files are listed by
   metadata; the bytes download individually via /documents/{id}.
3. **Guests can delete too.** DELETE /learner-data erases everything keyed to
   the resolved learner id without requiring an account — a guest's health
   data must not be less deletable than a member's.
"""
import time

from fastapi import APIRouter, Depends

import care
import care_context
import db
import memory
from device_auth import resolve_learner

router = APIRouter(tags=["privacy"])

_conn = None

# The consent catalog. Adding one here makes it exportable and mergeable;
# giving it EFFECT is the feature's job (and its test's).
CONSENT_KEYS = {"ai_personalisation": True}  # key -> default


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS consents ("
              " learner_id TEXT NOT NULL, key TEXT NOT NULL,"
              " granted INTEGER NOT NULL, updated REAL,"
              " PRIMARY KEY (learner_id, key))")
    c.commit()
    _conn = c


def consent(learner_id: str, key: str) -> bool:
    row = _conn.execute("SELECT granted FROM consents WHERE learner_id=? AND key=?",
                        (learner_id, key)).fetchone()
    return CONSENT_KEYS.get(key, False) if row is None else bool(row[0])


def merge(did: str, uid: str) -> None:
    """Sign-in: the device's explicit choices carry over unless the account
    already made its own (existing account rows win — an explicit choice is
    never overwritten by a merge)."""
    _conn.execute(
        "INSERT INTO consents (learner_id, key, granted, updated)"
        " SELECT ?, key, granted, updated FROM consents WHERE learner_id=?"
        " ON CONFLICT (learner_id, key) DO NOTHING", (uid, did))
    _conn.execute("DELETE FROM consents WHERE learner_id=?", (did,))
    _conn.commit()


def erase(learner_id: str) -> None:
    _conn.execute("DELETE FROM consents WHERE learner_id=?", (learner_id,))


# --- routes ------------------------------------------------------------------

@router.get("/consents")
def get_consents(learner_id: str = Depends(resolve_learner)):
    return {"consents": {key: consent(learner_id, key) for key in CONSENT_KEYS}}


@router.put("/consents")
def put_consents(body: dict, learner_id: str = Depends(resolve_learner)):
    now = time.time()
    for key, value in body.items():
        if key not in CONSENT_KEYS or not isinstance(value, bool):
            continue  # unknown keys ignored, not stored
        _conn.execute(
            "INSERT INTO consents (learner_id, key, granted, updated) VALUES (?,?,?,?)"
            " ON CONFLICT (learner_id, key) DO UPDATE SET granted=excluded.granted,"
            " updated=excluded.updated",
            (learner_id, key, int(value), now))
    _conn.commit()
    return {"consents": {key: consent(learner_id, key) for key in CONSENT_KEYS}}


@router.get("/export")
def export_data(learner_id: str = Depends(resolve_learner)):
    """Everything keyed to the caller, as one JSON document."""
    c = _conn
    audit = c.execute(
        "SELECT ts, input, stage, week, decision, source, reason FROM safety_audit"
        " WHERE learner_id=? ORDER BY ts", (learner_id,)).fetchall()
    meds = c.execute(
        "SELECT name, dose, time_of_day, notes, created FROM medicines"
        " WHERE learner_id=?", (learner_id,)).fetchall()
    docs = c.execute(
        "SELECT id, kind, filename, size, created FROM documents"
        " WHERE learner_id=?", (learner_id,)).fetchall()
    apts = c.execute(
        "SELECT title, when_ts, location, notes FROM appointments"
        " WHERE learner_id=?", (learner_id,)).fetchall()
    plan = c.execute(
        "SELECT title, done, created FROM care_plan_items"
        " WHERE learner_id=?", (learner_id,)).fetchall()
    mems = c.execute(
        "SELECT kind, content, created, updated FROM memory_items"
        " WHERE learner_id=?", (learner_id,)).fetchall()
    return {
        "exported_at": time.time(),
        "care_context": care_context.get(learner_id),
        "consents": {key: consent(learner_id, key) for key in CONSENT_KEYS},
        "memories": [dict(zip(("kind", "content", "created", "updated"), m)) for m in mems],
        "medicines": [dict(zip(("name", "dose", "time_of_day", "notes", "created"), m)) for m in meds],
        "documents": [dict(zip(("id", "kind", "filename", "size", "created"), d)) for d in docs],
        "appointments": [dict(zip(("title", "when_ts", "location", "notes"), a)) for a in apts],
        "care_plan": [dict(zip(("title", "done", "created"), p)) for p in plan],
        "safety_audit": [dict(zip(("ts", "input", "stage", "week", "decision", "source", "reason"), a)) for a in audit],
        "note": "Document files download individually via /documents/{id}.",
    }


@router.delete("/learner-data")
def delete_learner_data(learner_id: str = Depends(resolve_learner)):
    """Erase everything keyed to the caller — works for guests, who have no
    account to delete. (Safety-audit rows are retained: they are the record
    of escalation decisions, kept for accountability, and they are visible to
    the learner via /export.)"""
    with _conn.transaction():
        care_context.erase(learner_id)
        memory.erase(learner_id)
        care.erase(learner_id)
        erase(learner_id)
    return {"ok": True}
