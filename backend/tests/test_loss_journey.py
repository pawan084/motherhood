"""After a loss — a stage defined mostly by what it does not do.

Before this existed, somebody who had a miscarriage had two options: "Trying to
conceive", which offers conception content, or "Just exploring", which says
nothing happened. Both are wrong and one is cruel.

Almost every test here is an absence. That is the feature: no week count, no
countdown, no baby content, no video library written for people who are still
pregnant, and nothing that asks about trying again. Absences are exactly what
regresses silently when somebody later adds a cheerful default, so they are
asserted rather than assumed.
"""
from app.domains import content
from app.domains import videos


def _as_loss(client, headers):
    r = client.post("/v1/onboarding", json={"journey": "loss"}, headers=headers)
    assert r.status_code == 200, r.text
    return r


def test_loss_is_a_journey_the_backend_accepts(client, user):
    _as_loss(client, user["headers"])

    assert client.get("/v1/today", headers=user["headers"]).json()["journey"] == "loss"


def test_no_week_count_anywhere(client, user):
    """The single most important absence. A pregnancy week is a countdown, and
    there is nothing here to count down to."""
    client.post("/v1/onboarding", json={"journey": "pregnant", "weeks": 24},
                headers=user["headers"])
    _as_loss(client, user["headers"])

    today = client.get("/v1/today", headers=user["headers"]).json()
    journey = client.get("/v1/journey", headers=user["headers"]).json()

    assert today["weeks"] is None, "a week survived the switch to loss"
    assert journey["weeks"] is None
    assert "call_tip" not in journey, "pregnancy call tips reached a loss journey"


def test_the_video_library_is_empty_rather_than_filtered(client, user):
    """The on-demand catalogue is pregnancy symptoms, labour and newborn care.

    Falling through to it would open this screen with "Nausea and Vomiting in
    Pregnancy". No category filter makes that catalogue safe here; it was
    written for people who are still pregnant.
    """
    _as_loss(client, user["headers"])

    body = client.get("/v1/videos", headers=user["headers"]).json()

    assert body["items"] == []
    assert body["week_video"] is None


def test_the_copy_asks_nothing_of_the_person(client, user):
    """No "when you're ready", no "trying again", no next step.

    Every other journey's copy points somewhere. This one must not, because a
    nudge toward a next step is the thing an app should not be doing this week.
    """
    body = content.journey_content("loss")

    text = " ".join([body["title"], body["this_week"], body["body"]]).lower()
    for phrase in ("try again", "trying again", "next time", "when you're ready",
                   "when you are ready", "conceive"):
        assert phrase not in text, f"loss copy nudges: {phrase!r}"


def test_todays_one_proposal_can_be_ignored_without_cost(client, user):
    """Today proposes one thing to everybody. Here it has to be the one thing
    that asks nothing — not a check-in, which is a question."""
    action = content.next_action("loss")

    assert action["tool"] == "wellness"
    assert "check-in" not in action["title"].lower()


def test_switching_away_restores_the_other_journeys(client, user):
    """Loss is not a trap. Somebody who moves on, or who chose it by mistake,
    gets the full app back."""
    _as_loss(client, user["headers"])
    client.post("/v1/onboarding", json={"journey": "pregnant", "weeks": 30},
                headers=user["headers"])

    today = client.get("/v1/today", headers=user["headers"]).json()

    assert today["journey"] == "pregnant"
    assert today["weeks"] == 30
    assert client.get("/v1/videos", headers=user["headers"]).json()["items"]


def test_the_safety_gate_is_unchanged(client, user):
    """Physical recovery after a loss carries real risk. Nothing about the
    gentler copy may soften what the gate does with heavy bleeding."""
    _as_loss(client, user["headers"])

    r = client.post("/v1/chat/turn",
                    json={"message": "I have heavy bleeding and feel faint", "history": []},
                    headers=user["headers"]).json()

    assert r["safety"]["level"] == "red"
    assert r["urgent"] is True
    assert r["reply"] is None


def test_care_still_works(client, user):
    """The practical half of the app stays. Appointments and medicines do not
    stop mattering — often they are the reason somebody keeps the app."""
    _as_loss(client, user["headers"])

    r = client.post("/v1/care/reminders",
                    json={"title": "take iron", "time": "8:00 PM"},
                    headers=user["headers"])

    assert r.status_code == 200, r.text


def test_loss_topics_are_absent_from_the_catalogue_for_now():
    """A record of why the library is empty rather than curated: nothing in the
    catalogue was written for this. When that changes, this test fails and the
    empty-library rule should be revisited."""
    videos._load_catalog()

    loss_ish = [t for t in videos._TOPICS
                if "loss" in t["title"].lower() or "miscarriage" in t["title"].lower()]

    assert loss_ish == [], f"loss content now exists: {[t['id'] for t in loss_ish]}"
