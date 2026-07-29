"""Care context + tools: onboarding, a journey-aware Today/Journey/Care, and the
in-conversation tools (reminders, medicines, appointments, Care Vault documents,
check-ins, symptom logs) plus the offline emergency profile.

Everything is scoped to `current_user`. Journey-specific content comes from
content.py, so a postpartum user never sees pregnancy-week cards — the single
biggest correctness gap in the prototypes, fixed at the source.
"""
import datetime
import json
import logging
import os
import shutil
import time
import uuid

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse
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

# A pregnancy is dated to about 40 weeks and is not left to run far past 42.
# Beyond that this stops asserting a week rather than counting into fiction.
MAX_TRACKED_WEEK = 42


def current_weeks(reported: int | None, reported_at: float | None,
                  now: float | None = None) -> int | None:
    """The week the user is in NOW, from the week they told us and when.

    Weeks were stored once at onboarding and read back verbatim for ever, so
    someone who said "24" stayed at week 24 permanently — the app showing them
    week-24 content in month nine, and after the birth, and a year later. A
    pregnancy week is the one number in this app that changes without anyone
    touching it, and it was the one number treated as fixed.

    Past MAX_TRACKED_WEEK this returns None instead of a bigger number. At that
    point the pregnancy has almost certainly ended and we were not told; saying
    nothing is honest, where "week 61" is not.
    """
    if reported is None:
        return None
    if reported_at is None:
        return reported
    now = now if now is not None else time.time()
    elapsed_weeks = int((now - reported_at) // (7 * 86400))
    weeks = reported + max(0, elapsed_weeks)
    return weeks if weeks <= MAX_TRACKED_WEEK else None


def _context(uid: str) -> dict:
    init()
    row = _conn.execute(
        "SELECT weeks, priorities, updated FROM care_context WHERE user_id=?", (uid,)).fetchone()
    if not row:
        return {"weeks": None, "priorities": [], "weeks_reported": None}
    return {
        "weeks": current_weeks(row[0], row[2]),
        "priorities": json.loads(row[1] or "[]"),
        # What they actually typed, for an editor to prefill with.
        "weeks_reported": row[0],
    }


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
    # And every stored file. "Delete all my data" that leaves someone's scans
    # on disk is the most consequential possible version of a control that
    # says one thing and does another.
    shutil.rmtree(os.path.join(FILES_DIR, uid), ignore_errors=True)
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


class CareContextIn(BaseModel):
    weeks: int | None = None
    priorities: list[str] | None = None


@router.patch("/care/context")
def update_care_context(body: CareContextIn, uid: str = Depends(current_user)):
    """Correct the week, or change what Aira focuses on.

    Both were set once during onboarding and then unreachable. Priorities drive
    what Today suggests, so someone whose needs changed — and in this app they
    change by design — was stuck with an answer they gave before they had used
    it. And a week can be entered wrongly as easily as any other number.

    Setting the week restarts the clock: `updated` becomes now, so the week
    counts forward from what the user has just told us rather than from the
    original onboarding date.
    """
    init()
    row = _conn.execute(
        "SELECT weeks, priorities, updated FROM care_context WHERE user_id=?", (uid,)).fetchone()
    weeks = row[0] if row else None
    priorities = json.loads(row[1] or "[]") if row else []
    updated = row[2] if row else time.time()

    if body.weeks is not None:
        if not 1 <= body.weeks <= MAX_TRACKED_WEEK:
            raise HTTPException(
                status_code=400,
                detail=f"a pregnancy week is between 1 and {MAX_TRACKED_WEEK}")
        weeks = body.weeks
        updated = time.time()
    if body.priorities is not None:
        priorities = [str(p)[:60] for p in body.priorities][:5]

    _conn.execute(
        "INSERT INTO care_context (user_id, weeks, priorities, updated) VALUES (?,?,?,?) "
        "ON CONFLICT(user_id) DO UPDATE SET weeks=excluded.weeks, "
        "priorities=excluded.priorities, updated=excluded.updated",
        (uid, weeks, json.dumps(priorities), updated))
    _conn.commit()
    return _context(uid)


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
        # Take weeks from the journey payload, not raw from care_context.
        # `journey_content` already refuses to return a pregnancy week for a
        # non-pregnant journey; /today read the stored number directly and so
        # ignored that. Someone who moved from pregnant to postpartum — a change
        # that is often a loss — kept being shown "Week 24" and a 24-week ring
        # beside postpartum copy, counting the weeks of a pregnancy they had
        # just told Aira had ended.
        "weeks": jc.get("weeks"),
        # What the user last typed, so an editor prefills with their own answer
        # rather than the advanced number — otherwise every save would nudge
        # the date forward by however long it had been.
        "weeks_reported": ctx.get("weeks_reported"),
        "next_action": action,
        # No `all_clear` here. It was hardcoded True and read by nobody — a field
        # that would have been actively wrong the first time a client trusted it,
        # since it stayed True with medicines due and the safety gate degraded.
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
    # Soonest first among those with a date; undated ones after, since "we
    # haven't fixed a time yet" belongs below "Tuesday at 10".
    appts = sorted(
        _list_items(uid, "appointment"),
        key=lambda a: (a.get("at") is None, a.get("at") or 0),
    )
    docs = _list_items(uid, "document")
    return {
        "appointments": appts,
        # Due means "not yet taken today", not "never marked done". See
        # mark_taken: the old flag retired a daily medicine on first use.
        "medicines_due": [m for m in meds if not _taken_today(m)],
        # Every medicine with its dose history, so a client can show what was
        # taken and when rather than only what is outstanding.
        "medicines": [
            {**m, "taken_today": _taken_today(m),
             "last_taken": max((float(t) for t in (m.get("taken") or [])), default=None)}
            for m in meds
        ],
        "documents_count": len(docs),
        "reminders": _list_items(uid, "reminder"),
        "care_plan": _care_plan(uid),
    }


def _care_plan(uid: str) -> dict:
    reminders = _list_items(uid, "reminder")
    done = sum(1 for r in reminders if r.get("done"))
    return {"total": len(reminders), "on_track": done}


# ── scoped read for an invited partner (partner.py) ──────────────────────────

def shared_view(owner_id: str, scopes: dict) -> dict:
    """The subset of `owner_id`'s care a partner has been granted.

    Lives here rather than in partner.py so this module keeps owning its own
    SQL, and so a new care item kind can only become partner-visible by being
    added deliberately here. Everything defaults to withheld: an unrecognised
    or missing scope yields nothing rather than falling through to the full row.

    `health_details` is the sensitive one and is off by default — symptoms,
    check-ins and documents are never included without it, and even then only
    counts and titles are returned, never note or symptom free text.
    """
    # `kind` travels with every row: it is the item's type, not content, and the
    # clients key their icon and title-flattening off it. Leaving it out made
    # every shared row render as an untyped one.
    out: dict = {}
    if scopes.get("appointments"):
        out["appointments"] = [
            {k: v for k, v in a.items() if k in ("id", "kind", "doctor", "place", "when")}
            for a in _list_items(owner_id, "appointment")
        ]
    if scopes.get("reminders"):
        out["reminders"] = [
            {k: v for k, v in r.items() if k in ("id", "kind", "title", "time", "repeat", "done")}
            for r in _list_items(owner_id, "reminder")
        ]
        out["medicines"] = [
            {k: v for k, v in m.items() if k in ("id", "kind", "name", "dose", "schedule", "time")}
            for m in _list_items(owner_id, "medicine") if not m.get("done")
        ]
    if scopes.get("health_details"):
        # Counts and labels only. The body of a symptom log or a private
        # check-in note is not something an invite link should hand over.
        out["symptom_count"] = len(_list_items(owner_id, "symptom"))
        out["checkin_count"] = len(_list_items(owner_id, "checkin"))
        out["documents_count"] = len(_list_items(owner_id, "document"))
    return out


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


class ReminderDoneIn(BaseModel):
    done: bool = True


@router.post("/care/reminders/{item_id}/done")
def mark_reminder_done(item_id: str, body: ReminderDoneIn | None = None,
                       uid: str = Depends(current_user)):
    """Complete — or re-open — a reminder.

    Toggleable, unlike `medicines/{item_id}/taken`: a dose marked taken is a
    statement about the past, but a reminder ticked by mistake is just a
    mistake, and a checklist you cannot untick is a trap. Without this endpoint
    `care_items.done` was unreachable for reminders, so `care_plan.on_track`
    could only ever report 0 and the clients' tick was necessarily inert.
    """
    done = True if body is None else body.done
    init()
    cur = _conn.execute("UPDATE care_items SET done=? WHERE id=? AND user_id=? "
                        "AND kind='reminder'", (1 if done else 0, item_id, uid))
    _conn.commit()
    if getattr(cur, "rowcount", 0) == 0:
        raise HTTPException(status_code=404, detail="not found")
    return {"ok": True, "done": done}


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


# How many dose timestamps to keep. Enough to show a week and answer "did I
# take it yesterday?" without the row growing without limit.
MAX_DOSE_HISTORY = 60


@router.post("/care/medicines/{item_id}/taken")
def mark_taken(item_id: str, uid: str = Depends(current_user)):
    """Record a dose. Does NOT retire the medicine.

    This used to set done=1, and `/care` filtered done medicines out of
    `medicines_due` — so the first time someone tapped "Taken" on a daily
    prenatal vitamin it vanished from the list permanently and the app silently
    stopped prompting for a medication they were meant to take every day. The
    one thing a medicine list exists to do, undone by using it once.

    A dose is an event, so it is stored as one. What's due is now a question
    about today rather than a permanent flag.
    """
    init()
    row = _conn.execute("SELECT data FROM care_items WHERE id=? AND user_id=? AND kind='medicine'",
                        (item_id, uid)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="not found")
    data = json.loads(row[0] or "{}")
    taken = [float(t) for t in (data.get("taken") or [])]
    taken.append(time.time())
    data["taken"] = taken[-MAX_DOSE_HISTORY:]
    _conn.execute("UPDATE care_items SET data=? WHERE id=? AND user_id=?",
                  (json.dumps(data), item_id, uid))
    _conn.commit()
    return {"ok": True, "taken": data["taken"]}


def _taken_today(medicine: dict, now: float | None = None) -> bool:
    """Whether a dose was recorded since local midnight.

    Local, not "within 24 hours": someone taking a tablet at 8am wants a fresh
    prompt the next morning, not one that slides an hour later each day.
    """
    stamps = medicine.get("taken") or []
    if not stamps:
        return False
    now = now if now is not None else time.time()
    midnight = datetime.datetime.fromtimestamp(now).replace(
        hour=0, minute=0, second=0, microsecond=0).timestamp()
    return max(float(t) for t in stamps) >= midnight


class AppointmentIn(BaseModel):
    doctor: str
    place: str | None = None
    # Free text, as typed: "Friday", "after the scan", "10:30 with Dr Shah".
    # Kept because people describe appointments to themselves in their own
    # words, and throwing that away to force a picker loses information.
    when: str | None = None
    # The same moment as a number, when the client could offer a picker.
    # Unix seconds. Optional on purpose: an appointment someone knows only as
    # "sometime next week" is still worth recording, and refusing to store it
    # until they commit to a time is how a care app ends up with nothing in it.
    #
    # Everything that needs ordering — upcoming vs past, what to remind about —
    # keys off this. `when` alone could not support any of it: no amount of
    # parsing turns "Friday" into a date without guessing which Friday.
    at: float | None = None
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
    content_type = (file.content_type or "").split(";")[0].strip().lower()
    if content_type not in ALLOWED_DOCUMENT_TYPES:
        raise HTTPException(
            status_code=415,
            detail="only PDF, JPEG and PNG documents can be stored")
    data = await security.read_capped(file)
    item = _add_item(uid, "document", {
        "name": file.filename or "document", "type": kind,
        "size": len(data), "content_type": content_type,
        "use_in_answers": False,  # opt-in only, after approval
    })
    _store_document(uid, item["id"], data)
    return item


@router.get("/care/documents/{item_id}/file")
def get_document_file(item_id: str, uid: str = Depends(current_user)):
    """The file itself, for the person who uploaded it.

    Scoped to `current_user` like everything else here — the path is derived
    from the caller's own id, so one user's id can never address another's
    file even if they guess an item id.

    Served as an attachment with the stored content type. Never inline: these
    are user-supplied bytes, and a document rendered in place is a way to run
    someone else's content in this origin.
    """
    kind, data = _get_item(uid, item_id)
    if kind != "document":
        raise HTTPException(status_code=404, detail="not found")
    path = _document_path(uid, item_id)
    if not os.path.exists(path):
        # Uploaded before files were stored, or removed out of band. Say so
        # rather than serving an empty file that looks like a corrupt scan.
        raise HTTPException(status_code=410, detail="this file is no longer stored")
    return FileResponse(
        path,
        media_type=data.get("content_type") or "application/octet-stream",
        filename=data.get("name") or "document",
        content_disposition_type="attachment",
    )


# ── document bytes ───────────────────────────────────────────────────────────
#
# Uploads used to be read and thrown away: only the name, type and size were
# kept. The Care Vault then listed the file with the right filename and the
# right size, and the bytes did not exist. A user uploading their 20-week scan
# had every reason to believe they had a copy, and no way to discover they
# didn't until they went looking for it — which is the worst possible moment.
#
# Files live on disk beside the database rather than in it: a 20 MB scan in a
# row makes every unrelated query carry it. In production this points at object
# storage; the shape here — write on upload, read through an authorised route,
# delete with the item — is the same either way.
FILES_DIR = os.environ.get("AIRA_FILES_DIR") or os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "files")

