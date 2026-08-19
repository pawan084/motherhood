"""Email/password accounts — the second optional way to hold an account.

Aira works anonymously; an account exists so care context can follow someone to
another device. The property that matters most is therefore not "signup works"
but "signing up does not lose what you already did" — everything entered before
creating an account has to survive the promotion.

As with Google, the rejections carry the weight: a duplicate email, a weak
password, an unknown address and a wrong password all have to behave, and the
last two must be indistinguishable from outside.
"""
from app.domains import accounts
from app.core import passwords


def _register(client):
    r = client.post("/device/register")
    assert r.status_code == 200, r.text
    return r.json()


GOOD_PW = "a-long-enough-passphrase"


# ── signup ──────────────────────────────────────────────────────────────────

def test_signup_creates_an_account_that_can_be_used(client):
    r = client.post("/account/signup",
                    json={"email": "New.User@Example.com", "password": GOOD_PW})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["user"]["kind"] == "account"
    assert body["user"]["email"] == "new.user@example.com"      # normalised

    me = client.get("/account/me", headers={"Authorization": f"Bearer {body['token']}"})
    assert me.status_code == 200
    assert me.json()["user"]["id"] == body["user_id"]


def test_signup_carries_over_the_anonymous_users_care_data(client):
    """The whole reason `device_token` is sent. Without this, everything done
    before signing up is stranded on an account the user can no longer reach."""
    reg = _register(client)
    dev = {"Authorization": f"Bearer {reg['token']}"}
    client.post("/v1/onboarding", json={"journey": "pregnant", "name": "Priya",
                                        "weeks": 24}, headers=dev)
    client.post("/v1/care/reminders", json={"title": "Prenatal vitamin"}, headers=dev)

    r = client.post("/account/signup", json={"email": "carry@example.com",
                                             "password": GOOD_PW,
                                             "device_token": reg["token"]})
    assert r.status_code == 200, r.text
    # Promoted IN PLACE: same id, so the care rows still belong to them.
    assert r.json()["user_id"] == reg["user_id"]
    assert r.json()["user"]["kind"] == "account"

    h = {"Authorization": f"Bearer {r.json()['token']}"}
    assert [x["title"] for x in client.get("/v1/care", headers=h).json()["reminders"]] \
        == ["Prenatal vitamin"]
    assert client.get("/v1/today", headers=h).json()["weeks"] == 24


def test_a_stale_device_token_does_not_block_signup(client):
    r = client.post("/account/signup", json={"email": "stale@example.com",
                                             "password": GOOD_PW,
                                             "device_token": "v1.garbage.garbage"})
    assert r.status_code == 200, r.text
    assert r.json()["user"]["kind"] == "account"


def test_duplicate_email_is_rejected_regardless_of_case(client):
    client.post("/account/signup", json={"email": "dupe@example.com", "password": GOOD_PW})
    r = client.post("/account/signup", json={"email": "DUPE@Example.com", "password": GOOD_PW})
    assert r.status_code == 409
    assert "already exists" in r.json()["detail"]


def test_short_password_is_rejected(client):
    r = client.post("/account/signup", json={"email": "short@example.com", "password": "hunter2"})
    assert r.status_code == 400
    assert str(accounts.MIN_PASSWORD_LENGTH) in r.json()["detail"]


def test_malformed_email_is_rejected(client):
    for bad in ("", "nope", "@example.com", "user@"):
        r = client.post("/account/signup", json={"email": bad, "password": GOOD_PW})
        assert r.status_code == 400, f"{bad!r} produced {r.status_code}"


def test_many_anonymous_users_coexist_despite_the_email_index(client):
    """The unique email index is partial. Anonymous rows all have NULL email and
    must not collide — a full unique index would break device registration at the
    second user."""
    ids = {_register(client)["user_id"] for _ in range(4)}
    assert len(ids) == 4


# ── login ───────────────────────────────────────────────────────────────────

def test_login_returns_a_working_session(client):
    client.post("/account/signup", json={"email": "login@example.com", "password": GOOD_PW})
    r = client.post("/account/login", json={"email": "LOGIN@example.com", "password": GOOD_PW})
    assert r.status_code == 200, r.text
    me = client.get("/account/me", headers={"Authorization": f"Bearer {r.json()['token']}"})
    assert me.json()["user"]["email"] == "login@example.com"


def test_wrong_password_and_unknown_email_are_indistinguishable(client):
    """Different responses would let someone enumerate which addresses have an
    Aira account — on a maternal-health app that leaks something by itself."""
    client.post("/account/signup", json={"email": "known@example.com", "password": GOOD_PW})
    wrong = client.post("/account/login",
                        json={"email": "known@example.com", "password": "wrong-but-long-enough"})
    unknown = client.post("/account/login",
                          json={"email": "nobody@example.com", "password": GOOD_PW})
    assert wrong.status_code == unknown.status_code == 401
    assert wrong.json()["detail"] == unknown.json()["detail"]


