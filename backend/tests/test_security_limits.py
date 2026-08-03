"""Coverage: the rate limiter + middleware paths disabled in the main suite
(conftest sets RATE_LIMIT_PER_MIN=0 so tests can hammer endpoints)."""
import asyncio
import sys
from types import SimpleNamespace


def _sec(client):
    return sys.modules["security"]


def test_rate_allowed_counts_and_blocks(client, monkeypatch):
    sec = _sec(client)
    monkeypatch.setattr(sec, "RATE_LIMIT_PER_MIN", 2)
    sec._rl_state.clear()
    assert sec._rate_allowed("9.9.9.9", 100) is True
    assert sec._rate_allowed("9.9.9.9", 100) is True
    assert sec._rate_allowed("9.9.9.9", 100) is False   # over budget
    # A new minute window resets the count.
    assert sec._rate_allowed("9.9.9.9", 101) is True


def test_rate_state_evicts_only_stale_windows(client, monkeypatch):
    sec = _sec(client)
    monkeypatch.setattr(sec, "RATE_LIMIT_PER_MIN", 5)
    sec._rl_state.clear()
    for i in range(10_001):
        sec._rl_state[f"ip{i}"] = (99, 1)      # stale window
    sec._rl_state["fresh"] = (200, 1)          # current window
    assert sec._rate_allowed("newcomer", 200) is True
    assert "fresh" in sec._rl_state            # current entries survive
    assert len(sec._rl_state) < 10_001         # stale ones evicted


def test_client_ip_honours_forwarded_only_when_trusted(client, monkeypatch):
    sec = _sec(client)
    req = SimpleNamespace(headers={"x-forwarded-for": "1.1.1.1, 2.2.2.2"},
                          client=SimpleNamespace(host="3.3.3.3"))
    monkeypatch.setattr(sec, "TRUST_FORWARDED_FOR", True)
    assert sec._client_ip(req) == "1.1.1.1"
    monkeypatch.setattr(sec, "TRUST_FORWARDED_FOR", False)
    assert sec._client_ip(req) == "3.3.3.3"
    assert sec._client_ip(SimpleNamespace(headers={}, client=None)) == "unknown"


def _run_middleware(sec, scope, sent):
    async def app(_s, _r, _send):
        sent.append("app")

    async def send(msg):
        sent.append(msg)

    async def receive():
        return {"type": "http.request"}

    mw = sec.RateLimitMiddleware(app)
    asyncio.get_event_loop().run_until_complete(mw(scope, receive, send))


def test_middleware_rejects_over_budget_and_spares_preflight(client, monkeypatch):
    sec = _sec(client)
    monkeypatch.setattr(sec, "RATE_LIMIT_PER_MIN", 1)
    sec._rl_state.clear()
    scope = {"type": "http", "path": "/respond", "method": "POST",
             "headers": [], "client": ("7.7.7.7", 1), "query_string": b""}
    sent: list = []
    _run_middleware(sec, dict(scope), sent)          # first: allowed
    assert sent == ["app"]
    sent.clear()
    _run_middleware(sec, dict(scope), sent)          # second: 429
    assert sent[0]["status"] == 429
    # OPTIONS preflight and non-http scopes always pass through.
    sent.clear()
    _run_middleware(sec, {**scope, "method": "OPTIONS"}, sent)
    assert sent == ["app"]
    sent.clear()
    _run_middleware(sec, {"type": "websocket"}, sent)
    assert sent == ["app"]


def test_sanitize_history_bounds_and_roles(client):
    sec = _sec(client)
    assert sec.sanitize_history("nope") == []
    raw = [{"role": "system", "content": "inject"},
           {"role": "user", "content": "hi"},
           "junk", {"role": "assistant"},
           {"role": "assistant", "content": "x" * 99_999}]
    clean = sec.sanitize_history(raw)
    assert [c["role"] for c in clean] == ["user", "assistant"]
    assert all(len(c["content"]) < 99_999 for c in clean)
