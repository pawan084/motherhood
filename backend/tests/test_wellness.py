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
    assert med["title"] == "Prenatal vitamin"
    assert med["detail"] == "20:00"                # dose/time ride separately
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
    assert len(videos) >= 6
    first_other = next(i for i, v in enumerate(videos) if v["stage"] != "pregnant")
    assert all(v["stage"] == "pregnant" for v in videos[:first_other])


def test_suggested_rotates_daily_within_stage(client):
    """The pick is stage-scoped, deterministic for the day (two calls agree),
    and covers the whole stage catalog across days — the daily-rotation
    contract the Me tab's card depends on."""
    h = _register(client)
    _pregnant_at_week(client, h, 24)
    video = client.get("/videos/suggested", headers=h).json()["video"]
    assert video is not None and video["stage"] == "pregnant"
    again = client.get("/videos/suggested", headers=h).json()["video"]
    assert again["id"] == video["id"]
    # The day index walks every stage row as dates advance.
    wellness = sys.modules["wellness"]
    ids = {r[0] for r in wellness._conn.execute(
        "SELECT id FROM videos WHERE stage='pregnant'")}
    assert len(ids) > 1  # rotation is real, not a single-row no-op
    from datetime import date as _date
    rows = wellness._conn.execute(
        "SELECT id FROM videos WHERE stage='pregnant'"
        " ORDER BY week_start, id").fetchall()
    picks = {rows[(_date.today().toordinal() + d) % len(rows)][0]
             for d in range(len(rows))}
    assert picks == ids


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
        "UPDATE videos SET title='Edited' WHERE id='own-dodont'")
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


# ── report ───────────────────────────────────────────────────────────────────

def test_report_shape_and_history(client):
    h = _register(client)
    mid = client.post("/medicines", json={"name": "Iron"}, headers=h).json()["id"]
    client.post("/moods", json={"mood": "great"}, headers=h)
    water = next(r for r in client.get("/reminders", headers=h).json()["reminders"]
                 if r["kind"] == "water")
    client.post(f"/reminders/{water['id']}/tick", headers=h)
    client.post(f"/reminders/{water['id']}/tick", headers=h)
    client.post(f"/medicines/{mid}/taken", headers=h)

    report = client.get("/report?days=7", headers=h).json()
    assert report["days"] == 7
    assert report["moods"][-1]["mood"] == "great"

    by_kind = {r["kind"]: r for r in report["reminders"]}
    assert set(by_kind) == {"water", "exercise", "medicine"}
    today = date.today().isoformat()
    water_today = next(d for d in by_kind["water"]["days"] if d["day"] == today)
    assert water_today["ticks"] == 2 and water_today["done"] is False
    med_today = next(d for d in by_kind["medicine"]["days"] if d["day"] == today)
    assert med_today["done"] is True
    assert by_kind["exercise"]["days"] == []  # never ticked -> empty series


def test_report_owner_scoped_and_windowed(client):
    a, b = _register(client), _register(client)
    client.post("/moods", json={"mood": "low"}, headers=a)
    report_b = client.get("/report", headers=b).json()
    assert report_b["moods"] == []
    assert client.get("/report?days=3", headers=a).status_code == 422  # ge=7


# ── review-batch endpoints (untick, target, watched, stage seeds) ────────────

def test_untick_decrements_and_floors(client):
    h = _register(client)
    water = next(r for r in client.get("/reminders", headers=h).json()["reminders"]
                 if r["kind"] == "water")
    client.post(f"/reminders/{water['id']}/tick", headers=h)
    client.post(f"/reminders/{water['id']}/tick", headers=h)
    out = client.post(f"/reminders/{water['id']}/untick", headers=h).json()
    assert out["ticks_today"] == 1
    # Floors at zero — undoing more than was done is a no-op, not negative.
    client.post(f"/reminders/{water['id']}/untick", headers=h)
    out = client.post(f"/reminders/{water['id']}/untick", headers=h).json()
    assert out["ticks_today"] == 0


def test_untick_is_owner_scoped(client):
    h1, h2 = _register(client), _register(client)
    water = next(r for r in client.get("/reminders", headers=h1).json()["reminders"]
                 if r["kind"] == "water")
    assert client.post(f"/reminders/{water['id']}/untick", headers=h2).status_code == 404


def test_water_target_editable_and_clamped(client):
    h = _register(client)
    water = next(r for r in client.get("/reminders", headers=h).json()["reminders"]
                 if r["kind"] == "water")
    out = client.post(f"/reminders/{water['id']}/target",
                      json={"target_per_day": 10}, headers=h).json()
    assert out["target_per_day"] == 10
    assert client.post(f"/reminders/{water['id']}/target",
                       json={"target_per_day": 99}, headers=h).json()["target_per_day"] == 24
    h2 = _register(client)
    assert client.post(f"/reminders/{water['id']}/target",
                       json={"target_per_day": 5}, headers=h2).status_code == 404


