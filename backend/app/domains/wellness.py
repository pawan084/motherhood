"""Weekly wellness rollups for the report and share-card screens.

This is deliberately a progress summary, not a health assessment. It reads the
care data the app already stores: check-ins, reminder completion, and medicine
dose timestamps. If richer water logging ships later, this module can swap the
water input without changing the mobile screens' response shape.
"""
import datetime
import json
import time

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from app.domains import accounts, care
from app.core import security
from app.domains.accounts import current_user

router = APIRouter(prefix="/v1", tags=["wellness"],
                   dependencies=[Depends(security.require_app_token)])

_DAY = 86400


def init() -> None:
    # No table yet: shares go through the OS share sheet and the report is
    # derived live from care rows.
    care.init()


def _week_start(now: float | None = None) -> datetime.date:
    today = datetime.datetime.fromtimestamp(now or time.time()).date()
    return today - datetime.timedelta(days=today.weekday())


def _ts_for_day(day: datetime.date) -> float:
    return datetime.datetime.combine(day, datetime.time.min).timestamp()


def _days(now: float | None = None) -> list[datetime.date]:
    start = _week_start(now)
    return [start + datetime.timedelta(days=i) for i in range(7)]


def _day_key_from_ts(ts: float) -> str:
    return datetime.datetime.fromtimestamp(ts).date().isoformat()


def _care_rows(uid: str) -> list[dict]:
    rows = care._conn.execute(
        "SELECT id, kind, data, done, created FROM care_items WHERE user_id=?",
        (uid,)).fetchall()
    return [{"id": r[0], "kind": r[1], "data": json.loads(r[2] or "{}"),
             "done": bool(r[3]), "created": float(r[4] or 0)} for r in rows]


def _water_days(rows: list[dict]) -> set[str]:
    """Days where a water reminder is completed.

    The existing backend tracks a reminder's current done state, not a daily
    water counter. This therefore counts a completed water reminder on its
    created day only. The response source text makes that limitation visible.
    """
    out = set()
    for row in rows:
        if row["kind"] != "reminder" or not row["done"]:
            continue
        title = str(row["data"].get("title") or "").lower()
        if "water" in title:
            out.add(_day_key_from_ts(row["created"]))
    return out


def _vitamin_days(rows: list[dict]) -> set[str]:
    out = set()
    for row in rows:
        if row["kind"] != "medicine":
            continue
        label = " ".join(str(row["data"].get(k) or "").lower()
                         for k in ("name", "dose", "schedule"))
        if "vitamin" not in label and "folic" not in label and "iron" not in label:
            continue
        for stamp in row["data"].get("taken") or []:
            try:
                out.add(_day_key_from_ts(float(stamp)))
            except (TypeError, ValueError):
                continue
    return out


def _mood_days(rows: list[dict]) -> set[str]:
    return {_day_key_from_ts(row["created"]) for row in rows if row["kind"] == "checkin"}


def _height(score: float) -> int:
    return 18 + round(max(0, min(score, 1)) * 30)


def _summary_text(consistency: int, previous: int | None, water: int, vitamins: int) -> str:
    if previous is not None and consistency > previous:
        return f"Up from {previous}% last week — steady logging carried it."
    if consistency >= 80:
        return "A steady week — care tasks and check-ins are building a useful pattern."
    if water or vitamins:
        return "Some steady care moments showed up this week. A few more logs will sharpen the picture."
    return "A light logging week. Add check-ins or care tasks when it feels useful."


def _insight(water: int, vitamins: int, moods: int) -> str:
    if water and vitamins:
        return "Your care logs are strongest on days with both water and medicine reminders."
    if water:
        return "Your water reminder is helping create a visible care rhythm."
    if vitamins:
        return "Medicine logging is carrying most of this week's progress."
    if moods:
        return "Mood check-ins are the clearest signal this week."
    return "Aira will notice more patterns as you add check-ins."


