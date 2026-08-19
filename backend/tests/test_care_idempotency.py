"""Sending the same create twice must not create two.

This exists for one situation: a phone with no signal holds a write, sends it
when signal returns, and cannot tell "it never arrived" from "it arrived and the
reply was lost on the way back". The safe assumption is to send again, and the
server has to make that harmless.

The cost of getting it wrong is not a tidy-up job. A duplicate in a medicine
list is a second dose on the screen someone checks before taking one.
"""
import concurrent.futures

from app.domains import care


def _post(client, headers, path, body, key):
    return client.post(path, json={**body, "client_id": key}, headers=headers)


def test_the_same_client_id_creates_one_medicine(client, user):
    h = user["headers"]
    body = {"name": "Iron tablet", "dose": "65mg", "schedule": "Daily", "time": "8:00 PM"}

    first = _post(client, h, "/v1/care/medicines", body, "key-iron-1")
    second = _post(client, h, "/v1/care/medicines", body, "key-iron-1")

    assert first.status_code == 200, first.text
    # A replay must look like a success, not an error: the client is retrying
    # something the user already did, and from their side it worked.
    assert second.status_code == 200, second.text
    assert first.json()["id"] == second.json()["id"]

    meds = client.get("/v1/care", headers=h).json()["medicines"]
    assert len([m for m in meds if m["name"] == "Iron tablet"]) == 1


def test_a_different_client_id_creates_a_second_one(client, user):
    # The other direction, and the one a naive "ignore duplicates by content"
    # would break: two identical medicines really can both be wanted.
    h = user["headers"]
    body = {"name": "Paracetamol", "dose": "500mg", "schedule": "As needed"}
    a = _post(client, h, "/v1/care/medicines", body, "key-para-a")
    b = _post(client, h, "/v1/care/medicines", body, "key-para-b")
    assert a.json()["id"] != b.json()["id"]

    meds = client.get("/v1/care", headers=h).json()["medicines"]
    assert len([m for m in meds if m["name"] == "Paracetamol"]) == 2


def test_no_client_id_still_works(client, user):
    # The web app and every existing client send no key. They keep working and
    # simply have no retry safety — which is exactly what they had before.
    h = user["headers"]
    r = client.post("/v1/care/reminders", json={"title": "Drink water"}, headers=h)
    assert r.status_code == 200, r.text
    r2 = client.post("/v1/care/reminders", json={"title": "Drink water"}, headers=h)
    assert r2.status_code == 200
    assert r.json()["id"] != r2.json()["id"]


def test_the_key_is_scoped_to_the_user(client, user):
    """One person's key must never collide with another's.

    If it did, the second user's create would silently return the FIRST user's
    item — a cross-account data leak dressed up as a successful save.
    """
    h1 = user["headers"]
    other = client.post("/device/register").json()
    h2 = {"Authorization": f"Bearer {other['token']}"}

    a = _post(client, h1, "/v1/care/reminders", {"title": "Mine"}, "same-key")
    b = _post(client, h2, "/v1/care/reminders", {"title": "Theirs"}, "same-key")

    assert a.json()["id"] != b.json()["id"]
    assert b.json()["title"] == "Theirs"
    mine = [i["title"] for i in client.get("/v1/care", headers=h1).json()["reminders"]]
    theirs = [i["title"] for i in client.get("/v1/care", headers=h2).json()["reminders"]]
    assert "Mine" in mine and "Theirs" not in mine
    assert "Theirs" in theirs and "Mine" not in theirs


def test_the_key_is_not_stored_as_item_content(client, user):
    # It is plumbing, not something the user typed. Leaking it into the item's
    # data would put it on screen and into the export.
    h = user["headers"]
    r = _post(client, h, "/v1/care/reminders", {"title": "Walk"}, "key-walk")
    assert "client_id" not in r.json()
    items = client.get("/v1/care", headers=h).json()["reminders"]
    assert all("client_id" not in i for i in items)


def test_a_simultaneous_replay_still_creates_one(client, user):
    """The race the SELECT-then-INSERT alone does not cover.

    Two retries can arrive close enough that both look up the key, both find
    nothing, and both try to insert. The unique index is what actually enforces
    this; without it the check above passes and duplicates still get through
    under load.
    """
    h = user["headers"]
    body = {"what": "Headache", "severity": "Mild", "client_id": "key-race"}

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = [f.result() for f in [
            pool.submit(client.post, "/v1/care/symptom", json=body, headers=h)
            for _ in range(2)
        ]]

    assert [r.status_code for r in results] == [200, 200], [r.text for r in results]
    assert results[0].json()["id"] == results[1].json()["id"]
    timeline = client.get("/v1/care/timeline", headers=h).json()["items"]
    assert len([i for i in timeline if i.get("what") == "Headache"]) == 1


def test_the_unique_index_exists(client, user):
    """The race test above can pass by luck; this cannot.

    It asserts the constraint that makes the guarantee real rather than likely.
    """
    care.init()
    rows = care._conn.execute(
        "SELECT name FROM sqlite_master WHERE type='index' AND name='care_items_client'"
    ).fetchall()
    assert rows, "the unique index on (user_id, client_id) is missing"
