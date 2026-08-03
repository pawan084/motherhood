"""The /today proactive feed: tip rotation, focus rules, time-awareness."""
from datetime import date, datetime, timedelta


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


# ── review-batch: date param, streak, recap, countdowns ──────────────────────

def test_device_date_param_drives_tip_and_validation(client):
    h = _register(client)
    _pregnant_at_week(client, h, 8)
    t1 = client.get("/today?hour=10&date=2026-08-03", headers=h).json()["tip"]["text"]
    t2 = client.get("/today?hour=10&date=2026-08-04", headers=h).json()["tip"]["text"]
    assert t1 != t2  # adjacent days never repeat (pool >= 3)
    assert client.get("/today?date=nope", headers=h).status_code == 422


def test_streak_counts_full_days_and_forgives_today(client):
    h = _register(client)
    _pregnant_at_week(client, h, 8)
    reminders = client.get("/reminders", headers=h).json()["reminders"]
    body = client.get("/today?hour=10", headers=h).json()
    assert body["context"]["streak"] == 0
    for r in reminders:
        for _ in range(r["target_per_day"]):
            client.post(f"/reminders/{r['id']}/tick", headers=h)
    body = client.get("/today?hour=10", headers=h).json()
    assert body["context"]["streak"] == 1
    # Tomorrow morning (nothing ticked yet): the streak of 1 holds — an
    # incomplete today doesn't break the run.
    from datetime import date as _d, timedelta as _td
    tomorrow = (_d.today() + _td(days=1)).isoformat()
    body = client.get(f"/today?hour=8&date={tomorrow}", headers=h).json()
    assert body["context"]["streak"] == 1


def test_appointment_focus_carries_when_ts_and_countdown(client):
    h = _register(client)
    _pregnant_at_week(client, h, 8)
    client.post("/moods", json={"mood": "okay"}, headers=h)
    ts = (datetime.now() + timedelta(hours=5)).timestamp()
    client.post("/appointments", json={"title": "Scan", "when_ts": ts}, headers=h)
    focus = client.get("/today?hour=10", headers=h).json()["focus"]
    apt = next(f for f in focus if f["kind"] == "appointment_soon")
    assert apt["when_ts"] == ts
    assert "In about" in apt["body"]


def test_sunday_recap_present_only_on_sunday(client):
    h = _register(client)
    _pregnant_at_week(client, h, 8)
    # 2026-08-09 is a Sunday; 2026-08-05 is a Wednesday.
    sunday = client.get("/today?hour=10&date=2026-08-09", headers=h).json()
    assert "recap" in sunday and "water_avg" in sunday["recap"]
    weekday = client.get("/today?hour=10&date=2026-08-05", headers=h).json()
    assert "recap" not in weekday


# ── deferred batch: learning, language, cm ───────────────────────────────────

def test_size_includes_length_cm_and_hi_falls_back(client):
    h = _register(client)
    days_to_due = (40 - 8) * 7
    due = date.fromordinal(date.today().toordinal() + days_to_due).isoformat()
    client.put("/care-context", json={"stage": "pregnant", "due_date": due,
                                      "language": "hi"}, headers=h)
    body = client.get("/today?hour=10", headers=h).json()
    assert body["context"]["size"]["length_cm"] == 1.6  # week 8
    assert body["tip"]["text"]  # hi pool empty -> English fallback


def test_focus_taps_boost_acted_on_kinds(client):
    h = _register(client)
    _pregnant_at_week(client, h, 12)  # milestone week: mood + milestone both p2
    kinds = [f["kind"] for f in client.get("/today?hour=10", headers=h).json()["focus"]]
    assert kinds[0] == "mood_checkin"  # insertion order within equal priority
    # Acting on milestones teaches the ranker to lead with them.
    for _ in range(3):
        client.post("/today/focus-tap", json={"kind": "milestone_week"}, headers=h)
    kinds = [f["kind"] for f in client.get("/today?hour=10", headers=h).json()["focus"]]
    assert kinds[0] == "milestone_week"
    assert client.post("/today/focus-tap", json={"kind": "nope"},
                       headers=h).status_code == 422
