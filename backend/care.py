"""Care: medicines, the document vault, appointments, and the care plan.

The Care tab's four resources in one module — they share the same shape
(owner-scoped CRUD over learner-keyed rows) and the same obligations:

- Everything resolves through `resolve_learner`; another learner's ids 404.
- Sign-in reassigns rows to the account (merge); account deletion erases them,
  INCLUDING the uploaded files on disk — deleting the row but orphaning the
  PDF would make "delete my account" a lie.
- Documents are PHI. They live under UPLOAD_DIR/{learner}/{doc_id}.{ext} with
  server-generated names (no client path reaches the filesystem), an extension
  whitelist, and the shared upload-size cap. Local disk is the dev stand-in;
  production needs object storage (TODO.md).

Deliberately NOT here yet:
- Reminder *delivery* (web push needs a service worker + VAPID keys — the
  schedule is stored, delivery is later work).
- AI document extraction (queued work by design — see PLAN.md trap #4 — and
  it needs the Gemini key anyway). Uploads store what the USER says the
  document is; nothing pretends to have read it.
"""
import os
import re
import secrets
import time
from datetime import date

from fastapi import APIRouter, Depends, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse
from pydantic import BaseModel

import config
import db
import security
from device_auth import resolve_learner

router = APIRouter(tags=["care"])

_conn = None

DOC_KINDS = ("prescription", "lab_report", "scan", "other")
_EXT_TYPES = {".pdf": "application/pdf", ".jpg": "image/jpeg",
              ".jpeg": "image/jpeg", ".png": "image/png"}
_TIME_RE = re.compile(r"^([01]\d|2[0-3]):[0-5]\d$")


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS medicines ("
              " id TEXT PRIMARY KEY, learner_id TEXT NOT NULL,"
              " name TEXT NOT NULL, dose TEXT DEFAULT '',"
              " time_of_day TEXT DEFAULT '', notes TEXT DEFAULT '',"
              " created REAL)")
    c.execute("CREATE TABLE IF NOT EXISTS medicine_taken ("
              " medicine_id TEXT NOT NULL, day TEXT NOT NULL,"
              " ts REAL, PRIMARY KEY (medicine_id, day))")
    c.execute("CREATE TABLE IF NOT EXISTS documents ("
              " id TEXT PRIMARY KEY, learner_id TEXT NOT NULL,"
              " kind TEXT NOT NULL, filename TEXT NOT NULL,"
              " ext TEXT NOT NULL, size INTEGER, created REAL)")
    c.execute("CREATE TABLE IF NOT EXISTS appointments ("
              " id TEXT PRIMARY KEY, learner_id TEXT NOT NULL,"
              " title TEXT NOT NULL, when_ts REAL NOT NULL,"
              " location TEXT DEFAULT '', notes TEXT DEFAULT '',"
              " created REAL)")
    c.execute("CREATE TABLE IF NOT EXISTS care_plan_items ("
              " id TEXT PRIMARY KEY, learner_id TEXT NOT NULL,"
              " title TEXT NOT NULL, done INTEGER DEFAULT 0,"
              " created REAL)")
    for table in ("medicines", "documents", "appointments", "care_plan_items"):
        c.execute(f"CREATE INDEX IF NOT EXISTS idx_{table}_learner"
                  f" ON {table} (learner_id)")
    c.commit()
    _conn = c
    os.makedirs(config.UPLOAD_DIR, exist_ok=True)


def _doc_path(learner_id: str, doc_id: str, ext: str) -> str:
    # Every component is server-generated; no client string touches the path.
    return os.path.join(config.UPLOAD_DIR, learner_id, doc_id + ext)


def _owned(table: str, item_id: str, learner_id: str):
    row = _conn.execute(f"SELECT id FROM {table} WHERE id=? AND learner_id=?",
                        (item_id, learner_id)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="not found")


def merge(did: str, uid: str) -> None:
    """Sign-in: reassign the device's care data to the account, moving each
    document's file to the account's directory in the same pass."""
    docs = _conn.execute("SELECT id, ext FROM documents WHERE learner_id=?",
                         (did,)).fetchall()
    for doc_id, ext in docs:
        src, dst = _doc_path(did, doc_id, ext), _doc_path(uid, doc_id, ext)
        if os.path.exists(src):
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            os.replace(src, dst)
    for table in ("medicines", "documents", "appointments", "care_plan_items"):
        _conn.execute(f"UPDATE {table} SET learner_id=? WHERE learner_id=?", (uid, did))
    _conn.commit()


