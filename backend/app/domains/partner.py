"""Partner access: an invite a mother creates, shares out-of-band, and can revoke.

The clients previously rendered an "Invite partner" button that showed a toast
and created nothing. This is the real thing, kept deliberately narrow:

  owner  POST /v1/partner/invite          -> a single-use code + expiry
         GET  /v1/partner/invites         -> what they've issued, and its state
         POST /v1/partner/invites/{id}/revoke
  partner POST /v1/partner/accept         -> redeem a code
         GET  /v1/partner/shared          -> only what each scope allows

Security decisions worth stating, because this is the one route that lets a
second person see someone's maternal health context:

  * The code is 8 bytes from `secrets` (urlsafe, ~11 chars). It is compared with
    `hmac.compare_digest`, single-use, and expires in 7 days by default.
  * Scopes are least-privilege: `health_details` is off unless explicitly asked
    for, and even granted it yields counts, never symptom or check-in text.
    care.shared_view owns that filtering (this module never reads care tables).
  * Revocation is immediate and one-directional — a revoked or expired link
    cannot be un-revoked, only replaced by issuing a new one.
  * You cannot accept your own invite, and an invite that has already been
    redeemed cannot be redeemed again by anyone else.
  * The invite record stores no free text. There is nothing in it to leak
    beyond the fact that a link was issued.

Acceptance links the partner's own account to the owner's; the partner sees
`/partner/shared` and nothing else. There is no partner write path at all — an
invited partner can never modify the owner's care data.

CONSENT. Every read path here is gated on the owner's `partner_access` consent,
which defaults to OFF — sharing maternal health context with a second person is
an explicit opt-in, not something that happens because a button existed. The
gate is checked live on every request rather than only at invite time, so
turning the consent off in the privacy centre immediately stops all partner
reads, including from partners who already accepted.

Revoking consent PAUSES rather than destroys: the invite rows survive, so
turning it back on restores exactly the access that was there before. Permanent
removal is what per-invite revoke is for. Two controls, two meanings — a
privacy switch that quietly deleted things would be its own kind of surprise.
"""
import hmac
import json
import logging
import secrets
import time

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict

from app.domains import accounts
from app.domains import care
from app.domains import consent
from app.core import db
from app.core import security
from app.domains.accounts import current_user

log = logging.getLogger("aira.partner")
router = APIRouter(prefix="/v1", tags=["partner"],
                   dependencies=[Depends(security.require_app_token)])
_conn = None

INVITE_TTL_SECONDS = 7 * 24 * 3600
SCOPE_KEYS = ("appointments", "reminders", "health_details")


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS partner_invites ("
              " id TEXT PRIMARY KEY, owner_id TEXT NOT NULL, code TEXT NOT NULL,"
              " scopes TEXT NOT NULL, created REAL, expires REAL,"
              " accepted_by TEXT, accepted REAL, revoked INTEGER DEFAULT 0)")
    c.execute("CREATE INDEX IF NOT EXISTS partner_invites_owner "
              "ON partner_invites(owner_id)")
    c.execute("CREATE INDEX IF NOT EXISTS partner_invites_accepted_by "
              "ON partner_invites(accepted_by)")
    c.commit()
    _conn = c


_COLS = ("id", "owner_id", "code", "scopes", "created", "expires",
         "accepted_by", "accepted", "revoked")


def _row_to_invite(row, *, include_code: bool) -> dict:
    r = dict(zip(_COLS, row))
    scopes = json.loads(r["scopes"] or "{}")
    now = time.time()
    if r["revoked"]:
        state = "revoked"
    elif r["accepted_by"]:
        state = "accepted"
    elif (r["expires"] or 0) < now:
        state = "expired"
    else:
        state = "pending"
    out = {"id": r["id"], "scopes": scopes, "created": r["created"],
           "expires": r["expires"], "state": state,
           "accepted": r["accepted"]}
    if include_code and state == "pending":
        # Only ever returned to the owner, and only while the link is still
        # usable — there is no reason to keep echoing a spent secret.
        out["code"] = r["code"]
    return out


