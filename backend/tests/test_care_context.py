"""P1: care-context validation, week math, and merge semantics.

The week functions get direct unit tests (they feed the safety gate's context in
P2 — bleeding at week 6 and week 36 mean different things, so this math being
right matters more than it looks).
"""
from datetime import date


def _register(client) -> dict:
    r = client.post("/device/register")
    return {"X-Device-Token": r.json()["device_token"]}


# --- week math (pure functions) ---------------------------------------------

def test_pregnancy_week_at_due_date_is_40():
    import care_context
    today = date(2026, 8, 2)
    assert care_context.pregnancy_week(today, today) == 40


def test_pregnancy_week_16_weeks_out_is_24():
    """The mockup's Maya: week 24 with 16 weeks to go."""
    import care_context
    today = date(2026, 8, 2)
    due = date(2026, 11, 22)  # 112 days = 16 weeks out
    assert care_context.pregnancy_week(due, today) == 24


def test_pregnancy_week_overdue_clamps_at_42():
    import care_context
    today = date(2026, 8, 2)
    due = date(2026, 6, 1)  # 2 months overdue — clamp, don't lie
    assert care_context.pregnancy_week(due, today) == 42


def test_pregnancy_week_partial_week_rounds_up():
    import care_context
    today = date(2026, 8, 2)
    due = today.replace(day=5)  # 3 days out -> ceil(3/7)=1 week to go -> week 39
    assert care_context.pregnancy_week(due, today) == 39


def test_postpartum_week_day_zero_is_week_1():
    import care_context
    today = date(2026, 8, 2)
    assert care_context.postpartum_week(today, today) == 1
    assert care_context.postpartum_week(date(2026, 7, 27), today) == 1  # day 6
    assert care_context.postpartum_week(date(2026, 7, 26), today) == 2  # day 7


# --- endpoint validation ----------------------------------------------------

def test_unknown_stage_rejected(client):
    h = _register(client)
    r = client.put("/care-context", json={"stage": "expecting"}, headers=h)
    assert r.status_code == 422


def test_pregnant_requires_due_date(client):
    h = _register(client)
    r = client.put("/care-context", json={"stage": "pregnant"}, headers=h)
    assert r.status_code == 422
    assert "due_date" in r.json()["detail"]


def test_postpartum_requires_birth_date(client):
    h = _register(client)
    r = client.put("/care-context", json={"stage": "postpartum"}, headers=h)
    assert r.status_code == 422


def test_implausible_due_date_rejected(client):
    h = _register(client)
    r = client.put("/care-context",
                   json={"stage": "pregnant", "due_date": "2031-01-01"}, headers=h)
    assert r.status_code == 422


def test_future_birth_date_rejected(client):
    h = _register(client)
    r = client.put("/care-context",
                   json={"stage": "postpartum", "birth_date": "2030-01-01"}, headers=h)
    assert r.status_code == 422


def test_bad_language_rejected(client):
    h = _register(client)
    r = client.put("/care-context",
                   json={"stage": "trying_to_conceive", "language": "fr"}, headers=h)
    assert r.status_code == 422


def test_roundtrip_returns_computed_week(client):
    h = _register(client)
    due = date.today().toordinal() + 112  # 16 weeks out
    due_iso = date.fromordinal(due).isoformat()
    r = client.put("/care-context", json={"stage": "pregnant", "due_date": due_iso}, headers=h)
    assert r.status_code == 200
    ctx = r.json()["context"]
    assert ctx["week"] == 24
    assert ctx["stage"] == "pregnant"


def test_stage_change_clears_stale_anchor(client):
    """Moving pregnant -> postpartum must not leave the old due_date behind."""
    h = _register(client)
    due_iso = date.fromordinal(date.today().toordinal() + 100).isoformat()
    client.put("/care-context", json={"stage": "pregnant", "due_date": due_iso}, headers=h)
    birth_iso = date.fromordinal(date.today().toordinal() - 10).isoformat()
    r = client.put("/care-context",
                   json={"stage": "postpartum", "birth_date": birth_iso}, headers=h)
    ctx = r.json()["context"]
    assert ctx["due_date"] is None or ctx["due_date"] == ""
    assert ctx["week"] == 2  # day 10


def test_merge_freshest_wins(client):
    """Sign in with BOTH a device context and an account context: the newer
    `updated` timestamp wins."""
    import care_context

    device_headers = _register(client)
    client.put("/care-context", json={"stage": "trying_to_conceive"}, headers=device_headers)

    # Create the account and give it an OLDER context directly in the store.
    r = client.post("/account/dev-login", json={"email": "m@x.com", "name": ""})
    uid, session = r.json()["user_id"], r.json()["session"]
    care_context.upsert(uid, "postpartum", None, "2026-07-01", "Old", "en")
    care_context._conn.execute(
        "UPDATE care_context SET updated=1 WHERE learner_id=?", (uid,))
    care_context._conn.commit()

    # Re-sign-in presenting the device token: device context is fresher -> wins.
    client.post("/account/dev-login", json={"email": "m@x.com", "name": ""},
                headers=device_headers)
    r = client.get("/care-context", headers={"Authorization": f"Bearer {session}"})
    assert r.json()["context"]["stage"] == "trying_to_conceive"
