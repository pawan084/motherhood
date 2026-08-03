"""Admin API: authentication, the safety-audit review queue, the Journey CMS,
and an admin action log.

Modeled on sayli's admin with the surface cut to what Aira needs now:

- **Roles are a linear hierarchy**: owner > support > content. A dependency
  `require_admin("support")` admits support and owner. Owner manages admins;
  support reviews the safety queue; content edits Journey bands.
- **The safety review queue is the reason this phase exists**: every gate
  decision is already audited (P2); this gives a human the way to actually
  read them — filterable by decision, paginated, and markable as reviewed
  with a note. An escalation system nobody reviews is theater.
- **Every mutating admin action is logged** to admin_audit (who, what, when).
  The reviewers are subject to review too.
- Passwords: PBKDF2-HMAC-SHA256 (stdlib), per-admin salt, 600k iterations.
  Tokens: the same HMAC envelope as the rest of the product, disjoint
  claim shape (kind="admin"), signed with ADMIN_JWT_SECRET, 12h expiry.
- Bootstrap: ADMIN_BOOTSTRAP_EMAIL/_PASSWORD seed an owner on first boot of
  an empty admins table — same mechanism sayli used to solve day-zero.
"""
import base64
import hashlib
import hmac
import json
import os
import secrets
import time

from fastapi import APIRouter, Depends, Header, HTTPException, Query
from pydantic import BaseModel

import config
import db

router = APIRouter(prefix="/admin", tags=["admin"])

_conn = None

_ROLE_RANK = {"content": 1, "support": 2, "owner": 3}
_TOKEN_TTL = 12 * 3600
_PBKDF2_ITERS = 600_000


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS admins ("
              " id TEXT PRIMARY KEY, email TEXT UNIQUE NOT NULL,"
              " pw_hash TEXT NOT NULL, salt TEXT NOT NULL,"
              " role TEXT NOT NULL, created REAL)")
    c.execute("CREATE TABLE IF NOT EXISTS admin_audit ("
              " id TEXT PRIMARY KEY, admin_id TEXT, action TEXT,"
              " detail TEXT, ts REAL)")
    # Review state lives beside the safety audit, not inside it: the gate's
    # record stays append-only.
    c.execute("CREATE TABLE IF NOT EXISTS safety_reviews ("
              " audit_id TEXT PRIMARY KEY, admin_id TEXT,"
              " note TEXT DEFAULT '', ts REAL)")
    c.commit()
    _conn = c
    _bootstrap()


def _bootstrap() -> None:
    email = os.environ.get("ADMIN_BOOTSTRAP_EMAIL", "").strip().lower()
    password = os.environ.get("ADMIN_BOOTSTRAP_PASSWORD", "")
    if not email or not password:
        return
    if _conn.execute("SELECT COUNT(*) FROM admins").fetchone()[0]:
        return  # never overwrite an existing admin set
    create_admin(email, password, "owner")


def _hash_pw(password: str, salt: str) -> str:
    return hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt),
                               _PBKDF2_ITERS).hex()


def create_admin(email: str, password: str, role: str) -> str:
    if role not in _ROLE_RANK:
        raise HTTPException(status_code=422, detail=f"role must be one of {sorted(_ROLE_RANK)}")
    salt = secrets.token_hex(16)
    aid = "adm_" + secrets.token_hex(8)
    _conn.execute(
        "INSERT INTO admins (id, email, pw_hash, salt, role, created) VALUES (?,?,?,?,?,?)",
        (aid, email.strip().lower(), _hash_pw(password, salt), salt, role, time.time()))
    _conn.commit()
    return aid


def _make_token(admin_id: str, role: str) -> str:
    payload = {"sub": admin_id, "role": role, "kind": "admin",
               "exp": int(time.time()) + _TOKEN_TTL}
    body = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    sig = hmac.new(config.ADMIN_JWT_SECRET.encode(), body.encode(), hashlib.sha256).hexdigest()
    return f"{body}.{sig}"


