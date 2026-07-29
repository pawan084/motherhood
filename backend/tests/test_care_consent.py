"""Journey-awareness (postpartum users don't get pregnancy-week cards) and the
consent ledger's default + enforcement behaviour."""


def test_onboarding_sets_journey_and_today_is_journey_aware(client, user):
    r = client.post("/v1/onboarding",
                    json={"journey": "postpartum", "name": "Maya",
                          "priorities": ["Better sleep"]},
                    headers=user["headers"])
    assert r.status_code == 200, r.text
    today = r.json()
    assert today["journey"] == "postpartum"
    assert today["weeks"] is None                      # never a pregnancy week
    # Postpartum next action is recovery/sleep-oriented, not "prepare appointment".
    assert today["next_action"]["tool"] in ("wellness", "checkin")


def test_pregnant_journey_is_week_banded(client):
    reg = client.post("/device/register").json()
    h = {"Authorization": f"Bearer {reg['token']}"}
    client.post("/v1/onboarding", json={"journey": "pregnant", "weeks": 24}, headers=h)
    j = client.get("/v1/journey", headers=h).json()
    assert j["journey"] == "pregnant"
    assert j["weeks"] == 24
    assert j["this_week"]                                # a real week band, not blank


def test_invalid_journey_rejected(client, user):
    r = client.post("/v1/onboarding", json={"journey": "banana"}, headers=user["headers"])
    assert r.status_code == 400


def test_consent_defaults_and_grant(client, user):
    r = client.get("/v1/consent", headers=user["headers"]).json()
    feats = {f["key"]: f for f in r["features"]}
    assert feats["personalization"]["granted"] is True
    assert feats["partner_access"]["granted"] is False
    assert feats["data_for_ads"]["locked"] is True
    # Unbuilt features are advertised as unavailable rather than as off — see
    # test_consent_registry.py.
    assert feats["future_baby_story"]["available"] is False

    # Round-trip a feature that actually exists; granting one that doesn't is
    # refused, which is what makes the toggle mean something.
    client.post("/v1/consent", json={"feature": "partner_access", "granted": True},
                headers=user["headers"])
    r2 = client.get("/v1/consent", headers=user["headers"]).json()
    assert {f["key"]: f for f in r2["features"]}["partner_access"]["granted"] is True


def test_locked_consent_cannot_be_granted(client, user):
    r = client.post("/v1/consent", json={"feature": "data_for_ads", "granted": True},
                    headers=user["headers"])
    assert r.status_code == 400


def test_emergency_profile_roundtrip(client, user):
    client.put("/v1/emergency-profile",
               json={"care_team_name": "City Women's Clinic", "care_team_phone": "+911140000000"},
               headers=user["headers"])
    ep = client.get("/v1/emergency-profile", headers=user["headers"]).json()
    assert ep["care_team_phone"] == "+911140000000"
