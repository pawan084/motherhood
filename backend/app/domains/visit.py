"""Visit Copilot: appointment prep questions and shareable visit list.

Appointments themselves already live in care_items. This module owns only the
question checklist around a visit, so the Care detail screen and Visit Copilot
do not drift into two appointment stores.
"""
import time
import uuid

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from app.domains import care
from app.core import db
from app.core import security
from app.domains.accounts import current_user

router = APIRouter(prefix="/v1", tags=["visit-copilot"],
                   dependencies=[Depends(security.require_app_token)])
_conn = None


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS visit_questions ("
              " id TEXT PRIMARY KEY, user_id TEXT NOT NULL, text TEXT NOT NULL,"
              " checked INTEGER DEFAULT 0, source TEXT DEFAULT 'user',"
              " created REAL NOT NULL, updated REAL NOT NULL)")
    c.execute("CREATE INDEX IF NOT EXISTS visit_questions_user "
              "ON visit_questions(user_id, created)")
    c.commit()
    _conn = c


def _questions(uid: str) -> list[dict]:
    init()
    rows = _conn.execute(
        "SELECT id, text, checked, source, created, updated FROM visit_questions "
        "WHERE user_id=? ORDER BY created",
        (uid,)).fetchall()
    return [{"id": r[0], "text": r[1], "checked": bool(r[2]), "source": r[3],
             "created": r[4], "updated": r[5]} for r in rows]


def _next_appointment(uid: str) -> dict | None:
    care.init()
    appts = care.care(uid).get("appointments") or []
    now = time.time()
    future = [a for a in appts if a.get("at") is None or float(a.get("at") or 0) >= now]
    if not future:
        return None
    appt = future[0]
    return {
        "id": appt["id"],
        "doctor": appt.get("doctor") or "Care team",
        "place": appt.get("place"),
        "when": appt.get("when"),
        "at": appt.get("at"),
        "notes": appt.get("notes"),
        "title": _appointment_title(appt),
        "subtitle": _appointment_subtitle(appt),
    }


def _appointment_title(appt: dict) -> str:
    when = appt.get("when")
    if when:
        return f"{appt.get('doctor') or 'Appointment'} · {when}"
    return appt.get("doctor") or "Next appointment"


def _appointment_subtitle(appt: dict) -> str:
    parts = [p for p in (appt.get("doctor"), appt.get("place")) if p]
    return " · ".join(parts)


def _seed_questions(uid: str) -> None:
    if _questions(uid):
        return
    now = time.time()
    seeds = [
        "Confirm glucose test timing",
        "Ask about safe travel at week 26",
    ]
    for text in seeds:
        qid = "vis_" + uuid.uuid4().hex[:12]
        _conn.execute("INSERT INTO visit_questions "
                      "(id, user_id, text, checked, source, created, updated) "
                      "VALUES (?,?,?,?,?,?,?)",
                      (qid, uid, text, 0, "suggested", now, now))
    _conn.commit()


def _suggestion(uid: str) -> dict:
    qs = " ".join(q["text"].lower() for q in _questions(uid))
    if "iron" in qs:
        text = "Review iron levels and fatigue pattern."
    else:
        text = "Aira suggests: ask about iron levels"
    return {
        "text": text,
        "basis": "AI suggestion based on recent care context — not a clinician priority.",
    }


@router.get("/visit-copilot")
def visit_copilot(uid: str = Depends(current_user)):
    _seed_questions(uid)
    appointment = _next_appointment(uid)
    questions = _questions(uid)
    return {
        "has_appointment": appointment is not None,
        "appointment": appointment,
        "questions": questions,
        "count": len(questions),
        "checked_count": sum(1 for q in questions if q["checked"]),
        "auto_saved_label": "Auto-saved from Chat",
        "suggestion": _suggestion(uid),
        "empty": {
            "title": "No appointment added",
            "body": "You can still collect questions now and add the visit details later.",
            "privacy_note": "Aira never sends anything to a clinician without your review.",
        } if appointment is None else None,
    }


class QuestionIn(BaseModel):
    text: str
    source: str = "user"


@router.post("/visit-copilot/questions")
def add_question(body: QuestionIn, uid: str = Depends(current_user)):
    text = (body.text or "").strip()
    if not text:
        raise HTTPException(status_code=400, detail="question text is required")
    init()
    now = time.time()
    qid = "vis_" + uuid.uuid4().hex[:12]
    source = body.source if body.source in {"user", "chat", "suggested"} else "user"
    _conn.execute("INSERT INTO visit_questions "
                  "(id, user_id, text, checked, source, created, updated) "
                  "VALUES (?,?,?,?,?,?,?)",
                  (qid, uid, text[:240], 0, source, now, now))
    _conn.commit()
    return {"id": qid, "text": text[:240], "checked": False, "source": source,
            "created": now, "updated": now}


class QuestionPatchIn(BaseModel):
    text: str | None = None
    checked: bool | None = None


@router.patch("/visit-copilot/questions/{question_id}")
def update_question(question_id: str, body: QuestionPatchIn, uid: str = Depends(current_user)):
    init()
    row = _conn.execute(
        "SELECT text, checked, source, created FROM visit_questions WHERE id=? AND user_id=?",
        (question_id, uid)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="not found")
    text = row[0]
    checked = bool(row[1])
    if body.text is not None:
        text = body.text.strip()[:240]
        if not text:
            raise HTTPException(status_code=400, detail="question text is required")
    if body.checked is not None:
        checked = body.checked
    now = time.time()
    _conn.execute("UPDATE visit_questions SET text=?, checked=?, updated=? "
                  "WHERE id=? AND user_id=?",
                  (text, 1 if checked else 0, now, question_id, uid))
    _conn.commit()
    return {"id": question_id, "text": text, "checked": checked, "source": row[2],
            "created": row[3], "updated": now}


@router.delete("/visit-copilot/questions/{question_id}")
def delete_question(question_id: str, uid: str = Depends(current_user)):
    init()
    cur = _conn.execute("DELETE FROM visit_questions WHERE id=? AND user_id=?",
                        (question_id, uid))
    _conn.commit()
    if getattr(cur, "rowcount", 0) == 0:
        raise HTTPException(status_code=404, detail="not found")
    return {"ok": True}


class ShareIn(BaseModel):
    recipient: str | None = None


@router.post("/visit-copilot/share")
def share_visit_list(body: ShareIn, uid: str = Depends(current_user)):
    data = visit_copilot(uid)
    questions = [q for q in data["questions"] if q["text"]]
    if not questions:
        raise HTTPException(status_code=400, detail="add at least one question before sharing")
    doctor = (data["appointment"] or {}).get("doctor") if data["appointment"] else None
    return {
        "ok": True,
        "recipient": (body.recipient or doctor or "care team")[:120],
        "appointment": data["appointment"],
        "questions": questions,
        "share_text": f"{len(questions)} questions for {doctor or 'my visit'}",
        "privacy_note": "Nothing is sent until you choose a recipient in the share sheet.",
    }


def export_user(uid: str) -> dict:
    return {"questions": _questions(uid)}


def delete_user(uid: str) -> int:
    init()
    cur = _conn.execute("DELETE FROM visit_questions WHERE user_id=?", (uid,))
    _conn.commit()
    return getattr(cur, "rowcount", 0) or 0

