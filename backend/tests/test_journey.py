"""P5: journey content — band resolution, coverage, and seed-once semantics."""
import sys
from datetime import date


def _register(client) -> dict:
    r = client.post("/device/register")
    return {"X-Device-Token": r.json()["device_token"]}


def _pregnant_at_week(client, h, week: int):
    days_to_due = (40 - week) * 7
    due = date.fromordinal(date.today().toordinal() + days_to_due).isoformat()
    r = client.put("/care-context", json={"stage": "pregnant", "due_date": due}, headers=h)
    assert r.json()["context"]["week"] == week


# --- coverage ----------------------------------------------------------------

def test_every_pregnant_week_resolves_to_a_band(client):
    """No week 1..42 may fall through the cracks between bands."""
    journey = sys.modules["journey"]
    for week in range(1, 43):
        band = journey.band_for("pregnant", week)
        assert band is not None, f"pregnant week {week} has no content band"


def test_every_postpartum_week_resolves_including_past_the_end(client):
    journey = sys.modules["journey"]
    for week in (1, 2, 3, 6, 7, 12, 13, 52, 60, 104):
        band = journey.band_for("postpartum", week)
        assert band is not None, f"postpartum week {week} has no content band"
    # Past the last band clamps to it rather than erroring.
    assert journey.band_for("postpartum", 104)["id"] == "pp-13-52"


def test_band_boundaries_are_exact(client):
    journey = sys.modules["journey"]
    assert journey.band_for("pregnant", 24)["id"] == "preg-21-24"
    assert journey.band_for("pregnant", 25)["id"] == "preg-25-28"


# --- the endpoint ------------------------------------------------------------

def test_journey_derives_week_from_context(client):
    h = _register(client)
    _pregnant_at_week(client, h, 24)
    body = client.get("/journey", headers=h).json()
    assert body["current_week"] == 24
    assert body["shown_week"] == 24
    assert body["content"]["id"] == "preg-21-24"
    assert "movement" in body["content"]["title"].lower()
    assert body["disclaimer"]


def test_explicit_week_pages_without_touching_context(client):
    h = _register(client)
    _pregnant_at_week(client, h, 24)
    body = client.get("/journey?week=8", headers=h).json()
    assert body["shown_week"] == 8
    assert body["content"]["id"] == "preg-8-12"
    # The stored context is unchanged.
    assert client.get("/care-context", headers=h).json()["context"]["week"] == 24


def test_ttc_gets_stage_content_without_weeks(client):
    h = _register(client)
    client.put("/care-context", json={"stage": "trying_to_conceive"}, headers=h)
    body = client.get("/journey", headers=h).json()
    assert body["shown_week"] is None
    assert body["content"]["id"] == "ttc"
    assert body["content"]["baby"] == ""  # no baby section while trying


def test_no_context_is_404(client):
    h = _register(client)
    assert client.get("/journey", headers=h).status_code == 404


def test_week_param_validated(client):
    h = _register(client)
    _pregnant_at_week(client, h, 24)
    assert client.get("/journey?week=0", headers=h).status_code == 422
    assert client.get("/journey?week=99", headers=h).status_code == 422


def test_journey_requires_auth(client):
    assert client.get("/journey").status_code == 401


# --- seed-once ---------------------------------------------------------------

def test_admin_edits_survive_reseed(client):
    """The sayli lesson: re-running init() must not clobber an edited row."""
    journey = sys.modules["journey"]
    journey._conn.execute(
        "UPDATE journey_content SET title='Edited by admin' WHERE id='preg-21-24'")
    journey._conn.commit()
    journey._conn = None  # force re-init against the same DB
    journey.init()
    assert journey.band_for("pregnant", 24)["title"] == "Edited by admin"


def test_journey_carries_size_milestones_progress(client):
    h = _register(client)
    _pregnant_at_week(client, h, 24)
    body = client.get("/journey", headers=h).json()
    assert body["total_weeks"] == 40
    assert body["size"] == {"emoji": "🌽", "label": "an ear of corn"}
    weeks = [m["week"] for m in body["milestones"]]
    assert 12 in weeks and 40 in weeks
    # Browsing another week updates the size with it.
    body8 = client.get("/journey?week=8", headers=h).json()
    assert body8["size"]["label"] == "a raspberry"
    # Pre-detection weeks have no size to show.
    assert client.get("/journey?week=2", headers=h).json()["size"] is None


def test_hosted_videos_catalog_and_stream(client):
    h = _register(client)
    _pregnant_at_week(client, h, 8)
    videos = client.get("/videos", headers=h).json()["videos"]
    own = next(v for v in videos if v["id"] == "own-benefit")
    assert own["stream_path"] == "/media/videos/portrait/benefit.mp4"
    assert own["thumb_path"] == "/media/videos/thumbs/benefit.png"
    assert own["youtube_id"] is None
    # The suggested pick rotates daily across the stage catalog but is
    # stable within a day (rotation contract lives in test_wellness).
    suggested = client.get("/videos/suggested", headers=h).json()["video"]
    assert suggested["stage"] == "pregnant"
    again = client.get("/videos/suggested", headers=h).json()["video"]
    assert again["id"] == suggested["id"]
