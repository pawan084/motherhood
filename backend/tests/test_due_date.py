"""A due date, because a reported week drifts and a date does not.

Onboarding asked "how many weeks are you?" and stored the answer with a
timestamp, and `current_weeks` advanced it. That works, but it is a measurement
of a moment being carried forward by assumption: it assumes no time passed
between the scan and the typing, and it needs correcting whenever that
assumption was wrong.

A due date is a fixed point. The week falls out of it exactly, for ever, with
nobody coming back to fix anything — and it is the number a clinician actually
gave them, so it can be checked against their notes rather than remembered.

The property worth testing is the one that motivated it: a stored due date
gives the right week weeks later, where a stored week only gives the right week
if the advance guessed correctly.
"""
import datetime

import care


def _due_in(weeks_pregnant: int) -> str:
    """The due date of somebody who is `weeks_pregnant` weeks along today."""
    return (datetime.date.today()
            + datetime.timedelta(days=(40 - weeks_pregnant) * 7)).isoformat()


def test_the_week_falls_out_of_the_date(client, user):
    r = client.patch("/v1/care/context", json={"due_date": _due_in(31)},
                     headers=user["headers"])

    assert r.status_code == 200, r.text
    assert r.json()["weeks"] == 31


def test_it_does_not_drift(client, user):
    """The whole point.

    The same stored date read a month later gives the week a month later —
    without anyone re-entering anything, and without the app assuming how long
    ago the number was measured.
    """
    due = _due_in(20)
    client.patch("/v1/care/context", json={"due_date": due}, headers=user["headers"])

    later = datetime.datetime.now() + datetime.timedelta(days=28)
    assert care.weeks_from_due_date(due, now=later.timestamp()) == 24


def test_the_date_wins_over_a_reported_week(client, user):
    """Both can be present — somebody typed a week at onboarding and added a
    date later. The one that cannot drift is the one that counts."""
    client.patch("/v1/care/context", json={"weeks": 12}, headers=user["headers"])

    body = client.patch("/v1/care/context", json={"due_date": _due_in(31)},
                        headers=user["headers"]).json()

    assert body["weeks"] == 31
    # The typed week is still theirs, so an editor can still show it.
    assert body["weeks_reported"] == 12


def test_clearing_the_date_falls_back_to_the_reported_week(client, user):
    """Removing a date must not leave somebody with no week at all."""
    client.patch("/v1/care/context", json={"weeks": 12}, headers=user["headers"])
    client.patch("/v1/care/context", json={"due_date": _due_in(31)}, headers=user["headers"])

    body = client.patch("/v1/care/context", json={"due_date": ""},
                        headers=user["headers"]).json()

    assert body["due_date"] is None
    assert body["weeks"] == 12


def test_a_date_years_away_is_refused(client, user):
    """A typo, not a pregnancy. Deriving a negative week from it would be worse
    than refusing it."""
    r = client.patch("/v1/care/context", json={"due_date": "2099-01-01"},
                     headers=user["headers"])

    assert r.status_code == 400


def test_a_date_long_past_is_refused(client, user):
    r = client.patch("/v1/care/context", json={"due_date": "2001-05-05"},
                     headers=user["headers"])

    assert r.status_code == 400


def test_nonsense_is_refused_rather_than_ignored(client, user):
    assert client.patch("/v1/care/context", json={"due_date": "next tuesday"},
                        headers=user["headers"]).status_code == 400


def test_today_and_journey_both_use_it(client, user):
    client.post("/v1/onboarding", json={"journey": "pregnant"}, headers=user["headers"])
    client.patch("/v1/care/context", json={"due_date": _due_in(31)}, headers=user["headers"])

    assert client.get("/v1/today", headers=user["headers"]).json()["weeks"] == 31
    assert client.get("/v1/journey", headers=user["headers"]).json()["weeks"] == 31


def test_a_non_pregnant_journey_still_gets_no_week(client, user):
    """A due date does not override the rule that only a pregnancy has weeks —
    somebody who moves to postpartum keeps their date and stops being counted."""
    client.patch("/v1/care/context", json={"due_date": _due_in(31)}, headers=user["headers"])
    client.post("/v1/onboarding", json={"journey": "postpartum"}, headers=user["headers"])

    assert client.get("/v1/today", headers=user["headers"]).json()["weeks"] is None