def _clean_scopes(raw: dict | None) -> dict:
    """Least-privilege: unknown keys are dropped, absent keys are False."""
    raw = raw or {}
    return {k: bool(raw.get(k)) for k in SCOPE_KEYS}


# ── per-user data (privacy.py: export / delete) ──────────────────────────────

def export_user(uid: str) -> dict:
    init()
    issued = _conn.execute(
        f"SELECT {','.join(_COLS)} FROM partner_invites WHERE owner_id=? "
        "ORDER BY created DESC", (uid,)).fetchall()
    holding = _conn.execute(
        f"SELECT {','.join(_COLS)} FROM partner_invites WHERE accepted_by=? "
        "ORDER BY created DESC", (uid,)).fetchall()
    return {
        "invites_issued": [_row_to_invite(r, include_code=False) for r in issued],
        "partner_access_held": [
            {"owner_id": dict(zip(_COLS, r))["owner_id"],
             **_row_to_invite(r, include_code=False)} for r in holding
        ],
    }


def delete_user(uid: str) -> int:
    """Erase invites this user issued, and detach any access they hold.

    Both directions matter. Dropping only the issued rows would leave this user
    still listed as somebody else's partner; dropping the rows where they are
    the partner would silently delete an invite belonging to another user, whose
    data this is not. So: delete what they own, detach what they hold — the
    other person's invite row survives, marked revoked.

    Revoked rather than returned to pending: a pending invite still shows its
    code, and that code would then be acceptable by anyone the owner never meant
    to share with. The owner sees the link is dead and can issue a fresh one.
    """
    init()
    cur = _conn.execute("DELETE FROM partner_invites WHERE owner_id=?", (uid,))
    n = getattr(cur, "rowcount", 0) or 0
    cur2 = _conn.execute("UPDATE partner_invites SET accepted_by=NULL, accepted=NULL, "
                         "revoked=1 WHERE accepted_by=?", (uid,))
    n += getattr(cur2, "rowcount", 0) or 0
    _conn.commit()
    return n


# ── owner routes ─────────────────────────────────────────────────────────────

class InviteIn(BaseModel):
    # Unknown keys are rejected rather than ignored.
    #
    # The defaults here are permissive, so a body in the wrong shape — a client
    # sending {"scopes": {...}} instead of flat fields, say — used to be
    # silently accepted and every default applied, handing a partner MORE than
    # the user had ticked. For a control whose entire job is limiting what
    # somebody else can see, a misunderstood request has to fail loudly rather
    # than fall back to sharing.
    model_config = ConfigDict(extra="forbid")

    appointments: bool = True
    reminders: bool = True
    health_details: bool = False


@router.post("/partner/invite",
             dependencies=[Depends(consent.require_consent("partner_access"))])
def create_invite(body: InviteIn, uid: str = Depends(current_user)):
    """Issue a single-use invite code. The caller shares it however they like —
    the backend deliberately does not send email or SMS, so no contact detail
    for a third party is ever collected or stored.

    403s unless `partner_access` consent is granted. It defaults to off, so the
    first invite a user creates is preceded by an explicit decision to share."""
    init()
    scopes = _clean_scopes(body.model_dump())
    now = time.time()
    invite_id = "inv_" + secrets.token_hex(8)
    code = secrets.token_urlsafe(8)
    _conn.execute("INSERT INTO partner_invites "
                  "(id, owner_id, code, scopes, created, expires) VALUES (?,?,?,?,?,?)",
                  (invite_id, uid, code, json.dumps(scopes), now,
                   now + INVITE_TTL_SECONDS))
    _conn.commit()
    log.info("partner invite %s issued by %s", invite_id, uid)
    return {"id": invite_id, "code": code, "scopes": scopes,
            "expires": now + INVITE_TTL_SECONDS,
            "share_text": ("I'd like to share my care plan with you on Aira. "
                           f"Open Aira and enter this invite code: {code} "
                           "(it works once, and expires in 7 days.)")}


@router.get("/partner/invites")
def list_invites(uid: str = Depends(current_user)):
    init()
    rows = _conn.execute(
        f"SELECT {','.join(_COLS)} FROM partner_invites WHERE owner_id=? "
        "ORDER BY created DESC", (uid,)).fetchall()
    return {"items": [_row_to_invite(r, include_code=True) for r in rows]}


