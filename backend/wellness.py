"""Wellness: moods, habit reminders, and the video catalog.

Built for the Android Me/Videos tabs (P11 part 2); the web app can grow the
same features against these endpoints. Same obligations as every other
learner resource: owner-scoped via `resolve_learner`, merged on sign-in,
erased on deletion, present in `/export`.

Design notes:
- **Moods** are one row per learner per day, upsert latest-wins — a check-in
  is a correction, not an append-only log.
- **Reminders** are habit check-offs (water counts up to a per-day target;
  others toggle). Two defaults are seeded per learner on first list
  (seed-once, keyed on a stable id, so deleting one sticks). With
  `include_medicines=true` the list also carries the learner's medicines as
  `kind="medicine"` rows — one fetch for the whole "Today's care" card; the
  client writes medicine ticks through the existing idempotent
  `POST /medicines/{id}/taken`, never through this module.
- **Videos** live in the DB (admin-authorable later) from `seed_videos.py`,
  seed-once. `/videos/suggested` picks by the caller's stage + computed week,
  clamping past the last band — running off the end of the catalog is not a
  learner-visible error (same rule as journey.band_for).
"""
import secrets
import time
from datetime import date, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel

import care_context
import db
import seed_videos
from device_auth import resolve_learner

router = APIRouter(tags=["wellness"])

_conn = None

MOODS = ("great", "okay", "tired", "low", "unwell")
REMINDER_KINDS = ("water", "exercise", "custom")

# Per-learner defaults, seeded on first list. Stable seeding-per-learner lets
# a deletion stick (a deleted default is never re-seeded). Postpartum gets a
# recovery-shaped set — the same seeds for every stage was review point #23.
# HUMAN-GATED like all seeded content: clinician review before real users.
def _default_reminders(learner_id: str) -> list[tuple[str, str, int]]:
    ctx = care_context.get(learner_id)
    if ctx and ctx["stage"] == "postpartum":
        return [
            ("water", "Drink water", 8),
            ("custom", "Rest when baby rests", 1),
            ("exercise", "Gentle pelvic floor", 1),
        ]
    return [
        ("water", "Drink water", 8),
        ("exercise", "Move a little", 1),
    ]


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS moods ("
              " learner_id TEXT NOT NULL, day TEXT NOT NULL,"
              " mood TEXT NOT NULL, note TEXT DEFAULT '', ts REAL,"
              " PRIMARY KEY (learner_id, day))")
    c.execute("CREATE TABLE IF NOT EXISTS reminders ("
              " id TEXT PRIMARY KEY, learner_id TEXT NOT NULL,"
              " title TEXT NOT NULL, kind TEXT NOT NULL,"
              " target_per_day INTEGER DEFAULT 1, created REAL)")
    c.execute("CREATE TABLE IF NOT EXISTS reminder_ticks ("
              " reminder_id TEXT NOT NULL, day TEXT NOT NULL,"
              " ticks INTEGER DEFAULT 0, ts REAL,"
              " PRIMARY KEY (reminder_id, day))")
    # Tracks which learners already received the default reminders, so a
    # learner who deletes "Drink water" doesn't get it back on the next list.
    c.execute("CREATE TABLE IF NOT EXISTS reminder_seeded ("
              " learner_id TEXT PRIMARY KEY, ts REAL)")
    c.execute("CREATE TABLE IF NOT EXISTS videos ("
              " id TEXT PRIMARY KEY, title TEXT NOT NULL, topic TEXT NOT NULL,"
              " stage TEXT NOT NULL, week_start INTEGER NOT NULL,"
              " week_end INTEGER NOT NULL, youtube_id TEXT DEFAULT '',"
              " stream_path TEXT DEFAULT '', thumb_path TEXT DEFAULT '',"
              " duration_min INTEGER, updated REAL)")
    # Guarded ALTERs for databases created before hosted videos existed.
    for col in ("stream_path", "thumb_path"):
        try:
            c.execute(f"ALTER TABLE videos ADD COLUMN {col} TEXT DEFAULT ''")
        except Exception as e:  # pragma: no cover
            msg = str(e).lower()
            if "duplicate column" not in msg and "already exists" not in msg:
                raise
    c.execute("CREATE TABLE IF NOT EXISTS video_watched ("
              " learner_id TEXT NOT NULL, video_id TEXT NOT NULL, ts REAL,"
              " PRIMARY KEY (learner_id, video_id))")
    c.execute("CREATE TABLE IF NOT EXISTS video_likes ("
              " learner_id TEXT NOT NULL, video_id TEXT NOT NULL, ts REAL,"
              " PRIMARY KEY (learner_id, video_id))")
    c.execute("CREATE TABLE IF NOT EXISTS video_ratings ("
              " learner_id TEXT NOT NULL, video_id TEXT NOT NULL,"
              " stars INTEGER NOT NULL, ts REAL,"
              " PRIMARY KEY (learner_id, video_id))")
    c.execute("CREATE TABLE IF NOT EXISTS tip_feedback ("
              " learner_id TEXT NOT NULL, tip_id TEXT NOT NULL,"
              " helpful INTEGER NOT NULL, ts REAL,"
              " PRIMARY KEY (learner_id, tip_id))")
    for table in ("moods", "reminders"):
        c.execute(f"CREATE INDEX IF NOT EXISTS idx_{table}_learner"
                  f" ON {table} (learner_id)")
    now = time.time()
    for v in seed_videos.VIDEOS:
        # Seed-once: admin edits/deletions stick across restarts.
        c.execute(
            "INSERT INTO videos (id, title, topic, stage, week_start, week_end,"
            " youtube_id, stream_path, thumb_path, duration_min, updated)"
            " VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING",
            (v["id"], v["title"], v["topic"], v["stage"], v["week_start"],
             v["week_end"], v.get("youtube_id", ""), v.get("stream_path", ""),
             v.get("thumb_path", ""), v["duration_min"], now))
    c.commit()
    _conn = c


