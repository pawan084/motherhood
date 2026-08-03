"""P10: admin — auth, role hierarchy, the safety review queue, the Journey CMS,
and the admin action log."""
import sys


def _login(client, email="owner@test.dev", password="test-owner-password") -> dict:
    r = client.post("/admin/login", json={"email": email, "password": password})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['token']}"}


def _make_admin(client, owner, email, role):
    r = client.post("/admin/admins", json={
        "email": email, "password": "a-long-enough-password", "role": role}, headers=owner)
    assert r.status_code == 200
    return _login(client, email, "a-long-enough-password")


def _register(client) -> dict:
    r = client.post("/device/register")
    return {"X-Device-Token": r.json()["device_token"]}


def _screen_turns(client, monkeypatch):
    """Run two real gate screenings (LLM stubbed) to populate the audit."""
    safety = sys.modules["safety"]
    monkeypatch.setattr(safety, "_classify_llm",
                        lambda *a, **k: {"decision": "ok", "reason": "stub"})
    h = _register(client)
    services = sys.modules["services"]
    monkeypatch.setattr(services, "generate_reply", lambda *a: "reply")
    monkeypatch.setattr(services, "suggest_cards", lambda *a: [])
    monkeypatch.setattr(services, "extract_memory", lambda *a: [])
    client.post("/respond", json={"text": "good morning"}, headers=h)
    client.post("/respond", json={"text": "i want to hurt myself"}, headers=h)  # rules-urgent


# --- auth --------------------------------------------------------------------

def test_bootstrap_owner_and_login(client):
    h = _login(client)
    me = client.get("/admin/me", headers=h).json()
    assert me == {"email": "owner@test.dev", "role": "owner"}


def test_wrong_password_is_401(client):
    r = client.post("/admin/login", json={"email": "owner@test.dev", "password": "nope"})
    assert r.status_code == 401


def test_unknown_email_is_401_same_shape(client):
    r = client.post("/admin/login", json={"email": "ghost@test.dev", "password": "nope"})
    assert r.status_code == 401
    assert r.json()["detail"] == "invalid credentials"


def test_learner_tokens_do_not_work_on_admin_routes(client):
    """Disjoint claim shapes, once more: a learner session must never pass
    admin auth even though both are HMAC envelopes."""
    r = client.post("/account/dev-login", json={"email": "m@x.com", "name": ""})
    session = {"Authorization": f"Bearer {r.json()['session']}"}
    assert client.get("/admin/me", headers=session).status_code == 401


# --- role hierarchy ----------------------------------------------------------

def test_content_cannot_read_safety_queue(client):
    owner = _login(client)
    content = _make_admin(client, owner, "content@test.dev", "content")
    assert client.get("/admin/safety-audit", headers=content).status_code == 403
    assert client.get("/admin/journey", headers=content).status_code == 200


def test_support_cannot_manage_admins(client):
    owner = _login(client)
    support = _make_admin(client, owner, "support@test.dev", "support")
    assert client.get("/admin/admins", headers=support).status_code == 403
    assert client.get("/admin/safety-audit", headers=support).status_code == 200


def test_short_admin_password_rejected(client):
    owner = _login(client)
    r = client.post("/admin/admins", json={
        "email": "x@test.dev", "password": "short", "role": "support"}, headers=owner)
    assert r.status_code == 422


# --- safety review queue -----------------------------------------------------

def test_queue_lists_filters_and_reviews(client, monkeypatch):
    _screen_turns(client, monkeypatch)
    owner = _login(client)

    all_rows = client.get("/admin/safety-audit", headers=owner).json()
    assert all_rows["total"] == 2

    urgent = client.get("/admin/safety-audit?decision=urgent", headers=owner).json()
    assert urgent["total"] == 1
    assert urgent["rows"][0]["matched"] == ["self_harm_risk"]
    assert urgent["rows"][0]["review"] is None

    audit_id = urgent["rows"][0]["id"]
    r = client.post(f"/admin/safety-audit/{audit_id}/review",
                    json={"note": "correct escalation"}, headers=owner)
    assert r.status_code == 200

    urgent = client.get("/admin/safety-audit?decision=urgent", headers=owner).json()
    assert urgent["rows"][0]["review"]["note"] == "correct escalation"
    # The unreviewed filter now excludes it.
    left = client.get("/admin/safety-audit?decision=urgent&unreviewed=true",
                      headers=owner).json()
    assert left["total"] == 0


def test_overview_counts_unreviewed_urgent(client, monkeypatch):
    _screen_turns(client, monkeypatch)
    owner = _login(client)
    ov = client.get("/admin/overview", headers=owner).json()
    assert ov["turns_screened"] == 2
    assert ov["urgent_turns"] == 1
    assert ov["unreviewed_urgent"] == 1


# --- journey CMS -------------------------------------------------------------

def test_journey_edit_reaches_learners(client):
    owner = _login(client)
    r = client.put("/admin/journey/preg-21-24", json={
        "title": "Movement, revised", "yourself": "New copy.",
        "baby": "New baby copy.", "prepare": "New prepare copy."}, headers=owner)
    assert r.status_code == 200

    # A learner at week 24 sees the edit immediately.
    h = _register(client)
    from datetime import date
    due = date.fromordinal(date.today().toordinal() + 112).isoformat()
    client.put("/care-context", json={"stage": "pregnant", "due_date": due}, headers=h)
    body = client.get("/journey", headers=h).json()
    assert body["content"]["title"] == "Movement, revised"


def test_journey_edit_unknown_band_404(client):
    owner = _login(client)
    r = client.put("/admin/journey/nope", json={"title": "x"}, headers=owner)
    assert r.status_code == 404


# --- action log --------------------------------------------------------------

def test_mutations_are_logged(client, monkeypatch):
    _screen_turns(client, monkeypatch)
    owner = _login(client)
    client.put("/admin/journey/preg-1-3", json={"title": "Edited"}, headers=owner)
    audit_id = client.get("/admin/safety-audit?decision=urgent",
                          headers=owner).json()["rows"][0]["id"]
    client.post(f"/admin/safety-audit/{audit_id}/review", json={}, headers=owner)

    log = client.get("/admin/audit-log", headers=owner).json()["rows"]
    actions = [r["action"] for r in log]
    assert "journey_update" in actions and "safety_review" in actions
    assert all(r["email"] == "owner@test.dev" for r in log)
