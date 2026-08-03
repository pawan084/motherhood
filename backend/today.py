"""GET /today — the proactive brain of the Me tab.

One endpoint assembles everything that makes the home screen worth opening
several times a day, server-side so web and Android share the logic:

- **greeting slot** (morning/afternoon/evening) for time-aware copy
- **week + day-in-week + days-to-go** — finer-grained anticipation than the
  weekly number alone ("Week 8 · Day 3")
- **tip of the day** — deterministic daily rotation from seed_tips (changes
  every midnight; no LLM, no storage)
- **focus cards** — 0-2 rule-ranked proactive nudges computed from the
  learner's OWN state right now:
    mood_checkin      no mood logged today (from mid-morning)
    water_pace        water ticks behind the day's pace (from midday)
    appointment_soon  an appointment within ~36 hours
    milestone_week    a journey milestone within the next 7 days
    evening_wrapup    evening + care items still open
  Ranked by immediacy; capped at 2 — research note: few actionable nudges
  beat a feed of many (notification-fatigue findings).

The client hour comes from `?hour=` (0-23, the DEVICE's local hour) because
the server's clock is not the learner's timezone. Falls back to server time.

Known limit: "today" (tip rotation, mood/tick day checks) still uses the
SERVER's date, matching how /moods and /reminders stamp their `day` rows.
Fine while server and learners share a timezone (dev, single-region);
multi-region prod needs a device-date param threaded through all four.
"""
from datetime import date, datetime, timedelta

from fastapi import APIRouter, Depends, Query

import care
import care_context
import seed_journey
import seed_tips
import wellness
from device_auth import resolve_learner

router = APIRouter(tags=["today"])


def _tip_for(stage: str, week: int | None, today: date) -> dict | None:
    if stage == "pregnant":
        trimester = 1 if (week or 1) <= 13 else 2 if (week or 1) <= 27 else 3
        pool = seed_tips.TIPS.get(f"pregnant_t{trimester}", [])
    else:
        pool = seed_tips.TIPS.get(stage, [])
    if not pool:
        return None
    index = (today.toordinal() + (week or 0)) % len(pool)
    return {"id": f"{stage}-{index}", "text": pool[index]}


@router.get("/today")
def get_today(hour: int | None = Query(default=None, ge=0, le=23),
              learner_id: str = Depends(resolve_learner)):
    ctx = care_context.get(learner_id)
    now_hour = hour if hour is not None else datetime.now().hour
    slot = ("morning" if now_hour < 12 else
            "afternoon" if now_hour < 17 else "evening")
    today = date.today()

    out: dict = {"slot": slot, "context": None, "tip": None, "focus": []}
    if not ctx:
        return out

    stage, week = ctx["stage"], ctx.get("week")
    total = {"pregnant": 40, "postpartum": 52}.get(stage)
    # Day-within-week: derived from the anchor date so it advances daily.
    day_in_week = None
    days_to_go = None
    if stage == "pregnant" and ctx.get("due_date"):
        due = date.fromisoformat(ctx["due_date"])
        days_to_go = max(0, (due - today).days)
        day_in_week = 7 - (days_to_go % 7 or 7) + 1
    elif stage == "postpartum" and ctx.get("birth_date"):
        born = date.fromisoformat(ctx["birth_date"])
        day_in_week = ((today - born).days % 7) + 1

    size = (seed_journey.WEEK_SIZES.get(week)
            if stage == "pregnant" and week else None)
    out["context"] = {
        "stage": stage, "week": week, "total_weeks": total,
        "day_in_week": day_in_week, "days_to_go": days_to_go,
        "size": ({"emoji": size[0], "label": size[1]} if size else None),
        "display_name": ctx.get("display_name") or "",
    }
    out["tip"] = _tip_for(stage, week, today)

    # ── Focus rules (each computed from the learner's own live state) ───────
    focus: list[dict] = []
    today_iso = today.isoformat()

    mood_today = wellness._conn.execute(
        "SELECT 1 FROM moods WHERE learner_id=? AND day=?",
        (learner_id, today_iso)).fetchone()
    if not mood_today and now_hour >= 9:
        focus.append({
            "kind": "mood_checkin", "priority": 2,
            "title": "How are you feeling?",
            "body": "A ten-second check-in keeps your week's picture honest.",
        })

    water = wellness._conn.execute(
        "SELECT r.id, r.target_per_day, COALESCE(t.ticks, 0)"
        " FROM reminders r LEFT JOIN reminder_ticks t"
        " ON t.reminder_id = r.id AND t.day = ?"
        " WHERE r.learner_id=? AND r.kind='water'",
        (today_iso, learner_id)).fetchone()
    if water and now_hour >= 12:
        rid, target, ticks = water
        expected = round(target * min(1.0, (now_hour - 7) / 14))  # 7am-9pm pace
        if ticks < expected:
            focus.append({
                "kind": "water_pace", "priority": 3,
                "title": "Water check",
                "body": f"{ticks} of {target} glasses so far — a good moment for one.",
            })

    upcoming = care._conn.execute(
        "SELECT title, when_ts FROM appointments WHERE learner_id=?"
        " AND when_ts BETWEEN ? AND ? ORDER BY when_ts LIMIT 1",
        (learner_id, datetime.now().timestamp(),
         (datetime.now() + timedelta(hours=36)).timestamp())).fetchone()
    if upcoming:
        when = datetime.fromtimestamp(upcoming[1])
        focus.append({
            "kind": "appointment_soon", "priority": 1,
            "title": f"{upcoming[0]} · {when.strftime('%a %H:%M')}",
            "body": "Worth jotting the questions you want answered while they're fresh.",
        })

    if week is not None:
        milestone = next(
            (m for m in seed_journey.MILESTONES.get(stage, [])
             if 0 < m["week"] - week <= 1 or
             (m["week"] == week)), None)
        if milestone:
            focus.append({
                "kind": "milestone_week", "priority": 2,
                "title": f"{milestone['emoji']} {milestone['label']} is close",
                "body": f"Week {milestone['week']} on your path — a good week to prepare.",
            })

    if slot == "evening":
        # Dedupe: if the water card already fired, water is spoken for —
        # the wrap-up must count only OTHER open items, and stay silent
        # when there are none (two cards nagging about the same glass of
        # water is noise, not proactivity).
        exclude_water = any(f["kind"] == "water_pace" for f in focus)
        open_care = wellness._conn.execute(
            "SELECT COUNT(*) FROM reminders r LEFT JOIN reminder_ticks t"
            " ON t.reminder_id = r.id AND t.day = ?"
            " WHERE r.learner_id=? AND COALESCE(t.ticks,0) < r.target_per_day"
            + (" AND r.kind != 'water'" if exclude_water else ""),
            (today_iso, learner_id)).fetchone()[0]
        if open_care:
            focus.append({
                "kind": "evening_wrapup", "priority": 4,
                "title": "Close out the day",
                "body": f"{open_care} care item{'s' if open_care != 1 else ''} still open — or let today be enough.",
            })

    # Few actionable nudges beat many: rank by immediacy, cap at 2.
    out["focus"] = sorted(focus, key=lambda f: f["priority"])[:2]
    return out
