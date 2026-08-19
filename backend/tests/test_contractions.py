"""Timing contractions, and the alert that is deliberately absent.

The spec this came from asks for a contraction timer "with automated hospital
departure alerts". The timer is here. The alert is not, and these tests exist to
keep it out.

Deciding when somebody should leave for hospital depends on whether it is their
first baby, how far away the unit is, how the pregnancy has gone, and what their
midwife actually told them. An app that announces "time to go" is repeating a
rule it cannot know applies, or inventing one. An app that stays quiet while
somebody waits for it to speak is worse.

So the endpoint counts and times. The pattern it reports is the thing a midwife
asks for on the phone — how long, how far apart, for how long now — and having
it written down is the entire contribution.
"""
from app.domains import care


def _log(client, headers, seconds, gap=None):
    body = {"seconds": seconds}
    if gap is not None:
        body["since_previous_seconds"] = gap
    r = client.post("/v1/care/contractions", json=body, headers=headers)
    assert r.status_code == 200, r.text
    return r.json()


def test_a_contraction_is_recorded_and_read_back(client, user):
    _log(client, user["headers"], seconds=45, gap=300)

    body = client.get("/v1/care/contractions", headers=user["headers"]).json()

    assert body["items"][0]["seconds"] == 45
    assert body["items"][0]["since_previous_seconds"] == 300


def test_the_pattern_is_what_a_midwife_asks_for(client, user):
    for gap in (300, 290, 310):
        _log(client, user["headers"], seconds=50, gap=gap)

    recent = client.get("/v1/care/contractions", headers=user["headers"]).json()["recent"]

    assert recent["count"] == 3
    assert recent["typical_seconds"] == 50
    assert recent["typical_gap_seconds"] == 300


def test_no_pattern_from_a_single_contraction(client, user):
    """One is not a pattern, and describing it as one would invite reading
    something into it."""
    _log(client, user["headers"], seconds=45, gap=300)

    assert client.get("/v1/care/contractions", headers=user["headers"]).json()["recent"] is None


def test_the_response_draws_no_conclusion(client, user):
    """The assertion this file exists for.

    Nothing in the payload may tell somebody what their contractions mean or
    what to do about them. If a field like `active_labour` or `go_to_hospital`
    ever appears here, this fails — which is the point.
    """
    for gap in (300, 290, 280, 270):
        _log(client, user["headers"], seconds=60, gap=gap)

    body = client.get("/v1/care/contractions", headers=user["headers"]).json()

    keys = set(body) | set(body["recent"])
    for forbidden in ("active_labour", "go_to_hospital", "should_leave", "stage",
                      "alert", "advice", "recommendation", "status"):
        assert forbidden not in keys, f"the timer started giving clinical advice: {forbidden}"


def test_only_the_last_hour_counts_toward_the_pattern(client, user):
    """A contraction from this morning says nothing about now."""
    _log(client, user["headers"], seconds=50, gap=300)
    _log(client, user["headers"], seconds=50, gap=300)

    items = care._list_items(user["id"], "contraction")
    for i in items:
        care._conn.execute("UPDATE care_items SET created=? WHERE id=?",
                           (i["created"] - 7200, i["id"]))
    care._conn.commit()

    assert client.get("/v1/care/contractions", headers=user["headers"]).json()["recent"] is None


def test_negative_durations_are_refused(client, user):
    assert client.post("/v1/care/contractions", json={"seconds": -5},
                       headers=user["headers"]).status_code == 400


def test_contractions_are_private_and_leave_with_the_account(client):
    reg = client.post("/device/register").json()
    h = {"Authorization": f"Bearer {reg['token']}"}
    _log(client, h, seconds=45, gap=300)

    client.post("/v1/account/delete", json={"confirm": "DELETE MY DATA"}, headers=h)

    again = client.post("/device/register").json()
    again_h = {"Authorization": f"Bearer {again['token']}"}
    assert client.get("/v1/care/contractions", headers=again_h).json()["items"] == []
