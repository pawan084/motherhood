"""The Google Sign-In handshake, verified without Google.

`_verify_google_id_token` decides who gets a session, and it had no tests at all
— the one function in the identity layer where a mistake hands an attacker
somebody else's account. The live handshake needs real OAuth credentials this
project doesn't have, but everything except Google's servers is testable: mint
an RS256 token with a throwaway key, point the verifier at that key, and drive
the real code path.

Failure modes matter more than the happy path here, so most of these assert that
something is REJECTED. A verifier that accepts a valid token is easy; one that
refuses a token signed by the wrong key, aimed at another app, or already
expired is the point.
"""
import time

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

from app.domains import accounts

CLIENT_ID = "aira-test-client.apps.googleusercontent.com"
GOOGLE_ISS = "https://accounts.google.com"


@pytest.fixture(scope="module")
def keys():
    """One key Google 'owns', and one an attacker owns."""
    return {
        "google": rsa.generate_private_key(public_exponent=65537, key_size=2048),
        "attacker": rsa.generate_private_key(public_exponent=65537, key_size=2048),
    }


def make_token(keys, *, key="google", sub="google-sub-1", aud=CLIENT_ID,
               iss=GOOGLE_ISS, email="mum@example.com", email_verified=True,
               exp_delta=3600, name="Priya", alg="RS256", **extra):
    now = int(time.time())
    claims = {"sub": sub, "aud": aud, "iss": iss, "email": email,
              "email_verified": email_verified, "name": name,
              "iat": now, "exp": now + exp_delta, **extra}
    return jwt.encode(claims, keys[key], algorithm=alg)


@pytest.fixture()
def google(monkeypatch, keys):
    """Point the verifier at our test key instead of Google's JWKS, and give it
    a client id. Everything else runs unmodified."""
    monkeypatch.setattr(accounts, "GOOGLE_CLIENT_ID", CLIENT_ID)

    class _Key:
        key = keys["google"].public_key()

    class _Client:
        def get_signing_key_from_jwt(self, token):
            return _Key()

    monkeypatch.setattr(accounts, "_jwk_client", _Client())
    return keys


# ── the happy path ──────────────────────────────────────────────────────────

def test_valid_token_creates_an_account_and_returns_a_session(client, google):
    r = client.post("/account/google", json={"id_token": make_token(google)})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["user"]["email"] == "mum@example.com"
    assert body["user"]["kind"] == "account"
    assert body["token"]

    # The returned session actually works.
    me = client.get("/account/me", headers={"Authorization": f"Bearer {body['token']}"})
    assert me.status_code == 200
    assert me.json()["user"]["id"] == body["user_id"]


def test_signing_in_twice_returns_the_same_user(client, google):
    a = client.post("/account/google", json={"id_token": make_token(google, sub="stable-sub", email="stable@example.com")})
    b = client.post("/account/google", json={"id_token": make_token(google, sub="stable-sub", email="stable@example.com")})
    assert a.json()["user_id"] == b.json()["user_id"]


def test_signing_in_carries_over_the_anonymous_user_s_care_data(client, google):
    """The reason the device token is sent at all: everything done before
    signing in must survive, or the user is silently stranded on an account
    they can no longer reach."""
    reg = client.post("/device/register").json()
    dev = {"Authorization": f"Bearer {reg['token']}"}
    client.post("/v1/onboarding", json={"journey": "pregnant", "name": "Priya",
                                        "weeks": 24}, headers=dev)
    client.post("/v1/care/reminders", json={"title": "Prenatal vitamin"}, headers=dev)

    r = client.post("/account/google", json={"id_token": make_token(google, sub="promote-me", email="promote@example.com"),
                                             "device_token": reg["token"]})
    assert r.status_code == 200, r.text
    # Promoted IN PLACE — same user id, so the care rows still belong to them.
    assert r.json()["user_id"] == reg["user_id"]
    assert r.json()["user"]["kind"] == "account"

    h = {"Authorization": f"Bearer {r.json()['token']}"}
    care = client.get("/v1/care", headers=h).json()
    assert [x["title"] for x in care["reminders"]] == ["Prenatal vitamin"]
    assert client.get("/v1/today", headers=h).json()["weeks"] == 24


def test_a_stale_device_token_does_not_block_sign_in(client, google):
    """A dead device token must not stop someone signing in — it just means
    there is nothing to carry over."""
    r = client.post("/account/google", json={"id_token": make_token(google, sub="no-carry", email="nocarry@example.com"),
                                             "device_token": "v1.garbage.garbage"})
    assert r.status_code == 200, r.text
    assert r.json()["user"]["kind"] == "account"


def test_google_links_to_an_existing_password_account_with_the_same_email(client, google):
    """Someone who signed up with email/password and later taps Google must land
    in the SAME account, not a second one.

    Before the unique email index existed this quietly created a duplicate; with
    the index it became a 500. Linking is only safe because the email is stored
    only when Google reports it verified — linking on an unverified address is a
    known takeover route.
    """
    email = "both-ways@example.com"
    signup = client.post("/account/signup",
                         json={"email": email, "password": "a-long-enough-passphrase"}).json()
    client.post("/v1/care/reminders", json={"title": "Iron tablet"},
                headers={"Authorization": f"Bearer {signup['token']}"})

    r = client.post("/account/google",
                    json={"id_token": make_token(google, sub="links-to-existing", email=email)})
    assert r.status_code == 200, r.text
    assert r.json()["user_id"] == signup["user_id"]

    # Same account means the care data is still theirs.
    h = {"Authorization": f"Bearer {r.json()['token']}"}
    assert [x["title"] for x in client.get("/v1/care", headers=h).json()["reminders"]] \
        == ["Iron tablet"]

    # And the password still works — linking adds a way in, it doesn't replace one.
    assert client.post("/account/login",
                       json={"email": email,
                             "password": "a-long-enough-passphrase"}).status_code == 200