# Only what the clients offer and what a care record actually contains. An
# allow-list rather than a block-list, and enforced here rather than only in the
# picker, because the picker is a suggestion and this is the door.
ALLOWED_DOCUMENT_TYPES = {"application/pdf", "image/jpeg", "image/png"}


def _document_path(uid: str, item_id: str) -> str:
    # The item id is server-generated hex; the user's filename never touches the
    # path, so there is nothing here to traverse with.
    safe = "".join(c for c in item_id if c.isalnum() or c == "_")
    return os.path.join(FILES_DIR, uid, safe)


def _store_document(uid: str, item_id: str, data: bytes) -> None:
    path = _document_path(uid, item_id)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(data)


def _delete_document_file(uid: str, item_id: str) -> None:
    try:
        os.remove(_document_path(uid, item_id))
    except FileNotFoundError:
        pass


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


# ── editing and removing care items ──────────────────────────────────────────
#
# Everything above could only be CREATED. There was no PATCH and no DELETE for
# any kind, which meant a typo in a doctor's name was permanent, a cancelled
# appointment stayed on Today forever, and — the one that matters — a medicine
# the care team had stopped went on being listed as due. An app that keeps
# prompting someone to take a discontinued medicine is misleading about their
# care, however carefully the copy says Aira never changes medication.
#
# One generic pair rather than twelve near-identical routes. `kind` lives on the
# row, so the id alone is enough, and every query is scoped by user_id — an item
# id is not a capability.