def merge(did: str, uid: str) -> None:
    """Sign-in: reassign the device's wellness data to the account. Moods
    collide on (learner, day) — the account's own entry wins (an explicit
    account check-in is never overwritten by a merge, same rule as consents)."""
    _conn.execute(
        "INSERT INTO moods (learner_id, day, mood, note, ts)"
        " SELECT ?, day, mood, note, ts FROM moods WHERE learner_id=?"
        " ON CONFLICT (learner_id, day) DO NOTHING", (uid, did))
    _conn.execute("DELETE FROM moods WHERE learner_id=?", (did,))
    _conn.execute("UPDATE reminders SET learner_id=? WHERE learner_id=?", (uid, did))
    # If both sides were seeded, keep the account's marker; ticks ride their
    # reminder rows (reminder ids are globally unique).
    _conn.execute(
        "INSERT INTO reminder_seeded (learner_id, ts)"
        " SELECT ?, ts FROM reminder_seeded WHERE learner_id=?"
        " ON CONFLICT (learner_id) DO NOTHING", (uid, did))
    _conn.execute("DELETE FROM reminder_seeded WHERE learner_id=?", (did,))
    for table, key in (("video_watched", "video_id"),
                       ("video_likes", "video_id"),
                       ("video_ratings", "video_id"),
                       ("tip_feedback", "tip_id")):
        cols = {"video_watched": "video_id, ts",
                "video_likes": "video_id, ts",
                "video_ratings": "video_id, stars, ts",
                "tip_feedback": "tip_id, helpful, ts"}[table]
        _conn.execute(
            f"INSERT INTO {table} (learner_id, {cols})"
            f" SELECT ?, {cols} FROM {table} WHERE learner_id=?"
            f" ON CONFLICT (learner_id, {key}) DO NOTHING", (uid, did))
        _conn.execute(f"DELETE FROM {table} WHERE learner_id=?", (did,))
    _conn.commit()


def erase(learner_id: str) -> None:
    """Deletion cascade hook (accounts + guest learner-data paths)."""
    _conn.execute("DELETE FROM reminder_ticks WHERE reminder_id IN"
                  " (SELECT id FROM reminders WHERE learner_id=?)", (learner_id,))
    for table in ("moods", "reminders", "reminder_seeded", "video_watched",
                  "video_likes", "video_ratings", "tip_feedback"):
        _conn.execute(f"DELETE FROM {table} WHERE learner_id=?", (learner_id,))


