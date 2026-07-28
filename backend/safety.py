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
import time

import db
import prompts
import services

log = logging.getLogger("aira.safety")

GREEN, AMBER, RED = "green", "amber", "red"
_RANK = {GREEN: 0, AMBER: 1, RED: 2}


def worse(a: str, b: str) -> str:
    return a if _RANK.get(a, 0) >= _RANK.get(b, 0) else b


# Curated phrase lists. English plus common Hindi/Hinglish transliterations,
# since Aira's audience uses all three. Substring match on a lowercased message,
# so keep entries specific enough to avoid false positives ("bleeding" is fine;
# a bare "pain" is not — it would flag every "growing pains" message).
_RED_PHRASES = (
    "heavy bleeding", "bleeding heavily", "won't stop bleeding", "cannot stop bleeding",
    "gushing blood", "lot of blood", "severe bleeding", "bahut khoon", "khoon bah raha",
    "chest pain", "can't breathe", "cannot breathe", "trouble breathing", "saans nahi",
    "severe pain", "unbearable pain", "worst headache", "vision blur", "blurred vision",
    "seeing spots", "fainted", "passed out", "seizure", "convulsion",
    "water broke", "water breaking", "no movement", "baby not moving", "not moving",
    "kill myself", "end my life", "suicidal", "want to die", "harm my baby",
    "hurt my baby", "hurt the baby", "overdose", "can't go on",
)
_AMBER_PHRASES = (
    "bleeding", "spotting", "cramping", "cramps", "contractions", "headache",
    "dizzy", "dizziness", "swelling", "swollen", "fever", "vomiting", "throwing up",
    "reduced movement", "less movement", "leaking", "blurry", "anxious", "panic",
    "depressed", "hopeless", "can't sleep", "cant sleep", "so tired", "burning pee",
    "pain when", "hurts a lot", "dard", "bukhar", "chakkar",
)


def _keyword_level(message: str) -> tuple[str, list[str]]:
    """Deterministic floor. Returns (level, matched_categories)."""
    m = (message or "").lower()
    red = [p for p in _RED_PHRASES if p in m]
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
              " id INTEGER PRIMARY KEY AUTOINCREMENT,"
              " ts REAL, user_id TEXT, level TEXT, categories TEXT,"
              " message TEXT, degraded INTEGER DEFAULT 0,"
              " reviewed INTEGER DEFAULT 0, reviewed_by TEXT DEFAULT '', note TEXT DEFAULT '')")
    c.commit()
    _conn = c


def record(user_id: str, message: str, result: dict) -> None:
    """Persist an amber/red screen for admin review. Green turns are not stored
    (they're the overwhelming majority and carry no safety signal). The message
    is the user's own words — treated as sensitive; retention/redaction is a
    production policy decision (see PRODUCTION notes in README)."""
    if result.get("level") == GREEN and not result.get("degraded"):
        return
    init()
    import json
    _conn.execute(
        "INSERT INTO safety_flags (ts, user_id, level, categories, message, degraded) "
        "VALUES (?,?,?,?,?,?)",
        (time.time(), user_id or "", result.get("level"),
         json.dumps(result.get("categories") or []), (message or "")[:2000],
         1 if result.get("degraded") else 0))
    _conn.commit()


def recent_flags(limit: int = 100, level: str | None = None,
                 unreviewed_only: bool = False) -> list[dict]:
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
    params.append(min(int(limit), 500))
    rows = _conn.execute(q, tuple(params)).fetchall()
    out = []
    for r in rows:
        out.append({"id": r[0], "ts": r[1], "user_id": r[2], "level": r[3],
                    "categories": json.loads(r[4] or "[]"), "message": r[5],
                    "degraded": bool(r[6]), "reviewed": bool(r[7]),
                    "reviewed_by": r[8] or "", "note": r[9] or ""})
    return out


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
        return _conn.execute(sql, params).fetchone()[0]
    return {
        "total": _count("SELECT COUNT(*) FROM safety_flags"),
        "red": _count("SELECT COUNT(*) FROM safety_flags WHERE level='red'"),
        "amber": _count("SELECT COUNT(*) FROM safety_flags WHERE level='amber'"),
        "unreviewed": _count("SELECT COUNT(*) FROM safety_flags WHERE reviewed=0"),
        "degraded": _count("SELECT COUNT(*) FROM safety_flags WHERE degraded=1"),
    }