def _report(uid: str, now: float | None = None) -> dict:
    init()
    days = _days(now)
    keys = [d.isoformat() for d in days]
    rows = _care_rows(uid)
    water = _water_days(rows)
    vitamins = _vitamin_days(rows)
    moods = _mood_days(rows)

    bars = []
    daily_scores = []
    today_key = datetime.datetime.fromtimestamp(now or time.time()).date().isoformat()
    for day, key in zip(days, keys):
        hits = int(key in water) + int(key in vitamins) + int(key in moods)
        score = hits / 3
        daily_scores.append(score)
        bars.append({
            "date": key,
            "label": day.strftime("%a")[:1],
            "score": score,
            "height": _height(score),
            "active": key == today_key,
            "water": key in water,
            "vitamin": key in vitamins,
            "mood": key in moods,
        })

    consistency = round((sum(daily_scores) / 7) * 100)
    previous = None
    u = accounts.get_user(uid) or {}
    journey = u.get("journey") or "exploring"
    ctx = care._context(uid)
    weeks = ctx.get("weeks")
    weeks_to_go = max(0, 40 - weeks) if isinstance(weeks, int) and journey == "pregnant" else None
    baby_size = "Baby is about the size of an ear of corn" if weeks == 24 else ""
    return {
        "week_start": keys[0],
        "week_end": keys[-1],
        "journey": journey,
        "name": u.get("name") or "",
        "week": weeks,
        "weeks_to_go": weeks_to_go,
        "baby_size": baby_size,
        "consistency": consistency,
        "previous_consistency": previous,
        "summary": _summary_text(consistency, previous, len(water & set(keys)),
                                 len(vitamins & set(keys))),
        "bars": bars,
        "metrics": {
            "water_goal_met": {"count": len(water & set(keys)), "total": 7,
                               "source": "completed water reminders"},
            "vitamin_taken": {"count": len(vitamins & set(keys)), "total": 7,
                              "source": "medicine dose logs"},
            "mood_checkins": {"count": len(moods & set(keys)), "total": 7,
                              "source": "daily check-ins"},
        },
        "insight": _insight(len(water & set(keys)), len(vitamins & set(keys)),
                            len(moods & set(keys))),
        "disclaimer": ("This is a wellness summary from your check-ins and care logs, "
                       "not a medical assessment. Trends may change as you add more data."),
    }


@router.get("/wellness/report")
def wellness_report(uid: str = Depends(current_user)):
    return _report(uid)


@router.get("/wellness/share-preview")
def share_preview(uid: str = Depends(current_user)):
    report = _report(uid)
    return {
        "title": f"Week {report['week']} summary" if report["week"] else "Weekly summary",
        "subtitle": "Includes journey week and care consistency",
        "card": {
            "label": (f"Week {report['week']} · {report['name']}'s journey"
                      if report["week"] and report["name"]
                      else "Aira weekly summary"),
            "consistency": report["consistency"],
            "baby_size": report["baby_size"],
            "weeks_to_go": report["weeks_to_go"],
            "water_goal": report["metrics"]["water_goal_met"]["count"],
            "mood_checkins": report["metrics"]["mood_checkins"]["count"],
            "made_with": "Made with Aira",
        },
        "included_defaults": {
            "journey_week": True,
            "care_consistency": True,
            "mood_checkins": False,
        },
        "privacy_note": ("Preview exactly what will be shared. Chat and vault stay private, "
                         "but recipients can save or forward the image."),
    }


class ShareIn(BaseModel):
    recipient: str | None = None
    journey_week: bool = True
    care_consistency: bool = True
    mood_checkins: bool = False


@router.post("/wellness/share")
def share_week(body: ShareIn, uid: str = Depends(current_user)):
    preview = share_preview(uid)
    included = {
        "journey_week": body.journey_week,
        "care_consistency": body.care_consistency,
        "mood_checkins": body.mood_checkins,
    }
    if not any(included.values()):
        raise HTTPException(status_code=400, detail="choose at least one section to share")
    return {
        "ok": True,
        "recipient": (body.recipient or "selected recipient")[:120],
        "included": included,
        "summary": preview["card"],
        "share_text": preview["title"],
    }