def export(learner_id: str) -> dict:
    """Sections for privacy.export_data."""
    moods = _conn.execute(
        "SELECT day, mood, note FROM moods WHERE learner_id=? ORDER BY day",
        (learner_id,)).fetchall()
    reminders = _conn.execute(
        "SELECT title, kind, target_per_day, created FROM reminders"
        " WHERE learner_id=?", (learner_id,)).fetchall()
    watched = _conn.execute(
        "SELECT video_id, ts FROM video_watched WHERE learner_id=?",
        (learner_id,)).fetchall()
    feedback = _conn.execute(
        "SELECT tip_id, helpful, ts FROM tip_feedback WHERE learner_id=?",
        (learner_id,)).fetchall()
    return {
        "moods": [dict(zip(("day", "mood", "note"), m)) for m in moods],
        "reminders": [dict(zip(("title", "kind", "target_per_day", "created"), r))
                      for r in reminders],
        "videos_watched": [dict(zip(("video_id", "ts"), w)) for w in watched],
        "video_likes": [dict(zip(("video_id", "ts"), r)) for r in _conn.execute(
            "SELECT video_id, ts FROM video_likes WHERE learner_id=?",
            (learner_id,)).fetchall()],
        "video_ratings": [dict(zip(("video_id", "stars"), r)) for r in _conn.execute(
            "SELECT video_id, stars FROM video_ratings WHERE learner_id=?",
            (learner_id,)).fetchall()],
        "tip_feedback": [dict(zip(("tip_id", "helpful", "ts"), f))
                         for f in feedback],
    }


# ── Moods ────────────────────────────────────────────────────────────────────

class MoodIn(BaseModel):
    mood: str
    note: str = ""


@router.post("/moods")
def set_mood(body: MoodIn, learner_id: str = Depends(resolve_learner)):
    if body.mood not in MOODS:
        raise HTTPException(status_code=422, detail=f"mood must be one of {MOODS}")
    _conn.execute(
        "INSERT INTO moods (learner_id, day, mood, note, ts) VALUES (?,?,?,?,?)"
        " ON CONFLICT (learner_id, day) DO UPDATE SET mood=excluded.mood,"
        " note=excluded.note, ts=excluded.ts",
        (learner_id, date.today().isoformat(), body.mood,
         body.note.strip()[:300], time.time()))
    _conn.commit()
    return {"ok": True}


@router.get("/moods")
def list_moods(days: int = Query(default=7, ge=1, le=90),
               learner_id: str = Depends(resolve_learner)):
    since = (date.today() - timedelta(days=days - 1)).isoformat()
    rows = _conn.execute(
        "SELECT day, mood FROM moods WHERE learner_id=? AND day>=? ORDER BY day",
        (learner_id, since)).fetchall()
    return {"moods": [{"day": r[0], "mood": r[1]} for r in rows]}


# ── Reminders ────────────────────────────────────────────────────────────────

def _seed_reminders(learner_id: str) -> None:
    if _conn.execute("SELECT 1 FROM reminder_seeded WHERE learner_id=?",
                     (learner_id,)).fetchone():
        return
    now = time.time()
    for kind, title, target in _default_reminders(learner_id):
        _conn.execute(
            "INSERT INTO reminders (id, learner_id, title, kind, target_per_day, created)"
            " VALUES (?,?,?,?,?,?)",
            (f"rem_{kind}_{secrets.token_hex(6)}", learner_id, title, kind, target, now))
    _conn.execute("INSERT INTO reminder_seeded (learner_id, ts) VALUES (?,?)"
                  " ON CONFLICT (learner_id) DO NOTHING", (learner_id, now))
    _conn.commit()


class ReminderIn(BaseModel):
    title: str
    kind: str = "custom"
    target_per_day: int = 1


@router.get("/reminders")
def list_reminders(include_medicines: bool = Query(default=False),
                   learner_id: str = Depends(resolve_learner)):
    _seed_reminders(learner_id)
    today = date.today().isoformat()
    rows = _conn.execute(
        "SELECT r.id, r.title, r.kind, r.target_per_day,"
        " COALESCE(t.ticks, 0)"
        " FROM reminders r LEFT JOIN reminder_ticks t"
        " ON t.reminder_id = r.id AND t.day = ?"
        " WHERE r.learner_id=? ORDER BY r.created", (today, learner_id)).fetchall()
    out = [{"id": r[0], "title": r[1], "kind": r[2], "target_per_day": r[3],
            "ticks_today": r[4], "done_today": r[4] >= r[3]} for r in rows]
    if include_medicines:
        import care  # local import: avoids a cycle at module load
        meds = care._conn.execute(
            "SELECT m.id, m.name, m.time_of_day, m.dose,"
            " CASE WHEN t.day IS NULL THEN 0 ELSE 1 END"
            " FROM medicines m LEFT JOIN medicine_taken t"
            " ON t.medicine_id = m.id AND t.day = ?"
            " WHERE m.learner_id=? ORDER BY m.time_of_day, m.created",
            (today, learner_id)).fetchall()
        out += [{"id": m[0], "title": m[1],
                 "detail": " · ".join(x for x in (m[3], m[2]) if x),
                 "kind": "medicine", "target_per_day": 1,
                 "ticks_today": m[4], "done_today": bool(m[4])} for m in meds]
    return {"reminders": out}


