"""The emergency profile, and the contract its editors have to honour.

`PUT /v1/emergency-profile` REPLACES the stored blob with the fields it is
given. That is a reasonable rule — it is the only way to clear a field someone
wants gone — but it makes one demand of every client: send the whole profile,
not the part that changed.

Android did not. Its editor opened with all six fields hardcoded empty and
never read the profile back, so saving a corrected phone number wrote a payload
in which everything else was blank, and the emergency contact next to it was
erased. Found on a physical device, not in review: the form looked the same
whether nothing was stored or everything was.

These tests pin both halves — that a full write round-trips, and that a partial
one really does drop what it omits — so the replace semantics stay a decision
someone made rather than a surprise waiting for the next client.
"""

FULL = {
    "care_team_name": "Riverside Midwives",
    "care_team_phone": "+441234567890",
    "emergency_contact_name": "Sam",
    "emergency_contact_phone": "+449876543210",
    "blood_group": "O-",
    "allergies": "penicillin",
}


def test_a_full_profile_round_trips(client, user):
    r = client.put("/v1/emergency-profile", json=FULL, headers=user["headers"])
    assert r.status_code == 200, r.text

    got = client.get("/v1/emergency-profile", headers=user["headers"]).json()
    for key, value in FULL.items():
        assert got[key] == value, key


def test_the_response_carries_the_fields_an_editor_prefills_from(client, user):
    """The PUT returns the profile, so a client need not immediately re-GET."""
    body = client.put("/v1/emergency-profile", json=FULL, headers=user["headers"]).json()
    assert body["care_team_phone"] == FULL["care_team_phone"]
    assert body["allergies"] == FULL["allergies"]


def test_a_partial_write_drops_what_it_omits(client, user):
    """This is the behaviour that cost Android its emergency contact.

    Not a bug to fix here — replacing is what lets someone delete a number they
    no longer want. It is a contract, and this test is where a client author
    finds out about it before their users do.
    """
    client.put("/v1/emergency-profile", json=FULL, headers=user["headers"])

    client.put("/v1/emergency-profile",
               json={"care_team_phone": "+440000000000"}, headers=user["headers"])

    got = client.get("/v1/emergency-profile", headers=user["headers"]).json()
    assert got["care_team_phone"] == "+440000000000"
    assert not got.get("emergency_contact_name")
    assert not got.get("allergies")


def test_an_empty_string_clears_a_field(client, user):
    """The reason replace exists: someone whose contact has changed needs the
    old one gone, not merged back in."""
    client.put("/v1/emergency-profile", json=FULL, headers=user["headers"])

    cleared = {**FULL, "emergency_contact_phone": ""}
    client.put("/v1/emergency-profile", json=cleared, headers=user["headers"])

    got = client.get("/v1/emergency-profile", headers=user["headers"]).json()
    assert got["emergency_contact_phone"] == ""
    # And nothing else went with it.
    assert got["care_team_phone"] == FULL["care_team_phone"]


def test_an_unset_profile_reads_as_empty_rather_than_missing(client, user):
    """The editor distinguishes "loaded and empty" from "could not load", so the
    endpoint must answer rather than 404 for someone who has never saved one."""
    r = client.get("/v1/emergency-profile", headers=user["headers"])
    assert r.status_code == 200, r.text
    assert not r.json().get("care_team_phone")


def test_the_profile_is_per_user(client, user):
    client.put("/v1/emergency-profile", json=FULL, headers=user["headers"])

    other = client.post("/device/register").json()
    other_headers = {"Authorization": f"Bearer {other['token']}"}
    got = client.get("/v1/emergency-profile", headers=other_headers).json()

    assert not got.get("care_team_phone")
    assert not got.get("emergency_contact_name")
