"""Passwordless email codes for the mobile sign-in flow.

The app's sign-in screen is code-first rather than password-first. Until a real
mailer is configured, development/test returns the code explicitly; production
must fail closed instead of pretending delivery happened.
"""
import time

from app.domains import accounts


def _request_code(client, email="otp@example.com", purpose="signin"):
    r = client.post("/account/code/request", json={"email": email, "purpose": purpose})
    assert r.status_code == 200, r.text
    body = r.json()
    assert len(body["dev_code"]) == 6
    assert body["expires_in"] == accounts.OTP_TTL_SECONDS
    return body["dev_code"]


def test_request_and_verify_code_creates_an_account(client):
    code = _request_code(client, "New.Otp@Example.com", "signup")
    r = client.post("/account/code/verify",
                    json={"email": "new.otp@example.com", "code": code})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["is_new_user"] is True
    assert body["needs_onboarding"] is True
    assert body["user"]["kind"] == "account"
    assert body["user"]["email"] == "new.otp@example.com"

    me = client.get("/account/me", headers={"Authorization": f"Bearer {body['token']}"})
    assert me.status_code == 200


def test_otp_promotion_keeps_device_history(client):
    reg = client.post("/device/register").json()
    dev = {"Authorization": f"Bearer {reg['token']}"}
    client.post("/v1/onboarding", json={"journey": "pregnant", "weeks": 24}, headers=dev)
    client.post("/v1/care/reminders", json={"title": "Prenatal vitamin"}, headers=dev)

    code = _request_code(client, "carry-otp@example.com", "signup")
    r = client.post("/account/code/verify",
                    json={"email": "carry-otp@example.com", "code": code,
                          "device_token": reg["token"]})
    assert r.status_code == 200, r.text
    assert r.json()["user_id"] == reg["user_id"]
    assert r.json()["is_new_user"] is True
    assert r.json()["needs_onboarding"] is False

    h = {"Authorization": f"Bearer {r.json()['token']}"}
    assert client.get("/v1/today", headers=h).json()["weeks"] == 24
    assert [x["title"] for x in client.get("/v1/care", headers=h).json()["reminders"]] \
        == ["Prenatal vitamin"]


def test_otp_logs_into_existing_account_without_recreating_it(client):
    first = client.post("/account/signup",
                        json={"email": "existing-otp@example.com",
                              "password": "a-long-enough-passphrase"}).json()
    code = _request_code(client, "existing-otp@example.com")

    r = client.post("/account/code/verify",
                    json={"email": "existing-otp@example.com", "code": code})
    assert r.status_code == 200, r.text
    assert r.json()["user_id"] == first["user_id"]
    assert r.json()["is_new_user"] is False


def test_wrong_code_cannot_be_used(client):
    _request_code(client, "wrong-otp@example.com")
    r = client.post("/account/code/verify",
                    json={"email": "wrong-otp@example.com", "code": "000000"})
    assert r.status_code == 401
    assert "invalid or expired" in r.json()["detail"]


def test_code_cannot_be_reused(client):
    code = _request_code(client, "reuse-otp@example.com")
    assert client.post("/account/code/verify",
                       json={"email": "reuse-otp@example.com", "code": code}).status_code == 200
    again = client.post("/account/code/verify",
                        json={"email": "reuse-otp@example.com", "code": code})
    assert again.status_code == 401


def test_expired_code_shows_the_same_error(client):
    code = _request_code(client, "expired-otp@example.com")
    accounts.init()
    accounts._conn.execute(
        "UPDATE account_login_codes SET expires=? WHERE LOWER(email)=?",
        (time.time() - 1, "expired-otp@example.com"))
    accounts._conn.commit()

    r = client.post("/account/code/verify",
                    json={"email": "expired-otp@example.com", "code": code})
    assert r.status_code == 401
    assert "invalid or expired" in r.json()["detail"]


def test_resend_wait_is_enforced(client):
    _request_code(client, "wait-otp@example.com")
    r = client.post("/account/code/request", json={"email": "wait-otp@example.com"})
    assert r.status_code == 429
    assert "please wait" in r.json()["detail"]


def test_configured_smtp_hides_the_dev_code(monkeypatch, client):
    sent = {}

    monkeypatch.setattr(accounts.mailer, "configured", lambda: True)

    def fake_send(email, code, *, ttl_minutes):
        sent["email"] = email
        sent["code"] = code
        sent["ttl"] = ttl_minutes

    monkeypatch.setattr(accounts.mailer, "send_login_code", fake_send)
    r = client.post("/account/code/request", json={"email": "smtp@example.com"})

    assert r.status_code == 200, r.text
    body = r.json()
    assert body["delivery"] == "smtp"
    assert "dev_code" not in body
    assert sent["email"] == "smtp@example.com"
    assert len(sent["code"]) == 6
    assert sent["ttl"] == 10


def test_smtp_failure_does_not_store_a_login_code(monkeypatch, client):
    monkeypatch.setattr(accounts.mailer, "configured", lambda: True)

    def fail(*_args, **_kwargs):
        raise RuntimeError("boom")

    monkeypatch.setattr(accounts.mailer, "send_login_code", fail)
    r = client.post("/account/code/request", json={"email": "smtp-fail@example.com"})

    assert r.status_code == 503
    accounts.init()
    row = accounts._conn.execute(
        "SELECT 1 FROM account_login_codes WHERE LOWER(email)=?",
        ("smtp-fail@example.com",)).fetchone()
    assert row is None
