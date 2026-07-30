"""The streaming turn, and the one thing streaming must not change.

A reply that arrives a word at a time is only an improvement if the decision in
front of it is still made all at once. The gate has to finish before a single
character is emitted, and a RED result has to end the stream with the handoff
and no reply — because the first half of "please contact your care team" reads
like reassurance, and somebody acting on it is the failure this whole gate
exists to prevent.

These read the actual bytes off the endpoint rather than calling the generator,
so ordering is asserted against what a client would receive.
"""
import json

import pytest

import chat
import services


def _events(client, headers, message, history=None):
    r = client.post("/v1/chat/turn/stream",
                    json={"message": message, "history": history or []},
                    headers=headers)
    assert r.status_code == 200, r.text
    return [json.loads(line) for line in r.text.splitlines() if line.strip()]


def test_safety_is_the_first_thing_sent(client, user):
    events = _events(client, user["headers"], "how do I sleep better?")
    assert events[0]["type"] == "safety"
    assert events[0]["level"] in ("green", "amber", "red")


def test_a_red_message_streams_the_handoff_and_no_reply(client, user):
    events = _events(client, user["headers"], "I am bleeding heavily and feel faint")

    assert events[0]["type"] == "safety"
    assert events[0]["level"] == "red"
    kinds = [e["type"] for e in events]
    assert "urgent" in kinds
    # The point of the test: not one word of AI answer went out.
    assert "chunk" not in kinds, f"a red turn emitted reply text: {events}"
    assert "done" not in kinds


def test_an_ordinary_turn_ends_with_done_after_its_text(client, user):
    events = _events(client, user["headers"], "what should I ask at my next visit?")
    kinds = [e["type"] for e in events]
    assert kinds[0] == "safety"
    assert kinds[-1] == "done"
    assert "chunk" in kinds
    assert "".join(e["text"] for e in events if e["type"] == "chunk").strip()


def test_the_turn_is_saved_the_same_as_the_non_streaming_one(client, user):
    """History is what the next turn is told was already said. A streamed reply
    that never landed in chat_turns would vanish on the next app launch."""
    before = len(client.get("/v1/chat/history", headers=user["headers"]).json()["items"])
    _events(client, user["headers"], "is this normal?")
    after = client.get("/v1/chat/history", headers=user["headers"]).json()["items"]
    # Both sides of the exchange.
    assert len(after) == before + 2
    assert after[-1]["role"] != after[-2]["role"]


def test_prose_streams_before_the_structured_tail(client, user, monkeypatch):
    """With a model configured, the words come out before the JSON.

    The schema puts the prose first deliberately: it is what somebody is waiting
    for, and it can render before the model has decided anything about a card.
    """
    def fake_stream(system, user_prompt, temperature=0.6, **kw):
        yield "You could ask about "
        yield "your iron levels."
        yield "\n---AIRA---\n"
        yield '{"action_card": {"tool": "appointment", "title": "Prepare",'
        yield ' "detail": "Bring your notes"}, "disclaimer_needed": true}'

    monkeypatch.setattr(services, "configured", lambda: True)
    monkeypatch.setattr(services, "gemini_stream", fake_stream)

    events = _events(client, user["headers"], "what should I ask?")
    text = "".join(e["text"] for e in events if e["type"] == "chunk")

    assert "You could ask about your iron levels." in text
    # The delimiter and the JSON are protocol, not conversation.
    assert "---AIRA---" not in text
    assert "action_card" not in text

    done = events[-1]
    assert done["type"] == "done"
    assert done["action_card"]["tool"] == "appointment"
    assert done["disclaimer_needed"] is True


def test_a_broken_tail_costs_the_card_not_the_answer(client, user, monkeypatch):
    def fake_stream(system, user_prompt, temperature=0.6, **kw):
        yield "Rest when you can."
        yield "\n---AIRA---\n"
        yield "{not json at all"

    monkeypatch.setattr(services, "configured", lambda: True)
    monkeypatch.setattr(services, "gemini_stream", fake_stream)

    events = _events(client, user["headers"], "I am tired")
    text = "".join(e["text"] for e in events if e["type"] == "chunk")
    assert "Rest when you can." in text
    assert events[-1]["type"] == "done"
    assert events[-1]["action_card"] is None


def test_a_model_that_dies_mid_sentence_still_closes_the_turn(client, user, monkeypatch):
    """Half an answer and a closed stream beats a spinner that never resolves."""
    def fake_stream(system, user_prompt, temperature=0.6, **kw):
        yield "That can happen when"
        raise RuntimeError("connection reset")

    monkeypatch.setattr(services, "configured", lambda: True)
    monkeypatch.setattr(services, "gemini_stream", fake_stream)

    events = _events(client, user["headers"], "why am I dizzy sometimes?")
    assert events[-1]["type"] == "done"
    text = "".join(e["text"] for e in events if e["type"] == "chunk")
    assert text.strip()
