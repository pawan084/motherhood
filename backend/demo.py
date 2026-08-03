"""Demo personas: one-call setup of a fully-populated learner for each journey
stage. FOR DEMOS AND DEV ONLY — the router is mounted only when dev login is
allowed, and never in production (same gate as /account/dev-login).

POST /demo/{persona} mints a fresh device credential, populates a believable
care context + memories + care data for that persona, and returns the device
token. The web client's `?demo=<persona>` boot hook calls this and drops the
token into localStorage, so a demo browser lands in a lived-in app instead of
an empty one.

Every row goes through the same modules the product uses (care_context.upsert,
memory.remember, care tables) — the demo exercises real code paths, it doesn't
paint fake pixels.
"""
import time
from datetime import date, timedelta

from fastapi import APIRouter, HTTPException

import care
import care_context
import device_auth
import memory
import wellness

router = APIRouter(tags=["demo"])


def _iso(days_from_today: int) -> str:
    return (date.today() + timedelta(days=days_from_today)).isoformat()


PERSONAS = {
    "pregnant": {
        "context": dict(stage="pregnant", due_date=lambda: _iso(112),  # week 24
                        display_name="Maya", language="en"),
        "memories": [
            {"kind": "fact", "content": "first pregnancy"},
            {"kind": "concern", "content": "worried about fatigue at work"},
            {"kind": "symptom", "content": "lower back ache at night"},
            {"kind": "preference", "content": "prefers brief, warm answers"},
        ],
        "medicines": [("Prenatal vitamin", "1 tablet", "20:00"),
                      ("Iron supplement", "1 tablet", "08:30")],
        "appointment": ("Antenatal check — Dr. Mehta", 7, "City Women's Clinic"),
        "plan": ["Prepare questions for the next visit",
                 "Log fatigue for three days", "Try one two-minute reset"],
    },
    "postpartum": {
        "context": dict(stage="postpartum", birth_date=lambda: _iso(-21),  # week 4
                        display_name="Asha", language="hi-Latn"),
        "memories": [
            {"kind": "fact", "content": "baby is three weeks old"},
            {"kind": "concern", "content": "baby ko raat me neend nahi aati"},
            {"kind": "symptom", "content": "thakaan aur neend ki kami"},
            {"kind": "preference", "content": "Hinglish me baat karna pasand hai"},
        ],
        "medicines": [("Iron + calcium", "1 tablet", "09:00")],
        "appointment": ("Six-week check", 18, "Community health centre"),
        "plan": ["Six-week check ke liye sawaal likhna",
                 "Din me ek baar thodi walk", "Apne liye 10 minute"],
    },
    "trying_to_conceive": {
        "context": dict(stage="trying_to_conceive",
                        display_name="Divya", language="en"),
        "memories": [
            {"kind": "fact", "content": "trying for about eight months"},
            {"kind": "concern", "content": "anxious each cycle"},
            {"kind": "preference", "content": "wants evidence-based answers"},
        ],
        "medicines": [("Folic acid", "400 mcg", "21:00")],
        "appointment": ("Preconception consult", 12, "Family clinic"),
        "plan": ["Track cycle this month", "Book the preconception consult"],
    },
}


@router.post("/demo/{persona}")
def create_demo(persona: str):
    spec = PERSONAS.get(persona)
    if not spec:
        raise HTTPException(status_code=404,
                            detail=f"persona must be one of {sorted(PERSONAS)}")
    did = device_auth.new_device_id()
    token = device_auth._sign(did)

    ctx = {k: (v() if callable(v) else v) for k, v in spec["context"].items()}
    care_context.upsert(did, ctx.get("stage"), ctx.get("due_date"),
                        ctx.get("birth_date"), ctx.get("display_name"),
                        ctx.get("language"))
    memory.remember(did, spec["memories"])
    conn = care._conn
    now = time.time()
    for i, (name, dose, at) in enumerate(spec["medicines"]):
        conn.execute(
            "INSERT INTO medicines (id, learner_id, name, dose, time_of_day, created)"
            " VALUES (?,?,?,?,?,?)",
            (f"med_demo_{did[-8:]}_{i}", did, name, dose, at, now))
    title, days, location = spec["appointment"]
    conn.execute(
        "INSERT INTO appointments (id, learner_id, title, when_ts, location, created)"
        " VALUES (?,?,?,?,?,?)",
        (f"apt_demo_{did[-8:]}", did, title,
         time.time() + days * 86400 + 10 * 3600, location, now))
    for i, item in enumerate(spec["plan"]):
        conn.execute(
            "INSERT INTO care_plan_items (id, learner_id, title, done, created)"
            " VALUES (?,?,?,?,?)",
            (f"plan_demo_{did[-8:]}_{i}", did, item, 1 if i == 0 else 0, now))
    conn.commit()
    # A lived-in Me tab: recent moods + the seeded habit reminders with a
    # couple of water ticks (all through the real module).
    wconn = wellness._conn
    for offset, mood in enumerate(("okay", "tired", "great")):
        wconn.execute(
            "INSERT INTO moods (learner_id, day, mood, ts) VALUES (?,?,?,?)"
            " ON CONFLICT (learner_id, day) DO NOTHING",
            (did, _iso(-offset), mood, now))
    wellness._seed_reminders(did)
    water = wconn.execute(
        "SELECT id FROM reminders WHERE learner_id=? AND kind='water'",
        (did,)).fetchone()
    if water:
        wconn.execute(
            "INSERT INTO reminder_ticks (reminder_id, day, ticks, ts) VALUES (?,?,3,?)"
            " ON CONFLICT (reminder_id, day) DO NOTHING",
            (water[0], _iso(0), now))
    wconn.commit()
    return {"device_token": token, "persona": persona,
            "display_name": ctx.get("display_name")}
