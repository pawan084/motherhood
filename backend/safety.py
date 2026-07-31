"""The safety gate — screens EVERY inbound message before Aira replies.

Two layers, combined toward caution:

 1. A deterministic keyword pre-filter (`_keyword_level`). Always runs, needs no
    network, and catches the obvious emergencies even when the LLM is
    misconfigured or down. This is the fail-safe floor.
 2. The Gemini classifier (prompts `aira.safety_classifier`). Catches nuance the
    keywords miss.

The final level is the MORE urgent of the two. A `red` result short-circuits the
turn (chat.py never sends it to the reply model) and routes the client to the
urgent-care handoff. Every amber/red screen is recorded to `safety_flags` for
admin review, so escalations are auditable.

This lives server-side on purpose: the web and Android prototypes each shipped
only half of the safety flow, and drifted. One gate here means both clients
route urgent input identically and correctly.
"""
import logging
import re
import os
import time

import db
import prompts
import services

log = logging.getLogger("aira.safety")

# How long a flagged message is kept. The row holds the user's own words about
# their health, so it is the most sensitive text in the system; `purge_expired()`
# enforces this at startup. 0 disables the purge (not advisable in production).
RETENTION_DAYS = int(os.environ.get("SAFETY_FLAG_RETENTION_DAYS", "90"))

GREEN, AMBER, RED = "green", "amber", "red"
_RANK = {GREEN: 0, AMBER: 1, RED: 2}


def worse(a: str, b: str) -> str:
    return a if _RANK.get(a, 0) >= _RANK.get(b, 0) else b


# Curated phrase lists, matched against a NORMALISED message — see _normalise.
# English plus common Hindi/Hinglish transliterations, since Aira's audience
# uses all three.
#
# Every entry here is written without apostrophes because the message has had
# them stripped before matching. That one step closes a whole class of misses:
# the list previously held "can't breathe" and a user typing "i cant breathe"
# — which is how most people type on a phone — was screened GREEN and handed a
# normal AI reply.
# The languages this floor can actually read.
#
# This is a safety boundary, not a feature list. RED_PHRASES and _RED_PATTERNS
# below are English plus common Hindi/Hinglish transliterations, so a message in
# any other language reaches the deterministic floor and passes straight
# through. Offering a language the floor cannot read invites somebody to write
# in it — and when the LLM classifier is unavailable, which is the fallback
# state the floor exists for, nothing else is looking.
#
# The web client offered Spanish. Verified before this was added: "mi bebe no se
# mueve" — my baby isn't moving, the single most important signal here —
# screened GREEN, as did "sangrado abundante y no puedo respirar".
#
# Adding a language means adding its red-flag vocabulary here FIRST, checked by
# someone who speaks it. `test_language_coverage.py` holds this list to that.
SUPPORTED_LANGUAGES = ("English", "Hindi", "Hinglish")

RED_PHRASES = (
    "heavy bleeding", "bleeding heavily", "wont stop bleeding", "cannot stop bleeding",
    "gushing blood", "lot of blood", "so much blood", "severe bleeding",
    "soaking a pad", "soaking pads", "filling a pad", "bahut khoon", "khoon bah raha",
    "chest pain", "chest hurts", "cant breathe", "cannot breathe", "trouble breathing",
    "struggling to breathe", "saans nahi",
    "severe pain", "unbearable pain", "worst headache", "vision blur", "blurred vision",
    "blurry vision", "seeing spots", "flashing lights", "fainted", "passed out",
    "seizure", "convulsion",
    "water broke", "water breaking",
    "kill myself", "end my life", "end it all", "suicidal", "want to die",
    "hurt myself", "harm myself", "hurting myself", "harming myself",
    "harm my baby", "hurt my baby", "hurt the baby", "overdose", "cant go on",
    "dont want to be here", "no reason to live",
)

