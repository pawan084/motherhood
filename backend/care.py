"""Care context + tools: onboarding, a journey-aware Today/Journey/Care, and the
in-conversation tools (reminders, medicines, appointments, Care Vault documents,
check-ins, symptom logs) plus the offline emergency profile.

Everything is scoped to `current_user`. Journey-specific content comes from
content.py, so a postpartum user never sees pregnancy-week cards — the single
biggest correctness gap in the prototypes, fixed at the source.
"""
import json
import logging
import time
import uuid

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from pydantic import BaseModel

import accounts
import content
import db
import security
from accounts import current_user

log = logging.getLogger("aira.care")
router = APIRouter(prefix="/v1", tags=["care"], dependencies=[Depends(security.require_app_token)])
_conn = None

_ITEM_KINDS = {"reminder", "medicine", "appointment", "document", "checkin", "symptom"}


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS care_context ("
              " user_id TEXT PRIMARY KEY, weeks INTEGER, priorities TEXT DEFAULT '[]',"
              " updated REAL)")
    c.execute("CREATE TABLE IF NOT EXISTS care_items ("
              " id TEXT PRIMARY KEY, user_id TEXT, kind TEXT, data TEXT,"
              " done INTEGER DEFAULT 0, created REAL)")
    c.execute("CREATE INDEX IF NOT EXISTS care_items_user ON care_items(user_id, kind)")
    c.execute("CREATE TABLE IF NOT EXISTS emergency_profiles ("
              " user_id TEXT PRIMARY KEY, data TEXT, updated REAL)")
    c.commit()
    _conn = c


# ── context helpers ──────────────────────────────────────────────────────────

def _context(uid: str) -> dict:
    init()
    row = _conn.execute("SELECT weeks, priorities FROM care_context WHERE user_id=?", (uid,)).fetchone()
    if not row:
        return {"weeks": None, "priorities": []}
    return {"weeks": row[0], "priorities": json.loads(row[1] or "[]")}


def _add_item(uid: str, kind: str, data: dict) -> dict:
    init()
    iid = f"{kind[:3]}_" + uuid.uuid4().hex[:12]
    _conn.execute("INSERT INTO care_items (id, user_id, kind, data, created) VALUES (?,?,?,?,?)",
                  (iid, uid, kind, json.dumps(data), time.time()))
    _conn.commit()
    return {"id": iid, "kind": kind, "done": False, **data}


def _list_items(uid: str, kind: str) -> list[dict]:
    init()
    rows = _conn.execute("SELECT id, data, done, created FROM care_items "
                         "WHERE user_id=? AND kind=? ORDER BY created DESC", (uid, kind)).fetchall()
    out = []
    for iid, data, done, created in rows:
        out.append({"id": iid, "kind": kind, "done": bool(done),
                    "created": created, **json.loads(data or "{}")})
    return out


# ── per-user data (privacy.py: export / delete) ──────────────────────────────

def export_user(uid: str) -> dict:
    init()
    row = _conn.execute("SELECT data FROM emergency_profiles WHERE user_id=?", (uid,)).fetchone()
    return {"context": _context(uid),
            "items": {kind: _list_items(uid, kind) for kind in sorted(_ITEM_KINDS)},
            "emergency_profile": json.loads(row[0]) if row else {}}


def delete_user(uid: str) -> int:
    init()
    n = 0
    for table in ("care_items", "care_context", "emergency_profiles"):
        cur = _conn.execute(f"DELETE FROM {table} WHERE user_id=?", (uid,))
        n += getattr(cur, "rowcount", 0) or 0
    _conn.commit()
    return n


# ── onboarding ───────────────────────────────────────────────────────────────

class OnboardingIn(BaseModel):
    journey: str
    name: str | None = None
    language: str | None = None
    priorities: list[str] = []
    weeks: int | None = None  # pregnancy week, if known


@router.post("/onboarding")
def onboarding(body: OnboardingIn, uid: str = Depends(current_user)):
    """Persist the conversational onboarding into a private care context, then
    return the assembled Today. Journey is validated by accounts.update_profile."""
    accounts.update_profile(uid, name=body.name, journey=body.journey,
                            language=body.language, onboarded=True)
    init()
    pr = [str(p)[:60] for p in (body.priorities or [])][:5]
    _conn.execute("INSERT INTO care_context (user_id, weeks, priorities, updated) VALUES (?,?,?,?) "
                  "ON CONFLICT(user_id) DO UPDATE SET weeks=excluded.weeks, "
                  "priorities=excluded.priorities, updated=excluded.updated",
                  (uid, body.weeks, json.dumps(pr), time.time()))
    _conn.commit()
    return today(uid)


# ── Today / Journey / Care ───────────────────────────────────────────────────

@router.get("/today")
def today(uid: str = Depends(current_user)):
    u = accounts.get_user(uid) or {}
    ctx = _context(uid)
    journey = u.get("journey") or "exploring"
    name = u.get("name") or ""
    action = content.next_action(journey, ctx["priorities"])
    jc = content.journey_content(journey, ctx["weeks"])
    return {
        "name": name, "journey": journey,
        "context_line": jc.get("this_week") or "",
        "weeks": ctx["weeks"],
        "next_action": action,
        "all_clear": True,
        "priorities": ctx["priorities"],
    }


@router.get("/journey")
def journey(uid: str = Depends(current_user)):
    u = accounts.get_user(uid) or {}
    ctx = _context(uid)
    return content.journey_content(u.get("journey") or "exploring", ctx["weeks"])


