"""The Aira chat turn: gate -> reply -> cards.

Every turn, streaming or not, runs the same spine:

  1. resolve the learner and load their care context
  2. safety.screen() the input — ALWAYS, before anything else
  3. branch on the gate decision:
       urgent  -> the Urgent Help payload; NO model reply is generated
       error   -> a safe-error payload; NO model reply is generated
       caution -> reply with the caution addendum in the system prompt
       ok      -> reply normally
  4. suggest 0-3 typed action cards (failure => no cards, reply stands)

The trust label the client renders ("Safety checked") is the gate's actual
decision travelling with the response — it is never cosmetic. On the SSE
stream it is the FIRST event, so the client knows the turn's safety status
before any token renders.
"""
import json
import logging

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

import care_context
import memory
import safety
import security
import services
from device_auth import resolve_learner
from prompts import build_system

log = logging.getLogger("aira.chat")

router = APIRouter(tags=["chat"])

# Urgent Help copy, per language. Static and translated ahead of time — this
# screen must never depend on a model call. (ref/mockup.png "Urgent help".)
_URGENT_COPY = {
    "en": {
        "headline": "Please contact your care team now.",
        "body": "Do not wait for an AI response if you feel seriously unwell "
                "or are worried about your baby.",
        "self_harm": "You matter, and you don't have to carry this alone. "
                     "Please reach out to your care team or someone you trust "
                     "right now.",
    },
    "hi": {
        "headline": "कृपया अभी अपनी केयर टीम से संपर्क करें।",
        "body": "अगर आप गंभीर रूप से अस्वस्थ महसूस कर रही हैं या बच्चे को लेकर "
                "चिंतित हैं, तो AI के जवाब का इंतज़ार न करें।",
        "self_harm": "आप महत्वपूर्ण हैं, और आपको यह अकेले सहन नहीं करना है। "
                     "कृपया अभी अपनी केयर टीम या किसी भरोसेमंद व्यक्ति से बात करें।",
    },
    "hi-Latn": {
        "headline": "Please apni care team se abhi sampark karein.",
        "body": "Agar aap seriously unwell feel kar rahi hain ya baby ko lekar "
                "worried hain, to AI ke jawab ka intezaar na karein.",
        "self_harm": "Aap important hain, aur yeh akele jhelna zaroori nahi hai. "
                     "Please abhi apni care team ya kisi bharosemand insaan se "
                     "baat karein.",
    },
}

_URGENT_ACTIONS = [
    {"id": "call_care_team", "label": "Call care team"},
    {"id": "open_emergency_profile", "label": "Open emergency profile"},
    {"id": "im_safe", "label": "I'm safe for now"},
]

_ERROR_MESSAGE = ("Aira can't safely respond right now. Please try again in a "
                  "moment — and if anything feels urgent, contact your care "
                  "team; don't wait for the app.")


def _urgent_payload(result, language: str) -> dict:
    copy = _URGENT_COPY.get(language, _URGENT_COPY["en"])
    body = copy["self_harm"] if "self_harm_risk" in result.matched else copy["body"]
    return {
        "headline": copy["headline"],
        "body": body,
        "actions": _URGENT_ACTIONS,
    }


class RespondIn(BaseModel):
    text: str
    history: list | None = None


def _trust_label(decision: str) -> str:
    """The label must be honest: an `error` turn was NOT checked — saying
    "Safety checked" on it would be exactly the cosmetic labelling this
    product forbids."""
    return "Safety check unavailable" if decision == "error" else "Safety checked"


def _prepare_turn(body: RespondIn, learner_id: str):
    """The shared front half: context, gate, sanitized history."""
    ctx = care_context.get(learner_id)
    stage = (ctx or {}).get("stage")
    week = (ctx or {}).get("week")
    language = (ctx or {}).get("language") or "en"
    gate = safety.screen(learner_id, body.text, stage, week)
    history = security.sanitize_history(body.history)
    return ctx, language, gate, history


@router.post("/respond")
def respond(body: RespondIn, learner_id: str = Depends(resolve_learner)):
    ctx, language, gate, history = _prepare_turn(body, learner_id)
    safety_block = {"decision": gate.decision, "label": _trust_label(gate.decision)}

    if gate.decision == "urgent":
        return {"decision": "urgent", "safety": safety_block,
                "urgent_help": _urgent_payload(gate, language),
                "reply": None, "cards": []}
    if gate.decision == "error":
        return {"decision": "error", "safety": safety_block,
                "message": _ERROR_MESSAGE, "reply": None, "cards": []}

    system = build_system(ctx, caution=(gate.decision == "caution"),
                          memories=memory.recall(learner_id))
    try:
        reply = services.generate_reply(system, history, body.text)
    except Exception as e:  # noqa: BLE001
        # The gate passed but generation failed: same honest posture as a gate
        # error — no silent retry into an unscreened path.
        log.warning("reply generation failed: %s", e)
        return {"decision": "error", "safety": safety_block,
                "message": _ERROR_MESSAGE, "reply": None, "cards": []}
    cards = services.suggest_cards(body.text, reply, ctx)
    # Memory extraction runs only on turns Aira replied to. Urgent/error turns
    # store nothing here (urgent inputs live in the safety audit instead).
    memory.remember(learner_id, services.extract_memory(body.text, ctx))
    return {"decision": gate.decision, "safety": safety_block,
            "reply": reply, "cards": cards}


@router.post("/respond_stream")
def respond_stream(body: RespondIn, learner_id: str = Depends(resolve_learner)):
    """SSE variant. Event order: gate -> delta* -> cards -> done.
    On urgent/error the stream is gate -> done: the client routes on the gate
    event and no reply tokens ever exist to leak ahead of it."""
    ctx, language, gate, history = _prepare_turn(body, learner_id)

    def sse(obj: dict) -> str:
        return f"data: {json.dumps(obj, ensure_ascii=False)}\n\n"

    def event_stream():
        gate_event = {"type": "gate", "decision": gate.decision,
                      "label": _trust_label(gate.decision)}
        if gate.decision == "urgent":
            gate_event["urgent_help"] = _urgent_payload(gate, language)
            yield sse(gate_event)
            yield sse({"type": "done"})
            return
        if gate.decision == "error":
            gate_event["message"] = _ERROR_MESSAGE
            yield sse(gate_event)
            yield sse({"type": "done"})
            return

        yield sse(gate_event)
        system = build_system(ctx, caution=(gate.decision == "caution"),
                              memories=memory.recall(learner_id))
        parts: list[str] = []
        try:
            for chunk in services.stream_reply(system, history, body.text):
                parts.append(chunk)
                yield sse({"type": "delta", "text": chunk})
        except Exception as e:  # noqa: BLE001
            log.warning("stream reply failed mid-turn: %s", e)
            yield sse({"type": "error", "message": _ERROR_MESSAGE})
            yield sse({"type": "done"})
            return
        cards = services.suggest_cards(body.text, "".join(parts), ctx)
        if cards:
            yield sse({"type": "cards", "cards": cards})
        yield sse({"type": "done"})
        # After the stream is fully delivered: extraction can't delay the turn.
        memory.remember(learner_id, services.extract_memory(body.text, ctx))

    return StreamingResponse(event_stream(), media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache",
                                      "X-Accel-Buffering": "no"})
