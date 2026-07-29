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


# ── the journey moving on ───────────────────────────────────────────────────


def test_changing_journey_away_from_pregnant_stops_counting_weeks(client, user):
    """Found on a physical device, not in a test.

    Today read `weeks` straight out of care_context while the Journey screen
    read it through journey_content, which already refuses to hand back a
    pregnancy week for a non-pregnant journey. So after switching to postpartum
    the app rendered "Week 24" and a 24-week ring above postpartum copy — still
    counting the weeks of a pregnancy the user had just told it had ended. That
    change is frequently a loss, which makes this the worst possible field to
    get stale.
    """
    h = user["headers"]
    client.post("/v1/onboarding",
                json={"journey": "pregnant", "language": "English",
                      "priorities": [], "weeks": 24}, headers=h)
    assert client.get("/v1/today", headers=h).json()["weeks"] == 24

    client.patch("/account/profile", json={"journey": "postpartum"}, headers=h)

    today = client.get("/v1/today", headers=h).json()
    assert today["journey"] == "postpartum"
    assert today["weeks"] is None, "postpartum users must not be shown a pregnancy week"
    assert "week" not in today["context_line"].lower()
    assert client.get("/v1/journey", headers=h).json()["weeks"] is None


def test_returning_to_pregnant_restores_the_week_that_was_entered(client, user):
    """Hiding the week is not the same as discarding it. Someone who switches
    journeys by mistake, or is pregnant again, should not have to re-enter what
    they already told Aira — so the stored value survives, it just stops being
    reported while it doesn't apply."""
    h = user["headers"]
    client.post("/v1/onboarding",
                json={"journey": "pregnant", "language": "English",
                      "priorities": [], "weeks": 24}, headers=h)
    client.patch("/account/profile", json={"journey": "exploring"}, headers=h)
    assert client.get("/v1/today", headers=h).json()["weeks"] is None

    client.patch("/account/profile", json={"journey": "pregnant"}, headers=h)
    assert client.get("/v1/today", headers=h).json()["weeks"] == 24


# ── appointments with a real date ───────────────────────────────────────────


def test_an_appointment_can_carry_a_date_and_still_keep_the_words(client, user):
    """Both, not either. The date is what the app can sort and remind on; the
    free text is how the person remembers the visit ("Friday, early")."""
    h = user["headers"]
    r = client.post("/v1/care/appointments",
                    json={"doctor": "Dr Shah", "when": "Friday, early", "at": 1786000000},
                    headers=h)
    assert r.status_code == 200, r.text
    appt = client.get("/v1/care", headers=h).json()["appointments"][0]
    assert appt["at"] == 1786000000
    assert appt["when"] == "Friday, early"


def test_an_appointment_without_a_date_is_still_accepted(client, user):
    """"Sometime next week" is a real answer. Refusing to record it until the
    user commits to a day is how a care app ends up empty."""
    h = user["headers"]
    r = client.post("/v1/care/appointments", json={"doctor": "Dr Shah"}, headers=h)
    assert r.status_code == 200
    assert client.get("/v1/care", headers=h).json()["appointments"][0]["at"] is None


def test_appointments_come_back_soonest_first_with_undated_ones_last(client, user):
    h = user["headers"]
    client.post("/v1/care/appointments", json={"doctor": "later", "at": 2000}, headers=h)
    client.post("/v1/care/appointments", json={"doctor": "undated"}, headers=h)
    client.post("/v1/care/appointments", json={"doctor": "sooner", "at": 1000}, headers=h)

    order = [a["doctor"] for a in client.get("/v1/care", headers=h).json()["appointments"]]
    assert order == ["sooner", "later", "undated"]


def test_the_date_can_be_corrected_like_any_other_field(client, user):
    """Appointments move. The whole point of storing a real date is undone if
    it can't be changed when the clinic reschedules."""
    h = user["headers"]
    iid = client.post("/v1/care/appointments",
                      json={"doctor": "Dr Shah", "at": 1000}, headers=h).json()["id"]

    r = client.patch(f"/v1/care/items/{iid}", json={"at": 5000}, headers=h)
    assert r.status_code == 200, r.text
    assert client.get("/v1/care", headers=h).json()["appointments"][0]["at"] == 5000


# ── a dose is an event, not a flag ──────────────────────────────────────────


def _add_medicine(client, headers, name="Prenatal vitamin"):
    r = client.post("/v1/care/medicines", json={"name": name, "time": "9:00 AM"},
                    headers=headers)
    assert r.status_code == 200, r.text
    return r.json()["id"]


