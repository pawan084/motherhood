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
import time
from datetime import date, datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel

import care
import care_context
import seed_journey
import seed_tips
import wellness
from device_auth import resolve_learner

router = APIRouter(tags=["today"])


def _tip_for(stage: str, week: int | None, today: date,
             lang: str = "en") -> dict | None:
    # Language pools fall back to English until reviewed copy exists.
    tips = seed_tips.TIPS_BY_LANG.get(lang) or seed_tips.TIPS
    if stage == "pregnant":
        trimester = 1 if (week or 1) <= 13 else 2 if (week or 1) <= 27 else 3
        pool = tips.get(f"pregnant_t{trimester}") or seed_tips.TIPS.get(
            f"pregnant_t{trimester}", [])
    else:
        pool = tips.get(stage) or seed_tips.TIPS.get(stage, [])
    if not pool:
        return None
    # Adjacent days can never repeat for pools of 3+: same week -> index
    # moves by 1; a week flip -> by 2 (test-guarded). Trimester flips swap
    # the whole pool.
    index = (today.toordinal() + (week or 0)) % len(pool)
    return {"id": f"{stage}-{index}", "text": pool[index]}


@router.get("/today")
def get_today(hour: int | None = Query(default=None, ge=0, le=23),
              d: str | None = Query(default=None, alias="date"),
              learner_id: str = Depends(resolve_learner)):
    ctx = care_context.get(learner_id)
    now_hour = hour if hour is not None else datetime.now().hour
    slot = ("morning" if now_hour < 12 else
            "afternoon" if now_hour < 17 else "evening")
    # The DEVICE's local date, like ?hour= — the server's midnight is not the
    # learner's. Falls back to server date (single-timezone dev is fine).
    if d is not None:
        try:
            today = date.fromisoformat(d)
        except ValueError:
            raise HTTPException(status_code=422, detail="date must be ISO YYYY-MM-DD")
    else:
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
        "size": ({"emoji": size[0], "label": size[1],
                  "length_cm": seed_journey.WEEK_LENGTHS_CM.get(week)}
                 if size else None),
        "display_name": ctx.get("display_name") or "",
        "streak": _streak(learner_id, today),
    }
    out["tip"] = _tip_for(stage, week, today, ctx.get("language") or "en")

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
        hours_away = max(0, int((when - datetime.now()).total_seconds() // 3600))
        countdown = (f"In about {hours_away}h. " if hours_away >= 1
                     else "Coming up now. ")
        focus.append({
            "kind": "appointment_soon", "priority": 1,
            "title": f"{upcoming[0]} · {when.strftime('%a %H:%M')}",
            "body": countdown + "Worth jotting the questions you want answered.",
            "when_ts": upcoming[1],
        })

    if week is not None:
        milestone = next(
            (m for m in seed_journey.MILESTONES.get(stage, [])
             if 0 < m["week"] - week <= 1 or
             (m["week"] == week)), None)
        if milestone:
            days_to = ((milestone["week"] - week) * 7 -
                       ((day_in_week or 1) - 1))
            closeness = (f"in {days_to} day{'s' if days_to != 1 else ''}"
                         if 0 < days_to <= 7 else "this week")
            focus.append({
                "kind": "milestone_week", "priority": 2,
                "title": f"{milestone['emoji']} {milestone['label']} {closeness}",
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
    # Learning layer (review #11): kinds this learner has ACTED on get a
    # small deterministic boost — enough to reorder within a band, never
    # enough to outrank a more urgent class (appointment stays first).
    month_ago = time.time() - 30 * 86400
    taps = dict(wellness._conn.execute(
        "SELECT kind, COUNT(*) FROM focus_taps"
        " WHERE learner_id=? AND ts>=? GROUP BY kind",
        (learner_id, month_ago)).fetchall())
    out["focus"] = sorted(
        focus,
        key=lambda f: f["priority"] - min(taps.get(f["kind"], 0), 4) * 0.1,
    )[:2]

    # ── Sunday recap: the weekly digest that makes the reports visible ──────
    if today.weekday() == 6:
        week_ago = (today - timedelta(days=6)).isoformat()
        moods_logged = wellness._conn.execute(
            "SELECT COUNT(*) FROM moods WHERE learner_id=? AND day>=? AND day<=?",
            (learner_id, week_ago, today_iso)).fetchone()[0]
        water_total = wellness._conn.execute(
            "SELECT COALESCE(SUM(t.ticks), 0) FROM reminder_ticks t"
            " JOIN reminders r ON r.id = t.reminder_id"
            " WHERE r.learner_id=? AND r.kind='water'"
            " AND t.day>=? AND t.day<=?",
            (learner_id, week_ago, today_iso)).fetchone()[0]
        out["recap"] = {
            "moods_logged": moods_logged,
            "water_avg": round(water_total / 7, 1),
        }
    return out


def _streak(learner_id: str, today: date) -> int:
    """Consecutive days where EVERY reminder hit its target. An incomplete
    today doesn't break the run (the day isn't over); an incomplete yesterday
    does. Capped at 60 days of history — nobody needs more than the number."""
    rems = wellness._conn.execute(
        "SELECT id, target_per_day FROM reminders WHERE learner_id=?",
        (learner_id,)).fetchall()
    if not rems:
        return 0
    streak = 0
    for offset in range(60):
        day = (today - timedelta(days=offset)).isoformat()
        done_all = True
        for rid, target in rems:
            t = wellness._conn.execute(
                "SELECT ticks FROM reminder_ticks WHERE reminder_id=? AND day=?",
                (rid, day)).fetchone()
            if not t or t[0] < target:
                done_all = False
                break
        if done_all:
            streak += 1
        elif offset == 0:
            continue
        else:
            break
    return streak


class FocusTapIn(BaseModel):
    kind: str


@router.post("/today/focus-tap")
def focus_tap(body: FocusTapIn, learner_id: str = Depends(resolve_learner)):
    """The learner acted on a nudge — the ranking's only training signal."""
    if body.kind not in {"mood_checkin", "water_pace", "appointment_soon",
                         "milestone_week", "evening_wrapup"}:
        raise HTTPException(status_code=422, detail="unknown focus kind")
    wellness._conn.execute(
        "INSERT INTO focus_taps (learner_id, kind, ts) VALUES (?,?,?)",
        (learner_id, body.kind, time.time()))
    wellness._conn.commit()
    return {"ok": True}


class TipFeedbackIn(BaseModel):
    tip_id: str
    helpful: bool


@router.post("/today/tip-feedback")
def tip_feedback(body: TipFeedbackIn,
                 learner_id: str = Depends(resolve_learner)):
    """One thumb per tip per learner (upsert) — the signal that will
    eventually tune pool weights. Exported and erased with wellness data."""
    wellness._conn.execute(
        "INSERT INTO tip_feedback (learner_id, tip_id, helpful, ts)"
        " VALUES (?,?,?,?) ON CONFLICT (learner_id, tip_id)"
        " DO UPDATE SET helpful=excluded.helpful, ts=excluded.ts",
        (learner_id, body.tip_id, int(body.helpful), time.time()))
    wellness._conn.commit()
    return {"ok": True}
