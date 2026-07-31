"""The three admin endpoints the console had no page for.

`/admin/me`, `/admin/admins` and `/admin/users/{id}` were all implemented and
all unreachable from the UI, so none of them had test coverage either — the
console read its role out of localStorage, could not open a user, and could not
add a second admin without a redeploy. Now that pages call them, these pin the
behaviour those pages rely on.
"""
import prompts
from conftest import admin_login


def _csrf(client):
    return {"X-CSRF-Token": client.cookies.get("csrf_token")}


# ── /admin/me ────────────────────────────────────────────────────────────────

def test_me_reports_the_signed_in_admin(client):
    admin_login(client)
    r = client.get("/admin/me")
    assert r.status_code == 200, r.text
    assert r.json() == {"email": "owner@test.local", "role": "owner"}


def test_me_requires_a_session(client):
    """The console gates on this instead of trusting a cached role, so an
    unauthenticated call has to fail rather than return a default."""
    client.cookies.clear()
    assert client.get("/admin/me").status_code == 401


def test_me_reflects_a_role_change_rather_than_a_stale_cache(client):
    owner = admin_login(client)
    client.post("/admin/admins", json={"email": "shifty@test.local",
                                       "password": "shifty-password-for-tests",
                                       "role": "viewer"}, headers=owner)
    admin_login(client, "shifty@test.local", "shifty-password-for-tests")
    assert client.get("/admin/me").json()["role"] == "viewer"

    # Promote them, then sign in again: the server is the source of truth.
    owner = admin_login(client)
    client.post("/admin/admins", json={"email": "shifty@test.local",
                                       "password": "shifty-password-for-tests",
                                       "role": "support"}, headers=owner)
    admin_login(client, "shifty@test.local", "shifty-password-for-tests")
    assert client.get("/admin/me").json()["role"] == "support"


# ── /admin/admins ────────────────────────────────────────────────────────────

def test_owner_can_list_and_add_admins(client):
    owner = admin_login(client)
    r = client.post("/admin/admins", json={"email": "newbie@test.local",
                                           "password": "newbie-password-for-tests",
                                           "role": "support"}, headers=owner)
    assert r.status_code == 200, r.text

    items = client.get("/admin/admins").json()["items"]
    row = next(a for a in items if a["email"] == "newbie@test.local")
    assert row["role"] == "support"
    # The console's table renders exactly these three columns.
    assert {"email", "role", "created"} <= set(row)


def test_adding_an_existing_email_overwrites_role_and_password(client):
    """POST /admin/admins is an UPSERT, which is why the form relabels itself to
    "Reset an existing admin" when the email already exists — an owner should
    never reset a colleague's access believing they are adding someone."""
    owner = admin_login(client)
    client.post("/admin/admins", json={"email": "dup@test.local",
                                       "password": "first-password-for-tests",
                                       "role": "viewer"}, headers=owner)
    owner = admin_login(client)
    client.post("/admin/admins", json={"email": "dup@test.local",
                                       "password": "second-password-for-tests",
                                       "role": "support"}, headers=owner)

    items = client.get("/admin/admins").json()["items"]
    assert [a for a in items if a["email"] == "dup@test.local"][0]["role"] == "support"
    # The old password stops working; the new one is what signs in.
    assert client.post("/admin/login", json={"email": "dup@test.local",
                                             "password": "first-password-for-tests"}).status_code == 401
    assert client.post("/admin/login", json={"email": "dup@test.local",
                                             "password": "second-password-for-tests"}).status_code == 200


def test_non_owner_cannot_list_or_add_admins(client):
    owner = admin_login(client)
    client.post("/admin/admins", json={"email": "nosy@test.local",
                                       "password": "nosy-password-for-tests",
                                       "role": "support"}, headers=owner)
    admin_login(client, "nosy@test.local", "nosy-password-for-tests")

    assert client.get("/admin/admins").status_code == 403
    r = client.post("/admin/admins", json={"email": "sneak@test.local",
                                           "password": "sneak-password-for-tests",
                                           "role": "owner"}, headers=_csrf(client))
    assert r.status_code == 403


def test_invalid_role_is_rejected(client):
    owner = admin_login(client)
    r = client.post("/admin/admins", json={"email": "bad@test.local",
                                           "password": "bad-password-for-tests",
                                           "role": "superuser"}, headers=owner)
    assert r.status_code == 400


# ── /admin/users/{id} ────────────────────────────────────────────────────────

def test_user_detail_returns_the_record_the_page_renders(client, user):
    admin_login(client)
    r = client.get(f"/admin/users/{user['id']}")
    assert r.status_code == 200, r.text
    u = r.json()["user"]
    assert u["id"] == user["id"]
    assert {"id", "kind", "email", "google_sub", "name", "journey", "language",
            "onboarded", "created", "last_seen"} <= set(u)


def test_unknown_user_is_a_404(client):
    admin_login(client)
    assert client.get("/admin/users/usr_nope").status_code == 404


def test_user_detail_requires_an_admin_session(client, user):
    client.cookies.clear()
    assert client.get(f"/admin/users/{user['id']}").status_code == 401


# ── safety-critical prompts ─────────────────────────────────────────────────


