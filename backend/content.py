"""Clinical/wellness content service — journey-aware copy with review + version
metadata, admin-editable.

The prototypes hardcoded a single "24-weeks-pregnant" persona for everyone. Here
the copy is keyed by journey (trying / pregnant / postpartum / exploring), so a
postpartum user never sees fetal-week cards. Each entry carries `version`,
`status` (draft/published) and `reviewed_by/reviewed_at`, so clinical review is
first-class rather than an afterthought — the app only ever serves `published`
entries, falling back to the in-code seed.

`journey_content` resolves each field through `_published()` before the seed, so
a console edit reaches users. (It previously read `_SEED` alone, which made the
whole editor cosmetic: an admin could publish a new version and no user would
ever see it.) A blank field or a `draft` status falls back to the reviewed
in-code copy, so an edit can be reverted by clearing it.
"""
import logging
import time

import db

log = logging.getLogger("aira.content")
_conn = None

# In-code seed. Each is the reviewed default; admins can edit/version in console.
# `this_week` is the headline; `body` the gentle explanation; `focus` the tags a
# journey cares about. Pregnancy content is week-banded.
_SEED = {
    "trying": {
        "title": "Trying to conceive",
        "this_week": "Small, steady habits matter most",
        "body": "Gentle routines — sleep, movement, and noticing your cycle — do "
                "more than any single day. Nothing here is a diagnosis.",
        "sections": [
            {"title": "Your body", "text": "Cycle patterns and what feels normal for you"},
            {"title": "Everyday support", "text": "Sleep, nutrition and stress, kept simple"},
            {"title": "When to ask", "text": "What's worth raising with your care team"},
        ],
    },
    "pregnant": {
        "title": "Your pregnancy",
        # week-banded copy is chosen by `journey_content` from `_PREGNANCY_WEEKS`.
    },
    "postpartum": {
        "title": "Postpartum recovery",
        "this_week": "Recovery is not a race",
        "body": "Your body and rhythm are still settling. Rest when you can, and "
                "let Aira hold the small reminders so you don't have to.",
        "sections": [
            {"title": "Your recovery", "text": "Healing, sleep and mood, week by week"},
            {"title": "Feeding & baby", "text": "Rhythms and what's typical, without pressure"},
            {"title": "When to ask", "text": "Signs worth contacting your care team about"},
        ],
    },
    "exploring": {
        "title": "Exploring with Aira",
        "this_week": "Look around at your own pace",
        "body": "Nothing here is a diagnosis. Explore the tools and set up a "
                "private care context whenever you're ready.",
        "sections": [
            {"title": "How Aira helps", "text": "One calm conversation, not a dashboard"},
            {"title": "Privacy first", "text": "You control what Aira remembers"},
        ],
    },
}

# Pregnancy copy banded by trimester/week so the "Week N" card is real, not a
# fixed 24. Chosen by the largest band start <= weeks.
_PREGNANCY_WEEKS = [
    (1,  "Early days", "Your body is doing quiet, enormous work. Fatigue and "
                       "nausea are common; be gentle with yourself."),
    (13, "Second trimester begins", "Energy often returns. A good time to plan "
                                    "appointments and ask the questions on your mind."),
    (20, "Halfway there", "Movement may start to feel more regular. Notice your "
                          "own pattern rather than comparing with others."),
    (28, "Third trimester", "Rest matters more now. Small daily check-ins help "
                            "you and your care team spot changes early."),
    (37, "Nearly there", "Full term is close. Keep your care team's number and "
                         "your emergency profile easy to reach."),
]


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS content_entries ("
              " key TEXT PRIMARY KEY, journey TEXT, title TEXT, body TEXT,"
              " version INTEGER DEFAULT 1, status TEXT DEFAULT 'published',"
              " reviewed_by TEXT DEFAULT '', reviewed_at REAL, updated REAL)")
    c.commit()
    _conn = c
    # Seed published entries (idempotent).
    for journey, data in _SEED.items():
        _conn.execute(
            "INSERT INTO content_entries (key, journey, title, body, status, updated) "
            "VALUES (?,?,?,?,'published',?) ON CONFLICT(key) DO NOTHING",
            (f"journey.{journey}", journey, data.get("title", ""),
             data.get("body", ""), time.time()))
    _conn.commit()


def _band(weeks: int) -> tuple[str, str]:
    """The (headline, body) for a pregnancy week — the last band whose start is
    at or below `weeks`, falling back to the earliest band."""
    chosen = _PREGNANCY_WEEKS[0]
    for band in _PREGNANCY_WEEKS:
        if weeks >= band[0]:
            chosen = band
    return chosen[1], chosen[2]