# Some red flags are a shape, not a phrase. Fetal movement is the clearest
# case: "baby isnt moving", "havent felt the baby move", "no kicks since last
# night" and "the baby stopped moving" all describe the same emergency and
# share almost no words. Substring matching cannot express that, and reduced
# fetal movement is among the most important things this floor has to catch.
_RED_PATTERNS = (
    re.compile(r"\b(baby|bub|bump|little one)\b[^.?!]{0,30}"
               r"\b(isnt|is not|hasnt|has not|not|stopped|arent|are not)\b"
               r"[^.?!]{0,15}\b(mov\w*|kick\w*|active)\b"),
    re.compile(r"\b(no|any|fewer|less|reduced|hardly any)\b[^.?!]{0,15}"
               r"\b(kicks?|movements?|movement)\b"),
    re.compile(r"\b(havent|have not|hasnt|has not|not)\b[^.?!]{0,20}"
               r"\bfelt\b[^.?!]{0,20}\b(baby|move|moving|kick|kicks)\b"),
    # Self-harm phrased as intent rather than with the listed nouns.
    re.compile(r"\b(want|going|thinking|plan|planning)\b[^.?!]{0,20}"
               r"\b(end|kill|hurt|harm)\b[^.?!]{0,12}"
               r"\b(it|me|myself|my life|things|it all)\b"),
)

_AMBER_PHRASES = (
    "bleeding", "spotting", "cramping", "cramps", "contractions", "headache",
    "dizzy", "dizziness", "swelling", "swollen", "fever", "vomiting", "throwing up",
    "reduced movement", "less movement", "leaking", "blurry", "anxious", "panic",
    "depressed", "hopeless", "cant sleep", "so tired", "burning pee",
    "pain when", "hurts a lot", "dard", "bukhar", "chakkar",
)

# Apostrophes (straight and curly) are removed rather than replaced, so
# "isn't" becomes "isnt" and matches an entry written that way. Everything else
# non-alphanumeric collapses to a space so punctuation can't split a phrase.
_APOSTROPHES = str.maketrans("", "", "'’ʼ`")


def _normalise(message: str) -> str:
    lowered = (message or "").lower().translate(_APOSTROPHES)
    return re.sub(r"[^a-z0-9]+", " ", lowered).strip()


def _keyword_level(message: str) -> tuple[str, list[str]]:
    """Deterministic floor. Returns (level, matched_categories).

    This is the ONLY screening when no classifier is configured — which is the
    state this app currently ships in — so a miss here is not a degraded
    experience, it is no screening at all for that message.
    """
    m = _normalise(message)
    red = [p for p in RED_PHRASES if p in m]
    red += [p.pattern[:28] for p in _RED_PATTERNS if p.search(m)]
    if red:
        return RED, red
    amber = [p for p in _AMBER_PHRASES if p in m]
    if amber:
        return AMBER, amber
    return GREEN, []


def _llm_level(message: str, history: list[dict]) -> dict | None:
    """The classifier's verdict, or None when the LLM is unavailable/unparseable
    (so the caller falls back to the keyword floor rather than failing open)."""
    if not services.configured():
        return None
    system = prompts.resolve("aira.safety_classifier", prompts.SAFETY_CLASSIFIER)
    convo = "\n".join(f"{h['role']}: {h['content']}" for h in (history or [])[-6:])
    user = (f"Recent conversation:\n{convo}\n\n" if convo else "") + f"Latest message:\n{message}"
    try:
        data = services.gemini_json(system, user, temperature=0.0,
                                    model=services.SAFETY_MODEL)
    except Exception as e:  # noqa: BLE001 — never let the classifier crash a turn
        log.warning("safety classifier call failed: %s", e)
        return None
    level = str(data.get("level", "")).strip().lower()
    if level not in _RANK:
        return None
    cats = data.get("categories")
    cats = [str(c) for c in cats] if isinstance(cats, list) else []
    return {"level": level, "categories": cats, "reason": str(data.get("reason", ""))}


