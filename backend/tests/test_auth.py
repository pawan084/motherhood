"""P1: device tokens, sessions, and the identity-resolution order.

The properties under test are the security properties, not the happy path:
tampered tokens fail, device data is not addressable by a guessed id, and a
banned account cannot act through either credential type.
"""


def _register(client) -> str:
    r = client.post("/device/register")
    assert r.status_code == 200
    return r.json()["device_token"]


def _dev_login(client, email="maya@example.com", device_token=None) -> dict:
    headers = {"X-Device-Token": device_token} if device_token else {}
    r = client.post("/account/dev-login", json={"email": email, "name": "Maya"}, headers=headers)
    assert r.status_code == 200
    return r.json()


def test_device_register_and_use(client):
    token = _register(client)
    r = client.get("/care-context", headers={"X-Device-Token": token})
    assert r.status_code == 200
    assert r.json() == {"context": None}


def test_no_credentials_is_401(client):
    assert client.get("/care-context").status_code == 401


def test_tampered_device_token_is_401(client):
    token = _register(client)
    body, sig = token.split(".", 1)
    # Flip a signature character.
    bad_sig = ("0" if sig[0] != "0" else "1") + sig[1:]
    r = client.get("/care-context", headers={"X-Device-Token": f"{body}.{bad_sig}"})
    assert r.status_code == 401


def test_session_token_is_not_a_device_token_and_vice_versa(client):
    """Disjoint claim shapes: the two token kinds must never cross-validate,
    even though they may share an HMAC key."""
    device_token = _register(client)
    session = _dev_login(client)["session"]
    # A session presented as a device token: rejected.
    assert client.get("/care-context", headers={"X-Device-Token": session}).status_code == 401
    # A device token presented as a session: rejected (falls through to 401
    # because no device token header is present either).
    assert client.get("/account/me",
                      headers={"Authorization": f"Bearer {device_token}"}).status_code == 401


def test_two_devices_cannot_see_each_other(client):
    """The IDOR property. Device A writes context; device B must see nothing."""
    a, b = _register(client), _register(client)
    r = client.put("/care-context", json={"stage": "trying_to_conceive"},
                   headers={"X-Device-Token": a})
    assert r.status_code == 200
    r = client.get("/care-context", headers={"X-Device-Token": b})
    assert r.json() == {"context": None}


def test_dev_login_returns_working_session(client):
    session = _dev_login(client)["session"]
    r = client.get("/account/me", headers={"Authorization": f"Bearer {session}"})
    assert r.status_code == 200
    assert r.json()["email"] == "maya@example.com"


def test_sign_in_merges_device_context(client):
    """Guest onboards on a device, then signs in: the account inherits the
    device's care context via link_and_merge."""
    device = _register(client)
    client.put("/care-context",
               json={"stage": "pregnant", "due_date": "2026-11-20", "display_name": "Maya"},
               headers={"X-Device-Token": device})
    session = _dev_login(client, device_token=device)["session"]
    r = client.get("/care-context", headers={"Authorization": f"Bearer {session}"})
    ctx = r.json()["context"]
    assert ctx is not None and ctx["stage"] == "pregnant" and ctx["display_name"] == "Maya"


def test_google_sign_in_fails_closed_without_client_id(client):
    """GOOGLE_CLIENT_ID is human-gated config; until set, the endpoint must 503,
    never skip the audience check."""
    r = client.post("/account/google", json={"identity_token": "anything"})
    assert r.status_code == 503


def test_account_delete_erases_device_data_too(client):
    """Delete means delete: the cascade covers ids of linked devices."""
    device = _register(client)
    client.put("/care-context", json={"stage": "trying_to_conceive"},
               headers={"X-Device-Token": device})
    session = _dev_login(client, device_token=device)["session"]

    r = client.delete("/account", headers={"Authorization": f"Bearer {session}"})
    assert r.status_code == 200
    # The session is now dead (account row gone -> /me 404s)...
    assert client.get("/account/me",
                      headers={"Authorization": f"Bearer {session}"}).status_code == 404
    # ...and the device-scoped health data is gone as well.
    r = client.get("/care-context", headers={"X-Device-Token": device})
    assert r.json() == {"context": None}