def _published(journey: str) -> tuple[str, str]:
    """The admin-published (title, body) for a journey, or ('', '') when there is
    no row, the row is blank, or it is still a `draft`.

    Mirrors `prompts.resolve`: a blank field means "use the reviewed in-code
    default", so clearing a field in the console restores the seed rather than
    shipping an empty screen. Reads live, so an edit applies with no restart, and
    a registry error degrades to the seed instead of breaking the screen."""
    if _conn is None:
        return "", ""
    try:
        row = _conn.execute("SELECT title, body, status FROM content_entries WHERE key=?",
                            (f"journey.{journey}",)).fetchone()
    except Exception as e:  # noqa: BLE001 — never let a content lookup break a screen
        log.warning("content lookup for %s failed: %s", journey, e)
        return "", ""
    if not row or (row[2] or "") != "published":
        return "", ""          # drafts are never served to users
    return (row[0] or "").strip(), (row[1] or "").strip()


def journey_content(journey: str, weeks: int | None = None) -> dict:
    """The Journey-screen payload for a user. Journey-aware; pregnancy is
    week-banded. Never returns pregnancy content for a non-pregnant journey.

    Copy resolution, per field:
      `title`, `body` — the published `content_entries` row wins, else the
        in-code seed (or, for a known pregnancy week, the week band). This is
        what makes the admin content editor real: before, this function read
        only `_SEED`, so console edits bumped a version number and changed
        nothing a user ever saw.
      `this_week`, `sections` — in-code only. They are structural (the week
        headline is derived from `weeks`), and the console does not expose them,
        so pretending they were editable would be the same bug in reverse."""
    journey = (journey or "exploring").strip().lower()
    if journey not in _SEED:
        journey = "exploring"
    title_override, body_override = _published(journey)
    if journey == "pregnant":
        w = int(weeks or 0)
        if w > 0:
            this_week, band_body = _band(w)
        else:
            this_week, band_body = "Your pregnancy", "Week-by-week guidance, only what's useful now."
        return {
            "journey": "pregnant", "title": title_override or "Your pregnancy",
            "weeks": w or None,
            "this_week": this_week, "body": body_override or band_body,
            "sections": [
                {"title": "Your body", "text": "Energy, sleep and changes worth knowing"},
                {"title": "Your baby", "text": "Growth explained without overload"},
                {"title": "Your next visit", "text": "What to ask and what to bring"},
            ],
        }
    data = _SEED[journey]
    return {"journey": journey, "title": title_override or data["title"], "weeks": None,
            "this_week": data.get("this_week", ""),
            "body": body_override or data.get("body", ""),
            "sections": data.get("sections", [])}


# The "one meaningful next action" for Today — journey-aware, nudged by the
# user's stated priorities. Deliberately ONE card (the product's whole premise).
def next_action(journey: str, priorities: list[str] | None = None) -> dict:
    journey = (journey or "exploring").strip().lower()
    pr = [p.lower() for p in (priorities or [])]
    if journey == "pregnant":
        return {"tool": "appointment", "title": "Prepare for your next appointment",
                "detail": "A few questions based on how you've been feeling.",
                "minutes": 3}
    if journey == "postpartum":
        if any("sleep" in p for p in pr):
            return {"tool": "wellness", "title": "A two-minute reset",
                    "detail": "A calm pause for a tired day.", "minutes": 2}
        return {"tool": "checkin", "title": "A gentle check-in",
                "detail": "How are you recovering today?", "minutes": 2}
    if journey == "trying":
        return {"tool": "checkin", "title": "Log today's check-in",
                "detail": "Small, steady notes help you see your pattern.", "minutes": 2}
    return {"tool": "careplan", "title": "Set up your care context",
            "detail": "Tell Aira where you are, at your pace.", "minutes": 3}


# ── admin surface ────────────────────────────────────────────────────────────

def list_entries() -> list[dict]:
    init()
    cols = ("key", "journey", "title", "body", "version", "status", "reviewed_by",
            "reviewed_at", "updated")
    rows = _conn.execute(f"SELECT {','.join(cols)} FROM content_entries ORDER BY key").fetchall()
    return [dict(zip(cols, r)) for r in rows]


def update_entry(key: str, *, title=None, body=None, status=None, actor="") -> dict:
    init()
    row = _conn.execute("SELECT version FROM content_entries WHERE key=?", (key,)).fetchone()
    if row is None:
        raise KeyError(key)
    sets, params = ["version=version+1", "updated=?"], [time.time()]
    if title is not None:
        sets.append("title=?"); params.append(title)
    if body is not None:
        sets.append("body=?"); params.append(body)
    if status is not None:
        if status not in ("draft", "published"):
            raise ValueError("bad status")
        sets.append("status=?"); params.append(status)
    if actor:
        sets.append("reviewed_by=?"); params.append(actor)
        sets.append("reviewed_at=?"); params.append(time.time())
    params.append(key)
    _conn.execute(f"UPDATE content_entries SET {','.join(sets)} WHERE key=?", tuple(params))
    _conn.commit()
    cols = ("key", "journey", "title", "body", "version", "status", "reviewed_by",
            "reviewed_at", "updated")
    return dict(zip(cols, _conn.execute(
        f"SELECT {','.join(cols)} FROM content_entries WHERE key=?", (key,)).fetchone()))
