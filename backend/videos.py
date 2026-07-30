"""Educational video library — the clinician-review-gated topics Aira surfaces by
journey stage and gestational week, plus per-user "saved for later".

The topics are CONTENT, not user data: they load once from
`data/video_catalog.json` (shipped from the Aira-Video-Topic-Catalog package) and
are served read-only, mapped from the catalog's `trying_to_conceive | pregnancy |
postpartum` stages to Aira's `trying | pregnant | postpartum` journeys. No media
is produced yet, so every topic reports `playable: false` and the clients show an
honest "in production" state; playback stays gated until a topic is produced AND
clinically approved.

Safety mirrors the app's posture: an `urgent` topic is about *knowing when to get
help*, never AI reassurance. The client routes those to the care team; this module
just carries the `safety_level` so it can.

Only the "saved" list is per-user, so that is the sole table here and the only
thing export/delete touch (privacy.py fans out over export_user/delete_user).
"""
import json
import logging
import os
import time

from fastapi import APIRouter, Depends, HTTPException

import accounts
import care
import db
import security
from accounts import current_user

log = logging.getLogger("aira.videos")
router = APIRouter(prefix="/v1", tags=["videos"],
                   dependencies=[Depends(security.require_app_token)])
_conn = None

_CATALOG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                             "data", "video_catalog.json")

# Catalog stage -> Aira journey. `exploring` has no catalog stage; those users get
# the on-demand library (see _for_journey).
_JOURNEY = {"trying_to_conceive": "trying", "pregnancy": "pregnant", "postpartum": "postpartum"}

_CATEGORY_LABEL = {
    "pregnancy_week_by_week": "Week by week",
    "pregnancy_symptoms": "Symptoms",
    "nutrition": "Nutrition",
    "movement_and_wellness": "Movement & wellness",
    "tests_and_appointments": "Tests & appointments",
    "labour_and_delivery": "Labour & delivery",
    "postpartum_recovery": "Postpartum recovery",
    "newborn_care": "Newborn care",
}

_TOPICS: list[dict] = []
_BY_ID: dict[str, dict] = {}
_CATEGORIES: list[dict] = []


def _normalise(t: dict) -> dict:
    tim = t.get("timing", {}) or {}
    dur = t.get("recommended_duration_seconds", {}) or {}
    review = t.get("clinical_review", {}) or {}
    return {
        "id": t["id"],
        "slug": t.get("slug", t["id"]),
        "title": t["title"],
        "category": t["category"],
        "category_label": _CATEGORY_LABEL.get(t["category"], t["category"]),
        "journeys": [_JOURNEY[s] for s in t.get("journey_stage", []) if s in _JOURNEY],
        "timing": {
            "type": tim.get("type", "on_demand"),
            "start_week": tim.get("start_week"),
            "end_week": tim.get("end_week"),
        },
        "content_format": t.get("content_format", "explainer"),
        "duration": {"min_seconds": dur.get("minimum", 60), "max_seconds": dur.get("maximum", 120)},
        "description": t.get("description", ""),
        "safety_level": t.get("safety_level", "standard"),
        "in_app_actions": t.get("in_app_actions", []),
        "languages": t.get("languages", ["en"]),
        "status": t.get("status", "planned"),
        "clinical_review": {
            "required": review.get("required", True),
            "specialties": review.get("specialties", []),
            "status": review.get("status", "pending"),
        },
        # Playable only when produced/published AND clinically approved — none are
        # today, so the clients honestly show an "in production" state.
        "playable": t.get("status") == "published" and review.get("status") == "approved",
    }


def _load_catalog() -> None:
    global _TOPICS, _BY_ID, _CATEGORIES
    if _TOPICS:
        return
    try:
        with open(_CATALOG_PATH, encoding="utf-8") as f:
            raw = json.load(f)
    except Exception as e:  # noqa: BLE001 — a missing catalog must not crash boot
        log.error("could not load video catalog at %s: %s", _CATALOG_PATH, e)
        raw = {}
    topics = raw.get("topics") or raw.get("records") or (raw if isinstance(raw, list) else [])
    _TOPICS = [_normalise(t) for t in topics]
    _BY_ID = {t["id"]: t for t in _TOPICS}
    cats: list[dict] = []
    seen: set[str] = set()
    for t in _TOPICS:
        if t["category"] not in seen:
            seen.add(t["category"])
            cats.append({"key": t["category"], "label": t["category_label"]})
    _CATEGORIES = cats
    log.info("video catalog loaded: %d topics", len(_TOPICS))


def init() -> None:
    global _conn
    _load_catalog()
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS video_saves ("
              " user_id TEXT, video_id TEXT, created REAL,"
              " PRIMARY KEY (user_id, video_id))")
    c.commit()
    _conn = c