# What a user may change, per kind. An allow-list rather than a merge of whatever
# arrives: without it a client could write `done`, `kind` or invented keys into
# the payload blob and quietly reshape the row.
_EDITABLE_FIELDS = {
    "reminder": {"title", "time", "repeat"},
    "medicine": {"name", "dose", "schedule", "time"},
    "appointment": {"doctor", "place", "when", "at", "notes"},
    "document": {"type"},
    "checkin": {"feeling", "sleep_hours", "note"},
    "symptom": {"what", "severity", "started", "pattern"},
}


def _get_item(uid: str, item_id: str) -> tuple[str, dict]:
    init()
    row = _conn.execute("SELECT kind, data FROM care_items WHERE id=? AND user_id=?",
                        (item_id, uid)).fetchone()
    if not row:
        # 404 rather than 403 for someone else's id: distinguishing "not yours"
        # from "doesn't exist" would confirm that an id is real.
        raise HTTPException(status_code=404, detail="not found")
    return row[0], json.loads(row[1] or "{}")


@router.patch("/care/items/{item_id}")
def update_item(item_id: str, body: dict, uid: str = Depends(current_user)):
    """Edit a care item. Partial: only the fields supplied change."""
    kind, data = _get_item(uid, item_id)
    allowed = _EDITABLE_FIELDS.get(kind, set())
    unknown = set(body) - allowed
    if unknown:
        raise HTTPException(
            status_code=400,
            detail=f"cannot edit {', '.join(sorted(unknown))} on a {kind}")
    if not body:
        raise HTTPException(status_code=400, detail="nothing to change")
    data.update({k: v for k, v in body.items()})
    _conn.execute("UPDATE care_items SET data=? WHERE id=? AND user_id=?",
                  (json.dumps(data), item_id, uid))
    _conn.commit()
    row = _conn.execute("SELECT done, created FROM care_items WHERE id=?", (item_id,)).fetchone()
    return {"id": item_id, "kind": kind, "done": bool(row[0]), "created": row[1], **data}