@router.post("/partner/invites/{invite_id}/revoke")
def revoke_invite(invite_id: str, uid: str = Depends(current_user)):
    """Immediate and final. Also cuts off a partner who already accepted —
    revocation has to mean revocation, not just 'stop new sign-ups'."""
    init()
    cur = _conn.execute("UPDATE partner_invites SET revoked=1 WHERE id=? AND owner_id=?",
                        (invite_id, uid))
    _conn.commit()
    if getattr(cur, "rowcount", 0) == 0:
        raise HTTPException(status_code=404, detail="not found")
    log.info("partner invite %s revoked by %s", invite_id, uid)
    return {"ok": True}


# ── partner routes ───────────────────────────────────────────────────────────

class AcceptIn(BaseModel):
    code: str


@router.post("/partner/accept")
def accept_invite(body: AcceptIn, uid: str = Depends(current_user)):
    init()
    submitted = (body.code or "").strip()
    if not submitted:
        raise HTTPException(status_code=400, detail="code required")
    now = time.time()
    rows = _conn.execute(
        f"SELECT {','.join(_COLS)} FROM partner_invites "
        "WHERE revoked=0 AND accepted_by IS NULL AND expires > ?", (now,)).fetchall()
    # Scan + constant-time compare rather than a SQL equality lookup on the
    # secret, so a match doesn't reduce to a timing-visible index probe.
    match = next((r for r in rows
                  if hmac.compare_digest(dict(zip(_COLS, r))["code"], submitted)), None)
    if match is None:
        raise HTTPException(status_code=404, detail="that invite code is not valid")
    inv = dict(zip(_COLS, match))
    if inv["owner_id"] == uid:
        raise HTTPException(status_code=400, detail="you cannot accept your own invite")
    # The OWNER's consent, not the caller's — the person whose data would be
    # shared is the one who has to have agreed to share it. Checked here as well
    # as at creation because consent can be withdrawn while a code is in flight.
    if not consent.is_granted(inv["owner_id"], "partner_access"):
        raise HTTPException(status_code=403,
                            detail="that invite is no longer active")
    cur = _conn.execute("UPDATE partner_invites SET accepted_by=?, accepted=? "
                        "WHERE id=? AND accepted_by IS NULL AND revoked=0",
                        (uid, now, inv["id"]))
    _conn.commit()
    if getattr(cur, "rowcount", 0) == 0:
        # Someone redeemed it between the scan and the write.
        raise HTTPException(status_code=409, detail="that invite has already been used")
    owner = accounts.get_user(inv["owner_id"]) or {}
    log.info("partner invite %s accepted by %s", inv["id"], uid)
    return {"ok": True, "shared_by": owner.get("name") or "your partner",
            "scopes": json.loads(inv["scopes"] or "{}")}


@router.get("/partner/shared")
def shared(uid: str = Depends(current_user)):
    """What this user can see as somebody else's invited partner.

    Empty rather than 404 when they aren't one, so a client can call it
    unconditionally without treating a normal state as an error.
    """
    init()
    rows = _conn.execute(
        f"SELECT {','.join(_COLS)} FROM partner_invites "
        "WHERE accepted_by=? AND revoked=0 ORDER BY accepted DESC", (uid,)).fetchall()
    out = []
    for row in rows:
        inv = dict(zip(_COLS, row))
        # Live consent check per owner, so revoking `partner_access` cuts a
        # partner off on their very next request rather than at some later
        # refresh. This is the difference between a privacy switch and a label.
        if not consent.is_granted(inv["owner_id"], "partner_access"):
            continue
        scopes = _clean_scopes(json.loads(inv["scopes"] or "{}"))
        owner = accounts.get_user(inv["owner_id"]) or {}
        out.append({
            "invite_id": inv["id"],
            "shared_by": owner.get("name") or "your partner",
            "scopes": scopes,
            "data": care.shared_view(inv["owner_id"], scopes),
        })
    return {"items": out}