def test_medicine_untaken_undoes_today(client):
    h = _register(client)
    mid = client.post("/medicines", json={"name": "Iron"}, headers=h).json()["id"]
    client.post(f"/medicines/{mid}/taken", headers=h)
    client.post(f"/medicines/{mid}/untaken", headers=h)
    meds = client.get("/medicines", headers=h).json()["medicines"]
    assert meds[0]["taken_today"] is False


def test_postpartum_gets_recovery_seeds(client):
    h = _register(client)
    birth = date.today().isoformat()
    client.put("/care-context", json={"stage": "postpartum", "birth_date": birth},
               headers=h)
    titles = [r["title"] for r in client.get("/reminders", headers=h).json()["reminders"]]
    assert "Rest when baby rests" in titles
    assert "Gentle pelvic floor" in titles
    assert "Drink water" in titles


def test_watched_drives_unwatched_first_rotation(client):
    h = _register(client)
    _pregnant_at_week(client, h, 24)
    sys_wellness = sys.modules["wellness"]
    all_ids = [r[0] for r in sys_wellness._conn.execute(
        "SELECT id FROM videos WHERE stage='pregnant' ORDER BY week_start, id")]
    suggested = client.get("/videos/suggested", headers=h).json()["video"]
    # Watch today's pick: tomorrow's pool shrinks, today re-picks from the rest.
    client.post(f"/videos/{suggested['id']}/watched", headers=h)
    after = client.get("/videos/suggested", headers=h).json()["video"]
    assert after["id"] != suggested["id"]
    # Watch everything: the rotation falls back to the full catalog.
    for vid in all_ids:
        client.post(f"/videos/{vid}/watched", headers=h)
    assert client.get("/videos/suggested", headers=h).json()["video"] is not None
    # Unknown id is a 404, not a silent write.
    assert client.post("/videos/nope/watched", headers=h).status_code == 404


def test_watched_and_feedback_survive_merge_and_export(client):
    h = _register(client)
    _pregnant_at_week(client, h, 8)
    suggested = client.get("/videos/suggested", headers=h).json()["video"]
    client.post(f"/videos/{suggested['id']}/watched", headers=h)
    client.post("/today/tip-feedback", json={"tip_id": "pregnant-1", "helpful": True},
                headers=h)
    export = client.get("/export", headers=h).json()
    assert export["videos_watched"][0]["video_id"] == suggested["id"]
    assert export["tip_feedback"][0]["tip_id"] == "pregnant-1"


# ── likes + star ratings (real aggregates only) ──────────────────────────────

def test_like_toggles_and_counts_across_learners(client):
    a, b = _register(client), _register(client)
    for h in (a, b):
        client.put("/care-context", json={"stage": "trying_to_conceive"}, headers=h)
    out = client.post("/videos/own-stat/like", headers=a).json()
    assert out == {"liked": True, "like_count": 1}
    out = client.post("/videos/own-stat/like", headers=b).json()
    assert out["like_count"] == 2
    # Second tap unlikes; the other learner's like stays.
    out = client.post("/videos/own-stat/like", headers=a).json()
    assert out == {"liked": False, "like_count": 1}
    video = client.get("/videos/suggested", headers=b).json()["video"]
    assert video["like_count"] == 1 and video["my_like"] is True
    assert client.post("/videos/nope/like", headers=a).status_code == 404


def test_ratings_average_truthfully(client):
    a, b = _register(client), _register(client)
    for h in (a, b):
        client.put("/care-context", json={"stage": "trying_to_conceive"}, headers=h)
    assert client.post("/videos/own-stat/rate", json={"stars": 9},
                       headers=a).status_code == 422
    client.post("/videos/own-stat/rate", json={"stars": 5}, headers=a)
    out = client.post("/videos/own-stat/rate", json={"stars": 4}, headers=b).json()
    assert out["avg_stars"] == 4.5 and out["rating_count"] == 2
    # Upsert: re-rating replaces, not appends.
    out = client.post("/videos/own-stat/rate", json={"stars": 2}, headers=a).json()
    assert out["avg_stars"] == 3.0 and out["rating_count"] == 2
    video = client.get("/videos/suggested", headers=a).json()["video"]
    assert video["my_stars"] == 2 and video["avg_stars"] == 3.0


def test_likes_ratings_export_and_erase(client):
    h = _register(client)
    client.put("/care-context", json={"stage": "trying_to_conceive"}, headers=h)
    client.post("/videos/own-stat/like", headers=h)
    client.post("/videos/own-stat/rate", json={"stars": 4}, headers=h)
    export = client.get("/export", headers=h).json()
    assert export["video_likes"][0]["video_id"] == "own-stat"
    assert export["video_ratings"][0]["stars"] == 4
    client.delete("/learner-data", headers=h)
    export = client.get("/export", headers=h).json()
    assert export.get("video_likes") == [] and export.get("video_ratings") == []
