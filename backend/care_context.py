"""Care context: the state every other feature reads.

Journey stage (trying to conceive / pregnant / postpartum), the anchor date the
week index is computed from, display name, and language. The chat prompt (P3),
the Journey tab's week content (P5), and Today's single next action all derive
from this module, which is why it exists on its own instead of living inside a
chat handler.

Week semantics:
- pregnant: gestational week, derived from the due date (week 40 falls on the
  due date). Clamped to 1..42 — overdue pregnancies keep counting to 42 rather
  than going negative or lying.
- postpartum: weeks since birth, week 1 = the first seven days.
- trying_to_conceive: no week index.

The week is COMPUTED at read time, never stored — a stored week goes stale
silently, and a wrong gestational week would poison every downstream feature
(wrong Journey content, wrong chat grounding, wrong red-flag context in P2:
bleeding at week 6 and bleeding at week 36 mean different things).
"""
import time
from datetime import date, timedelta

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

import config
import db
from device_auth import resolve_learner

router = APIRouter(tags=["care-context"])

_conn = None

# Sanity bounds for anchor dates. A due date more than 310 days out (~44 weeks)
# or more than 6 weeks past is a typo, not a pregnancy; a birth date in the
# future or more than 3 years back is likewise rejected rather than stored.
_MAX_DUE_AHEAD_DAYS = 310
_MAX_DUE_PAST_DAYS = 42
_MAX_POSTPARTUM_DAYS = 3 * 365


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS care_context ("
              " learner_id TEXT PRIMARY KEY,"
              " stage TEXT NOT NULL,"
              " due_date TEXT,"
              " birth_date TEXT,"
              " display_name TEXT DEFAULT '',"
              " language TEXT DEFAULT 'en',"
              " created REAL, updated REAL)")
    c.commit()
    _conn = c


# --- week computation --------------------------------------------------------

def pregnancy_week(due: date, today: date | None = None) -> int:
    """Gestational week: week 40 falls on the due date, clamped to 1..42."""
    today = today or date.today()
    days_to_due = (due - today).days
    week = 40 - (days_to_due + 6) // 7  # ceil(days/7) without floats
    return max(1, min(42, week))


def postpartum_week(birth: date, today: date | None = None) -> int:
    """Weeks since birth; week 1 = the first seven days."""
    today = today or date.today()
    return max(1, (today - birth).days // 7 + 1)


def _parse_iso_date(value: str, field: str) -> date:
    try:
        return date.fromisoformat(value)
    except ValueError:
        raise HTTPException(status_code=422, detail=f"{field} must be an ISO date (YYYY-MM-DD)")


# --- storage -----------------------------------------------------------------

def get(learner_id: str) -> dict | None:
    row = _conn.execute(
        "SELECT stage, due_date, birth_date, display_name, language, updated"
        " FROM care_context WHERE learner_id=?", (learner_id,)).fetchone()
    if not row:
        return None
    ctx = {
        "stage": row[0],
        "due_date": row[1],
        "birth_date": row[2],
        "display_name": row[3] or "",
        "language": row[4] or "en",
        "updated": row[5],
        "week": None,
    }
    if row[0] == "pregnant" and row[1]:
        ctx["week"] = pregnancy_week(date.fromisoformat(row[1]))
    elif row[0] == "postpartum" and row[2]:
        ctx["week"] = postpartum_week(date.fromisoformat(row[2]))
    return ctx


def upsert(learner_id: str, stage: str, due_date: str | None, birth_date: str | None,
           display_name: str | None, language: str | None) -> None:
    now = time.time()
    _conn.execute(
        "INSERT INTO care_context (learner_id, stage, due_date, birth_date,"
        " display_name, language, created, updated) VALUES (?,?,?,?,?,?,?,?)"
        " ON CONFLICT (learner_id) DO UPDATE SET stage=excluded.stage,"
        " due_date=excluded.due_date, birth_date=excluded.birth_date,"
        " display_name=excluded.display_name, language=excluded.language,"
        " updated=excluded.updated",
        (learner_id, stage, due_date, birth_date, display_name or "", language or "en", now, now))
    _conn.commit()


def merge(did: str, uid: str) -> None:
    """On sign-in, fold the device's context into the account. The freshest
    `updated` wins: a guest who onboarded yesterday then signed into a stale
    account should keep yesterday's answers, and vice versa."""
    dev = _conn.execute("SELECT stage, due_date, birth_date, display_name, language, updated"
                        " FROM care_context WHERE learner_id=?", (did,)).fetchone()
    if not dev:
        return
    acc = _conn.execute("SELECT updated FROM care_context WHERE learner_id=?", (uid,)).fetchone()
    if acc and acc[0] and acc[0] >= (dev[5] or 0):
        return  # account context is fresher; keep it
    upsert(uid, dev[0], dev[1], dev[2], dev[3], dev[4])


def erase(learner_id: str) -> None:
    """Called from the account-deletion cascade. Delete means delete."""
    _conn.execute("DELETE FROM care_context WHERE learner_id=?", (learner_id,))


# --- routes ------------------------------------------------------------------

class ContextIn(BaseModel):
    stage: str
    due_date: str | None = None
    birth_date: str | None = None
    display_name: str | None = None
    language: str | None = None


@router.get("/care-context")
def read_context(learner_id: str = Depends(resolve_learner)):
    return {"context": get(learner_id)}


@router.put("/care-context")
def write_context(body: ContextIn, learner_id: str = Depends(resolve_learner)):
    if body.stage not in config.JOURNEY_STAGES:
        raise HTTPException(status_code=422, detail=f"stage must be one of {config.JOURNEY_STAGES}")
    if body.language and body.language not in config.SUPPORTED_LANGUAGES:
        raise HTTPException(status_code=422,
                            detail=f"language must be one of {config.SUPPORTED_LANGUAGES}")

    today = date.today()
    due_date = birth_date = None
    if body.stage == "pregnant":
        if not body.due_date:
            raise HTTPException(status_code=422, detail="due_date is required when pregnant")
        due = _parse_iso_date(body.due_date, "due_date")
        if due > today + timedelta(days=_MAX_DUE_AHEAD_DAYS) or due < today - timedelta(days=_MAX_DUE_PAST_DAYS):
            raise HTTPException(status_code=422, detail="due_date is out of a plausible range")
        due_date = due.isoformat()
    elif body.stage == "postpartum":
        if not body.birth_date:
            raise HTTPException(status_code=422, detail="birth_date is required when postpartum")
        birth = _parse_iso_date(body.birth_date, "birth_date")
        if birth > today or birth < today - timedelta(days=_MAX_POSTPARTUM_DAYS):
            raise HTTPException(status_code=422, detail="birth_date is out of a plausible range")
        birth_date = birth.isoformat()

    name = (body.display_name or "").strip()[:80]
    upsert(learner_id, body.stage, due_date, birth_date, name, body.language)
    return {"context": get(learner_id)}