@router.delete("/care/items/{item_id}")
def delete_item(item_id: str, uid: str = Depends(current_user)):
    """Remove a care item for good.

    A real delete, not a hidden flag. Someone removing a medicine they no longer
    take, or a scan they uploaded by mistake, means it should be gone — leaving
    it in the row and merely hiding it would keep health data they asked to
    remove.
    """
    init()
    cur = _conn.execute("DELETE FROM care_items WHERE id=? AND user_id=?", (item_id, uid))
    _conn.commit()
    if getattr(cur, "rowcount", 0) == 0:
        raise HTTPException(status_code=404, detail="not found")
    # The bytes go with the row. Removing the record and leaving the scan on
    # disk would be exactly the "hidden flag" this route exists to avoid, one
    # layer down.
    _delete_document_file(uid, item_id)
    return {"ok": True}


# ── the timeline ─────────────────────────────────────────────────────────────

@router.get("/care/timeline")
def timeline(limit: int = 50, uid: str = Depends(current_user)):
    """Check-ins and symptom logs, newest first.

    These were write-only. The clients said "Add to timeline", the note field
    said "a private note for your timeline", and the success message said
    "Added to your timeline" — while nothing could read either kind back. Someone
    logging symptoms for three weeks to show their doctor arrived at the
    appointment with nothing to show.
    """
    items = _list_items(uid, "checkin") + _list_items(uid, "symptom")
    items.sort(key=lambda i: i.get("created") or 0, reverse=True)
    return {"items": items[:max(1, min(limit, 200))]}


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
