"""The per-IP rate limiter, exercised directly.

conftest turns the middleware off for the suite (one shared TestClient IP means
every test draws on a single budget, so an unrelated new test eventually trips
it). That left the limiter with no coverage at all, which is worse — it is the
only thing standing between the public API and a scripted flood. These tests
drive `_rate_allowed` and the middleware's open-path rules directly, so they
neither depend on nor disturb the suite-wide setting.
"""
import security


def _reset():
    security._rl_state.clear()


def test_allows_up_to_the_budget_then_rejects(monkeypatch):
    _reset()
    monkeypatch.setattr(security, "RATE_LIMIT_PER_MIN", 3)
    assert [security._rate_allowed("1.2.3.4", 100) for _ in range(3)] == [True] * 3
    assert security._rate_allowed("1.2.3.4", 100) is False


def test_budget_is_per_ip(monkeypatch):
    _reset()
    monkeypatch.setattr(security, "RATE_LIMIT_PER_MIN", 2)
    security._rate_allowed("1.1.1.1", 100)
    security._rate_allowed("1.1.1.1", 100)
    assert security._rate_allowed("1.1.1.1", 100) is False
    # A different caller is unaffected by the first one's flood.
    assert security._rate_allowed("2.2.2.2", 100) is True


def test_window_rolls_over(monkeypatch):
    _reset()
    monkeypatch.setattr(security, "RATE_LIMIT_PER_MIN", 1)
    assert security._rate_allowed("3.3.3.3", 100) is True
    assert security._rate_allowed("3.3.3.3", 100) is False
    assert security._rate_allowed("3.3.3.3", 101) is True     # next minute


def test_health_is_never_rate_limited(client):
    """/health is in _OPEN_PATHS: a platform health probe that gets 429'd during
    a flood makes the orchestrator kill a server that is otherwise coping."""
    assert "/health" in security._OPEN_PATHS
    assert client.get("/health").status_code == 200
