"""The chat turn — the product's spine, exactly as the flow diagram draws it:

    message ──▶ SAFETY GATE ──▶ red  ──▶ urgent-care handoff (no AI reply)
                            └──▶ green/amber ──▶ AI reply + trust label + ONE action card

Every inbound message is screened by safety.py FIRST. A red result never reaches
the reply model — the user is routed straight to their care team. Green/amber
turns get a short, journey-grounded reply carrying a trust label (wellness /
watchful) and at most one dynamic action card.

This is the single safety implementation both clients call, so web and Android
behave identically — closing the gap where each prototype shipped only half of
the flow.
"""
import json
import logging
import time
import uuid

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from app.domains import accounts
from app.domains import care
from app.core import db
from app.domains import memory
from app import prompts
from app import safety
from app.core import security
from app.core import llm
from app.domains.accounts import current_user

log = logging.getLogger("aira.chat")
router = APIRouter(prefix="/v1", tags=["chat"],
                   dependencies=[Depends(security.require_app_token)])
_conn = None

_JOURNEY_KEYS = {
    "trying": "aira.journey.trying", "pregnant": "aira.journey.pregnant",
    "postpartum": "aira.journey.postpartum", "exploring": "aira.journey.exploring",
}
_TRUST_LABEL = {"green": "wellness", "amber": "watchful"}


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS chat_turns ("
              " id TEXT PRIMARY KEY, user_id TEXT, ts REAL, role TEXT, text TEXT,"
              " safety_level TEXT DEFAULT '')")
    c.execute("CREATE INDEX IF NOT EXISTS chat_turns_user ON chat_turns(user_id, ts)")
    c.commit()
    _conn = c


def _save(uid: str, role: str, text: str, level: str = "") -> None:
    init()
    _conn.execute("INSERT INTO chat_turns (id, user_id, ts, role, text, safety_level) "
                  "VALUES (?,?,?,?,?,?)",
                  ("t_" + uuid.uuid4().hex[:14], uid, time.time(), role, (text or "")[:4000], level))
    _conn.commit()


def export_user(uid: str) -> list[dict]:
    """Every turn this user has exchanged, for their data export (privacy.py)."""
    init()
    rows = _conn.execute("SELECT ts, role, text, safety_level FROM chat_turns "
                         "WHERE user_id=? ORDER BY ts", (uid,)).fetchall()
    return [{"ts": r[0], "role": r[1], "text": r[2], "safety_level": r[3]} for r in rows]


def delete_user(uid: str) -> int:
    init()
    cur = _conn.execute("DELETE FROM chat_turns WHERE user_id=?", (uid,))
    _conn.commit()
    return getattr(cur, "rowcount", 0) or 0


def _urgent_payload(uid: str) -> dict:
    """The urgent-care handoff. The care-team number is sourced from the user's
    emergency profile (single source of truth) — never a hardcoded demo number.
    `phone` is null when unset; the client then shows local-emergency guidance."""
    ep = care.get_emergency_profile(uid)
    return {
        "headline": "Please contact your care team now.",
        "message": "Do not wait for an AI response if you feel seriously unwell "
                   "or are worried about your baby.",
        "care_team": {"name": ep.get("care_team_name"), "phone": ep.get("care_team_phone")},
        "emergency_contact": {"name": ep.get("emergency_contact_name"),
                              "phone": ep.get("emergency_contact_phone")},
        "show_emergency_services": True,
    }


def _journey_phrase(journey: str) -> str:
    key = _JOURNEY_KEYS.get((journey or "exploring").lower(), "aira.journey.exploring")
    default = prompts.default_for(key) or "exploring maternal-wellness support"
    return prompts.resolve(key, default)


class TurnIn(BaseModel):
    message: str
    history: list[dict] = []