@router.get("/care")
def care(uid: str = Depends(current_user)):
    meds = _list_items(uid, "medicine")
    appts = _list_items(uid, "appointment")
    docs = _list_items(uid, "document")
    return {
        "appointments": appts,
        "medicines_due": [m for m in meds if not m.get("done")],
        "documents_count": len(docs),
        "reminders": _list_items(uid, "reminder"),
        "care_plan": _care_plan(uid),
    }


def _care_plan(uid: str) -> dict:
    reminders = _list_items(uid, "reminder")
    done = sum(1 for r in reminders if r.get("done"))
    return {"total": len(reminders), "on_track": done}


# ── tools (in-conversation) ──────────────────────────────────────────────────

class ReminderIn(BaseModel):
    title: str
    time: str | None = None
    repeat: str | None = "Daily"
    private_label: bool = True


@router.get("/care/reminders")
def get_reminders(uid: str = Depends(current_user)):
    return {"items": _list_items(uid, "reminder")}


@router.post("/care/reminders")
def add_reminder(body: ReminderIn, uid: str = Depends(current_user)):
    return _add_item(uid, "reminder", body.model_dump())


class MedicineIn(BaseModel):
    name: str
    dose: str | None = None
    schedule: str | None = "Daily"
    time: str | None = None


@router.get("/care/medicines")
def get_medicines(uid: str = Depends(current_user)):
    return {"items": _list_items(uid, "medicine")}


@router.post("/care/medicines")
def add_medicine(body: MedicineIn, uid: str = Depends(current_user)):
    # Aira organises reminders but never starts/stops/changes medication — this
    # only records a schedule the user set; it never prescribes.
    return _add_item(uid, "medicine", body.model_dump())


@router.post("/care/medicines/{item_id}/taken")
def mark_taken(item_id: str, uid: str = Depends(current_user)):
    init()
    cur = _conn.execute("UPDATE care_items SET done=1 WHERE id=? AND user_id=? AND kind='medicine'",
                        (item_id, uid))
    _conn.commit()
    if getattr(cur, "rowcount", 0) == 0:
        raise HTTPException(status_code=404, detail="not found")
    return {"ok": True}


class AppointmentIn(BaseModel):
    doctor: str
    place: str | None = None
    when: str | None = None
    notes: str | None = None


@router.get("/care/appointments")
def get_appointments(uid: str = Depends(current_user)):
    return {"items": _list_items(uid, "appointment")}


@router.post("/care/appointments")
def add_appointment(body: AppointmentIn, uid: str = Depends(current_user)):
    return _add_item(uid, "appointment", body.model_dump())


@router.get("/care/documents")
def get_documents(uid: str = Depends(current_user)):
    return {"items": _list_items(uid, "document")}


@router.post("/care/documents")
async def upload_document(kind: str = Form("Other"), file: UploadFile = File(...),
                          uid: str = Depends(current_user)):
    """Care Vault upload. Reads with the size cap; stores metadata only in this
    scaffold (byte storage → object storage/R2 is a production integration).
    OCR/extraction happens only after the user approves it (not implemented)."""
    data = await security.read_capped(file)
    return _add_item(uid, "document", {
        "name": file.filename or "document", "type": kind,
        "size": len(data), "content_type": file.content_type or "",
        "use_in_answers": False,  # opt-in only, after approval
    })


class CheckinIn(BaseModel):
    feeling: str | None = None
    sleep_hours: float | None = None
    note: str | None = None


@router.post("/care/checkin")
def add_checkin(body: CheckinIn, uid: str = Depends(current_user)):
    return _add_item(uid, "checkin", body.model_dump())


class SymptomIn(BaseModel):
    what: str
    severity: str | None = None  # Mild | Moderate | Severe
    started: str | None = None
    pattern: str | None = None


@router.post("/care/symptom")
def add_symptom(body: SymptomIn, uid: str = Depends(current_user)):
    # Track, don't diagnose. A severe symptom is nudged toward the care team by
    # the client's warning copy; the safety gate covers the chat path separately.
    return _add_item(uid, "symptom", body.model_dump())


# ── emergency profile (offline) ──────────────────────────────────────────────

class EmergencyProfileIn(BaseModel):
    blood_group: str | None = None
    allergies: str | None = None
    care_team_name: str | None = None
    care_team_phone: str | None = None
    hospital: str | None = None
    emergency_contact_name: str | None = None
    emergency_contact_phone: str | None = None
    notes: str | None = None


@router.get("/emergency-profile")
def get_emergency_profile(uid: str = Depends(current_user)):
    init()
    row = _conn.execute("SELECT data FROM emergency_profiles WHERE user_id=?", (uid,)).fetchone()
    u = accounts.get_user(uid) or {}
    data = json.loads(row[0]) if row else {}
    # The single source of truth for the urgent-help dialer (fixes the clients'
    # hardcoded/no-op "Call care team").
    return {"stage": u.get("journey"), "name": u.get("name"), **data}


@router.put("/emergency-profile")
def put_emergency_profile(body: EmergencyProfileIn, uid: str = Depends(current_user)):
    init()
    data = {k: v for k, v in body.model_dump().items() if v is not None}
    _conn.execute("INSERT INTO emergency_profiles (user_id, data, updated) VALUES (?,?,?) "
                  "ON CONFLICT(user_id) DO UPDATE SET data=excluded.data, updated=excluded.updated",
                  (uid, json.dumps(data), time.time()))
    _conn.commit()
    return get_emergency_profile(uid)
