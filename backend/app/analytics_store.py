"""Product analytics — an events table plus rollups the admin dashboard reads.

`record_event` is a fire-and-forget write callers can use; the rollups here also
derive activity directly from real tables (users.last_seen, chat_turns) so the
dashboard is meaningful even before any explicit events are emitted.
"""
import json
import logging
import time

from app.core import db

log = logging.getLogger("aira.analytics")
_conn = None
_DAY = 86400


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS events ("
              f" id {db.AUTOINC_PK},"
              " ts REAL, user_id TEXT, name TEXT, props TEXT DEFAULT '{}')")
    c.execute("CREATE INDEX IF NOT EXISTS events_ts ON events(ts)")
    c.commit()
    _conn = c


def record_event(user_id: str, name: str, props: dict | None = None) -> None:
    init()
    try:
        _conn.execute("INSERT INTO events (ts, user_id, name, props) VALUES (?,?,?,?)",
                      (time.time(), user_id or "", name, json.dumps(props or {})))
        _conn.commit()
    except Exception as e:  # noqa: BLE001 — analytics must never break a request
        log.warning("record_event failed: %s", e)


def export_user(uid: str) -> list[dict]:
    init()
    rows = _conn.execute("SELECT ts, name, props FROM events WHERE user_id=? ORDER BY ts",
                         (uid,)).fetchall()
    return [{"ts": r[0], "name": r[1], "props": json.loads(r[2] or "{}")} for r in rows]


def delete_user(uid: str) -> int:
    init()
    cur = _conn.execute("DELETE FROM events WHERE user_id=?", (uid,))
    _conn.commit()
    return getattr(cur, "rowcount", 0) or 0


def _count(sql: str, params=()) -> int:
    init()
    row = _conn.execute(sql, params).fetchone()
    return int(row[0]) if row and row[0] is not None else 0


def _active_since(cutoff: float) -> int:
    # Distinct users seen since `cutoff`, from the real users table.
    return _count("SELECT COUNT(*) FROM users WHERE last_seen >= ?", (cutoff,))


def overview(days: int = 30) -> dict:
    """Headline metrics for the admin dashboard."""
    init()
    now = time.time()
    events_by_name: dict[str, int] = {}
    for name, n in _conn.execute(
            "SELECT name, COUNT(*) FROM events WHERE ts >= ? GROUP BY name",
            (now - days * _DAY,)).fetchall():
        events_by_name[name] = n
    # New-users-by-day series over the window.
    series: dict[str, int] = {}
    for created, in _conn.execute("SELECT created FROM users WHERE created >= ?",
                                  (now - days * _DAY,)).fetchall():
        if created:
            day = time.strftime("%Y-%m-%d", time.gmtime(created))
            series[day] = series.get(day, 0) + 1
    return {
        "total_users": _count("SELECT COUNT(*) FROM users"),
        "accounts": _count("SELECT COUNT(*) FROM users WHERE kind='account'"),
        "dau": _active_since(now - _DAY),
        "wau": _active_since(now - 7 * _DAY),
        "mau": _active_since(now - 30 * _DAY),
        "new_users_by_day": series,
        "chat_turns": _count("SELECT COUNT(*) FROM chat_turns"),
        "events_by_name": events_by_name,
        "range_days": days,
    }
