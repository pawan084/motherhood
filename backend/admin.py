"""Admin API — auth (RBAC), audit log, and Aira's operational surfaces
(dashboard, users, safety-flag review, feedback, content, prompts, system).

Dependency-free auth by design (stdlib only): pbkdf2 password hashing +
HMAC-signed session cookies with CSRF double-submit, account-keyed login
throttling, sliding idle + absolute session caps, and token-version revocation —
the same hardened pattern a high-value console needs. Mounted under `/admin/*`.

Bootstrap an owner by setting env before first run:
    ADMIN_BOOTSTRAP_EMAIL=you@acme.com  ADMIN_BOOTSTRAP_PASSWORD=...
    ADMIN_JWT_SECRET=<long-random>      # MUST be set in production
"""
import base64
import hashlib
import hmac
import json
import logging
import os
import secrets
import time

from fastapi import APIRouter, Cookie, Depends, Header, HTTPException, Request, Response
from pydantic import BaseModel

import accounts
import analytics_store
import content
import db
import feedback
import passwords
import prompts
import safety
import security
import services

log = logging.getLogger("aira.admin")
ADMIN_SECRET = os.environ.get("ADMIN_JWT_SECRET", "dev-admin-secret-change-me")
_ROLES = {"viewer": 0, "support": 1, "owner": 2}
SESSION_COOKIE = "admin_session"
SESSION_TTL = int(os.environ.get("ADMIN_SESSION_IDLE_TTL", str(2 * 3600)))          # 2h idle
SESSION_ABSOLUTE_TTL = int(os.environ.get("ADMIN_SESSION_ABSOLUTE_TTL", str(12 * 3600)))  # 12h max
CSRF_COOKIE = "csrf_token"
SESSION_SAMESITE = os.environ.get("ADMIN_COOKIE_SAMESITE", "strict").strip().lower()
_UNSAFE_METHODS = {"POST", "PUT", "DELETE", "PATCH"}
SESSION_COOKIE_DOMAIN = os.environ.get("ADMIN_COOKIE_DOMAIN", "").strip() or None

router = APIRouter(prefix="/admin", tags=["admin"])
_conn = None


# ── storage ──────────────────────────────────────────────────────────────────

def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS admin_users ("
              " email TEXT PRIMARY KEY, pw_hash TEXT NOT NULL, role TEXT NOT NULL,"
              " created REAL, token_version INTEGER DEFAULT 0)")
    c.execute("CREATE TABLE IF NOT EXISTS admin_audit ("
              " ts REAL, actor TEXT, action TEXT, detail TEXT)")
    c.commit()
    _conn = c
    email = os.environ.get("ADMIN_BOOTSTRAP_EMAIL", "").strip().lower()
    pw = os.environ.get("ADMIN_BOOTSTRAP_PASSWORD", "")
    if email and pw and c.execute("SELECT COUNT(*) FROM admin_users").fetchone()[0] == 0:
        create_user(email, pw, "owner")
        log.info("Seeded bootstrap admin owner %s", email)
    if ADMIN_SECRET == "dev-admin-secret-change-me":
        log.warning("ADMIN_JWT_SECRET is the insecure default — set a real one in production.")


def create_user(email: str, password: str, role: str) -> None:
    init()
    email = email.lower()
    existed = _conn.execute("SELECT 1 FROM admin_users WHERE email=?", (email,)).fetchone() is not None
    _conn.execute("INSERT INTO admin_users (email, pw_hash, role, created) VALUES (?,?,?,?) "
                  "ON CONFLICT(email) DO UPDATE SET pw_hash=excluded.pw_hash, role=excluded.role",
                  (email, _hash_pw(password), role, time.time()))
    _conn.commit()
    if existed:
        bump_token_version(email)


def _audit(actor: str, action: str, detail: str = "") -> None:
    _conn.execute("INSERT INTO admin_audit (ts, actor, action, detail) VALUES (?,?,?,?)",
                  (time.time(), actor, action, detail))
    _conn.commit()


# ── password + token (stdlib) ────────────────────────────────────────────────
#
# The hashing, timing dummy and login throttle moved to `passwords.py` when
# consumer email/password sign-in needed the same primitives. Keeping a second
# copy here would have been the kind of duplication that quietly diverges — one
# side gets an iteration bump or a timing fix and the other doesn't. These names
# stay as aliases so the call sites below read unchanged.

PBKDF2_ITERATIONS = passwords.PBKDF2_ITERATIONS
_hash_pw = passwords.hash_pw
_verify_pw = passwords.verify_pw
_DUMMY_PW_HASH = passwords.DUMMY_PW_HASH