# ── selection ────────────────────────────────────────────────────────────────

def video_for_week(journey: str | None, week: int | None) -> dict | None:
    """The week-by-week video whose gestational band covers `week`, for a
    pregnant user. None otherwise — nothing here guesses at a stage."""
    if journey != "pregnant" or week is None:
        return None
    for t in _TOPICS:
        tm = t["timing"]
        if tm["type"] == "gestational_week" and tm["start_week"] is not None \
                and tm["end_week"] is not None and tm["start_week"] <= week <= tm["end_week"]:
            return t
    return None


def _for_journey(journey: str | None) -> list[dict]:
    if journey in ("trying", "pregnant", "postpartum"):
        return [t for t in _TOPICS if journey in t["journeys"]]
    # `exploring` / unknown: the on-demand library, never stage-specific cards.
    return [t for t in _TOPICS if t["timing"]["type"] == "on_demand"]


def _saved_ids(uid: str) -> list[str]:
    init()
    rows = _conn.execute("SELECT video_id FROM video_saves WHERE user_id=? ORDER BY created DESC",
                         (uid,)).fetchall()
    return [r[0] for r in rows]


# ── per-user data (privacy.py: export / delete) ──────────────────────────────

def export_user(uid: str) -> dict:
    return {"saved": _saved_ids(uid)}


def delete_user(uid: str) -> int:
    init()
    cur = _conn.execute("DELETE FROM video_saves WHERE user_id=?", (uid,))
    _conn.commit()
    return getattr(cur, "rowcount", 0) or 0


# ── stage resolution ─────────────────────────────────────────────────────────

def _resolve_journey(uid: str, given: str | None) -> str | None:
    if given:
        return given.strip().lower()
    u = accounts.get_user(uid)
    return (u or {}).get("journey") or None


def _resolve_week(uid: str, given: int | None) -> int | None:
    if given is not None:
        return given
    try:
        return care._context(uid).get("weeks")
    except Exception:  # noqa: BLE001 — no care context yet is just "unknown week"
        return None


# ── routes ───────────────────────────────────────────────────────────────────

@router.get("/videos")
def list_videos(uid: str = Depends(current_user),
                journey: str | None = None, week: int | None = None,
                category: str | None = None, q: str | None = None,
                saved: bool = False):
    """The library for where the caller is. `journey`/`week` default to the
    caller's own profile + care context; pass them to override. `week_video` is
    the pregnant caller's current week-by-week video (suggest-only on the client).
    """
    init()
    j = _resolve_journey(uid, journey)
    w = _resolve_week(uid, week)
    saved_ids = set(_saved_ids(uid))

    items = _for_journey(j)
    if saved:
        items = [t for t in items if t["id"] in saved_ids]
    if category:
        items = [t for t in items if t["category"] == category]
    if q:
        needle = q.strip().lower()
        items = [t for t in items
                 if needle in t["title"].lower() or needle in t["description"].lower()]

    wv = video_for_week(j, w)
    return {
        "items": [{**t, "saved": t["id"] in saved_ids} for t in items],
        "week_video": ({**wv, "saved": wv["id"] in saved_ids} if wv else None),
        "categories": _CATEGORIES,
        "saved_ids": sorted(saved_ids),
    }


@router.get("/videos/saved")
def saved_videos(uid: str = Depends(current_user)):
    """Saved topics, newest first. Declared before /videos/{id} so 'saved' is not
    captured as a video id."""
    return {"items": [{**_BY_ID[i], "saved": True} for i in _saved_ids(uid) if i in _BY_ID]}


@router.get("/videos/{video_id}")
def get_video(video_id: str, uid: str = Depends(current_user)):
    init()
    t = _BY_ID.get(video_id)
    if not t:
        raise HTTPException(status_code=404, detail="unknown video")
    return {**t, "saved": video_id in set(_saved_ids(uid))}


@router.post("/videos/{video_id}/save")
def save_video(video_id: str, uid: str = Depends(current_user)):
    """Bookmark a topic. Idempotent — saving twice is not an error."""
    init()
    if video_id not in _BY_ID:
        raise HTTPException(status_code=404, detail="unknown video")
    _conn.execute("INSERT INTO video_saves (user_id, video_id, created) VALUES (?,?,?) "
                  "ON CONFLICT(user_id, video_id) DO NOTHING",
                  (uid, video_id, time.time()))
    _conn.commit()
    return {"saved": True, "video_id": video_id}


@router.delete("/videos/{video_id}/save")
def unsave_video(video_id: str, uid: str = Depends(current_user)):
    """Remove a bookmark. Idempotent — removing one you don't have is fine."""
    init()
    _conn.execute("DELETE FROM video_saves WHERE user_id=? AND video_id=?", (uid, video_id))
    _conn.commit()
    return {"saved": False, "video_id": video_id}
