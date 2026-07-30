"""Educational video library: the catalog served by journey/week, saved-for-later,
and the safety posture (urgent topics carry their level so the client routes to
care rather than reassure). Saved videos ride the export/delete fan-out like any
other user data.
"""


def _pregnant(client, headers, weeks=24):
    r = client.post("/v1/onboarding",
                    json={"journey": "pregnant", "name": "Ava", "weeks": weeks},
                    headers=headers)
    assert r.status_code == 200, r.text
    return r


def test_library_is_scoped_to_journey_and_week(client, user):
    h = user["headers"]
    _pregnant(client, h, weeks=24)
    body = client.get("/v1/videos", headers=h).json()
    assert body["items"], "a pregnant user should see pregnancy topics"
    assert all("pregnant" in v["journeys"] for v in body["items"])
    wv = body["week_video"]
    assert wv is not None
    assert wv["timing"]["type"] == "gestational_week"
    assert wv["timing"]["start_week"] <= 24 <= wv["timing"]["end_week"]


def test_week_video_none_before_onboarding(client, user):
    # No journey/week yet -> the on-demand library, and no week video.
    body = client.get("/v1/videos", headers=user["headers"]).json()
    assert body["week_video"] is None
    assert all(v["timing"]["type"] == "on_demand" for v in body["items"])


def test_category_and_search_filter(client, user):
    h = user["headers"]
    _pregnant(client, h)
    nut = client.get("/v1/videos", params={"category": "nutrition"}, headers=h).json()
    assert nut["items"] and all(v["category"] == "nutrition" for v in nut["items"])
    found = client.get("/v1/videos", params={"q": "folate"}, headers=h).json()
    assert any("folate" in (v["title"] + v["description"]).lower() for v in found["items"])


def test_urgent_topics_carry_their_safety_level(client, user):
    h = user["headers"]
    _pregnant(client, h)
    sym = client.get("/v1/videos", params={"category": "pregnancy_symptoms"}, headers=h).json()
    assert any(v["safety_level"] == "urgent" for v in sym["items"]), \
        "symptom category should include urgent topics the client routes to care"


def test_save_unsave_and_saved_list(client, user):
    h = user["headers"]
    _pregnant(client, h)
    vid = client.get("/v1/videos", headers=h).json()["items"][0]["id"]

    assert client.post(f"/v1/videos/{vid}/save", headers=h).json()["saved"] is True
    # Idempotent — saving twice is not an error.
    assert client.post(f"/v1/videos/{vid}/save", headers=h).json()["saved"] is True

    saved = client.get("/v1/videos/saved", headers=h).json()
    assert [v["id"] for v in saved["items"]] == [vid]

    lib = client.get("/v1/videos", headers=h).json()
    assert vid in lib["saved_ids"]
    assert next(v for v in lib["items"] if v["id"] == vid)["saved"] is True

    assert client.delete(f"/v1/videos/{vid}/save", headers=h).json()["saved"] is False
    assert client.get("/v1/videos/saved", headers=h).json()["items"] == []


def test_saved_is_declared_before_the_id_route(client, user):
    # /videos/saved must not be captured as a video id by /videos/{id}.
    r = client.get("/v1/videos/saved", headers=user["headers"])
    assert r.status_code == 200
    assert "items" in r.json()


def test_unknown_video_is_404(client, user):
    h = user["headers"]
    assert client.get("/v1/videos/nope-not-real", headers=h).status_code == 404
    assert client.post("/v1/videos/nope-not-real/save", headers=h).status_code == 404


def test_saved_videos_are_private_to_the_user(client, user):
    h = user["headers"]
    _pregnant(client, h)
    vid = client.get("/v1/videos", headers=h).json()["items"][0]["id"]
    client.post(f"/v1/videos/{vid}/save", headers=h)

    other = client.post("/device/register").json()
    oh = {"Authorization": f"Bearer {other['token']}"}
    assert client.get("/v1/videos/saved", headers=oh).json()["items"] == []


def test_saved_videos_export_and_delete_with_account(client):
    reg = client.post("/device/register").json()
    h = {"Authorization": f"Bearer {reg['token']}"}
    _pregnant(client, h)
    vid = client.get("/v1/videos", headers=h).json()["items"][0]["id"]
    client.post(f"/v1/videos/{vid}/save", headers=h)

    exp = client.get("/v1/account/export", headers=h).json()
    assert vid in exp["data"]["videos"]["saved"]

    d = client.post("/v1/account/delete", json={"confirm": "DELETE MY DATA"}, headers=h)
    assert d.status_code == 200, d.text
    assert "videos" in d.json()["deleted"]
