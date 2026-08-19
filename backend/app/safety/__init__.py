"""The safety gate — screens EVERY inbound message before Aira replies.

Two halves, kept in separate modules on purpose:

    gate.py    the DECISION. Pure: a message in, a level out. No database, so
               it can be exercised exhaustively without fixtures.
    flags.py   the RECORD. Persists amber/red screens for admin review, owns
               retention, and is the only half that can fail on a disk.

Callers import this package and use it as one namespace (`safety.screen(...)`,
`safety.record(...)`) — the split is an internal boundary, not a new API. Reach
for `aira.safety.flags` directly only when you specifically mean the storage,
which the tests covering retention and export do.
"""
from app.safety.gate import (
    AMBER,
    GREEN,
    RED,
    RED_PHRASES,
    SUPPORTED_LANGUAGES,
    _keyword_level,
    _normalise,
    screen,
    worse,
)
from app.safety.flags import (
    RETENTION_DAYS,
    delete_user,
    export_user,
    init,
    mark_reviewed,
    purge_expired,
    recent_flags,
    record,
    stats,
)

__all__ = [
    # decision
    "GREEN", "AMBER", "RED", "worse", "screen",
    "RED_PHRASES", "SUPPORTED_LANGUAGES",
    # record
    "init", "record", "recent_flags", "purge_expired", "mark_reviewed",
    "stats", "export_user", "delete_user", "RETENTION_DAYS",
]
