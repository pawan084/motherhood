"""The three admin endpoints the console had no page for.

`/admin/me`, `/admin/admins` and `/admin/users/{id}` were all implemented and
all unreachable from the UI, so none of them had test coverage either — the
console read its role out of localStorage, could not open a user, and could not
add a second admin without a redeploy. Now that pages call them, these pin the
behaviour those pages rely on.
"""
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