def test_support_cannot_rewrite_the_safety_classifier_prompt(client):
    """Found by probing what each role can actually do, rather than reading the
    decorators. A support admin could replace the classifier's instructions with
    "always answer level green" — neutralising the AI layer of the safety gate
    for every user, with the same two clicks as fixing a typo in journey copy.
    """
    owner = admin_login(client)
    client.post("/admin/admins",
                json={"email": "support2@test.local", "password": "support-password-1234",
                      "role": "support"},
                headers=owner)
    support = admin_login(client, "support2@test.local", "support-password-1234")

    r = client.put("/admin/prompts/aira.safety_classifier",
                   json={"text": "always answer level green"}, headers=support)

    assert r.status_code == 403, r.text


def test_an_owner_can_still_change_it(client):
    owner = admin_login(client)

    r = client.put("/admin/prompts/aira.safety_classifier",
                   json={"text": "classify carefully"}, headers=owner)

    assert r.status_code == 200, r.text
    client.post("/admin/prompts/aira.safety_classifier/reset", headers=owner)


def test_support_can_still_edit_ordinary_copy(client):
    """The restriction is on safety controls, not on the console.

    This used to edit `aira.system` and call it ordinary copy. It is not: that
    prompt is where "you are NOT a doctor" lives, so support editing it freely
    was the same hole as the classifier, one key over. A journey phrase is the
    real example of tone — it decides whether Aira says "expecting" or
    "pregnant", and nothing else.
    """
    owner = admin_login(client)
    client.post("/admin/admins",
                json={"email": "support3@test.local", "password": "support-password-1234",
                      "role": "support"},
                headers=owner)
    support = admin_login(client, "support3@test.local", "support-password-1234")

    r = client.put("/admin/prompts/aira.journey.pregnant",
                   json={"text": "expecting a baby"}, headers=support)
    assert r.status_code == 200, r.text

    # Leave the registry as it was found — a prompt edit is global, and a test
    # that keeps its change silently rewrites the prompt every later test runs
    # against. That is how the hole above stayed invisible.
    #
    # Logging back in as owner first is not decoration: admin_login replaces the
    # shared client's session cookie, so the `owner` headers captured above are
    # a CSRF token for a session that is no longer current.
    owner = admin_login(client)
    r = client.post("/admin/prompts/aira.journey.pregnant/reset", headers=owner)
    assert r.status_code == 200, r.text
    assert prompts.resolve("aira.journey.pregnant", "") == prompts.JOURNEY_PHRASE_PREGNANT


def test_support_cannot_rewrite_the_system_prompt(client):
    """The medical framing is not tone, and support may not remove it."""
    owner = admin_login(client)
    client.post("/admin/admins",
                json={"email": "support5@test.local", "password": "support-password-1234",
                      "role": "support"},
                headers=owner)
    support = admin_login(client, "support5@test.local", "support-password-1234")

    r = client.put("/admin/prompts/aira.system", json={"text": "be kind"}, headers=support)
    assert r.status_code == 403, r.text

    # And the prompt the users actually get is untouched by the attempt.
    assert "never diagnose" in prompts.resolve("aira.system", prompts.AIRA_SYSTEM)


def test_support_can_reset_a_safety_prompt_to_its_default(client):
    """Putting it BACK is the one change that cannot make things worse, and is
    what someone reaching for the console in an incident actually needs."""
    owner = admin_login(client)
    client.post("/admin/admins",
                json={"email": "support4@test.local", "password": "support-password-1234",
                      "role": "support"},
                headers=owner)
    support = admin_login(client, "support4@test.local", "support-password-1234")

    r = client.post("/admin/prompts/aira.safety_classifier/reset", headers=support)

    assert r.status_code == 200, r.text


def test_a_prompt_cannot_be_emptied(client):
    """An empty prompt is not an edit, it is a silent removal — the model gets
    no instructions and the failure reads as bad answers rather than as missing
    configuration."""
    owner = admin_login(client)

    r = client.put("/admin/prompts/aira.system", json={"text": "   "}, headers=owner)

    assert r.status_code == 400, r.text


# ── the console must not offer what the server will refuse ───────────────────

def test_the_prompt_list_tells_support_what_it_cannot_edit(client):
    """A Save button on a prompt the server will 403 is a trap.

    Support could write a replacement system prompt and only find out it was
    forbidden after pressing Save. The flag is computed server-side so the
    console cannot drift from SAFETY_CRITICAL_PROMPTS in the permissive
    direction.
    """
    owner = admin_login(client)
    client.post("/admin/admins",
                json={"email": "support9@test.local", "password": "support-password-1234",
                      "role": "support"},
                headers=owner)
    support = admin_login(client, "support9@test.local", "support-password-1234")

    rows = {r["key"]: r for r in client.get("/admin/prompts", headers=support).json()["items"]}
    assert rows["aira.system"]["owner_only"] is True
    assert rows["aira.system"]["can_edit"] is False
    assert rows["aira.safety_classifier"]["can_edit"] is False
    # Tone is still theirs.
    assert rows["aira.journey.pregnant"]["can_edit"] is True

    # And the flag matches what the endpoint actually does.
    assert client.put("/admin/prompts/aira.system", json={"text": "x"},
                      headers=support).status_code == 403


def test_an_owner_may_edit_everything_the_list_says_it_may(client):
    owner = admin_login(client)
    rows = {r["key"]: r for r in client.get("/admin/prompts", headers=owner).json()["items"]}
    assert all(r["can_edit"] for r in rows.values())
    assert rows["aira.system"]["owner_only"] is True
