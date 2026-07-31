"""What each admin role may see and do, on the routes nothing was testing.

Ten of the twenty /admin routes had no test at all. The ten that did were the
prompt and admin-management ones — the obvious blast radius — which left the
safety queue, the content editor and the clinical video review unguarded, and
those are the pages where the console touches a person's own words and the
copy they are shown.

Two boundaries here are easy to erase by accident and expensive to erase:

  1. A `viewer` gets safety-flag METADATA but not the verbatim message. That is
     a deliberate privacy control, written as one line inside a route handler,
     and it had nothing asserting it. Whoever refactors `recent_flags` next has
     no way to learn from the code that dropping one keyword argument publishes
     the health complaints of every user to the lowest-privileged account.

  2. A `viewer` may read but never write. Every mutating route below requires
     `support`, and each is checked, because "you can look but not touch" is
     not a property that survives on the honour system.
"""
from conftest import admin_login


def _csrf(client):
    return {"X-CSRF-Token": client.cookies.get("csrf_token")}


def _as(client, role):
    """Sign in as a freshly created admin with `role`."""
    owner = admin_login(client)
    email = f"{role}-boundary@test.local"
    password = f"{role}-boundary-password-for-tests"
    r = client.post("/admin/admins",
                    json={"email": email, "password": password, "role": role},
                    headers=owner)
    assert r.status_code == 200, r.text
    return admin_login(client, email=email, password=password)


def _flag(client, user, message="I have heavy bleeding and feel faint"):
    """Produce one real safety flag by saying something the gate catches."""
    r = client.post("/v1/chat/turn", json={"message": message, "history": []},
                    headers=user["headers"])
    assert r.status_code == 200, r.text
    return message


# ── the verbatim message ─────────────────────────────────────────────────────

def test_a_viewer_never_sees_what_someone_actually_wrote(client, user):
    """The assertion this file exists for.

    The flag message is somebody describing their own symptoms. A viewer cannot
    action a flag, so reading those words is access with no job attached to it.
    """
    said = _flag(client, user)
    _as(client, "viewer")

    body = client.get("/admin/safety/flags").json()

    assert body["messages_redacted"] is True
    assert body["items"], "no flag was recorded, so this proves nothing"
    for item in body["items"]:
        assert said not in str(item), "a viewer was shown the user's own words"
        assert "message" not in item or not item["message"]


def test_support_does_see_it_because_support_has_to_act_on_it(client, user):
    """The other half. Redaction that also blinds the people who trigger the
    escalation would be privacy theatre with a real cost."""
    said = _flag(client, user)
    _as(client, "support")

    body = client.get("/admin/safety/flags").json()

    assert body["messages_redacted"] is False
    assert any(said in (item.get("message") or "") for item in body["items"])


def test_the_flag_still_carries_everything_needed_to_triage_it(client, user):
    """Redacted is not empty. A viewer should still be able to see that flags
    are arriving, at what level, and whether anyone has looked at them."""
    _flag(client, user)
    _as(client, "viewer")

    item = client.get("/admin/safety/flags").json()["items"][0]

    assert item["level"] in ("amber", "red")
    assert "ts" in item and "reviewed" in str(item)


# ── look, but do not touch ───────────────────────────────────────────────────

def test_a_viewer_cannot_review_a_safety_flag(client, user):
    _flag(client, user)
    admin_login(client)
    flag_id = client.get("/admin/safety/flags").json()["items"][0]["id"]
    _as(client, "viewer")

    r = client.post(f"/admin/safety/flags/{flag_id}/review",
                    json={"note": "looked at it"}, headers=_csrf(client))

    assert r.status_code == 403


def test_a_viewer_cannot_edit_the_copy_users_are_shown(client):
    """/admin/content is how the clinical tips and journey copy are edited. A
    viewer changing what a pregnant user reads would be the console's worst
    single failure."""
    _as(client, "viewer")

    r = client.patch("/admin/content/tip.pregnant.week28",
                     json={"body": "rewritten by someone who may not"},
                     headers=_csrf(client))

    assert r.status_code == 403


def test_a_viewer_cannot_approve_a_video_for_publication(client):
    """Approval is what makes a topic `playable` in both clients."""
    _as(client, "viewer")

    r = client.post("/admin/videos/preg-week-24/review",
                    json={"status": "approved"}, headers=_csrf(client))

    assert r.status_code == 403


def test_a_viewer_cannot_resolve_feedback(client):
    _as(client, "viewer")

    r = client.post("/admin/feedback/does-not-matter/resolve", headers=_csrf(client))

    # 403 before 404: whether the id exists is itself information.
    assert r.status_code == 403


# ── the read-only routes a viewer IS meant to have ───────────────────────────

def test_a_viewer_can_still_do_the_job_they_were_given(client):
    """These exist so the role is not accidentally reduced to nothing. A viewer
    who cannot open the console has no reason to have an account."""
    _as(client, "viewer")

    for path in ("/admin/overview", "/admin/content", "/admin/videos",
                 "/admin/feedback", "/admin/system"):
        assert client.get(path).status_code == 200, f"a viewer was locked out of {path}"


# ── signing out ──────────────────────────────────────────────────────────────

def test_logout_actually_ends_the_session(client):
    """Untested until now, on a console that manages safety escalations and runs
    on shared machines."""
    admin_login(client)
    assert client.get("/admin/me").status_code == 200

    client.post("/admin/logout", headers=_csrf(client))

    assert client.get("/admin/me").status_code == 401