def erase(learner_id: str) -> None:
    """Deletion cascade: rows AND the files on disk."""
    docs = _conn.execute("SELECT id, ext FROM documents WHERE learner_id=?",
                         (learner_id,)).fetchall()
    for doc_id, ext in docs:
        try:
            os.remove(_doc_path(learner_id, doc_id, ext))
        except FileNotFoundError:
            pass
    _conn.execute("DELETE FROM medicine_taken WHERE medicine_id IN"
                  " (SELECT id FROM medicines WHERE learner_id=?)", (learner_id,))
    for table in ("medicines", "documents", "appointments", "care_plan_items"):
        _conn.execute(f"DELETE FROM {table} WHERE learner_id=?", (learner_id,))


# ── Medicines ────────────────────────────────────────────────────────────────

class MedicineIn(BaseModel):
    name: str
    dose: str = ""
    time_of_day: str = ""   # "HH:MM", 24h — schedule only; delivery is later work
    notes: str = ""


@router.get("/medicines")
def list_medicines(learner_id: str = Depends(resolve_learner)):
    today = date.today().isoformat()
    rows = _conn.execute(
        "SELECT m.id, m.name, m.dose, m.time_of_day, m.notes,"
        " CASE WHEN t.day IS NULL THEN 0 ELSE 1 END"
        " FROM medicines m LEFT JOIN medicine_taken t"
        " ON t.medicine_id = m.id AND t.day = ?"
        " WHERE m.learner_id=? ORDER BY m.time_of_day, m.created",
        (today, learner_id)).fetchall()
    return {"medicines": [
        {"id": r[0], "name": r[1], "dose": r[2], "time_of_day": r[3],
         "notes": r[4], "taken_today": bool(r[5])} for r in rows]}


@router.post("/medicines")
def add_medicine(body: MedicineIn, learner_id: str = Depends(resolve_learner)):
    name = body.name.strip()[:80]
    if not name:
        raise HTTPException(status_code=422, detail="name is required")
    if body.time_of_day and not _TIME_RE.match(body.time_of_day):
        raise HTTPException(status_code=422, detail="time_of_day must be HH:MM")
    mid = "med_" + secrets.token_hex(8)
    _conn.execute(
        "INSERT INTO medicines (id, learner_id, name, dose, time_of_day, notes, created)"
        " VALUES (?,?,?,?,?,?,?)",
        (mid, learner_id, name, body.dose.strip()[:80],
         body.time_of_day, body.notes.strip()[:300], time.time()))
    _conn.commit()
    return {"id": mid}


@router.post("/medicines/{medicine_id}/taken")
def mark_taken(medicine_id: str, learner_id: str = Depends(resolve_learner)):
    """Idempotent per day: marking twice is one mark."""
    _owned("medicines", medicine_id, learner_id)
    _conn.execute("INSERT INTO medicine_taken (medicine_id, day, ts) VALUES (?,?,?)"
                  " ON CONFLICT (medicine_id, day) DO NOTHING",
                  (medicine_id, date.today().isoformat(), time.time()))
    _conn.commit()
    return {"ok": True}


@router.delete("/medicines/{medicine_id}")
def delete_medicine(medicine_id: str, learner_id: str = Depends(resolve_learner)):
    _owned("medicines", medicine_id, learner_id)
    _conn.execute("DELETE FROM medicine_taken WHERE medicine_id=?", (medicine_id,))
    _conn.execute("DELETE FROM medicines WHERE id=?", (medicine_id,))
    _conn.commit()
    return {"ok": True}


# ── Documents (Care Vault) ───────────────────────────────────────────────────

@router.get("/documents")
def list_documents(learner_id: str = Depends(resolve_learner)):
    rows = _conn.execute(
        "SELECT id, kind, filename, size, created FROM documents"
        " WHERE learner_id=? ORDER BY created DESC", (learner_id,)).fetchall()
    return {"documents": [
        {"id": r[0], "kind": r[1], "filename": r[2], "size": r[3], "created": r[4]}
        for r in rows]}


@router.post("/documents")
async def upload_document(file: UploadFile, kind: str = Form(default="other"),
                          learner_id: str = Depends(resolve_learner)):
    if kind not in DOC_KINDS:
        raise HTTPException(status_code=422, detail=f"kind must be one of {DOC_KINDS}")
    ext = os.path.splitext(file.filename or "")[1].lower()
    if ext not in _EXT_TYPES:
        raise HTTPException(status_code=422,
                            detail=f"file type must be one of {sorted(_EXT_TYPES)}")
    data = await security.read_capped(file)  # 413 past MAX_UPLOAD_BYTES
    doc_id = "doc_" + secrets.token_hex(8)
    path = _doc_path(learner_id, doc_id, ext)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)
    # Keep the display name, bounded; it never touches the filesystem.
    display = os.path.basename(file.filename or "document")[:120]
    _conn.execute(
        "INSERT INTO documents (id, learner_id, kind, filename, ext, size, created)"
        " VALUES (?,?,?,?,?,?,?)",
        (doc_id, learner_id, kind, display, ext, len(data), time.time()))
    _conn.commit()
    return {"id": doc_id, "size": len(data)}


