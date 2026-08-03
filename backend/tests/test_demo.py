"""Demo personas: seeded through real modules, gated out of production."""


def test_demo_persona_is_fully_populated(client):
    r = client.post("/demo/pregnant")
    assert r.status_code == 200
    h = {"X-Device-Token": r.json()["device_token"]}
    ctx = client.get("/care-context", headers=h).json()["context"]
    assert ctx["display_name"] == "Maya" and ctx["week"] == 24
    assert len(client.get("/memory", headers=h).json()["items"]) == 4
    assert len(client.get("/medicines", headers=h).json()["medicines"]) == 2
    assert len(client.get("/appointments", headers=h).json()["appointments"]) == 1
    assert len(client.get("/care-plan", headers=h).json()["items"]) == 3
    # Journey works off the seeded context.
    assert client.get("/journey", headers=h).json()["content"]["id"] == "preg-21-24"


def test_demo_personas_all_exist(client):
    for persona in ("pregnant", "postpartum", "trying_to_conceive"):
        assert client.post(f"/demo/{persona}").status_code == 200
    assert client.post("/demo/unknown").status_code == 404


def test_demo_router_absent_without_dev_login(tmp_path, monkeypatch):
    """In a production-shaped env the route must not exist at all."""
    import importlib
    import sys
    monkeypatch.setenv("SQLITE_PATH", str(tmp_path / "p.db"))
    monkeypatch.setenv("UPLOAD_DIR", str(tmp_path / "up"))
    monkeypatch.delenv("ALLOW_DEV_LOGIN", raising=False)
    monkeypatch.delenv("ENV", raising=False)
    monkeypatch.setenv("RATE_LIMIT_PER_MIN", "0")
    for mod in ("app", "demo", "admin", "care", "chat", "privacy", "journey",
                "seed_journey", "memory", "services", "prompts", "safety",
                "safety_taxonomy", "care_context", "device_auth", "accounts",
                "security", "db", "config"):
        sys.modules.pop(mod, None)
    app_module = importlib.import_module("app")
    from fastapi.testclient import TestClient
    with TestClient(app_module.app) as c:
        assert c.post("/demo/pregnant").status_code == 404
