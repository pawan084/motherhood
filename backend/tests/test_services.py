"""P3: services.suggest_cards' contract — never raises, [] on any failure,
and only catalog card types survive. The genai client is mocked at the
`services._client` boundary; the parsing/validation under test is real.
"""
import importlib
import sys

import pytest


@pytest.fixture()
def services(monkeypatch):
    for mod in ("services", "prompts", "config"):
        sys.modules.pop(mod, None)
    return importlib.import_module("services")


class _Resp:
    def __init__(self, text):
        self.text = text


def _mock_model(monkeypatch, services, text=None, error=None):
    class _Models:
        def generate_content(self, **kw):
            if error:
                raise error
            return _Resp(text)

    class _Client:
        models = _Models()

    monkeypatch.setattr(services, "_client", lambda: _Client())


def test_valid_cards_pass_and_are_capped_at_three(services, monkeypatch):
    _mock_model(monkeypatch, services, text="""{"cards": [
        {"type": "reminder", "title": "Set a reminder", "subtitle": "vitamin at 8"},
        {"type": "check_in", "title": "Check in", "subtitle": ""},
        {"type": "symptom_log", "title": "Log it", "subtitle": ""},
        {"type": "wellness_session", "title": "Breathe", "subtitle": ""}]}""")
    cards = services.suggest_cards("u", "r", None)
    assert len(cards) == 3
    assert cards[0] == {"type": "reminder", "title": "Set a reminder",
                        "subtitle": "vitamin at 8"}


def test_unknown_card_types_are_dropped(services, monkeypatch):
    _mock_model(monkeypatch, services, text="""{"cards": [
        {"type": "buy_premium", "title": "Upgrade!", "subtitle": ""},
        {"type": "reminder", "title": "ok", "subtitle": ""}]}""")
    cards = services.suggest_cards("u", "r", None)
    assert [c["type"] for c in cards] == ["reminder"]


def test_malformed_json_returns_empty(services, monkeypatch):
    _mock_model(monkeypatch, services, text="sure! here are some cards: ...")
    assert services.suggest_cards("u", "r", None) == []


def test_provider_error_returns_empty(services, monkeypatch):
    _mock_model(monkeypatch, services, error=RuntimeError("gemini 500"))
    assert services.suggest_cards("u", "r", None) == []


def test_overlong_titles_are_truncated(services, monkeypatch):
    _mock_model(monkeypatch, services, text=(
        '{"cards": [{"type": "reminder", "title": "' + "x" * 200
        + '", "subtitle": "' + "y" * 300 + '"}]}'))
    card = services.suggest_cards("u", "r", None)[0]
    assert len(card["title"]) == 60 and len(card["subtitle"]) == 120