@router.get("/documents/{doc_id}")
def download_document(doc_id: str, learner_id: str = Depends(resolve_learner)):
    row = _conn.execute(
        "SELECT ext, filename FROM documents WHERE id=? AND learner_id=?",
        (doc_id, learner_id)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="not found")
    path = _doc_path(learner_id, doc_id, row[0])
    if not os.path.exists(path):
        raise HTTPException(status_code=404, detail="file missing")
    return FileResponse(path, media_type=_EXT_TYPES[row[0]], filename=row[1])


@router.delete("/documents/{doc_id}")
def delete_document(doc_id: str, learner_id: str = Depends(resolve_learner)):
    row = _conn.execute(
        "SELECT ext FROM documents WHERE id=? AND learner_id=?",
        (doc_id, learner_id)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="not found")
    try:
        os.remove(_doc_path(learner_id, doc_id, row[0]))
    except FileNotFoundError:
        pass
    _conn.execute("DELETE FROM documents WHERE id=?", (doc_id,))
    _conn.commit()
    return {"ok": True}


# ── Appointments ─────────────────────────────────────────────────────────────

class AppointmentIn(BaseModel):
    title: str
    when_ts: float          # unix seconds, client-localized display
    location: str = ""
    notes: str = ""


@router.get("/appointments")
def list_appointments(learner_id: str = Depends(resolve_learner)):
    rows = _conn.execute(
        "SELECT id, title, when_ts, location, notes FROM appointments"
        " WHERE learner_id=? ORDER BY when_ts", (learner_id,)).fetchall()
    return {"appointments": [
        {"id": r[0], "title": r[1], "when_ts": r[2], "location": r[3], "notes": r[4]}
        for r in rows]}


@router.post("/appointments")
def add_appointment(body: AppointmentIn, learner_id: str = Depends(resolve_learner)):
    title = body.title.strip()[:120]
    if not title:
        raise HTTPException(status_code=422, detail="title is required")
    aid = "apt_" + secrets.token_hex(8)
    _conn.execute(
        "INSERT INTO appointments (id, learner_id, title, when_ts, location, notes, created)"
        " VALUES (?,?,?,?,?,?,?)",
        (aid, learner_id, title, body.when_ts,
         body.location.strip()[:160], body.notes.strip()[:500], time.time()))
    _conn.commit()
    return {"id": aid}


@router.delete("/appointments/{appointment_id}")
def delete_appointment(appointment_id: str, learner_id: str = Depends(resolve_learner)):
    _owned("appointments", appointment_id, learner_id)
    _conn.execute("DELETE FROM appointments WHERE id=?", (appointment_id,))
    _conn.commit()
    return {"ok": True}


# ── Care plan ────────────────────────────────────────────────────────────────

class PlanItemIn(BaseModel):
    title: str


@router.get("/care-plan")
def list_plan(learner_id: str = Depends(resolve_learner)):
    rows = _conn.execute(
        "SELECT id, title, done FROM care_plan_items WHERE learner_id=?"
        " ORDER BY done, created", (learner_id,)).fetchall()
    return {"items": [{"id": r[0], "title": r[1], "done": bool(r[2])} for r in rows]}


@router.post("/care-plan")
def add_plan_item(body: PlanItemIn, learner_id: str = Depends(resolve_learner)):
    title = body.title.strip()[:120]
    if not title:
        raise HTTPException(status_code=422, detail="title is required")
    pid = "plan_" + secrets.token_hex(8)
    _conn.execute("INSERT INTO care_plan_items (id, learner_id, title, created)"
                  " VALUES (?,?,?,?)", (pid, learner_id, title, time.time()))
    _conn.commit()
    return {"id": pid}


@router.post("/care-plan/{item_id}/toggle")
def toggle_plan_item(item_id: str, learner_id: str = Depends(resolve_learner)):
    _owned("care_plan_items", item_id, learner_id)
    _conn.execute("UPDATE care_plan_items SET done = 1 - done WHERE id=?", (item_id,))
    _conn.commit()
    return {"ok": True}


@router.delete("/care-plan/{item_id}")
def delete_plan_item(item_id: str, learner_id: str = Depends(resolve_learner)):
    _owned("care_plan_items", item_id, learner_id)
    _conn.execute("DELETE FROM care_plan_items WHERE id=?", (item_id,))
    _conn.commit()
    return {"ok": True}
