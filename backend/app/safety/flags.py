"""Where an amber/red screen is RECORDED, so escalations are auditable.

Split from `gate.py` deliberately: the decision is a pure function, this half
talks to a database, and keeping them in one file meant the most safety-critical
logic in the product could not be read without also reading its SQL.

What is stored here is the user's own words about their health — the most
sensitive text in the system. So:

  - green turns are NEVER stored (they are the overwhelming majority and carry
    no safety signal),
  - rows expire via `purge_expired()`, run at startup so retention holds without
    an external cron, and
  - the message is withheld from `viewer` admins; reviewing a flag needs
    `support`.
"""
import json
import logging
import time

from app import config
from app.core import db
from app.safety.gate import GREEN, _RANK

log = logging.getLogger("aira.safety.flags")

# How long a flagged message is kept. The row holds the user's own words about
# their health, so it is the most sensitive text in the system; `purge_expired()`
# enforces this at startup. 0 disables the purge (not advisable in production).
RETENTION_DAYS = config.SAFETY_FLAG_RETENTION_DAYS

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