def screen(message: str, history: list[dict] | None = None,
           ctx: dict | None = None) -> dict:
    """Screen one message. Returns:
        {level, categories[], reason, degraded, keyword_level, llm_level}
    `degraded` is True when the LLM layer was unavailable and only the keyword
    floor applied — the client should still honor `level`, but the admin console
    surfaces it so a silent classifier outage is visible."""
    kw_level, kw_cats = _keyword_level(message)
    llm = _llm_level(message, history or [])

    if llm is None:
        return {"level": kw_level, "categories": kw_cats,
                "reason": "keyword-only (classifier unavailable)",
                "degraded": True, "keyword_level": kw_level, "llm_level": None}

    final = worse(kw_level, llm["level"])
    cats = sorted(set(kw_cats) | set(llm["categories"]))
    return {"level": final, "categories": cats, "reason": llm["reason"],
            "degraded": False, "keyword_level": kw_level, "llm_level": llm["level"]}


# ── Flag storage (admin review) ──────────────────────────────────────────────

_conn = None


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS safety_flags ("
              f" id {db.AUTOINC_PK},"
              " ts REAL, user_id TEXT, level TEXT, categories TEXT,"
              " message TEXT, degraded INTEGER DEFAULT 0,"
              " reviewed INTEGER DEFAULT 0, reviewed_by TEXT DEFAULT '', note TEXT DEFAULT '')")
    c.execute("CREATE INDEX IF NOT EXISTS safety_flags_ts ON safety_flags(ts)")
    # Degradation is counted, not sampled. A green turn carries no safety signal
    # worth storing, but a classifier outage still has to be VISIBLE — so the
    # count of degraded screens lives here, per UTC hour, with no message text.
    c.execute("CREATE TABLE IF NOT EXISTS safety_degraded ("
              " bucket TEXT PRIMARY KEY, n INTEGER DEFAULT 0)")
    c.commit()
    _conn = c


def _note_degraded() -> None:
    """Count one screen that ran keyword-only, bucketed by UTC hour."""
    bucket = time.strftime("%Y-%m-%dT%H", time.gmtime())
    try:
        _conn.execute("INSERT INTO safety_degraded (bucket, n) VALUES (?,1) "
                      "ON CONFLICT(bucket) DO UPDATE SET n = safety_degraded.n + 1",
                      (bucket,))
        _conn.commit()
    except Exception as e:  # noqa: BLE001 — telemetry must never break a turn
        log.warning("degraded counter failed: %s", e)


def record(user_id: str, message: str, result: dict) -> None:
    """Persist an amber/red screen for admin review.

    Green turns are NEVER stored — they're the overwhelming majority and carry
    no safety signal. That holds in degraded mode too: this used to make an
    exception for `degraded`, which meant a classifier outage (also the default
    state with no GEMINI_API_KEY) quietly persisted every ordinary message
    verbatim, i.e. retention silently INCREASED exactly when things were broken.
    The outage is now surfaced by `_note_degraded()`, which counts without
    keeping any text.

    Stored messages are the user's own words about their health — the most
    sensitive text in the system. They expire via `purge_expired()` and are
    withheld from `viewer` admins (see `recent_flags(include_message=...)`)."""
    init()
    if result.get("degraded"):
        _note_degraded()
    if result.get("level") == GREEN:
        return
    import json
    _conn.execute(
        "INSERT INTO safety_flags (ts, user_id, level, categories, message, degraded) "
        "VALUES (?,?,?,?,?,?)",
        (time.time(), user_id or "", result.get("level"),
         json.dumps(result.get("categories") or []), (message or "")[:2000],
         1 if result.get("degraded") else 0))
    _conn.commit()


