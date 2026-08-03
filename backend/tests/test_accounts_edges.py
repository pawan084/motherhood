"""Coverage: accounts.py google-verify fail-closed paths + disabled guard."""
import sys


def _acc(client):
    return sys.modules["accounts"]


def test_google_signin_unconfigured_fails_closed(client):
    # No GOOGLE_CLIENT_ID in tests -> 503, never a skipped audience check.
    r = client.post("/account/google", json={"identity_token": "x"})
    assert r.status_code == 503


def test_google_signin_bad_token_is_401(client, monkeypatch):
    acc = _acc(client)
    monkeypatch.setattr(acc, "GOOGLE_CLIENT_ID", "cid.apps.googleusercontent.com")
    r = client.post("/account/google", json={"identity_token": "garbage"})
    assert r.status_code == 401


def test_google_signin_happy_path_and_disabled_guard(client, monkeypatch):
    acc = _acc(client)
    monkeypatch.setattr(acc, "verify_google_token",
                        lambda t: {"sub": "g123", "email": "a@b.c", "name": "A"})
    r = client.post("/account/google", json={"identity_token": "ok"})
    assert r.status_code == 200 and "session" in r.json()
    uid = r.json()["user_id"]
    acc._conn.execute("UPDATE accounts SET disabled=1 WHERE id=?", (uid,))
    acc._conn.commit()
    assert client.post("/account/google",
                       json={"identity_token": "ok"}).status_code == 403