# Admin lockouts are keyed separately from consumer ones, so a locked admin
# address never affects a consumer account that happens to share the email.
def _throttle_check(email: str) -> None:
    if passwords.throttle_locked(f"admin:{email}"):
        raise HTTPException(status_code=401, detail="invalid credentials")


def _throttle_fail(email: str) -> None:
    passwords.throttle_fail(f"admin:{email}")


def _throttle_reset(email: str) -> None:
    passwords.throttle_reset(f"admin:{email}")


def _token_version(email: str) -> int:
    row = _conn.execute("SELECT token_version FROM admin_users WHERE email=?", (email,)).fetchone()
    if row is None:
        return -1
    return int(row[0]) if row[0] is not None else 0


def bump_token_version(email: str) -> None:
    init()
    _conn.execute("UPDATE admin_users SET token_version=COALESCE(token_version,0)+1 WHERE email=?",
                  (email.lower(),))
    _conn.commit()


def _make_token(email: str, role: str, tv: int, iat: int | None = None, ttl: int = SESSION_TTL) -> str:
    now = int(time.time())
    iat = iat or now
    payload = {"sub": email, "role": role, "exp": now + ttl, "iat": iat, "tv": tv}
    body = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    sig = hmac.new(ADMIN_SECRET.encode(), body.encode(), hashlib.sha256).hexdigest()
    return f"{body}.{sig}"


