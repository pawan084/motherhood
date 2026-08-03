"""Coverage: services.py genai paths via a mocked client — the live suite
never exercises generate/stream/transcribe internals without a key."""
import sys
from types import SimpleNamespace


def _svc(client):
    return sys.modules["services"]


class _FakeModels:
    def __init__(self, text=None, chunks=None, fail=False):
        self._text, self._chunks, self._fail = text, chunks, fail

    def generate_content(self, **kwargs):
        if self._fail:
            raise RuntimeError("provider down")
        return SimpleNamespace(text=self._text)

    def generate_content_stream(self, **kwargs):
        for c in self._chunks or []:
            yield SimpleNamespace(text=c)


def test_to_contents_maps_roles(client):
    svc = _svc(client)
    contents = svc._to_contents(
        [{"role": "user", "content": "hi"},
         {"role": "assistant", "content": "hello"}], "next")
    assert [c.role for c in contents] == ["user", "model", "user"]
    assert contents[-1].parts[0].text == "next"


def test_generate_reply_strips_and_raises_on_empty(client, monkeypatch):
    svc = _svc(client)
    monkeypatch.setattr(svc, "_client",
                        lambda: SimpleNamespace(models=_FakeModels(text="  ok  ")))
    assert svc.generate_reply("sys", [], "hi") == "ok"
    monkeypatch.setattr(svc, "_client",
                        lambda: SimpleNamespace(models=_FakeModels(text="")))
    try:
        svc.generate_reply("sys", [], "hi")
        assert False, "should raise on empty"
    except RuntimeError:
        pass


def test_stream_reply_yields_only_nonempty_chunks(client, monkeypatch):
    svc = _svc(client)
    monkeypatch.setattr(
        svc, "_client",
        lambda: SimpleNamespace(models=_FakeModels(chunks=["a", "", "b", None])))
    assert list(svc.stream_reply("sys", [], "hi")) == ["a", "b"]


def test_generate_reply_propagates_provider_failure(client, monkeypatch):
    svc = _svc(client)
    monkeypatch.setattr(svc, "_client",
                        lambda: SimpleNamespace(models=_FakeModels(fail=True)))
    try:
        svc.generate_reply("sys", [], "hi")
        assert False
    except RuntimeError:
        pass
