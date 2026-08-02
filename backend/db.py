"""Storage adapter: one connection helper for the whole data layer, backed by
SQLite (default, dev) or Postgres (`DATABASE_URL`, prod) — the HTTP surface and
all module SQL stay identical.

Ported from ref/sayli/backend/db.py, which ran this in production. Unchanged in
substance: the thread-local connection model and the `?`→`%s` rewrite are the
whole point, and both were arrived at by fixing real concurrency bugs.

Portability comes from (a) using `ON CONFLICT … DO UPDATE/NOTHING` everywhere
(supported by both SQLite ≥3.24 and Postgres) instead of SQLite-only
`INSERT OR REPLACE/IGNORE`, and (b) a thin wrapper that rewrites `?` placeholders
to `%s` for psycopg. Modules keep their existing `?`-style SQL.

Aira note: this database holds health data — care context, chat turns, symptom
logs, and safety-gate audit rows. SQLite is a DEV default only. Production runs
on Postgres with encryption at rest; see TODO.md's human-gated infrastructure
list.

To run on Postgres:  pip install 'psycopg[binary]'  and set
    DATABASE_URL=postgresql://user:pass@host/db
"""
import os
import sqlite3
import threading
from contextlib import contextmanager

_DATABASE_URL = os.environ.get("DATABASE_URL", "")
IS_POSTGRES = _DATABASE_URL.startswith(("postgres://", "postgresql://"))

# Shared SQLite file (kept next to this module), used when no DATABASE_URL.
# Override with SQLITE_PATH for tests/hermetic runs or an alternate data dir.
SQLITE_PATH = os.environ.get("SQLITE_PATH") or os.path.join(os.path.dirname(__file__), "aira.db")


class _PGCursor:  # pragma: no cover
    """Wrap a psycopg cursor so `?` placeholders and `.fetchone/.fetchall` work
    exactly like the sqlite3 cursors the modules already use."""

    def __init__(self, cur):
        self._cur = cur

    def execute(self, sql, params=()):
        # Escape literal % (psycopg treats it as a format char) BEFORE turning our
        # `?` placeholders into `%s`. Our SQL is first-party/static, so this is safe.
        self._cur.execute(sql.replace("%", "%%").replace("?", "%s"), params)
        return self

    def fetchone(self):
        return self._cur.fetchone()

    def fetchall(self):
        return self._cur.fetchall()

    def __iter__(self):
        # sqlite3 cursors are iterable and several call sites do
        # `for row in conn.execute(...)` — psycopg cursors are natively iterable
        # too, so delegate rather than TypeError-ing only on the Postgres backend.
        return iter(self._cur)

    @property
    def rowcount(self):
        return self._cur.rowcount


class _PGConn:  # pragma: no cover
    def __init__(self, raw):
        self._raw = raw

    def execute(self, sql, params=()):
        cur = _PGCursor(self._raw.cursor())
        return cur.execute(sql, params)

    def commit(self):
        self._raw.commit()

    @contextmanager
    def transaction(self):
        # psycopg's transaction() issues an explicit BEGIN/COMMIT (ROLLBACK on
        # error) even though the connection runs in autocommit mode, making a
        # batch of writes atomic without leaving reads idle-in-transaction.
        with self._raw.transaction():
            yield


class _ThreadLocalConn:
    """A connection handle that transparently gives each thread its OWN real DB
    connection to the same database.

    The HTTP layer runs every handler in `asyncio.to_thread` (real OS threads),
    so a single shared sqlite3/psycopg connection would have multiple threads
    interleaving cursors and commits on one connection object — which neither
    driver makes safe (sqlite3 cursors aren't thread-safe even with WAL, and a
    shared psycopg connection interleaves transactions). Instead each thread
    lazily opens its own connection on first use; SQLite WAL + busy_timeout lets
    those per-thread connections read/write the same file concurrently, and the
    Postgres path gets one autocommit connection per thread."""

    def __init__(self, opener):
        self._opener = opener
        self._local = threading.local()

    def _conn(self):
        c = getattr(self._local, "conn", None)
        if c is None:
            c = self._opener()
            self._local.conn = c
        return c

    def execute(self, sql, params=()):
        return self._conn().execute(sql, params)

    def commit(self):
        self._conn().commit()

    @contextmanager
    def transaction(self):
        """Make a batch of writes atomic on either backend: everything in the
        block commits together, or rolls back if the block raises. Use this for
        multi-statement writes that must not half-apply — notably a safety-gate
        decision and its audit row, which must never be written apart."""
        c = self._conn()
        pg_tx = getattr(c, "transaction", None)
        if pg_tx is not None:  # _PGConn (Postgres)
            with pg_tx():
                yield self
        else:  # sqlite3.Connection: `with conn` commits, or rolls back on error
            with c:
                yield self


def _open_sqlite(path: str):
    conn = sqlite3.connect(path, check_same_thread=False)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.execute("PRAGMA busy_timeout=3000")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn


# One shared handle per database. Modules each call connect() at init, and they
# MUST all receive the same object: a cross-module transaction (e.g. the
# account-deletion cascade, which spans accounts + care_context tables) only
# works if every module's writes ride the same underlying connection. Two
# handles per thread would mean two SQLite connections, one uncommitted write
# each — a "database is locked" deadlock (found by test, not by luck).
_handles: dict[str, _ThreadLocalConn] = {}
_handles_lock = threading.Lock()


def connect(sqlite_path: str | None = None):
    """Return the shared connection handle for the configured backend.

    The handle exposes `.execute(sql, params).fetchone()/.fetchall()`,
    `.commit()` and `.transaction()` — the subset every module uses — and hands
    each thread its own underlying connection so concurrent handlers can't
    corrupt one another's cursors. Repeated calls with the same target return
    the SAME handle (see _handles above).

    `sqlite_path`: override the SQLite file (default: the shared `SQLITE_PATH`).
    Ignored on the Postgres backend.
    """
    if IS_POSTGRES:
        key = "pg"
    else:
        key = sqlite_path or SQLITE_PATH
    with _handles_lock:
        handle = _handles.get(key)
        if handle is None:
            if IS_POSTGRES:
                def _open_pg():
                    import psycopg  # lazy: only needed in the Postgres deployment
                    # autocommit so a per-call execute can't ride/steal another
                    # thread's transaction.
                    return _PGConn(psycopg.connect(_DATABASE_URL, autocommit=True))
                handle = _ThreadLocalConn(_open_pg)
            else:
                handle = _ThreadLocalConn(lambda: _open_sqlite(key))
            _handles[key] = handle
        return handle


def like_param(term: str) -> str:
    r"""Escape a user-supplied search term for a case-insensitive
    `LOWER(col) LIKE ? ESCAPE '\'` match: backslash, % and _ become literals,
    the term is lowercased and wrapped in %...%. Portable SQLite/Postgres."""
    t = (term or "").strip().lower()
    t = t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return f"%{t}%"