def recent_flags(limit: int = 100, level: str | None = None,
                 unreviewed_only: bool = False,
                 include_message: bool = True) -> list[dict]:
    """Flags for the admin console, newest first.

    `include_message=False` returns every reviewable field EXCEPT the user's
    verbatim words. Callers pass it for admins below `support` — a `viewer`
    cannot action a flag (`review_flag` requires `support`), so letting them
    read the health text was privilege without purpose."""
    init()
    import json
    q = ("SELECT id, ts, user_id, level, categories, message, degraded, reviewed, "
         "reviewed_by, note FROM safety_flags")
    where, params = [], []
    if level in _RANK:
        where.append("level=?"); params.append(level)
    if unreviewed_only:
        where.append("reviewed=0")
    if where:
        q += " WHERE " + " AND ".join(where)
    q += " ORDER BY ts DESC LIMIT ?"
    # Clamp low as well as high: a negative limit means "no limit" to SQLite
    # (LIMIT -1) and is a hard error on Postgres.
    params.append(max(1, min(int(limit), 500)))
    rows = _conn.execute(q, tuple(params)).fetchall()
    out = []
    for r in rows:
        out.append({"id": r[0], "ts": r[1], "user_id": r[2], "level": r[3],
                    "categories": json.loads(r[4] or "[]"),
                    "message": r[5] if include_message else "",
                    "message_redacted": not include_message,
                    "degraded": bool(r[6]), "reviewed": bool(r[7]),
                    "reviewed_by": r[8] or "", "note": r[9] or ""})
    return out


def purge_expired(retention_days: int | None = None) -> int:
    """Delete flags older than the retention window; returns rows removed. Runs
    at startup so retention holds without an external cron. The degraded
    counters are kept twice as long: they hold no message text, and a slow leak
    in classifier availability is easier to see over a longer window."""
    days = RETENTION_DAYS if retention_days is None else retention_days
    if days <= 0:
        return 0
    init()
    cutoff = time.time() - days * 86400
    cur = _conn.execute("DELETE FROM safety_flags WHERE ts < ?", (cutoff,))
    removed = getattr(cur, "rowcount", 0) or 0
    _conn.execute("DELETE FROM safety_degraded WHERE bucket < ?",
                  (time.strftime("%Y-%m-%dT%H", time.gmtime(time.time() - days * 2 * 86400)),))
    _conn.commit()
    if removed:
        log.info("purged %d safety flag(s) older than %d days", removed, days)
    return removed


def mark_reviewed(flag_id: int, actor: str, note: str = "") -> bool:
    init()
    cur = _conn.execute(
        "UPDATE safety_flags SET reviewed=1, reviewed_by=?, note=? WHERE id=?",
        (actor, note[:1000], int(flag_id)))
    _conn.commit()
    return getattr(cur, "rowcount", 0) != 0


def stats() -> dict:
    """Counts for the admin dashboard."""
    init()
    def _count(sql, params=()):
        row = _conn.execute(sql, params).fetchone()
        return int(row[0]) if row and row[0] is not None else 0
    since = time.strftime("%Y-%m-%dT%H", time.gmtime(time.time() - 86400))
    return {
        "total": _count("SELECT COUNT(*) FROM safety_flags"),
        "red": _count("SELECT COUNT(*) FROM safety_flags WHERE level='red'"),
        "amber": _count("SELECT COUNT(*) FROM safety_flags WHERE level='amber'"),
        "unreviewed": _count("SELECT COUNT(*) FROM safety_flags WHERE reviewed=0"),
        # Flagged turns the classifier missed (it was down when they came in).
        "degraded": _count("SELECT COUNT(*) FROM safety_flags WHERE degraded=1"),
        # ALL keyword-only screens in the last 24h, green included. This is the
        # real outage signal: it moves even when no flag is raised, which is the
        # common case during an outage.
        "degraded_screens_24h": _count(
            "SELECT COALESCE(SUM(n),0) FROM safety_degraded WHERE bucket >= ?", (since,)),
        "retention_days": RETENTION_DAYS,
    }


# ── per-user data (privacy.py: export / delete) ──────────────────────────────

def export_user(uid: str) -> list[dict]:
    """This user's own flagged messages, for their data export."""
    init()
    import json
    rows = _conn.execute(
        "SELECT ts, level, categories, message, degraded FROM safety_flags "
        "WHERE user_id=? ORDER BY ts", (uid,)).fetchall()
    return [{"ts": r[0], "level": r[1], "categories": json.loads(r[2] or "[]"),
             "message": r[3], "degraded": bool(r[4])} for r in rows]


def delete_user(uid: str) -> int:
    init()
    cur = _conn.execute("DELETE FROM safety_flags WHERE user_id=?", (uid,))
    _conn.commit()
    return getattr(cur, "rowcount", 0) or 0