@router.post("/reminders")
def add_reminder(body: ReminderIn, learner_id: str = Depends(resolve_learner)):
    title = body.title.strip()[:80]
    if not title:
        raise HTTPException(status_code=422, detail="title is required")
    if body.kind not in REMINDER_KINDS:
        raise HTTPException(status_code=422,
                            detail=f"kind must be one of {REMINDER_KINDS}")
    target = max(1, min(24, body.target_per_day))
    rid = "rem_" + secrets.token_hex(8)
    _seed_reminders(learner_id)  # keep ordering stable: defaults first
    _conn.execute(
        "INSERT INTO reminders (id, learner_id, title, kind, target_per_day, created)"
        " VALUES (?,?,?,?,?,?)",
        (rid, learner_id, title, body.kind, target, time.time()))
    _conn.commit()
    return {"id": rid}


@router.post("/reminders/{reminder_id}/tick")
def tick_reminder(reminder_id: str, learner_id: str = Depends(resolve_learner)):
    row = _conn.execute(
        "SELECT target_per_day FROM reminders WHERE id=? AND learner_id=?",
        (reminder_id, learner_id)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="not found")
    today = date.today().isoformat()
    # Count up, capped at the target — an extra tap past done is a no-op,
    # not an error and not a 9/8.
    _conn.execute(
        "INSERT INTO reminder_ticks (reminder_id, day, ticks, ts) VALUES (?,?,1,?)"
        " ON CONFLICT (reminder_id, day) DO UPDATE SET"
        " ticks = MIN(reminder_ticks.ticks + 1, ?), ts = excluded.ts",
        (reminder_id, today, time.time(), row[0]))
    _conn.commit()
    ticks = _conn.execute(
        "SELECT ticks FROM reminder_ticks WHERE reminder_id=? AND day=?",
        (reminder_id, today)).fetchone()[0]
    return {"ticks_today": ticks, "done_today": ticks >= row[0]}


@router.post("/reminders/{reminder_id}/untick")
def untick_reminder(reminder_id: str, learner_id: str = Depends(resolve_learner)):
    """Undo for an accidental tick — decrements, floored at zero."""
    row = _conn.execute(
        "SELECT 1 FROM reminders WHERE id=? AND learner_id=?",
        (reminder_id, learner_id)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="not found")
    today = date.today().isoformat()
    _conn.execute(
        "UPDATE reminder_ticks SET ticks = MAX(ticks - 1, 0), ts=?"
        " WHERE reminder_id=? AND day=?", (time.time(), reminder_id, today))
    _conn.commit()
    ticks_row = _conn.execute(
        "SELECT t.ticks, r.target_per_day FROM reminder_ticks t"
        " JOIN reminders r ON r.id = t.reminder_id"
        " WHERE t.reminder_id=? AND t.day=?", (reminder_id, today)).fetchone()
    ticks = ticks_row[0] if ticks_row else 0
    target = ticks_row[1] if ticks_row else 1
    return {"ticks_today": ticks, "done_today": ticks >= target}


class TargetIn(BaseModel):
    target_per_day: int


@router.post("/reminders/{reminder_id}/target")
def set_target(reminder_id: str, body: TargetIn,
               learner_id: str = Depends(resolve_learner)):
    """Personalise the daily target (e.g. a doctor said 10 glasses)."""
    row = _conn.execute(
        "SELECT 1 FROM reminders WHERE id=? AND learner_id=?",
        (reminder_id, learner_id)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="not found")
    target = max(1, min(24, body.target_per_day))
    _conn.execute("UPDATE reminders SET target_per_day=? WHERE id=?",
                  (target, reminder_id))
    _conn.commit()
    return {"target_per_day": target}


