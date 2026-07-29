"""Retention/redaction of flagged health text, and the export + delete rights.

The suite runs with no GEMINI_API_KEY, so every screen is `degraded` — which is
exactly the condition that used to make the gate persist ordinary green messages
verbatim. These pin the corrected behaviour.
"""
import time

import safety
from conftest import admin_login


# ── 3.1 green turns are never retained, degradation is still visible ─────────

def test_green_turn_stores_no_message_even_when_degraded(client, user):
    secret = "my totally ordinary breakfast question xyzzy"
    r = client.post("/v1/chat/turn", json={"message": secret}, headers=user["headers"])
    assert r.json()["safety"]["level"] == "green"
    assert r.json()["safety"]["degraded"] is True      # no LLM in tests
    flags = safety.recent_flags(limit=500)
    assert not any(secret in (f["message"] or "") for f in flags)
    assert not any(f["user_id"] == user["id"] for f in flags)


def test_degraded_screens_are_counted_without_text(client, user):
    before = safety.stats()["degraded_screens_24h"]
    client.post("/v1/chat/turn", json={"message": "hello there"}, headers=user["headers"])
    assert safety.stats()["degraded_screens_24h"] == before + 1


def test_amber_and_red_are_still_stored_with_the_message(client, user):
    client.post("/v1/chat/turn", json={"message": "I have heavy bleeding now"},
                headers=user["headers"])
    mine = [f for f in safety.recent_flags(limit=500) if f["user_id"] == user["id"]]
    assert mine and mine[0]["level"] == "red"
    assert "heavy bleeding" in mine[0]["message"]


def test_recent_flags_can_withhold_the_message(client, user):
    client.post("/v1/chat/turn", json={"message": "I keep getting headaches"},
                headers=user["headers"])
    redacted = [f for f in safety.recent_flags(limit=500, include_message=False)
                if f["user_id"] == user["id"]]
    assert redacted
    assert all(f["message"] == "" and f["message_redacted"] for f in redacted)
    # ...but the reviewable metadata survives redaction.
    assert redacted[0]["level"] == "amber"


def test_purge_expired_drops_old_flags_only(client, user):
    client.post("/v1/chat/turn", json={"message": "chest pain"}, headers=user["headers"])
    safety.init()
    safety._conn.execute(
        "INSERT INTO safety_flags (ts, user_id, level, categories, message, degraded) "
        "VALUES (?,?,?,?,?,?)",
        (time.time() - 200 * 86400, "usr_ancient", "red", "[]", "old and expired", 0))
    safety._conn.commit()

    assert any(f["user_id"] == "usr_ancient" for f in safety.recent_flags(limit=500))
    removed = safety.purge_expired(90)
    assert removed >= 1
    remaining = safety.recent_flags(limit=500)
    assert not any(f["user_id"] == "usr_ancient" for f in remaining)
    assert any(f["user_id"] == user["id"] for f in remaining)   # recent one kept


def test_zero_retention_disables_the_purge(client):
    assert safety.purge_expired(0) == 0


def test_viewer_admin_cannot_read_flag_messages(client, user):
    client.post("/v1/chat/turn", json={"message": "chest pain and dizzy"},
                headers=user["headers"])

    owner_csrf = admin_login(client)
    r = client.post("/admin/admins", json={"email": "viewer@test.local",
                                           "password": "viewer-password-for-tests",
                                           "role": "viewer"}, headers=owner_csrf)
    assert r.status_code == 200, r.text

    owner_view = client.get("/admin/safety/flags?limit=100").json()
    assert owner_view["messages_redacted"] is False
    assert any("chest pain" in (f["message"] or "") for f in owner_view["items"])

    admin_login(client, "viewer@test.local", "viewer-password-for-tests")
    viewer_view = client.get("/admin/safety/flags?limit=100").json()
    assert viewer_view["messages_redacted"] is True
    assert all(f["message"] == "" for f in viewer_view["items"])
    # The viewer still sees what it needs to triage volume, just not the words.
    assert viewer_view["stats"]["red"] >= 1
    assert any(f["level"] == "red" for f in viewer_view["items"])

    # And a viewer still cannot action a flag.
    flag_id = viewer_view["items"][0]["id"]
    r = client.post(f"/admin/safety/flags/{flag_id}/review", json={"note": "nope"},
                    headers={"X-CSRF-Token": client.cookies.get("csrf_token")})
    assert r.status_code == 403


# ── 3.8 export + delete ──────────────────────────────────────────────────────

def test_export_returns_the_users_own_data(client, user):
    h = user["headers"]
    client.post("/v1/onboarding", json={"journey": "pregnant", "name": "Exporty",
                                        "priorities": ["Better sleep"], "weeks": 20}, headers=h)
    client.post("/v1/chat/turn", json={"message": "hello Aira"}, headers=h)
    client.post("/v1/memory", json={"label": "Stage", "value": "Week 20"}, headers=h)
    client.post("/v1/consent", json={"feature": "avatar", "granted": True}, headers=h)
    client.post("/v1/feedback", json={"kind": "general", "message": "nice"}, headers=h)
    client.put("/v1/emergency-profile", json={"care_team_phone": "+911140000000"}, headers=h)

    r = client.get("/v1/account/export", headers=h)
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["user_id"] == user["id"]
    data = body["data"]
    assert data["account"]["name"] == "Exporty"
    assert data["care"]["context"]["weeks"] == 20
    assert data["care"]["emergency_profile"]["care_team_phone"] == "+911140000000"
    assert any(t["text"] == "hello Aira" for t in data["chat"])
    assert any(m["label"] == "Stage" for m in data["memory"])
    assert data["consent"]["current"]["avatar"] is True
    assert any(f["message"] == "nice" for f in data["feedback"])
    assert "safety" in data and "events" in data


def test_export_requires_auth(client):
    assert client.get("/v1/account/export").status_code == 401


def test_delete_needs_the_exact_confirmation(client, user):
    r = client.post("/v1/account/delete", json={"confirm": "yes"}, headers=user["headers"])
    assert r.status_code == 400
    # The account still works.
    assert client.get("/account/me", headers=user["headers"]).status_code == 200


def test_delete_erases_everything_and_kills_the_token(client, user):
    h = user["headers"]
    uid = user["id"]
    client.post("/v1/onboarding", json={"journey": "postpartum"}, headers=h)
    client.post("/v1/chat/turn", json={"message": "I feel dizzy"}, headers=h)   # amber -> a flag
    client.post("/v1/memory", json={"label": "Note", "value": "remember this"}, headers=h)
    client.post("/v1/care/reminders", json={"title": "Vitamin"}, headers=h)

    assert any(f["user_id"] == uid for f in safety.recent_flags(limit=500))

    r = client.post("/v1/account/delete", json={"confirm": "DELETE MY DATA"}, headers=h)
    assert r.status_code == 200, r.text
    assert r.json()["deleted"]["account"] == 1

    # The token is dead the moment the users row goes.
    assert client.get("/account/me", headers=h).status_code == 401
    assert client.get("/v1/account/export", headers=h).status_code == 401

    # And nothing of theirs is left behind.
    assert not any(f["user_id"] == uid for f in safety.recent_flags(limit=500))
    import chat, memory, care, consent, feedback
    assert chat.export_user(uid) == []
    assert memory.export_user(uid) == []
    assert feedback.export_user(uid) == []
    assert consent.export_user(uid)["history"] == []
    assert care.export_user(uid)["items"]["reminder"] == []
    assert care.export_user(uid)["context"]["weeks"] is None
