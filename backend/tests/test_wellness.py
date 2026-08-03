"""P11 part 2: wellness — moods, habit reminders, video catalog."""
import sys
from datetime import date


def _register(client) -> dict:
    r = client.post("/device/register")
    return {"X-Device-Token": r.json()["device_token"]}


def _pregnant_at_week(client, h, week: int):
    days_to_due = (40 - week) * 7
    due = date.fromordinal(date.today().toordinal() + days_to_due).isoformat()
    client.put("/care-context", json={"stage": "pregnant", "due_date": due}, headers=h)


# ── moods ────────────────────────────────────────────────────────────────────

def test_mood_upserts_per_day(client):
    h = _register(client)
    assert client.post("/moods", json={"mood": "tired"}, headers=h).status_code == 200
    assert client.post("/moods", json={"mood": "okay"}, headers=h).status_code == 200
    moods = client.get("/moods", headers=h).json()["moods"]
    assert len(moods) == 1                      # one row per day
    assert moods[0]["mood"] == "okay"           # latest wins
    assert moods[0]["day"] == date.today().isoformat()


def test_mood_catalog_enforced(client):
    h = _register(client)
    assert client.post("/moods", json={"mood": "ecstatic"}, headers=h).status_code == 422


def test_moods_owner_scoped(client):
    a, b = _register(client), _register(client)
    client.post("/moods", json={"mood": "great"}, headers=a)
    assert client.get("/moods", headers=b).json()["moods"] == []


# ── reminders ────────────────────────────────────────────────────────────────

def test_defaults_seeded_once_and_deletion_sticks(client):
    h = _register(client)
    reminders = client.get("/reminders", headers=h).json()["reminders"]
    assert [r["kind"] for r in reminders] == ["water", "exercise"]
    water = next(r for r in reminders if r["kind"] == "water")
    assert water["target_per_day"] == 8 and water["ticks_today"] == 0

    # Delete a default; the next list must NOT re-seed it.
    assert client.delete(f"/reminders/{water['id']}", headers=h).status_code == 200
    again = client.get("/reminders", headers=h).json()["reminders"]
    assert [r["kind"] for r in again] == ["exercise"]


def test_water_ticks_count_up_and_cap_at_target(client):
    h = _register(client)
    water = next(r for r in client.get("/reminders", headers=h).json()["reminders"]
                 if r["kind"] == "water")
    for i in range(1, 9):
        body = client.post(f"/reminders/{water['id']}/tick", headers=h).json()
        assert body["ticks_today"] == i
    assert body["done_today"] is True
    # An extra tap past done is a no-op, not a 9/8.
    body = client.post(f"/reminders/{water['id']}/tick", headers=h).json()
    assert body["ticks_today"] == 8


def test_custom_reminder_crud_and_validation(client):
    h = _register(client)
    assert client.post("/reminders", json={"title": " ", "kind": "custom"},
                       headers=h).status_code == 422
    assert client.post("/reminders", json={"title": "X", "kind": "medicine"},
                       headers=h).status_code == 422  # medicine rows are read-only merges
    rid = client.post("/reminders", json={"title": "Stretch", "kind": "custom"},
                      headers=h).json()["id"]
    body = client.post(f"/reminders/{rid}/tick", headers=h).json()
    assert body["done_today"] is True  # default target 1
    assert client.delete(f"/reminders/{rid}", headers=h).status_code == 200


def test_reminders_owner_scoped(client):
    a, b = _register(client), _register(client)
    water = next(r for r in client.get("/reminders", headers=a).json()["reminders"]
                 if r["kind"] == "water")
    assert client.post(f"/reminders/{water['id']}/tick", headers=b).status_code == 404
    assert client.delete(f"/reminders/{water['id']}", headers=b).status_code == 404


def test_medicines_merge_into_reminders(client):
    h = _register(client)
    mid = client.post("/medicines", json={"name": "Prenatal vitamin",
                                          "time_of_day": "20:00"}, headers=h).json()["id"]
    merged = client.get("/reminders?include_medicines=true", headers=h).json()["reminders"]
    med = next(r for r in merged if r["kind"] == "medicine")
    assert med["id"] == mid                       # the REAL medicine id
    assert "Prenatal vitamin" in med["title"] and "20:00" in med["title"]
    assert med["done_today"] is False
    # Write path stays on the medicines endpoint; the merge reflects it.
    client.post(f"/medicines/{mid}/taken", headers=h)
    merged = client.get("/reminders?include_medicines=true", headers=h).json()["reminders"]
    assert next(r for r in merged if r["kind"] == "medicine")["done_today"] is True
    # Without the flag, medicines stay out.
    plain = client.get("/reminders", headers=h).json()["reminders"]
    assert all(r["kind"] != "medicine" for r in plain)