@router.delete("/reminders/{reminder_id}")
def delete_reminder(reminder_id: str, learner_id: str = Depends(resolve_learner)):
    row = _conn.execute("SELECT 1 FROM reminders WHERE id=? AND learner_id=?",
                        (reminder_id, learner_id)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="not found")
    _conn.execute("DELETE FROM reminder_ticks WHERE reminder_id=?", (reminder_id,))
    _conn.execute("DELETE FROM reminders WHERE id=?", (reminder_id,))
    _conn.commit()
    return {"ok": True}


# ── Reports ──────────────────────────────────────────────────────────────────

@router.get("/report")
def wellness_report(days: int = Query(default=30, ge=7, le=90),
                    learner_id: str = Depends(resolve_learner)):
    """One fetch powering the weekly/monthly report views: the mood series
    plus each reminder's per-day tick history over the window. Medicines are
    included (their taken-log is a day series too) so the care report covers
    the whole "Today's care" list."""
    _seed_reminders(learner_id)
    since = (date.today() - timedelta(days=days - 1)).isoformat()

    moods = _conn.execute(
        "SELECT day, mood FROM moods WHERE learner_id=? AND day>=? ORDER BY day",
        (learner_id, since)).fetchall()

    reminders_out = []
    for rid, title, kind, target in _conn.execute(
            "SELECT id, title, kind, target_per_day FROM reminders"
            " WHERE learner_id=? ORDER BY created", (learner_id,)).fetchall():
        rows = _conn.execute(
            "SELECT day, ticks FROM reminder_ticks WHERE reminder_id=? AND day>=?"
            " ORDER BY day", (rid, since)).fetchall()
        reminders_out.append({
            "id": rid, "title": title, "kind": kind, "target_per_day": target,
            "days": [{"day": r[0], "ticks": r[1], "done": r[1] >= target}
                     for r in rows],
        })
    import care  # local import: avoids a cycle at module load
    for mid, name, target_days in care._conn.execute(
            "SELECT id, name, 1 FROM medicines WHERE learner_id=?"
            " ORDER BY time_of_day, created", (learner_id,)).fetchall():
        rows = care._conn.execute(
            "SELECT day FROM medicine_taken WHERE medicine_id=? AND day>=?"
            " ORDER BY day", (mid, since)).fetchall()
        reminders_out.append({
            "id": mid, "title": name, "kind": "medicine", "target_per_day": 1,
            "days": [{"day": r[0], "ticks": 1, "done": True} for r in rows],
        })

    return {
        "days": days,
        "since": since,
        "moods": [{"day": m[0], "mood": m[1]} for m in moods],
        "reminders": reminders_out,
    }


# ── Videos ───────────────────────────────────────────────────────────────────

def _video_dict(row, learner_id: str | None = None) -> dict:
    out = {"id": row[0], "title": row[1], "topic": row[2], "stage": row[3],
           "week_band": (f"{row[4]}-{row[5]}" if row[5] > 0 else None),
           "youtube_id": row[6] or None,
           "stream_path": row[7] or None,
           "thumb_path": row[8] or None,
           "duration_minutes": row[9]}
    # Social layer (all REAL aggregates — nothing invented): total likes,
    # true star average, and the caller's own state.
    vid = row[0]
    out["like_count"] = _conn.execute(
        "SELECT COUNT(*) FROM video_likes WHERE video_id=?", (vid,)).fetchone()[0]
    avg = _conn.execute(
        "SELECT AVG(stars), COUNT(*) FROM video_ratings WHERE video_id=?",
        (vid,)).fetchone()
    out["avg_stars"] = round(avg[0], 1) if avg[0] is not None else None
    out["rating_count"] = avg[1]
    if learner_id:
        out["my_like"] = bool(_conn.execute(
            "SELECT 1 FROM video_likes WHERE learner_id=? AND video_id=?",
            (learner_id, vid)).fetchone())
        my = _conn.execute(
            "SELECT stars FROM video_ratings WHERE learner_id=? AND video_id=?",
            (learner_id, vid)).fetchone()
        out["my_stars"] = my[0] if my else None
    return out


_VIDEO_COLS = ("id, title, topic, stage, week_start, week_end, youtube_id,"
               " stream_path, thumb_path, duration_min")


@router.get("/videos")
def list_videos(learner_id: str = Depends(resolve_learner)):
    """The catalog, the caller's stage first (stage-wide rows included)."""
    ctx = care_context.get(learner_id)
    stage = (ctx or {}).get("stage")
    rows = _conn.execute(
        f"SELECT {_VIDEO_COLS} FROM videos"
        " ORDER BY CASE WHEN stage=? THEN 0 ELSE 1 END, stage, week_start",
        (stage or "",)).fetchall()
    return {"videos": [_video_dict(r, learner_id) for r in rows]}


