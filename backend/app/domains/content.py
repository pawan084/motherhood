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

from app.core import db

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
    # After a loss.
    #
    # A separate stage rather than a flag on another one, because every other
    # stage in this app is a direction of travel and this one is not. Someone
    # who has had a miscarriage previously had to pick "Trying to conceive" —
    # which offers conception content — or "Just exploring", which says nothing
    # happened. Both are wrong and one is cruel.
    #
    # The copy asks for nothing. It does not mention trying again, does not
    # count anything, does not say "when you are ready" — because that is still
    # a nudge toward a next step, from an app whose job here is to not be one.
    "loss": {
        "title": "After a loss",
        "this_week": "There is nothing you need to do here",
        "body": "Aira will keep hold of appointments, medicines and questions "
                "for as long as that is useful, and will not ask you for "
                "anything else.",
        "sections": [
            {"title": "Your body", "text": "Physical recovery, and what is worth asking about"},
            {"title": "Support", "text": "People to talk to, when and if you want them"},
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

# What is worth a call rather than a wait, banded the same way.
#
# ── Where this copy comes from, and what it is not ──
#
# Every signal named here is already in `safety.RED_PHRASES` or its fetal-
# movement patterns — the deterministic floor that ends a chat turn with the
# urgent handoff. Nothing new is being asserted: this says out loud, in advance,
# what the app already treats as an emergency when someone types it. Telling
# people only *after* they describe something frightening is a poor way to run a
# safety net.
#
# What it is not: a symptom checker, a diagnosis, or a threshold anyone should
# reason from. Each line names a signal and one instruction — contact your care
# team — because the decision this content supports is "do I call?", not "what
# is wrong with me?".
#
# ── Review ──
#
# These seeds have NOT been through clinical review. They are drafted from the
# app's existing red-flag vocabulary and are editable and publishable from the
# admin console like every other entry, precisely so a clinician can correct
# them without a release. `call_tip_reviewed` on the payload says which state a
# given tip is in, and clients are expected to show that honestly rather than
# implying a doctor wrote it.
_PREGNANCY_CALL_TIPS = [
    (1, "When to call, early on",
        "Heavy bleeding, severe or one-sided pain, or feeling faint are worth "
        "contacting your care team about the same day — not waiting to see."),
    (13, "When to call",
         "Heavy bleeding, a severe headache, or changes in your vision are "
         "reasons to contact your care team now rather than wait."),
    (20, "When to call",
         "Once you know your baby's usual pattern, any change in how much they "
         "move is worth calling about straight away — at any hour. Never wait "
         "for the next appointment to mention it."),
    (28, "When to call",
         "A severe headache, blurred vision or seeing spots, sudden swelling in "
         "your face or hands, or reduced movement all mean contact your care "
         "team now. These can be checked quickly, and checking is the point."),
    (37, "When to call",
         "Your waters breaking, any bleeding, or reduced movement mean call "
         "your care team now, whatever the time. So does anything that simply "
         "feels wrong to you."),
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
    # The "when to call" tips seed as DRAFT, not published.
    #
    # Draft is not a formality here. Until someone publishes a row, users are
    # served this file's seed with `reviewed: false`, and the console shows a
    # row waiting to be read — which is the honest state for copy that tells a
    # pregnant person when to ring their care team and has not been checked by
    # anyone qualified. Publishing is the act of taking responsibility for it.
    for band_start, title, body in _PREGNANCY_CALL_TIPS:
        _conn.execute(
            "INSERT INTO content_entries (key, journey, title, body, status, updated) "
            "VALUES (?,?,?,?,'draft',?) ON CONFLICT(key) DO NOTHING",
            (call_tip_key(band_start), "pregnant", title, body, time.time()))
    _conn.commit()


def _band(weeks: int) -> tuple[str, str]:
    """The (headline, body) for a pregnancy week — the last band whose start is
    at or below `weeks`, falling back to the earliest band."""
    chosen = _PREGNANCY_WEEKS[0]
    for band in _PREGNANCY_WEEKS:
        if weeks >= band[0]:
            chosen = band
    return chosen[1], chosen[2]


def _call_band(weeks: int) -> tuple[int, str, str]:
    """The (band_start, title, body) "when to call" tip for a pregnancy week."""
    chosen = _PREGNANCY_CALL_TIPS[0]
    for band in _PREGNANCY_CALL_TIPS:
        if weeks >= band[0]:
            chosen = band
    return chosen


def call_tip_key(band_start: int) -> str:
    """The content_entries key an admin edits to override a band's tip."""
    return f"tip.pregnant.{band_start}"


def _published_entry(key: str) -> tuple[str, str, bool]:
    """(title, body, reviewed) for any content key.

    `reviewed` is True only when an admin has published a row for it — which is
    the single thing that distinguishes clinician-corrected copy from the seed
    drafted in this file. Clients show that distinction rather than implying
    every tip was written by a doctor."""
    if _conn is None:
        return "", "", False
    try:
        row = _conn.execute(
            "SELECT title, body, status, reviewed_by FROM content_entries WHERE key=?",
            (key,)).fetchone()
    except Exception as e:  # noqa: BLE001 — never let a content lookup break a screen
        log.warning("content lookup for %s failed: %s", key, e)
        return "", "", False
    if not row or (row[2] or "") != "published":
        return "", "", False
    return (row[0] or "").strip(), (row[1] or "").strip(), bool((row[3] or "").strip())


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


def _call_tip_payload(weeks: int) -> dict:
    """The `call_tip` fields for a pregnancy week, or nothing when the week is
    unknown."""
    if weeks <= 0:
        return {}
    band_start, seed_title, seed_body = _call_band(weeks)
    title, body, reviewed = _published_entry(call_tip_key(band_start))
    return {
        "call_tip": {
            "title": title or seed_title,
            "body": body or seed_body,
            # False while the copy is this file's seed. A client that shows a
            # "when to call" line as clinician-reviewed when nobody has reviewed
            # it is making the strongest claim in the app on no evidence.
            "reviewed": reviewed and bool(title or body),
        },
    }


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
            # What is worth a call rather than a wait, for this band. Only ever
            # sent to a pregnant user with a known week: a "when to call" line
            # is meaningless without knowing which signals apply, and guessing
            # at a week is how the old hardcoded "Week 24" showed a stranger's
            # pregnancy to everyone.
            **_call_tip_payload(w),
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
    if journey == "loss":
        # Today proposes one thing to everybody else. Here it proposes the one
        # thing that asks nothing of you and can be ignored without cost. A
        # check-in would be a question, and a care-plan prompt would be an
        # errand; neither is something to put in front of somebody this week.
        return {"tool": "wellness", "title": "A two-minute reset",
                "detail": "Only if it would help. Nothing here needs doing.",
                "minutes": 2}
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
