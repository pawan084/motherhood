"""The safety gate is the product's safety-critical core, so it gets the most
tests. These assert the deterministic keyword floor (no LLM needed)."""
import safety


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