def test_an_unverified_google_email_does_not_link_to_an_existing_account(client, google):
    """The takeover case: claiming someone's address at an IdP without proving
    it must not inherit their Aira account."""
    email = "victim-link@example.com"
    signup = client.post("/account/signup",
                         json={"email": email, "password": "a-long-enough-passphrase"}).json()

    r = client.post("/account/google",
                    json={"id_token": make_token(google, sub="attacker-sub",
                                                 email=email, email_verified=False)})
    assert r.status_code == 200, r.text
    assert r.json()["user_id"] != signup["user_id"]     # a separate, empty account
    assert r.json()["user"]["email"] is None


# ── the rejections that matter ──────────────────────────────────────────────

def test_token_signed_by_the_wrong_key_is_rejected(client, google):
    """The whole point of signature verification: anyone can craft claims."""
    r = client.post("/account/google",
                    json={"id_token": make_token(google, key="attacker", sub="evil")})
    assert r.status_code == 401


def test_token_for_another_app_is_rejected(client, google):
    """A token Google legitimately issued to a DIFFERENT application is signed
    correctly and still must not grant an Aira session."""
    r = client.post("/account/google",
                    json={"id_token": make_token(google, aud="some-other-app.apps.googleusercontent.com")})
    assert r.status_code == 401


def test_expired_token_is_rejected(client, google):
    r = client.post("/account/google",
                    json={"id_token": make_token(google, exp_delta=-60)})
    assert r.status_code == 401


def test_token_from_a_bogus_issuer_is_rejected(client, google):
    r = client.post("/account/google",
                    json={"id_token": make_token(google, iss="https://evil.example.com")})
    assert r.status_code == 401


def test_unsigned_token_is_rejected(client, google):
    """`alg: none` is the classic JWT bypass. RS256 is pinned, so a token with
    no signature must not be accepted however well-formed its claims are."""
    now = int(time.time())
    unsigned = jwt.encode({"sub": "evil", "aud": CLIENT_ID, "iss": GOOGLE_ISS,
                           "iat": now, "exp": now + 3600},
                          key="", algorithm="none")
    r = client.post("/account/google", json={"id_token": unsigned})
    assert r.status_code == 401


def test_token_missing_required_claims_is_rejected(client, google):
    now = int(time.time())
    # No `sub` — without it there is no stable identity to key the account on.
    token = jwt.encode({"aud": CLIENT_ID, "iss": GOOGLE_ISS, "iat": now,
                        "exp": now + 3600}, google["google"], algorithm="RS256")
    r = client.post("/account/google", json={"id_token": token})
    assert r.status_code == 401


def test_garbage_is_rejected_without_a_500(client, google):
    for junk in ("", "not-a-jwt", "a.b.c"):
        r = client.post("/account/google", json={"id_token": junk})
        assert r.status_code == 401, f"{junk!r} produced {r.status_code}"


def test_only_the_configured_client_id_is_a_valid_audience(client, google):
    """The Android package name was accepted as an audience too. Google puts a
    client id in `aud`, never a package name, so no real token could carry it —
    it only widened what we would accept. Pinned so it can't creep back."""
    assert not hasattr(accounts, "ANDROID_PACKAGE_NAME")
    r = client.post("/account/google",
                    json={"id_token": make_token(google, aud="com.aira.companion")})
    assert r.status_code == 401


# ── unverified email ────────────────────────────────────────────────────────

def test_an_unverified_email_is_not_stored_on_the_account(client, google):
    """Identity is keyed on `sub`, so this isn't takeover — but the admin
    console searches users by email, and an address the holder may not own
    would put support in front of the wrong person's record."""
    r = client.post("/account/google",
                    json={"id_token": make_token(google, sub="unverified-1",
                                                 email="victim@example.com",
                                                 email_verified=False)})
    assert r.status_code == 200, r.text          # sign-in still succeeds
    assert r.json()["user"]["email"] is None     # ...but the claim isn't kept


def test_a_verified_email_is_stored(client, google):
    r = client.post("/account/google",
                    json={"id_token": make_token(google, sub="verified-1",
                                                 email="Real.User@Example.com",
                                                 email_verified=True)})
    assert r.json()["user"]["email"] == "real.user@example.com"   # normalised


def test_a_missing_email_verified_claim_is_treated_as_unverified(client, google):
    """Absent means unproven. Defaulting the other way would make a stripped
    claim as good as a verified one."""
    now = int(time.time())
    token = jwt.encode({"sub": "no-ev", "aud": CLIENT_ID, "iss": GOOGLE_ISS,
                        "email": "someone@example.com",
                        "iat": now, "exp": now + 3600},
                       google["google"], algorithm="RS256")
    r = client.post("/account/google", json={"id_token": token})
    assert r.status_code == 200, r.text
    assert r.json()["user"]["email"] is None


# ── configuration failure modes ─────────────────────────────────────────────

def test_sign_in_is_503_when_no_client_id_is_configured(client, monkeypatch):
    """Fails CLOSED: with no client id the audience cannot be checked, so the
    endpoint refuses rather than accepting an unverified token."""
    monkeypatch.setattr(accounts, "GOOGLE_CLIENT_ID", "")
    r = client.post("/account/google", json={"id_token": "anything"})
    assert r.status_code == 503
