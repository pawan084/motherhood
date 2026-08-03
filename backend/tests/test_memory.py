"""P4: memory — the control-surface guarantees.

What matters here is not that memory works, but that its obligations hold:
items are scoped to their owner, deletion is immediate and reflected in the
very next prompt, urgent turns store nothing, and the account-deletion
cascade covers memories.
"""
import sys


def _register(client) -> dict:
    r = client.post("/device/register")
    return {"X-Device-Token": r.json()["device_token"]}


def _mods():
    return sys.modules["safety"], sys.modules["services"], sys.modules["memory"]


def _stub_turn(monkeypatch, decision="ok", extracted=None):
    """Stub the gate + LLM services around a chat turn."""
    safety, services, _ = _mods()
    from safety import GateResult
    monkeypatch.setattr(
        safety, "screen",
        lambda learner_id, text, stage=None, week=None: GateResult(decision, "stub", [], ""))
    systems: list[str] = []

    def fake_generate(system, history, text):
        systems.append(system)
        return "A reply."

    monkeypatch.setattr(services, "generate_reply", fake_generate)
    monkeypatch.setattr(services, "suggest_cards", lambda *a: [])
    monkeypatch.setattr(services, "extract_memory", lambda *a: extracted or [])
    return systems


# --- storage contract --------------------------------------------------------

def test_chat_turn_stores_extracted_items(client, monkeypatch):
    h = _register(client)
    _stub_turn(monkeypatch, extracted=[{"kind": "concern", "content": "worried about the scan"}])
    client.post("/respond", json={"text": "..."}, headers=h)
    items = client.get("/memory", headers=h).json()["items"]
    assert len(items) == 1
    assert items[0]["kind"] == "concern"


def test_duplicate_items_refresh_not_duplicate(client, monkeypatch):
    h = _register(client)
    _stub_turn(monkeypatch, extracted=[{"kind": "fact", "content": "First pregnancy"}])
    client.post("/respond", json={"text": "a"}, headers=h)
    client.post("/respond", json={"text": "b"}, headers=h)
    items = client.get("/memory", headers=h).json()["items"]
    assert len(items) == 1


def test_invalid_kinds_are_dropped_by_the_store(client, monkeypatch):
    """Defense in depth: even if the extractor stub misbehaves, the store
    enforces the catalog."""
    h = _register(client)
    _stub_turn(monkeypatch, extracted=[
        {"kind": "diagnosis", "content": "preeclampsia"},   # never a kind
        {"kind": "fact", "content": ""},                     # empty
        {"kind": "fact", "content": "works night shifts"},
    ])
    client.post("/respond", json={"text": "..."}, headers=h)
    items = client.get("/memory", headers=h).json()["items"]
    assert [i["content"] for i in items] == ["works night shifts"]


def test_urgent_turn_stores_nothing(client, monkeypatch):
    h = _register(client)
    _stub_turn(monkeypatch, decision="urgent",
               extracted=[{"kind": "symptom", "content": "should never be stored"}])
    client.post("/respond", json={"text": "I am bleeding"}, headers=h)
    assert client.get("/memory", headers=h).json()["items"] == []


# --- the control surface -----------------------------------------------------

def test_memory_grounds_the_next_prompt_and_deletion_removes_it(client, monkeypatch):
    h = _register(client)
    systems = _stub_turn(monkeypatch, extracted=[
        {"kind": "preference", "content": "prefers short answers"}])
    client.post("/respond", json={"text": "keep it brief please"}, headers=h)

    # Next turn's system prompt carries the memory.
    client.post("/respond", json={"text": "hello again"}, headers=h)
    assert "prefers short answers" in systems[-1]

    # Delete it; the turn after that must NOT carry it.
    item = client.get("/memory", headers=h).json()["items"][0]
    assert client.delete(f"/memory/{item['id']}", headers=h).status_code == 200
    client.post("/respond", json={"text": "third turn"}, headers=h)
    assert "prefers short answers" not in systems[-1]


def test_forget_all(client, monkeypatch):
    h = _register(client)
    _stub_turn(monkeypatch, extracted=[
        {"kind": "fact", "content": "a"}, {"kind": "concern", "content": "b"}])
    client.post("/respond", json={"text": "..."}, headers=h)
    assert client.delete("/memory", headers=h).status_code == 200
    assert client.get("/memory", headers=h).json()["items"] == []


def test_memory_is_owner_scoped(client, monkeypatch):
    """The IDOR property again, for memories: B can't read or delete A's."""
    a, b = _register(client), _register(client)
    _stub_turn(monkeypatch, extracted=[{"kind": "fact", "content": "private to A"}])
    client.post("/respond", json={"text": "..."}, headers=a)
    assert client.get("/memory", headers=b).json()["items"] == []
    item = client.get("/memory", headers=a).json()["items"][0]
    assert client.delete(f"/memory/{item['id']}", headers=b).status_code == 404
    # Still there for A.
    assert len(client.get("/memory", headers=a).json()["items"]) == 1


def test_sign_in_moves_memories_to_the_account(client, monkeypatch):
    h = _register(client)
    _stub_turn(monkeypatch, extracted=[{"kind": "fact", "content": "guest memory"}])
    client.post("/respond", json={"text": "..."}, headers=h)
    r = client.post("/account/dev-login", json={"email": "m@x.com", "name": ""}, headers=h)
    session = {"Authorization": f"Bearer {r.json()['session']}"}
    items = client.get("/memory", headers=session).json()["items"]
    assert [i["content"] for i in items] == ["guest memory"]


def test_account_deletion_erases_memories(client, monkeypatch):
    h = _register(client)
    _stub_turn(monkeypatch, extracted=[{"kind": "fact", "content": "to be erased"}])
    client.post("/respond", json={"text": "..."}, headers=h)
    r = client.post("/account/dev-login", json={"email": "m@x.com", "name": ""}, headers=h)
    session = {"Authorization": f"Bearer {r.json()['session']}"}
    client.delete("/account", headers=session)
    # The device credential still resolves, but its memories are gone.
    assert client.get("/memory", headers=h).json()["items"] == []


def test_eviction_keeps_the_freshest(client, monkeypatch):
    _, _, memory = _mods()
    h = _register(client)
    learner_id = _learner_of(h)
    monkeypatch.setattr(memory, "MAX_ITEMS_PER_LEARNER", 5)
    for i in range(8):
        memory.remember(learner_id, [{"kind": "fact", "content": f"item {i}"}])
    items = client.get("/memory", headers=h).json()["items"]
    assert len(items) == 5
    assert items[0]["content"] == "item 7"  # freshest kept


def _learner_of(headers) -> str:
    """Resolve the device token to its learner id the same way the app does."""
    import device_auth
    return device_auth.verify_device_token(headers["X-Device-Token"])