# ── videos ───────────────────────────────────────────────────────────────────

def test_videos_require_auth(client):
    assert client.get("/videos").status_code == 401
    assert client.get("/videos/suggested").status_code == 401


def test_catalog_orders_own_stage_first(client):
    h = _register(client)
    _pregnant_at_week(client, h, 24)
    videos = client.get("/videos", headers=h).json()["videos"]
    assert len(videos) >= 12
    first_other = next(i for i, v in enumerate(videos) if v["stage"] != "pregnant")
    assert all(v["stage"] == "pregnant" for v in videos[:first_other])


def test_suggested_picks_tightest_band_for_week(client):
    h = _register(client)
    _pregnant_at_week(client, h, 24)
    video = client.get("/videos/suggested", headers=h).json()["video"]
    assert video is not None and video["stage"] == "pregnant"
    start, end = map(int, video["week_band"].split("-"))
    assert start <= 24 <= end


def test_suggested_clamps_past_last_band(client):
    h = _register(client)
    birth = date.fromordinal(date.today().toordinal() - 500).isoformat()  # ~71 weeks pp
    # 500 days is past the 3-year bound? no — well inside; week ~72 beyond all pp bands
    client.put("/care-context", json={"stage": "postpartum", "birth_date": birth}, headers=h)
    video = client.get("/videos/suggested", headers=h).json()["video"]
    assert video is not None and video["stage"] == "postpartum"


def test_suggested_for_ttc_and_no_context(client):
    h = _register(client)
    assert client.get("/videos/suggested", headers=h).json()["video"] is None
    client.put("/care-context", json={"stage": "trying_to_conceive"}, headers=h)
    video = client.get("/videos/suggested", headers=h).json()["video"]
    assert video is not None and video["stage"] == "trying_to_conceive"


def test_video_seed_once_admin_edit_survives(client):
    wellness = sys.modules["wellness"]
    wellness._conn.execute(
        "UPDATE videos SET title='Edited' WHERE id='vid-preg-mid-movement'")
    wellness._conn.commit()
    wellness._conn = None
    wellness.init()
    h = _register(client)
    _pregnant_at_week(client, h, 20)
    videos = client.get("/videos", headers=h).json()["videos"]
    assert any(v["title"] == "Edited" for v in videos)


# ── identity flows ───────────────────────────────────────────────────────────

def test_merge_moves_wellness_and_account_mood_wins(client):
    import wellness
    h = _register(client)
    client.post("/moods", json={"mood": "tired"}, headers=h)
    water = next(r for r in client.get("/reminders", headers=h).json()["reminders"]
                 if r["kind"] == "water")
    client.post(f"/reminders/{water['id']}/tick", headers=h)

    # Account already checked in today: its entry must survive the merge.
    r = client.post("/account/dev-login", json={"email": "m@x.com", "name": ""})
    uid, session = r.json()["user_id"], {"Authorization": f"Bearer {r.json()['session']}"}
    wellness._conn.execute(
        "INSERT INTO moods (learner_id, day, mood, ts) VALUES (?,?, 'great', 1)",
        (uid, date.today().isoformat()))
    wellness._conn.commit()

    client.post("/account/dev-login", json={"email": "m@x.com", "name": ""}, headers=h)
    moods = client.get("/moods", headers=session).json()["moods"]
    assert [m["mood"] for m in moods] == ["great"]  # account's own entry kept
    reminders = client.get("/reminders", headers=session).json()["reminders"]
    water = next(r_ for r_ in reminders if r_["kind"] == "water")
    assert water["ticks_today"] == 1                # device ticks rode along
    # No duplicate defaults from the account's own first list.
    assert [r_["kind"] for r_ in reminders] == ["water", "exercise"]


def test_erase_and_export_cover_wellness(client):
    h = _register(client)
    client.post("/moods", json={"mood": "low"}, headers=h)
    client.get("/reminders", headers=h)  # seeds defaults

    data = client.get("/export", headers=h).json()
    assert data["moods"][0]["mood"] == "low"
    assert len(data["reminders"]) == 2

    client.delete("/learner-data", headers=h)
    assert client.get("/moods", headers=h).json()["moods"] == []
    # Seed marker erased too -> a fresh start re-seeds defaults.
    assert len(client.get("/reminders", headers=h).json()["reminders"]) == 2
