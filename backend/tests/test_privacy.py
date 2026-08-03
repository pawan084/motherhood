"""P7: privacy — consent with real effect, export completeness, guest deletion."""
import sys


def _register(client) -> dict:
    r = client.post("/device/register")
    return {"X-Device-Token": r.json()["device_token"]}


def _stub_turn(monkeypatch, extracted=None):
    safety, services = sys.modules["safety"], sys.modules["services"]
    from safety import GateResult
    monkeypatch.setattr(safety, "screen",
                        lambda *a, **k: GateResult("ok", "stub", [], ""))
    systems: list[str] = []

    def fake_generate(system, history, text):
        systems.append(system)
        return "A reply."

    monkeypatch.setattr(services, "generate_reply", fake_generate)
    monkeypatch.setattr(services, "suggest_cards", lambda *a: [])
    monkeypatch.setattr(services, "extract_memory", lambda *a: extracted or [])
    return systems


# --- consent has real effect -------------------------------------------------

def test_personalisation_defaults_on(client):
    h = _register(client)
    assert client.get("/consents", headers=h).json()["consents"]["ai_personalisation"] is True


def test_personalisation_off_stops_memory_use_and_extraction(client, monkeypatch):
    h = _register(client)
    systems = _stub_turn(monkeypatch,
                         extracted=[{"kind": "fact", "content": "night shifts"}])
    # One turn with consent on: memory stored + grounds the next prompt.
    client.post("/respond", json={"text": "I work night shifts"}, headers=h)
    client.post("/respond", json={"text": "hello"}, headers=h)
    assert "night shifts" in systems[-1]

    # Turn it off: prompts stop carrying memories AND nothing new is stored.
    r = client.put("/consents", json={"ai_personalisation": False}, headers=h)
    assert r.json()["consents"]["ai_personalisation"] is False
    client.post("/respond", json={"text": "I also do yoga"}, headers=h)
    assert "night shifts" not in systems[-1]
    contents = [i["content"] for i in client.get("/memory", headers=h).json()["items"]]
    assert "night shifts" in contents      # existing memory retained (not erased)
    assert len(contents) == 1              # but nothing new extracted

    # Back on: grounding resumes.
    client.put("/consents", json={"ai_personalisation": True}, headers=h)
    client.post("/respond", json={"text": "hi"}, headers=h)
    assert "night shifts" in systems[-1]


def test_unknown_consent_keys_ignored(client):
    h = _register(client)
    r = client.put("/consents", json={"sell_my_data": True, "ai_personalisation": "yes"},
                   headers=h)
    assert r.json()["consents"] == {"ai_personalisation": True}  # string not bool -> ignored


def test_consent_merge_account_choice_wins(client):
    import privacy
    h = _register(client)
    client.put("/consents", json={"ai_personalisation": False}, headers=h)
    r = client.post("/account/dev-login", json={"email": "m@x.com", "name": ""})
    uid, session = r.json()["user_id"], {"Authorization": f"Bearer {r.json()['session']}"}
    # The account made its own explicit choice before linking the device.
    privacy._conn.execute(
        "INSERT INTO consents (learner_id, key, granted, updated) VALUES (?,?,1,1)",
        (uid, "ai_personalisation"))
    privacy._conn.commit()
    client.post("/account/dev-login", json={"email": "m@x.com", "name": ""}, headers=h)
    assert client.get("/consents", headers=session).json()["consents"]["ai_personalisation"] is True


# --- export ------------------------------------------------------------------

def test_export_is_complete_and_own_data_only(client, monkeypatch):
    a, b = _register(client), _register(client)
    # Stub one level deeper than the other tests: the REAL gate must run so a
    # real audit row exists for the export to contain.
    safety, services = sys.modules["safety"], sys.modules["services"]
    monkeypatch.setattr(safety, "_classify_llm",
                        lambda *a_, **k: {"decision": "ok", "reason": "stub"})
    monkeypatch.setattr(services, "generate_reply", lambda *a_: "A reply.")
    monkeypatch.setattr(services, "suggest_cards", lambda *a_: [])
    monkeypatch.setattr(services, "extract_memory",
                        lambda *a_: [{"kind": "concern", "content": "scan nerves"}])
    client.put("/care-context", json={"stage": "trying_to_conceive", "display_name": "Maya"},
               headers=a)
    client.post("/respond", json={"text": "nervous about the scan"}, headers=a)
    client.post("/medicines", json={"name": "Folic acid"}, headers=a)
    client.post("/care-plan", json={"title": "Book appointment"}, headers=a)
    client.post("/medicines", json={"name": "B's secret med"}, headers=b)

    data = client.get("/export", headers=a).json()
    assert data["care_context"]["display_name"] == "Maya"
    assert data["memories"][0]["content"] == "scan nerves"
    assert [m["name"] for m in data["medicines"]] == ["Folic acid"]
    assert data["care_plan"][0]["title"] == "Book appointment"
    # The safety audit of their own inputs is part of their record.
    assert any("nervous about the scan" in row["input"] for row in data["safety_audit"])
    # And nothing of B's.
    assert "B's secret med" not in str(data)


# --- guest deletion ----------------------------------------------------------

def test_guest_can_delete_everything(client, monkeypatch):
    h = _register(client)
    _stub_turn(monkeypatch, extracted=[{"kind": "fact", "content": "x"}])
    client.put("/care-context", json={"stage": "trying_to_conceive"}, headers=h)
    client.post("/respond", json={"text": "..."}, headers=h)
    client.post("/medicines", json={"name": "Iron"}, headers=h)
    client.put("/consents", json={"ai_personalisation": False}, headers=h)

    assert client.delete("/learner-data", headers=h).status_code == 200

    assert client.get("/care-context", headers=h).json() == {"context": None}
    assert client.get("/memory", headers=h).json()["items"] == []
    assert client.get("/medicines", headers=h).json()["medicines"] == []
    # Consent rows are gone too -> back to defaults.
    assert client.get("/consents", headers=h).json()["consents"]["ai_personalisation"] is True