def _verify_token(token: str) -> dict | None:
    try:
        body, sig = token.split(".", 1)
        expect = hmac.new(config.ADMIN_JWT_SECRET.encode(), body.encode(),
                          hashlib.sha256).hexdigest()
        if not hmac.compare_digest(sig, expect):
            return None
        pad = "=" * (-len(body) % 4)
        payload = json.loads(base64.urlsafe_b64decode(body + pad))
        if (not isinstance(payload, dict) or payload.get("kind") != "admin"
                or "sub" not in payload or "exp" not in payload):
            return None
        return None if payload["exp"] < time.time() else payload
    except Exception:
        return None


def require_admin(min_role: str):
    """Dependency factory: admits admins at or above `min_role`."""
    need = _ROLE_RANK[min_role]

    def dep(authorization: str = Header(default="")) -> dict:
        payload = _verify_token(authorization.removeprefix("Bearer ").strip())
        if not payload:
            raise HTTPException(status_code=401, detail="unauthorized")
        if _ROLE_RANK.get(payload.get("role"), 0) < need:
            raise HTTPException(status_code=403, detail="insufficient role")
        return payload

    return dep


def _log(admin_id: str, action: str, detail: str = "") -> None:
    _conn.execute("INSERT INTO admin_audit (id, admin_id, action, detail, ts)"
                  " VALUES (?,?,?,?,?)",
                  ("aud_" + secrets.token_hex(8), admin_id, action, detail[:500], time.time()))
    _conn.commit()


# --- auth --------------------------------------------------------------------

class LoginIn(BaseModel):
    email: str
    password: str


@router.post("/login")
def login(body: LoginIn):
    row = _conn.execute("SELECT id, pw_hash, salt, role FROM admins WHERE email=?",
                        (body.email.strip().lower(),)).fetchone()
    # Constant-shape: hash the attempt even for unknown emails so timing
    # doesn't distinguish "no such admin" from "wrong password".
    salt = row[2] if row else secrets.token_hex(16)
    attempt = _hash_pw(body.password, salt)
    if not row or not hmac.compare_digest(attempt, row[1]):
        raise HTTPException(status_code=401, detail="invalid credentials")
    return {"token": _make_token(row[0], row[3]), "role": row[3]}


@router.get("/me")
def me(admin: dict = Depends(require_admin("content"))):
    row = _conn.execute("SELECT email, role FROM admins WHERE id=?",
                        (admin["sub"],)).fetchone()
    if not row:
        raise HTTPException(status_code=401, detail="unauthorized")
    return {"email": row[0], "role": row[1]}


# --- overview ----------------------------------------------------------------

@router.get("/overview")
def overview(admin: dict = Depends(require_admin("content"))):
    c = _conn
    screens = c.execute("SELECT COUNT(*) FROM safety_audit").fetchone()[0]
    urgent = c.execute("SELECT COUNT(*) FROM safety_audit WHERE decision='urgent'").fetchone()[0]
    unreviewed_urgent = c.execute(
        "SELECT COUNT(*) FROM safety_audit a LEFT JOIN safety_reviews r"
        " ON r.audit_id = a.id WHERE a.decision='urgent' AND r.audit_id IS NULL"
    ).fetchone()[0]
    return {
        "learners_with_context": c.execute("SELECT COUNT(*) FROM care_context").fetchone()[0],
        "accounts": c.execute("SELECT COUNT(*) FROM accounts").fetchone()[0],
        "turns_screened": screens,
        "urgent_turns": urgent,
        "unreviewed_urgent": unreviewed_urgent,
    }


# --- safety review queue -----------------------------------------------------

@router.get("/safety-audit")
def safety_audit(decision: str | None = Query(default=None),
                 unreviewed: bool = Query(default=False),
                 offset: int = Query(default=0, ge=0),
                 limit: int = Query(default=50, ge=1, le=200),
                 admin: dict = Depends(require_admin("support"))):
    where, params = [], []
    if decision:
        where.append("a.decision=?")
        params.append(decision)
    if unreviewed:
        where.append("r.audit_id IS NULL")
    clause = (" WHERE " + " AND ".join(where)) if where else ""
    base = (" FROM safety_audit a LEFT JOIN safety_reviews r ON r.audit_id=a.id" + clause)
    total = _conn.execute("SELECT COUNT(*)" + base, params).fetchone()[0]
    rows = _conn.execute(
        "SELECT a.id, a.ts, a.input, a.stage, a.week, a.decision, a.source,"
        " a.matched, a.reason, a.latency_ms, r.note, r.ts" + base +
        " ORDER BY a.ts DESC LIMIT ? OFFSET ?", (*params, limit, offset)).fetchall()
    return {"total": total, "rows": [
        {"id": r[0], "ts": r[1], "input": r[2], "stage": r[3], "week": r[4],
         "decision": r[5], "source": r[6], "matched": json.loads(r[7] or "[]"),
         "reason": r[8], "latency_ms": r[9],
         "review": ({"note": r[10], "ts": r[11]} if r[11] is not None else None)}
        for r in rows]}


