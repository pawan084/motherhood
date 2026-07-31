"""Counting movements, and the baseline the rest of the app already assumed.

The safety floor screens "the baby stopped moving" as RED, and the week-20
"when to call" tip says any change from your baby's usual pattern is worth
calling about straight away. Both ask somebody to notice a change from a
baseline nothing in the app let them establish.

The one design rule worth testing: there is no threshold here. No "10 kicks in
2 hours", no target, no green tick for reaching a number. Every guideline says
to ring if the pattern changes, and a target invites the opposite — reach it,
feel reassured, wait. What is stored is what happened, and `usual` is the
person's own median, offered only once it is a pattern rather than a guess.
"""


def _session(client, headers, count, minutes, cid=None):
    body = {"count": count, "minutes": minutes}
    if cid:
        body["client_id"] = cid
    r = client.post("/v1/care/movements", json=body, headers=headers)
    assert r.status_code == 200, r.text
    return r.json()


def test_a_session_is_recorded_and_read_back(client, user):
    _session(client, user["headers"], count=10, minutes=38)

    body = client.get("/v1/care/movements", headers=user["headers"]).json()

    assert len(body["items"]) == 1
    assert body["items"][0]["count"] == 10
    assert body["items"][0]["minutes"] == 38


def test_no_usual_until_it_is_a_pattern(client, user):
    """Two sessions is not a baseline, and presenting one would be a
    fabrication — the same class this app keeps removing."""
    _session(client, user["headers"], 10, 40)
    assert client.get("/v1/care/movements", headers=user["headers"]).json()["usual"] is None

    _session(client, user["headers"], 10, 44)
    assert client.get("/v1/care/movements", headers=user["headers"]).json()["usual"] is None

    _session(client, user["headers"], 10, 36)
    assert client.get("/v1/care/movements", headers=user["headers"]).json()["usual"] is not None


def test_usual_is_a_median_so_one_odd_session_does_not_move_it(client, user):
    """A single very long count is exactly the session someone would want the
    baseline NOT to absorb."""
    for minutes in (40, 42, 44, 300):
        _session(client, user["headers"], 10, minutes)

    usual = client.get("/v1/care/movements", headers=user["headers"]).json()["usual"]

    assert usual["minutes"] <= 50, f"the 300-minute session dragged the baseline to {usual}"
    assert usual["sessions"] == 4


def test_sessions_come_back_newest_first(client, user):
    _session(client, user["headers"], 5, 20)
    _session(client, user["headers"], 9, 30)

    items = client.get("/v1/care/movements", headers=user["headers"]).json()["items"]

    assert items[0]["count"] == 9


def test_a_retry_does_not_double_count(client, user):
    """Same client_id twice is one session. A duplicated session would distort
    the very baseline this exists to establish."""
    _session(client, user["headers"], 10, 40, cid="abc-123")
    _session(client, user["headers"], 10, 40, cid="abc-123")

    assert len(client.get("/v1/care/movements", headers=user["headers"]).json()["items"]) == 1


def test_negative_values_are_refused(client, user):
    assert client.post("/v1/care/movements", json={"count": -1, "minutes": 10},
                       headers=user["headers"]).status_code == 400
    assert client.post("/v1/care/movements", json={"count": 10, "minutes": -5},
                       headers=user["headers"]).status_code == 400


def test_sessions_are_private_to_the_user(client, user):
    _session(client, user["headers"], 10, 40)

    other = client.post("/device/register").json()
    other_headers = {"Authorization": f"Bearer {other['token']}"}

    assert client.get("/v1/care/movements", headers=other_headers).json()["items"] == []


def test_sessions_leave_with_the_account(client):
    """Movement history is health data and rides the deletion fan-out."""
    reg = client.post("/device/register").json()
    h = {"Authorization": f"Bearer {reg['token']}"}
    _session(client, h, 10, 40)

    client.post("/v1/account/delete", json={"confirm": "DELETE MY DATA"}, headers=h)

    # The token is dead with the account, so a fresh one proves the rows went.
    again = client.post("/device/register").json()
    again_h = {"Authorization": f"Bearer {again['token']}"}
    assert client.get("/v1/care/movements", headers=again_h).json()["items"] == []