def _verify_token(token: str) -> dict | None:
    try:
        body, sig = token.split(".", 1)
        expect = hmac.new(ADMIN_SECRET.encode(), body.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(sig, expect):
            return None
        pad = "=" * (-len(body) % 4)
        payload = json.loads(base64.urlsafe_b64decode(body + pad))
        now = time.time()
        if not isinstance(payload, dict) or "sub" not in payload or "exp" not in payload:
            return None
        if payload["exp"] < now:
            return None
        if now - payload.get("iat", now) > SESSION_ABSOLUTE_TTL:
            return None
        if payload.get("tv", 0) != _token_version(payload["sub"]):
            return None
        return payload
    except Exception:
        return None


def _set_session_cookie(response: Response, token: str) -> None:
    response.set_cookie(SESSION_COOKIE, token, max_age=SESSION_TTL, httponly=True,
                        secure=security.is_production(), samesite=SESSION_SAMESITE,
                        path="/", domain=SESSION_COOKIE_DOMAIN)


def _set_csrf_cookie(response: Response, value: str) -> None:
    response.set_cookie(CSRF_COOKIE, value, max_age=SESSION_TTL, httponly=False,
                        secure=security.is_production(), samesite=SESSION_SAMESITE,
                        path="/", domain=SESSION_COOKIE_DOMAIN)


def require_admin(min_role: str = "viewer"):
    def dep(request: Request, response: Response,
            authorization: str = Header(default=""),
            admin_session: str = Cookie(default=""),
            csrf_token: str = Cookie(default=""),
            x_csrf_token: str = Header(default="")) -> dict:
        init()
        from_header = bool(authorization.removeprefix("Bearer ").strip())
        token = authorization.removeprefix("Bearer ").strip() or admin_session
        payload = _verify_token(token)
        if not payload:
            raise HTTPException(status_code=401, detail="unauthorized")
        if _ROLES.get(payload.get("role"), -1) < _ROLES.get(min_role, 99):
            raise HTTPException(status_code=403, detail="forbidden")
        if request.method in _UNSAFE_METHODS and not from_header:
            if not csrf_token or not hmac.compare_digest(x_csrf_token, csrf_token):
                raise HTTPException(status_code=403, detail="csrf token missing or invalid")
        if not from_header:
            age = time.time() - payload.get("iat", time.time())
            if age > SESSION_TTL / 2:
                fresh = _make_token(payload["sub"], payload["role"], payload.get("tv", 0),
                                    iat=int(payload.get("iat", time.time())))
                _set_session_cookie(response, fresh)
                _set_csrf_cookie(response, csrf_token or secrets.token_hex(16))
        return payload
    return dep


# ── auth routes ──────────────────────────────────────────────────────────────

class LoginIn(BaseModel):
    email: str
    password: str


@router.post("/login")
def login(body: LoginIn, response: Response):
    init()
    email = body.email.strip().lower()
    _throttle_check(email)
    row = _conn.execute("SELECT pw_hash, role FROM admin_users WHERE email=?", (email,)).fetchone()
    stored = row[0] if row else _DUMMY_PW_HASH  # constant-time whether or not the account exists
    if not _verify_pw(body.password, stored) or not row:
        _throttle_fail(email)
        raise HTTPException(status_code=401, detail="invalid credentials")
    _throttle_reset(email)
    role = row[1]
    if _needs_rehash(stored):
        _conn.execute("UPDATE admin_users SET pw_hash=? WHERE email=?", (_hash_pw(body.password), email))
        _conn.commit()
    tv = _token_version(email)
    token = _make_token(email, role, tv)
    _set_session_cookie(response, token)
    csrf = secrets.token_hex(16)
    _set_csrf_cookie(response, csrf)
    _audit(email, "login")
    return {"role": role, "email": email}


def _needs_rehash(stored: str) -> bool:
    parts = stored.split("$")
    if len(parts) != 3:
        return True
    try:
        return int(parts[0]) < PBKDF2_ITERATIONS
    except ValueError:
        return True


@router.post("/logout")
def logout(response: Response, admin=Depends(require_admin())):
    response.delete_cookie(SESSION_COOKIE, path="/", domain=SESSION_COOKIE_DOMAIN)
    response.delete_cookie(CSRF_COOKIE, path="/", domain=SESSION_COOKIE_DOMAIN)
    return {"ok": True}


@router.get("/me")
def me(admin=Depends(require_admin())):
    return {"email": admin["sub"], "role": admin["role"]}


# ── dashboard ────────────────────────────────────────────────────────────────

@router.get("/overview")
def overview(days: int = 30, admin=Depends(require_admin())):
    return {"metrics": analytics_store.overview(days),
            "safety": safety.stats(), "feedback": feedback.stats()}


# ── users ────────────────────────────────────────────────────────────────────

@router.get("/users")
def list_users(q: str = "", limit: int = 100, admin=Depends(require_admin())):
    accounts.init()
    cols = ("id", "kind", "email", "name", "journey", "language", "onboarded",
            "created", "last_seen")
    sql = f"SELECT {','.join(cols)} FROM users"
    params: list = []
    if q.strip():
        like = db.like_param(q)
        sql += " WHERE LOWER(COALESCE(email,'')) LIKE ? ESCAPE '\\' OR LOWER(COALESCE(name,'')) LIKE ? ESCAPE '\\'"
        params += [like, like]
    sql += " ORDER BY COALESCE(last_seen, created) DESC LIMIT ?"
    params.append(min(int(limit), 500))
    rows = accounts._conn.execute(sql, tuple(params)).fetchall()
    items = []
    for r in rows:
        d = dict(zip(cols, r))
        d["onboarded"] = bool(d["onboarded"])
        items.append(d)
    return {"items": items}


@router.get("/users/{user_id}")
def get_user(user_id: str, admin=Depends(require_admin())):
    u = accounts.get_user(user_id)
    if not u:
        raise HTTPException(status_code=404, detail="not found")
    return {"user": u}


# ── safety flags ─────────────────────────────────────────────────────────────

@router.get("/safety/flags")
def safety_flags(level: str = "", unreviewed: bool = False, limit: int = 100,
                 admin=Depends(require_admin())):
    # The flag message is the user's verbatim words about their health. Reviewing
    # a flag already requires `support` (see review_flag), so a `viewer` reading
    # that text was access without a job to do — they get the metadata instead.
    can_read_message = _ROLES.get(admin.get("role"), -1) >= _ROLES["support"]
    return {"items": safety.recent_flags(limit=limit, level=level or None,
                                         unreviewed_only=unreviewed,
                                         include_message=can_read_message),
            "messages_redacted": not can_read_message,
            "stats": safety.stats()}


class ReviewIn(BaseModel):
    note: str | None = None


@router.post("/safety/flags/{flag_id}/review")
def review_flag(flag_id: int, body: ReviewIn, admin=Depends(require_admin("support"))):
    if not safety.mark_reviewed(flag_id, admin["sub"], body.note or ""):
        raise HTTPException(status_code=404, detail="not found")
    _audit(admin["sub"], "safety.review", str(flag_id))
    return {"ok": True}


# ── feedback ─────────────────────────────────────────────────────────────────

@router.get("/feedback")
def get_feedback(kind: str = "", status: str = "", limit: int = 200,
                 admin=Depends(require_admin())):
    return {"items": feedback.list_feedback(kind or None, status or None, limit),
            "stats": feedback.stats()}


@router.post("/feedback/{fid}/resolve")
def resolve_feedback(fid: str, admin=Depends(require_admin("support"))):
    if not feedback.resolve_feedback(fid, admin["sub"]):
        raise HTTPException(status_code=404, detail="not found")
    _audit(admin["sub"], "feedback.resolve", fid)
    return {"ok": True}


# ── content ──────────────────────────────────────────────────────────────────

@router.get("/content")
def get_content(admin=Depends(require_admin())):
    return {"items": content.list_entries()}


class ContentIn(BaseModel):
    title: str | None = None
    body: str | None = None
    status: str | None = None


@router.patch("/content/{key}")
def patch_content(key: str, body: ContentIn, admin=Depends(require_admin("support"))):
    try:
        entry = content.update_entry(key, title=body.title, body=body.body,
                                     status=body.status, actor=admin["sub"])
    except KeyError:
        raise HTTPException(status_code=404, detail="not found")
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    _audit(admin["sub"], "content.edit", key)
    return {"entry": entry}


# ── prompts ──────────────────────────────────────────────────────────────────

@router.get("/prompts")
def get_prompts(admin=Depends(require_admin())):
    return {"items": prompts.all_rows()}


class PromptIn(BaseModel):
    text: str


# Prompts that are safety controls rather than copy.
#
# Editing the tone of the pregnancy journey text and editing the instructions
# the safety classifier runs on are not the same risk, and they sat behind the
# same role: a support admin could replace the classifier's prompt with "always
# answer level green" and neutralise the AI layer of the gate for every user,
# with the same two clicks as fixing a typo. The keyword floor would still
# apply, but the classifier is precisely the layer that catches what a word
# list cannot.
# `aira.system` joined it for the same reason, found the same way. It is not
# tone — it is the only place that says "You are NOT a doctor and you never
# diagnose, prescribe, or tell someone to start/stop/change any medication",
# that the care team stays in view, and that nothing is presented as medical
# advice. A support admin could replace the whole thing with "be kind" (a test
# in this repo did exactly that, and called it ordinary copy) and every GREEN
# and AMBER reply would lose those constraints at once, invisibly — the gate
# still fires on RED, so nothing looks broken. Tone still belongs to support
# via the journey phrases and the content entries; the medical framing does not.
SAFETY_CRITICAL_PROMPTS = {"aira.safety_classifier", "aira.system"}


def _guard_prompt_key(key: str, admin: dict) -> None:
    if key in SAFETY_CRITICAL_PROMPTS and admin.get("role") != "owner":
        raise HTTPException(
            status_code=403,
            detail="this prompt is a safety control and can only be changed by an owner")


@router.put("/prompts/{key}")
def put_prompt(key: str, body: PromptIn, admin=Depends(require_admin("support"))):
    _guard_prompt_key(key, admin)
    # An empty prompt is not an edit, it is a silent removal: the model gets no
    # instructions and the failure looks like bad answers rather than a missing
    # configuration.
    if not body.text.strip():
        raise HTTPException(status_code=400, detail="a prompt cannot be empty")
    prompts.set_row(key, body.text, actor=admin["sub"])
    _audit(admin["sub"], "prompt.edit", key)
    return {"ok": True}


@router.post("/prompts/{key}/reset")
def reset_prompt(key: str, admin=Depends(require_admin("support"))):
    # Reset is always allowed for support: putting a safety prompt BACK to its
    # in-code default is the one change to it that cannot make things worse.
    default = prompts.default_for(key)
    if default is None:
        raise HTTPException(status_code=404, detail="no in-code default for key")
    prompts.set_row(key, default, actor=admin["sub"])
    _audit(admin["sub"], "prompt.reset", key)
    return {"ok": True}


# ── admins management (owner) ────────────────────────────────────────────────

@router.get("/admins")
def list_admins(admin=Depends(require_admin("owner"))):
    init()
    rows = _conn.execute("SELECT email, role, created FROM admin_users ORDER BY created").fetchall()
    return {"items": [{"email": r[0], "role": r[1], "created": r[2]} for r in rows]}


class AdminIn(BaseModel):
    email: str
    password: str
    role: str = "viewer"


@router.post("/admins")
def add_admin(body: AdminIn, admin=Depends(require_admin("owner"))):
    if body.role not in _ROLES:
        raise HTTPException(status_code=400, detail="bad role")
    create_user(body.email, body.password, body.role)
    _audit(admin["sub"], "admin.create", f"{body.email}:{body.role}")
    return {"ok": True}


# ── system ───────────────────────────────────────────────────────────────────

@router.get("/system")
def system(admin=Depends(require_admin())):
    return {
        "env": "production" if security.is_production() else "development",
        "db": {"engine": "postgres" if db.IS_POSTGRES else "sqlite"},
        "providers": [services.provider_health()],
        "auth": {"app_token_required": bool(security.APP_SHARED_SECRET),
                 "google_signin": bool(accounts.GOOGLE_CLIENT_ID)},
    }