@router.post("/chat/turn")
def chat_turn(body: TurnIn, uid: str = Depends(current_user)):
    message = (body.message or "").strip()
    history = security.sanitize_history(body.history)
    u = accounts.get_user(uid) or {}
    journey = u.get("journey") or "exploring"

    # 1) SAFETY GATE — always, before anything else.
    result = safety.screen(message, history, {"journey": journey})
    safety.record(uid, message, result)
    _save(uid, "user", message, result["level"])

    base_safety = {"level": result["level"], "categories": result["categories"],
                   "degraded": result["degraded"]}

    # 2) RED → urgent handoff, no AI reply.
    if result["level"] == safety.RED:
        payload = _urgent_payload(uid)
        _save(uid, "aira", payload["headline"], "red")
        return {"safety": base_safety, "urgent": True, "urgent_help": payload,
                "reply": None, "trust_label": None, "action_card": None}

    # 3) GREEN/AMBER → journey-grounded reply with a trust label + one card.
    trust_label = _TRUST_LABEL.get(result["level"], "wellness")
    if not llm.configured():
        # No LLM configured: still safe. Return a calm, non-AI fallback rather
        # than erroring — the gate already ran.
        reply = ("I'm here with you. I can help you organise a next step, or show "
                 "you when contacting your care team would be safer.")
        _save(uid, "aira", reply, result["level"])
        return {"safety": base_safety, "urgent": False, "reply": reply,
                "trust_label": trust_label, "action_card": None,
                "disclaimer_needed": result["level"] == safety.AMBER, "degraded_llm": True}

    system = prompts.fill(
        prompts.resolve("aira.system", prompts.AIRA_SYSTEM),
        journey_phrase=_journey_phrase(journey), name=u.get("name") or "there",
        language=u.get("language") or "English", trust_label=trust_label)
    mem = memory.context_summary(uid)
    if mem:
        system += f"\nApproved context you may use: {mem}"
    schema = prompts.resolve("aira.reply_schema", prompts.AIRA_REPLY_SCHEMA)
    convo = "\n".join(f"{h['role']}: {h['content']}" for h in history[-8:])
    user = (f"Conversation so far:\n{convo}\n\n" if convo else "") + \
           f"The person's latest message:\n{message}\n\n{schema}"

    try:
        data = llm.gemini_json(system, user, temperature=0.6)
    except Exception as e:  # noqa: BLE001
        log.warning("reply generation failed: %s", e)
        data = {}

    reply = str(data.get("reply") or
                "I'm here. Tell me a little more and we'll take it one step at a time.")
    card = data.get("action_card")
    if not isinstance(card, dict) or not card.get("tool"):
        card = None
    else:
        card = {"tool": str(card.get("tool")), "title": str(card.get("title") or ""),
                "detail": str(card.get("detail") or "")}
    disclaimer = bool(data.get("disclaimer_needed")) or result["level"] == safety.AMBER

    _save(uid, "aira", reply, result["level"])
    return {"safety": base_safety, "urgent": False, "reply": reply,
            "trust_label": trust_label, "action_card": card,
            "disclaimer_needed": disclaimer}


class ScreenIn(BaseModel):
    message: str
    history: list[dict] = []


@router.post("/safety/screen")
def safety_screen(body: ScreenIn, uid: str = Depends(current_user)):
    """Standalone screen (no reply). A client can call this to check input before
    sending, or to gate a non-chat surface. Records the flag like a real turn."""
    u = accounts.get_user(uid) or {}
    result = safety.screen((body.message or "").strip(),
                           security.sanitize_history(body.history),
                           {"journey": u.get("journey")})
    safety.record(uid, body.message, result)
    out = {"safety": {"level": result["level"], "categories": result["categories"],
                      "degraded": result["degraded"]}, "urgent": result["level"] == safety.RED}
    if out["urgent"]:
        out["urgent_help"] = _urgent_payload(uid)
    return out


@router.get("/chat/history")
def chat_history(uid: str = Depends(current_user), limit: int = 50):
    init()
    # Clamp low as well as high: LIMIT -1 means "unlimited" to SQLite and is an
    # error on Postgres, so a negative `limit` would defeat the cap entirely.
    rows = _conn.execute("SELECT ts, role, text, safety_level FROM chat_turns "
                         "WHERE user_id=? ORDER BY ts DESC LIMIT ?",
                         (uid, max(1, min(int(limit), 200)))).fetchall()
    items = [{"ts": r[0], "role": r[1], "text": r[2], "safety_level": r[3]}
             for r in reversed(rows)]
    return {"items": items}


# ── streaming ────────────────────────────────────────────────────────────────

