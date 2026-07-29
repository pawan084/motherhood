"""Editing, removing, and reading back care — the three things a user couldn't do.

Every care kind was create-only. A typo in a doctor's name was permanent, a
cancelled appointment stayed on Today forever, and a medicine the care team had
stopped went on being listed as due — an app prompting someone to take a
discontinued medicine is misleading about their care whatever the copy says.

Check-ins and symptom logs were worse than uneditable: they were unreadable. The
UI promised a "timeline" that had no endpoint behind it.
"""


def _register(client):
    r = client.post("/device/register")
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['token']}"}


def _add(client, headers, path, body):
    r = client.post(path, json=body, headers=headers)
    assert r.status_code == 200, r.text
    return r.json()["id"]


# ── editing ─────────────────────────────────────────────────────────────────

def test_a_typo_in_an_appointment_can_be_corrected(client, user):
    h = user["headers"]
    iid = _add(client, h, "/v1/care/appointments", {"doctor": "Dr Metha", "place": "City"})

    r = client.patch(f"/v1/care/items/{iid}", json={"doctor": "Dr Mehta"}, headers=h)
    assert r.status_code == 200, r.text
    assert r.json()["doctor"] == "Dr Mehta"

    appt = client.get("/v1/care", headers=h).json()["appointments"][0]
    assert appt["doctor"] == "Dr Mehta"
    assert appt["place"] == "City"          # untouched fields survive a partial edit


def test_editing_does_not_disturb_completion_state(client, user):
    h = user["headers"]
    iid = _add(client, h, "/v1/care/reminders", {"title": "Walk"})
    client.post(f"/v1/care/reminders/{iid}/done", json={"done": True}, headers=h)

    r = client.patch(f"/v1/care/items/{iid}", json={"title": "Evening walk"}, headers=h)
    assert r.json()["done"] is True
    assert client.get("/v1/care", headers=h).json()["care_plan"]["on_track"] == 1


def test_only_fields_that_belong_to_the_kind_can_be_edited(client, user):
    """Without an allow-list a client could write `done`, `kind`, or invented
    keys straight into the stored payload and reshape the row."""
    h = user["headers"]
    iid = _add(client, h, "/v1/care/medicines", {"name": "Iron"})
    for bad in ({"doctor": "Dr Who"}, {"done": True}, {"kind": "appointment"},
                {"anything": "at all"}):
        r = client.patch(f"/v1/care/items/{iid}", json=bad, headers=h)
        assert r.status_code == 400, f"{bad} was accepted"


def test_an_empty_edit_is_rejected(client, user):
    h = user["headers"]
    iid = _add(client, h, "/v1/care/reminders", {"title": "Walk"})
    assert client.patch(f"/v1/care/items/{iid}", json={}, headers=h).status_code == 400


def test_you_cannot_edit_someone_elses_item(client, user):
    iid = _add(client, user["headers"], "/v1/care/reminders", {"title": "Private"})
    r = client.patch(f"/v1/care/items/{iid}", json={"title": "Hijacked"},
                     headers=_register(client))
    # 404, not 403 — distinguishing "not yours" from "doesn't exist" would
    # confirm the id is real.
    assert r.status_code == 404
    assert client.get("/v1/care", headers=user["headers"]).json()["reminders"][0]["title"] \
        == "Private"


# ── removing ────────────────────────────────────────────────────────────────

def test_a_stopped_medicine_can_be_removed(client, user):
    """The safety case. Until this existed the app went on listing a medicine
    the care team had discontinued, with no way for anyone to stop it."""
    h = user["headers"]
    iid = _add(client, h, "/v1/care/medicines", {"name": "Iron"})
    assert len(client.get("/v1/care", headers=h).json()["medicines_due"]) == 1

    assert client.delete(f"/v1/care/items/{iid}", headers=h).status_code == 200
    assert client.get("/v1/care", headers=h).json()["medicines_due"] == []


def test_a_cancelled_appointment_can_be_removed(client, user):
    h = user["headers"]
    iid = _add(client, h, "/v1/care/appointments", {"doctor": "Dr Rao"})
    client.delete(f"/v1/care/items/{iid}", headers=h)
    assert client.get("/v1/care", headers=h).json()["appointments"] == []


def test_deleting_really_deletes(client, user):
    """A hidden flag would keep health data someone asked to remove."""
    import care
    h = user["headers"]
    iid = _add(client, h, "/v1/care/reminders", {"title": "Gone"})
    client.delete(f"/v1/care/items/{iid}", headers=h)
    care.init()
    assert care._conn.execute("SELECT COUNT(*) FROM care_items WHERE id=?",
                              (iid,)).fetchone()[0] == 0


def test_you_cannot_delete_someone_elses_item(client, user):
    iid = _add(client, user["headers"], "/v1/care/reminders", {"title": "Mine"})
    assert client.delete(f"/v1/care/items/{iid}", headers=_register(client)).status_code == 404
    assert len(client.get("/v1/care", headers=user["headers"]).json()["reminders"]) == 1


def test_deleting_a_missing_item_is_a_404_not_a_silent_ok(client, user):
    assert client.delete("/v1/care/items/rem_nope", headers=user["headers"]).status_code == 404


# ── the timeline ────────────────────────────────────────────────────────────

def test_check_ins_and_symptoms_can_finally_be_read_back(client, user):
    """The UI has always said "Add to timeline". This is the timeline."""
    h = user["headers"]
    client.post("/v1/care/checkin", json={"feeling": "Tired", "sleep_hours": 6}, headers=h)
    client.post("/v1/care/symptom", json={"what": "headache", "severity": "Mild"}, headers=h)

    items = client.get("/v1/care/timeline", headers=h).json()["items"]
    assert {i["kind"] for i in items} == {"checkin", "symptom"}
    assert any(i.get("what") == "headache" for i in items)
    assert any(i.get("feeling") == "Tired" for i in items)


def test_the_timeline_is_newest_first(client, user):
    h = user["headers"]
    client.post("/v1/care/symptom", json={"what": "first"}, headers=h)
    client.post("/v1/care/symptom", json={"what": "second"}, headers=h)
    items = client.get("/v1/care/timeline", headers=h).json()["items"]
    assert [i["what"] for i in items[:2]] == ["second", "first"]


def test_the_timeline_only_shows_your_own_entries(client, user):
    client.post("/v1/care/symptom", json={"what": "PRIVATE-SYMPTOM"}, headers=user["headers"])
    other = client.get("/v1/care/timeline", headers=_register(client))
    assert other.json()["items"] == []


def test_an_empty_timeline_is_an_empty_list_not_an_error(client, user):
    assert client.get("/v1/care/timeline", headers=user["headers"]).json()["items"] == []


def test_a_timeline_entry_can_be_corrected_and_removed(client, user):
    """Symptom logs are the entries most likely to need fixing — they're written
    quickly, often one-handed, often at 3am."""
    h = user["headers"]
    iid = _add(client, h, "/v1/care/symptom", {"what": "hedache", "severity": "Mild"})
    client.patch(f"/v1/care/items/{iid}", json={"what": "headache"}, headers=h)
    assert client.get("/v1/care/timeline", headers=h).json()["items"][0]["what"] == "headache"

    client.delete(f"/v1/care/items/{iid}", headers=h)
    assert client.get("/v1/care/timeline", headers=h).json()["items"] == []