def test_a_daily_medicine_survives_being_taken(client, user):
    """The one that mattered. Marking taken used to set done=1, and /care
    filtered done medicines out of medicines_due — so the first tap on a daily
    prenatal vitamin removed it from the list for good and the app quietly
    stopped prompting for a medication meant to be taken every day."""
    h = user["headers"]
    iid = _add_medicine(client, h)

    client.post(f"/v1/care/medicines/{iid}/taken", headers=h)

    care = client.get("/v1/care", headers=h).json()
    # Not due again until tomorrow...
    assert care["medicines_due"] == []
    # ...but still a medicine this person takes.
    assert [m["name"] for m in care["medicines"]] == ["Prenatal vitamin"]
    assert care["medicines"][0]["taken_today"] is True


def test_takingItAgainRecordsASecondDose(client, user):
    h = user["headers"]
    iid = _add_medicine(client, h)

    client.post(f"/v1/care/medicines/{iid}/taken", headers=h)
    r = client.post(f"/v1/care/medicines/{iid}/taken", headers=h)

    assert len(r.json()["taken"]) == 2


def test_aMedicineNeverTakenIsDueAndHasNoHistory(client, user):
    h = user["headers"]
    _add_medicine(client, h)

    care = client.get("/v1/care", headers=h).json()
    assert len(care["medicines_due"]) == 1
    assert care["medicines"][0]["taken_today"] is False
    assert care["medicines"][0]["last_taken"] is None


def test_yesterdaysDoseDoesNotCountAsToday(client, user):
    """Local midnight, not a rolling 24 hours: someone taking a tablet at 8am
    wants a fresh prompt the next morning, not one sliding an hour later daily."""
    import care as care_module

    yesterday = {"taken": [__import__("time").time() - 26 * 3600]}
    assert care_module._taken_today(yesterday) is False


def test_takingAMedicineThatIsNotYoursIs404(client, user):
    other = _register(client)
    iid = _add_medicine(client, user["headers"])

    assert client.post(f"/v1/care/medicines/{iid}/taken", headers=other).status_code == 404


# ── a pregnancy week moves on its own ───────────────────────────────────────


def test_the_week_advances_with_time(client, user):
    """The defect this fixes: weeks were stored once at onboarding and read back
    verbatim for ever, so someone who said "24" was shown week-24 content in
    month nine, after the birth, and a year later. It is the one number in this
    app that changes without anyone touching it."""
    import care

    now = 1_800_000_000.0
    assert care.current_weeks(24, now, now) == 24
    assert care.current_weeks(24, now - 7 * 86400, now) == 25
    assert care.current_weeks(24, now - 3 * 7 * 86400, now) == 27


def test_a_week_past_term_stops_being_asserted(client, user):
    """Rather than counting into fiction. Past 42 the pregnancy has almost
    certainly ended and nobody told us; saying nothing is honest, "week 61" is
    not."""
    import care

    now = 1_800_000_000.0
    assert care.current_weeks(24, now - 30 * 7 * 86400, now) is None


def test_the_week_can_be_corrected_and_restarts_the_clock(client, user):
    h = user["headers"]
    client.post("/v1/onboarding",
                json={"journey": "pregnant", "language": "English",
                      "priorities": [], "weeks": 24}, headers=h)

    r = client.patch("/v1/care/context", json={"weeks": 30}, headers=h)

    assert r.status_code == 200, r.text
    assert r.json()["weeks"] == 30
    assert client.get("/v1/today", headers=h).json()["weeks"] == 30


def test_an_impossible_week_is_refused(client, user):
    assert client.patch("/v1/care/context", json={"weeks": 0},
                        headers=user["headers"]).status_code == 400
    assert client.patch("/v1/care/context", json={"weeks": 60},
                        headers=user["headers"]).status_code == 400


def test_priorities_can_be_changed_after_onboarding(client, user):
    """They drive what Today suggests, and were set once before the user had
    used the app at all."""
    h = user["headers"]
    client.post("/v1/onboarding",
                json={"journey": "pregnant", "language": "English",
                      "priorities": ["Feel calmer"], "weeks": 20}, headers=h)

    r = client.patch("/v1/care/context", json={"priorities": ["Plan my care"]}, headers=h)

    assert r.json()["priorities"] == ["Plan my care"]
    assert client.get("/v1/today", headers=h).json()["priorities"] == ["Plan my care"]


def test_changing_priorities_leaves_the_week_alone(client, user):
    h = user["headers"]
    client.post("/v1/onboarding",
                json={"journey": "pregnant", "language": "English",
                      "priorities": [], "weeks": 22}, headers=h)

    client.patch("/v1/care/context", json={"priorities": ["Feel calmer"]}, headers=h)

    assert client.get("/v1/today", headers=h).json()["weeks"] == 22