def _stream_events(uid: str, message: str, history: list[dict]):
    """The same turn as chat_turn, emitted as it becomes known.

    Newline-delimited JSON rather than SSE: there is one consumer, it is our own
    client, and NDJSON is a line read instead of a protocol.

    The order is the point. The safety gate runs to completion BEFORE anything
    is emitted, and a RED result ends the stream with the urgent handoff and no
    reply at all. Streaming a decision would mean acting on half of one, and the
    half of "you should call your care team" that arrives first is a sentence
    that reads like reassurance.
    """
    u = accounts.get_user(uid) or {}
    journey = u.get("journey") or "exploring"

    result = safety.screen(message, history, {"journey": journey})
    safety.record(uid, message, result)
    _save(uid, "user", message, result["level"])
    base_safety = {"level": result["level"], "categories": result["categories"],
                   "degraded": result["degraded"]}
    yield json.dumps({"type": "safety", **base_safety}) + "\n"

    if result["level"] == safety.RED:
        payload = _urgent_payload(uid)
        _save(uid, "aira", payload["headline"], "red")
        yield json.dumps({"type": "urgent", "urgent_help": payload}) + "\n"
        return

    trust_label = _TRUST_LABEL.get(result["level"], "wellness")
    if not llm.configured():
        reply = ("I'm here with you. I can help you organise a next step, or show "
                 "you when contacting your care team would be safer.")
        _save(uid, "aira", reply, result["level"])
        yield json.dumps({"type": "chunk", "text": reply}) + "\n"
        yield json.dumps({"type": "done", "trust_label": trust_label,
                          "action_card": None, "degraded_llm": True,
                          "disclaimer_needed": result["level"] == safety.AMBER}) + "\n"
        return

    system = prompts.fill(
        prompts.resolve("aira.system", prompts.AIRA_SYSTEM),
        journey_phrase=_journey_phrase(journey), name=u.get("name") or "there",
        language=u.get("language") or "English", trust_label=trust_label)
    mem = memory.context_summary(uid)
    if mem:
        system += f"\nApproved context you may use: {mem}"
    convo = "\n".join(f"{h['role']}: {h['content']}" for h in history[-8:])
    user = (f"Conversation so far:\n{convo}\n\n" if convo else "") + \
           f"The person's latest message:\n{message}\n\n{prompts.AIRA_STREAM_SCHEMA}"

    prose: list[str] = []
    tail = ""
    seen_delimiter = False
    try:
        for piece in llm.gemini_stream(system, user, temperature=0.6):
            if seen_delimiter:
                tail += piece
                continue
            tail += piece
            if prompts.STREAM_DELIMITER in tail:
                before, tail = tail.split(prompts.STREAM_DELIMITER, 1)
                if before:
                    prose.append(before)
                    yield json.dumps({"type": "chunk", "text": before}) + "\n"
                seen_delimiter = True
                continue
            # Hold back only as much as could still turn out to be the
            # delimiter, so the person sees words rather than a stall.
            keep = len(prompts.STREAM_DELIMITER) - 1
            if len(tail) > keep:
                out, tail = tail[:-keep], tail[-keep:]
                prose.append(out)
                yield json.dumps({"type": "chunk", "text": out}) + "\n"
    except Exception as e:  # noqa: BLE001
        log.warning("stream failed: %s", e)

    if not seen_delimiter and tail:
        prose.append(tail)
        yield json.dumps({"type": "chunk", "text": tail}) + "\n"

    reply = "".join(prose).strip()
    if not reply:
        reply = "I'm here. Tell me a little more and we'll take it one step at a time."
        yield json.dumps({"type": "chunk", "text": reply}) + "\n"

    card, disclaimer = None, result["level"] == safety.AMBER
    if seen_delimiter:
        try:
            extra = json.loads(tail.strip() or "{}")
            raw_card = extra.get("action_card")
            if isinstance(raw_card, dict) and raw_card.get("tool"):
                card = {"tool": str(raw_card.get("tool")),
                        "title": str(raw_card.get("title") or ""),
                        "detail": str(raw_card.get("detail") or "")}
            disclaimer = bool(extra.get("disclaimer_needed", disclaimer))
        except (ValueError, TypeError):
            # A malformed tail costs the card, not the answer. The prose has
            # already been delivered and is the part that mattered.
            log.warning("stream tail was not JSON")

    _save(uid, "aira", reply, result["level"])
    yield json.dumps({"type": "done", "trust_label": trust_label,
                      "action_card": card, "disclaimer_needed": disclaimer}) + "\n"


@router.post("/chat/turn/stream")
def chat_turn_stream(body: TurnIn, uid: str = Depends(current_user)):
    message = (body.message or "").strip()
    history = security.sanitize_history(body.history)
    return StreamingResponse(
        _stream_events(uid, message, history),
        media_type="application/x-ndjson",
        # Proxies that buffer would defeat the point of this endpoint.
        headers={"Cache-Control": "no-store", "X-Accel-Buffering": "no"},
    )