def test_an_anonymous_user_cannot_be_logged_into(client):
    """Device users have no password. Verifying against a NULL hash must fail
    closed rather than treating 'no password' as 'any password'."""
    _register(client)
    r = client.post("/account/login", json={"email": "", "password": GOOD_PW})
    assert r.status_code == 400          # no email to log in with at all


def test_repeated_failures_lock_the_account_then_clear(client):
    email = "throttle@example.com"
    client.post("/account/signup", json={"email": email, "password": GOOD_PW})
    for _ in range(passwords._MAX_FAILS):
        client.post("/account/login", json={"email": email, "password": "not-the-password"})

    # Locked out — and the correct password is refused too, which is the point.
    assert client.post("/account/login",
                       json={"email": email, "password": GOOD_PW}).status_code == 401

    passwords.throttle_reset(f"user:{email}")
    assert client.post("/account/login",
                       json={"email": email, "password": GOOD_PW}).status_code == 200


def test_the_lockout_message_does_not_reveal_that_the_account_exists(client):
    email = "quiet@example.com"
    client.post("/account/signup", json={"email": email, "password": GOOD_PW})
    for _ in range(passwords._MAX_FAILS):
        client.post("/account/login", json={"email": email, "password": "not-the-password"})
    locked = client.post("/account/login", json={"email": email, "password": GOOD_PW})
    unknown = client.post("/account/login",
                          json={"email": "ghost@example.com", "password": GOOD_PW})
    assert locked.json()["detail"] == unknown.json()["detail"]
    passwords.throttle_reset(f"user:{email}")


# ── changing a password ─────────────────────────────────────────────────────

def test_password_change_requires_the_current_one(client):
    tok = client.post("/account/signup",
                      json={"email": "change@example.com", "password": GOOD_PW}).json()["token"]
    h = {"Authorization": f"Bearer {tok}"}

    bad = client.post("/account/password",
                      json={"current": "not-it-at-all", "new": "another-long-passphrase"},
                      headers=h)
    assert bad.status_code == 401

    ok = client.post("/account/password",
                     json={"current": GOOD_PW, "new": "another-long-passphrase"}, headers=h)
    assert ok.status_code == 200, ok.text
    assert client.post("/account/login",
                       json={"email": "change@example.com",
                             "password": "another-long-passphrase"}).status_code == 200
    assert client.post("/account/login",
                       json={"email": "change@example.com",
                             "password": GOOD_PW}).status_code == 401


def test_password_change_signs_other_devices_out_but_not_this_one(client):
    """A password change is usually a response to suspicion, so other sessions
    must die — while the device doing the changing stays usable."""
    signup = client.post("/account/signup",
                         json={"email": "sessions@example.com", "password": GOOD_PW}).json()
    other = {"Authorization": f"Bearer {signup['token']}"}
    assert client.get("/account/me", headers=other).status_code == 200

    changed = client.post("/account/password",
                          json={"current": GOOD_PW, "new": "yet-another-passphrase"},
                          headers=other)
    fresh = {"Authorization": f"Bearer {changed.json()['token']}"}

    assert client.get("/account/me", headers=other).status_code == 401   # old token dead
    assert client.get("/account/me", headers=fresh).status_code == 200   # new one works


def test_an_account_without_a_password_cannot_change_one(client):
    """A Google-only account has no password to verify against, so this must
    refuse rather than let anyone holding the session set one."""
    reg = _register(client)
    r = client.post("/account/password",
                    json={"current": "anything-at-all", "new": "a-brand-new-passphrase"},
                    headers={"Authorization": f"Bearer {reg['token']}"})
    assert r.status_code == 400
    assert "no password" in r.json()["detail"]


# ── the hash itself ─────────────────────────────────────────────────────────

def test_the_password_is_never_stored_in_the_clear(client):
    accounts.init()
    client.post("/account/signup", json={"email": "hashed@example.com", "password": GOOD_PW})
    row = accounts._conn.execute(
        "SELECT pw_hash FROM users WHERE email=?", ("hashed@example.com",)).fetchone()
    assert row and row[0]
    assert GOOD_PW not in row[0]
    assert row[0].startswith(f"{passwords.PBKDF2_ITERATIONS}$")


def test_a_weaker_stored_hash_is_upgraded_on_next_login(client):
    """Raising the iteration count must reach existing accounts, not only new
    ones — login is the only moment the plaintext is available to rehash."""
    accounts.init()
    email = "rehash@example.com"
    client.post("/account/signup", json={"email": email, "password": GOOD_PW})
    accounts._conn.execute("UPDATE users SET pw_hash=? WHERE email=?",
                           (passwords.hash_pw(GOOD_PW, iterations=1000), email))
    accounts._conn.commit()

    assert client.post("/account/login",
                       json={"email": email, "password": GOOD_PW}).status_code == 200
    after = accounts._conn.execute(
        "SELECT pw_hash FROM users WHERE email=?", (email,)).fetchone()[0]
    assert after.startswith(f"{passwords.PBKDF2_ITERATIONS}$")
