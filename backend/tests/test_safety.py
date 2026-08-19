"""The safety gate is the product's safety-critical core, so it gets the most
tests. These assert the deterministic keyword floor (no LLM needed)."""
from app import safety


def test_red_phrases():
    for msg in ["I have heavy bleeding", "I want to kill myself",
                "I can't breathe", "my baby is not moving"]:
        assert safety.screen(msg)["level"] == "red", msg


def test_amber_phrases():
    for msg in ["I have a headache today", "feeling really dizzy",
                "some swelling in my feet"]:
        assert safety.screen(msg)["level"] == "amber", msg


def test_green_is_ordinary():
    for msg in ["hello Aira", "what should I eat for breakfast",
                "thank you so much"]:
        assert safety.screen(msg)["level"] == "green", msg


def test_worse_prefers_urgent():
    assert safety.worse("green", "red") == "red"
    assert safety.worse("amber", "green") == "amber"
    assert safety.worse("red", "amber") == "red"


def test_degraded_without_llm():
    # No GEMINI_API_KEY in tests -> classifier unavailable -> keyword-only.
    assert safety.screen("hello")["degraded"] is True


def test_red_beats_llm_absence():
    # Even with the classifier down, a keyword red still escalates (fail-safe).
    res = safety.screen("there is heavy bleeding")
    assert res["level"] == "red" and res["degraded"] is True


# ── how people actually type ────────────────────────────────────────────────
#
# The keyword floor is the ONLY screening when no classifier is configured,
# which is the state this app currently ships in. A miss here is not a degraded
# experience for that message — it is no screening at all.
#
# Every phrasing below was screened GREEN and handed a normal reply before this
# was fixed. They were found by writing out how someone would actually say
# these things on a phone, rather than by re-reading the phrase list.

import pytest

from app import safety


@pytest.mark.parametrize("message", [
    # Reduced fetal movement: the same emergency, almost no shared words.
    "my baby isn't moving",
    "baby hasn't moved today",
    "I haven't felt the baby move all day",
    "no kicks since last night",
    "the baby stopped moving",
    # Apostrophes. "can't breathe" was listed; this is how it gets typed.
    "i cant breathe",
    "my chest hurts badly",
    # Haemorrhage, described rather than named.
    "there is so much blood",
    "I'm soaking a pad every hour",
    # Self-harm as intent, without the listed nouns.
    "I don't want to be here anymore",
    "I want to end it all",
    "thinking of hurting myself",
    # Pre-eclampsia.
    "seeing flashing lights",
])
def test_urgent_phrasings_reach_red(message):
    level, matched = safety._keyword_level(message)
    assert level == safety.RED, f"{message!r} screened {level} (matched {matched})"


@pytest.mark.parametrize("message", [
    "the baby is moving a lot today",
    "I felt the baby move this morning",
    "I am not moving much today because I am tired",
    "how do I plan my meals",
    "what should I ask at my next appointment",
])
def test_ordinary_messages_do_not_reach_red(message):
    """The cost of a false red is a frightened user sent to an emergency screen,
    so the patterns have to be specific as well as broad."""
    level, matched = safety._keyword_level(message)
    assert level != safety.RED, f"{message!r} screened red (matched {matched})"


def test_apostrophes_do_not_change_the_verdict():
    """Straight, curly and absent should all screen the same."""
    for variant in ["i can't breathe", "i can\u2019t breathe", "i cant breathe"]:
        assert safety._keyword_level(variant)[0] == safety.RED, variant


def test_punctuation_does_not_split_a_phrase():
    assert safety._keyword_level("heavy, bleeding!")[0] == safety.RED
