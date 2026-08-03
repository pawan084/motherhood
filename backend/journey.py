"""Journey: week-indexed editorial content per stage.

Content lives in the DB (admin-authorable at P10); `seed_journey.py` provides
the initial bands with seed-once semantics — a row is inserted only when its
id is absent, so admin edits and deletions survive restarts (sayli's
scenarios lesson, inherited).

The learner-facing endpoint derives the week from the caller's care context
(the same computed-at-read-time week everything else uses) and resolves it to
its band. An explicit `?week=` lets the client page to other weeks without
touching the stored context.
"""
import time

from fastapi import APIRouter, Depends, HTTPException, Query

import care_context
import db
import seed_journey
from device_auth import resolve_learner

router = APIRouter(tags=["journey"])

_conn = None

_DISCLAIMER = ("General information, not medical advice. Every pregnancy and "
               "recovery is different — your care team knows yours.")


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS journey_content ("
              " id TEXT PRIMARY KEY,"
              " stage TEXT NOT NULL,"
              " week_start INTEGER NOT NULL,"
              " week_end INTEGER NOT NULL,"
              " title TEXT NOT NULL,"
              " yourself TEXT DEFAULT '',"
              " baby TEXT DEFAULT '',"
              " prepare TEXT DEFAULT '',"
              " updated REAL)")
    c.execute("CREATE INDEX IF NOT EXISTS idx_journey_stage"
              " ON journey_content (stage, week_start)")
    now = time.time()
    for band in seed_journey.BANDS:
        # Seed-once: ON CONFLICT DO NOTHING keeps admin edits authoritative.
        c.execute(
            "INSERT INTO journey_content (id, stage, week_start, week_end,"
            " title, yourself, baby, prepare, updated)"
            " VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING",
            (band["id"], band["stage"], band["week_start"], band["week_end"],
             band["title"], band["yourself"], band["baby"], band["prepare"], now))
    c.commit()
    _conn = c


def band_for(stage: str, week: int | None) -> dict | None:
    """Resolve a stage + week to its content band. Weeks past the last band
    clamp to it (postpartum week 60 still gets "The long arc") — running off
    the end of the content is not an error state a learner should see."""
    if stage == "trying_to_conceive":
        row = _conn.execute(
            "SELECT id, title, yourself, baby, prepare FROM journey_content"
            " WHERE stage=? LIMIT 1", (stage,)).fetchone()
    elif week is None:
        return None
    else:
        row = _conn.execute(
            "SELECT id, title, yourself, baby, prepare FROM journey_content"
            " WHERE stage=? AND week_start<=? AND week_end>=?"
            " ORDER BY week_start LIMIT 1", (stage, week, week)).fetchone()
        if not row:
            row = _conn.execute(
                "SELECT id, title, yourself, baby, prepare FROM journey_content"
                " WHERE stage=? ORDER BY week_end DESC LIMIT 1", (stage,)).fetchone()
    if not row:
        return None
    return {"id": row[0], "title": row[1], "yourself": row[2],
            "baby": row[3], "prepare": row[4]}


@router.get("/journey")
def get_journey(week: int | None = Query(default=None, ge=1, le=52),
                learner_id: str = Depends(resolve_learner)):
    """The caller's journey content. `?week=` pages to another week within
    their own stage; the stored context is never modified by browsing."""
    ctx = care_context.get(learner_id)
    if not ctx:
        raise HTTPException(status_code=404, detail="no care context yet")
    stage = ctx["stage"]
    shown_week = week if week is not None else ctx["week"]
    content = band_for(stage, shown_week)
    size = (seed_journey.WEEK_SIZES.get(shown_week)
            if stage == "pregnant" and shown_week else None)
    total_weeks = {"pregnant": 40, "postpartum": 52}.get(stage)
    return {
        "stage": stage,
        "current_week": ctx["week"],
        "shown_week": shown_week if stage != "trying_to_conceive" else None,
        "total_weeks": total_weeks,
        "size": ({"emoji": size[0], "label": size[1]} if size else None),
        "milestones": seed_journey.MILESTONES.get(stage, []),
        "content": content,
        "disclaimer": _DISCLAIMER,
    }