@router.get("/videos/suggested")
def suggested_video(learner_id: str = Depends(resolve_learner)):
    """One pick for the Me tab, ROTATING DAILY over the stage's catalog
    (same deterministic day-keyed rotation as the /today tip): the home
    card must change every day, and with today's small catalog (one video
    per band) band-scoped rotation would pin the same video for weeks.
    The client shows each video's week band, so an out-of-band day is
    honest, not wrong. Stable within a day, across devices.
    TODO when the catalog grows past ~3 per band: rotate within the
    matching band first."""
    ctx = care_context.get(learner_id)
    if not ctx:
        return {"video": None}
    rows = _conn.execute(
        f"SELECT {_VIDEO_COLS} FROM videos WHERE stage=?"
        " ORDER BY week_start, id", (ctx["stage"],)).fetchall()
    if not rows:
        return {"video": None}
    # Unwatched-first (review #32): re-suggesting a watched video while
    # unwatched ones exist wastes the slot. Once everything is watched the
    # rotation covers the full catalog again.
    watched = {r[0] for r in _conn.execute(
        "SELECT video_id FROM video_watched WHERE learner_id=?",
        (learner_id,)).fetchall()}
    pool = [r for r in rows if r[0] not in watched] or rows
    row = pool[date.today().toordinal() % len(pool)]
    return {"video": _video_dict(row, learner_id)}


@router.post("/videos/{video_id}/watched")
def mark_watched(video_id: str, learner_id: str = Depends(resolve_learner)):
    """Completion signal from the in-app player; drives unwatched-first."""
    if not _conn.execute("SELECT 1 FROM videos WHERE id=?",
                         (video_id,)).fetchone():
        raise HTTPException(status_code=404, detail="not found")
    _conn.execute(
        "INSERT INTO video_watched (learner_id, video_id, ts) VALUES (?,?,?)"
        " ON CONFLICT (learner_id, video_id) DO UPDATE SET ts=excluded.ts",
        (learner_id, video_id, time.time()))
    _conn.commit()
    return {"ok": True}


@router.post("/videos/{video_id}/like")
def toggle_like(video_id: str, learner_id: str = Depends(resolve_learner)):
    """Toggle — a second tap unlikes. Returns the new truth for the card."""
    if not _conn.execute("SELECT 1 FROM videos WHERE id=?",
                         (video_id,)).fetchone():
        raise HTTPException(status_code=404, detail="not found")
    existing = _conn.execute(
        "SELECT 1 FROM video_likes WHERE learner_id=? AND video_id=?",
        (learner_id, video_id)).fetchone()
    if existing:
        _conn.execute("DELETE FROM video_likes WHERE learner_id=? AND video_id=?",
                      (learner_id, video_id))
    else:
        _conn.execute("INSERT INTO video_likes (learner_id, video_id, ts)"
                      " VALUES (?,?,?)", (learner_id, video_id, time.time()))
    _conn.commit()
    count = _conn.execute("SELECT COUNT(*) FROM video_likes WHERE video_id=?",
                          (video_id,)).fetchone()[0]
    return {"liked": not existing, "like_count": count}


class RatingIn(BaseModel):
    stars: int


@router.post("/videos/{video_id}/rate")
def rate_video(video_id: str, body: RatingIn,
               learner_id: str = Depends(resolve_learner)):
    """One rating per learner per video, upserted; the card shows the TRUE
    average — never an invented number."""
    if not 1 <= body.stars <= 5:
        raise HTTPException(status_code=422, detail="stars must be 1-5")
    if not _conn.execute("SELECT 1 FROM videos WHERE id=?",
                         (video_id,)).fetchone():
        raise HTTPException(status_code=404, detail="not found")
    _conn.execute(
        "INSERT INTO video_ratings (learner_id, video_id, stars, ts)"
        " VALUES (?,?,?,?) ON CONFLICT (learner_id, video_id)"
        " DO UPDATE SET stars=excluded.stars, ts=excluded.ts",
        (learner_id, video_id, body.stars, time.time()))
    _conn.commit()
    avg = _conn.execute(
        "SELECT AVG(stars), COUNT(*) FROM video_ratings WHERE video_id=?",
        (video_id,)).fetchone()
    return {"my_stars": body.stars, "avg_stars": round(avg[0], 1),
            "rating_count": avg[1]}
