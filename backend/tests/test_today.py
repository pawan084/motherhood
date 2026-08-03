"""The /today proactive feed: tip rotation, focus rules, time-awareness."""
from datetime import date, datetime


def _register(client) -> dict:
    r = client.post("/device/register")
    return {"X-Device-Token": r.json()["device_token"]}


def _pregnant_at_week(client, h, week: int):
    days_to_due = (40 - week) * 7
    due = date.fromordinal(date.today().toordinal() + days_to_due).isoformat()
    client.put("/care-context", json={"stage": "pregnant", "due_date": due}, headers=h)


def test_no_context_is_empty_but_valid(client):
    h = _register(client)
    body = client.get("/today?hour=10", headers=h).json()
    assert body["context"] is None and body["focus"] == [] and body["tip"] is None


def test_context_tip_and_size(client):
    h = _register(client)
    _pregnant_at_week(client, h, 8)
    body = client.get("/today?hour=10", headers=h).json()
    ctx = body["context"]
    assert ctx["week"] == 8 and ctx["total_weeks"] == 40
    assert ctx["size"]["label"] == "a raspberry"
    assert 1 <= ctx["day_in_week"] <= 7
    assert body["tip"]["text"]  # first-trimester pool
    # Deterministic within the day.
    again = client.get("/today?hour=15", headers=h).json()
    assert again["tip"] == body["tip"]


def test_slots(client):
    h = _register(client)
    assert client.get("/today?hour=8", headers=h).json()["slot"] == "morning"
    assert client.get("/today?hour=13", headers=h).json()["slot"] == "afternoon"
    assert client.get("/today?hour=20", headers=h).json()["slot"] == "evening"


def test_mood_focus_appears_then_clears(client):
    h = _register(client)
    _pregnant_at_week(client, h, 8)
    kinds = [f["kind"] for f in client.get("/today?hour=10", headers=h).json()["focus"]]
    assert "mood_checkin" in kinds
    # Early morning: no nagging yet.
    kinds_early = [f["kind"] for f in client.get("/today?hour=7", headers=h).json()["focus"]]
    assert "mood_checkin" not in kinds_early
    # Logged -> the card disappears.
    client.post("/moods", json={"mood": "okay"}, headers=h)
    kinds_after = [f["kind"] for f in client.get("/today?hour=10", headers=h).json()["focus"]]
    assert "mood_checkin" not in kinds_after


def test_water_pace_focus(client):
    h = _register(client)
    _pregnant_at_week(client, h, 8)
    client.post("/moods", json={"mood": "okay"}, headers=h)  # clear mood card
    client.get("/reminders", headers=h)  # seed defaults
    kinds = [f["kind"] for f in client.get("/today?hour=15", headers=h).json()["focus"]]
    assert "water_pace" in kinds
    # Not before midday.
    kinds_am = [f["kind"] for f in client.get("/today?hour=10", headers=h).json()["focus"]]
    assert "water_pace" not in kinds_am


def test_appointment_soon_outranks_everything(client):
    h = _register(client)
    _pregnant_at_week(client, h, 8)
    client.post("/appointments", json={
        "title": "Antenatal check",
        "when_ts": datetime.now().timestamp() + 8 * 3600}, headers=h)
    focus = client.get("/today?hour=15", headers=h).json()["focus"]
    assert focus[0]["kind"] == "appointment_soon"
    assert len(focus) <= 2  # capped


def test_milestone_week_focus(client):
    h = _register(client)
    _pregnant_at_week(client, h, 12)  # first-scan week
    client.post("/moods", json={"mood": "okay"}, headers=h)
    kinds = [f["kind"] for f in client.get("/today?hour=10", headers=h).json()["focus"]]
    assert "milestone_week" in kinds


def test_evening_wrapup(client):
    h = _register(client)
    _pregnant_at_week(client, h, 8)
    client.post("/moods", json={"mood": "okay"}, headers=h)
    client.get("/reminders", headers=h)
    kinds = [f["kind"] for f in client.get("/today?hour=20", headers=h).json()["focus"]]
    assert "evening_wrapup" in kinds or "water_pace" in kinds  # capped at 2, ranked


def test_water_and_wrapup_never_nag_about_the_same_item(client):
    """Dedupe: when water_pace fires and water is the ONLY open item, the
    evening wrap-up stays silent instead of restating it."""
    h = _register(client)
    _pregnant_at_week(client, h, 8)
    client.post("/moods", json={"mood": "okay"}, headers=h)
    reminders = client.get("/reminders", headers=h).json()["reminders"]
    # Finish everything except water (leave water at 0 so water_pace fires).
    for r in reminders:
        if r["kind"] != "water":
            for _ in range(r["target_per_day"]):
                client.post(f"/reminders/{r['id']}/tick", headers=h)
    kinds = [f["kind"] for f in client.get("/today?hour=20", headers=h).json()["focus"]]
    assert "water_pace" in kinds
    assert "evening_wrapup" not in kinds