class ReviewIn(BaseModel):
    note: str = ""


@router.post("/safety-audit/{audit_id}/review")
def review_audit(audit_id: str, body: ReviewIn,
                 admin: dict = Depends(require_admin("support"))):
    row = _conn.execute("SELECT id FROM safety_audit WHERE id=?", (audit_id,)).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="not found")
    _conn.execute(
        "INSERT INTO safety_reviews (audit_id, admin_id, note, ts) VALUES (?,?,?,?)"
        " ON CONFLICT (audit_id) DO UPDATE SET admin_id=excluded.admin_id,"
        " note=excluded.note, ts=excluded.ts",
        (audit_id, admin["sub"], body.note.strip()[:500], time.time()))
    _conn.commit()
    _log(admin["sub"], "safety_review", audit_id)
    return {"ok": True}


# --- journey CMS -------------------------------------------------------------

@router.get("/journey")
def list_journey(admin: dict = Depends(require_admin("content"))):
    rows = _conn.execute(
        "SELECT id, stage, week_start, week_end, title, yourself, baby, prepare"
        " FROM journey_content ORDER BY stage, week_start").fetchall()
    return {"bands": [
        {"id": r[0], "stage": r[1], "week_start": r[2], "week_end": r[3],
         "title": r[4], "yourself": r[5], "baby": r[6], "prepare": r[7]}
        for r in rows]}


class BandIn(BaseModel):
    title: str
    yourself: str = ""
    baby: str = ""
    prepare: str = ""


@router.put("/journey/{band_id}")
def update_band(band_id: str, body: BandIn,
                admin: dict = Depends(require_admin("content"))):
    cur = _conn.execute(
        "UPDATE journey_content SET title=?, yourself=?, baby=?, prepare=?, updated=?"
        " WHERE id=?",
        (body.title.strip()[:120], body.yourself.strip()[:2000],
         body.baby.strip()[:2000], body.prepare.strip()[:2000], time.time(), band_id))
    _conn.commit()
    if cur.rowcount == 0:
        raise HTTPException(status_code=404, detail="not found")
    _log(admin["sub"], "journey_update", band_id)
    return {"ok": True}


# --- admin management (owner) ------------------------------------------------

class AdminIn(BaseModel):
    email: str
    password: str
    role: str


@router.get("/admins")
def list_admins(admin: dict = Depends(require_admin("owner"))):
    rows = _conn.execute("SELECT email, role, created FROM admins ORDER BY created").fetchall()
    return {"admins": [{"email": r[0], "role": r[1], "created": r[2]} for r in rows]}


@router.post("/admins")
def add_admin(body: AdminIn, admin: dict = Depends(require_admin("owner"))):
    if len(body.password) < 12:
        raise HTTPException(status_code=422, detail="password must be at least 12 characters")
    if _conn.execute("SELECT 1 FROM admins WHERE email=?",
                     (body.email.strip().lower(),)).fetchone():
        raise HTTPException(status_code=409, detail="email already exists")
    create_admin(body.email, body.password, body.role)
    _log(admin["sub"], "admin_create", f"{body.email} ({body.role})")
    return {"ok": True}


@router.get("/audit-log")
def audit_log(offset: int = Query(default=0, ge=0),
              limit: int = Query(default=50, ge=1, le=200),
              admin: dict = Depends(require_admin("owner"))):
    rows = _conn.execute(
        "SELECT l.ts, a.email, l.action, l.detail FROM admin_audit l"
        " LEFT JOIN admins a ON a.id = l.admin_id"
        " ORDER BY l.ts DESC LIMIT ? OFFSET ?", (limit, offset)).fetchall()
    return {"rows": [{"ts": r[0], "email": r[1], "action": r[2], "detail": r[3]}
                     for r in rows]}
