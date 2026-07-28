"""End-to-end turn behaviour through the API: the gate routes red to the urgent
handoff (no AI reply), and green returns a trust-labelled reply."""


def test_auth_required(client):
    assert client.post("/v1/chat/turn", json={"message": "hi"}).status_code == 401
    assert client.get("/v1/today").status_code == 401


def test_bad_token_rejected(client):
    r = client.get("/account/me", headers={"Authorization": "Bearer v1.bogus.sig"})
    assert r.status_code == 401


def test_green_turn_has_reply_and_label(client, user):
    r = client.post("/v1/chat/turn", json={"message": "good morning Aira"},
                    headers=user["headers"])
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["urgent"] is False
    assert body["safety"]["level"] == "green"
    assert body["trust_label"] == "wellness"
    assert body["reply"]                      # a reply string (LLM-free fallback in tests)


def test_red_turn_routes_to_urgent(client, user):
    r = client.post("/v1/chat/turn", json={"message": "I have heavy bleeding and severe pain"},
                    headers=user["headers"])
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["urgent"] is True
    assert body["reply"] is None              # a red turn NEVER reaches the reply model
    assert body["safety"]["level"] == "red"
    assert "care_team" in body["urgent_help"]


def test_amber_turn_flags_disclaimer(client, user):
    r = client.post("/v1/chat/turn", json={"message": "I keep getting headaches"},
                    headers=user["headers"])
    body = r.json()
    assert body["safety"]["level"] == "amber"
    assert body["trust_label"] == "watchful"
    assert body["disclaimer_needed"] is True


def test_safety_flag_recorded_for_red(client, user):
    client.post("/v1/chat/turn", json={"message": "chest pain right now"},
                headers=user["headers"])
    import safety
    flags = safety.recent_flags(limit=10, level="red")
    assert any(f["user_id"] == user["id"] for f in flags)
