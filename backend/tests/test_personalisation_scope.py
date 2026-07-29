"""What the AI-personalisation switch actually changes about a reply.

`test_consent_content.py` already proves the switch empties
`memory.context_summary`. That is one input among several, and it turns out the
interesting question is the other direction: with the switch OFF, what does the
model still receive?

The answer decides what the app is allowed to SAY about the switch, and this is
where it went wrong. The Android control read "Off — Aira answers every question
as if it were the first thing you'd asked", which is a promise of amnesia the
code never made: the recent conversation, the person's name and their journey
all still go into the prompt. Overclaiming privacy is the worse direction of
error, so these tests capture the real prompt and pin both halves — what stops,
and what does not — so the next person to write that copy can read what is true
rather than guess.

Deliberately NOT asserted here: that name and journey *should* stop. They are
load-bearing (journey also selects the safety framing), the registry declares
this consent as `enforced_by: memory.context_summary` and nothing wider, and a
switch that silently made every answer generic would be its own broken promise.
If that ever changes, the tests below fail and the copy has to change with them.
"""
import pytest

import prompts
import services


@pytest.fixture()
def prompt_capture(monkeypatch):
    """Run a real turn with a stubbed model, and hand back what it was sent."""
    seen = {}

    def fake_gemini_json(system, user, temperature=0.6, **kw):
        seen["system"] = system
        seen["user"] = user
        return {"reply": "ok", "action_card": None, "disclaimer_needed": False}

    monkeypatch.setattr(services, "configured", lambda: True)
    monkeypatch.setattr(services, "gemini_json", fake_gemini_json)
    seen["all"] = lambda: seen.get("system", "") + "\n" + seen.get("user", "")
    return seen


def _set_up(client, user, granted: bool, prompt_capture):
    h = user["headers"]
    client.patch("/account/profile", json={"name": "Priya", "journey": "pregnant"}, headers=h)
    client.post("/v1/memory", json={"label": "Diet", "value": "vegetarian"}, headers=h)
    r = client.post("/v1/consent",
                    json={"feature": "personalization", "granted": granted}, headers=h)
    assert r.status_code == 200
    history = [
        {"role": "user", "content": "I had a loss in March"},
        {"role": "assistant", "content": "I'm so sorry."},
    ]
    r = client.post("/v1/chat/turn",
                    json={"message": "is this normal?", "history": history}, headers=h)
    assert r.status_code == 200, r.text
    # Without this, every `not in` assertion below would pass on an empty
    # string the moment the stub stopped being reached — the turn falls back to
    # a canned reply when no model is configured, and a vacuous pass is exactly
    # the kind of false evidence these tests exist to prevent.
    assert r.json().get("degraded_llm") is not True, "fell back; the model stub was bypassed"
    assert prompt_capture.get("system"), "no prompt captured"


def test_saved_memory_reaches_the_prompt_when_the_switch_is_on(client, user, prompt_capture):
    _set_up(client, user, granted=True, prompt_capture=prompt_capture)
    assert "vegetarian" in prompt_capture["all"]()


def test_saved_memory_is_absent_from_the_prompt_when_the_switch_is_off(
        client, user, prompt_capture):
    # The claim on the control — "nothing saved under What Aira remembers is
    # used" — checked against the bytes the model receives, not against the
    # helper that builds them.
    _set_up(client, user, granted=False, prompt_capture=prompt_capture)
    assert "vegetarian" not in prompt_capture["all"]()


def test_the_conversation_still_reaches_the_prompt_when_the_switch_is_off(
        client, user, prompt_capture):
    # Aira has to be able to follow a conversation to hold one, and these are
    # the messages already on the person's screen. Sending them is right — the
    # copy must not claim otherwise.
    _set_up(client, user, granted=False, prompt_capture=prompt_capture)
    assert "I had a loss in March" in prompt_capture["all"]()


def test_name_and_journey_still_reach_the_prompt_when_the_switch_is_off(
        client, user, prompt_capture):
    # If this test ever fails because someone widened the switch, that is a
    # defensible change — but the Android control and the privacy page both
    # state these two survive it, and they would then be lying.
    _set_up(client, user, granted=False, prompt_capture=prompt_capture)
    blob = prompt_capture["all"]()
    assert "Priya" in blob
    # Resolved rather than hardcoded: the journey phrase is admin-editable, and
    # asserting the literal "pregnant" makes this test fail for the wrong reason
    # the day someone changes it to "expecting".
    assert prompts.resolve("aira.journey.pregnant", prompts.JOURNEY_PHRASE_PREGNANT) in blob
