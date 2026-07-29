"""The three surfaces added to make dead client CTAs real.

Reminders could be created but never completed (no endpoint existed, so
`care_items.done` was unreachable for that kind and `care_plan.on_track` was
permanently 0). Voice settings and partner invites were toasts on Android that
persisted nothing. These pin the behaviour, including the parts that must NOT
work: scope leakage, self-acceptance, replay of a used code.
"""


def _register(client):
    r = client.post("/device/register")
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['token']}"}


# ── reminder completion ──────────────────────────────────────────────────────

def test_reminder_can_be_completed_and_reopened(client, user):
    h = user["headers"]
    rid = client.post("/v1/care/reminders", json={"title": "Prenatal vitamin"},
                      headers=h).json()["id"]
    assert client.get("/v1/care", headers=h).json()["care_plan"]["on_track"] == 0

    r = client.post(f"/v1/care/reminders/{rid}/done", json={"done": True}, headers=h)
    assert r.status_code == 200, r.text
    assert r.json()["done"] is True
    care = client.get("/v1/care", headers=h).json()
    assert care["care_plan"]["on_track"] == 1
    assert next(x for x in care["reminders"] if x["id"] == rid)["done"] is True

    # Un-ticking has to work: a checklist you can't untick is a trap.
    client.post(f"/v1/care/reminders/{rid}/done", json={"done": False}, headers=h)
    assert client.get("/v1/care", headers=h).json()["care_plan"]["on_track"] == 0


def test_reminder_done_defaults_to_true_with_no_body(client, user):
    h = user["headers"]
    rid = client.post("/v1/care/reminders", json={"title": "Walk"}, headers=h).json()["id"]
    assert client.post(f"/v1/care/reminders/{rid}/done", headers=h).json()["done"] is True


def test_cannot_complete_another_users_reminder(client, user):
    rid = client.post("/v1/care/reminders", json={"title": "Private"},
                      headers=user["headers"]).json()["id"]
    other = _register(client)
    assert client.post(f"/v1/care/reminders/{rid}/done", headers=other).status_code == 404


def test_medicine_id_is_not_accepted_by_the_reminder_route(client, user):
    h = user["headers"]
    mid = client.post("/v1/care/medicines", json={"name": "Iron"}, headers=h).json()["id"]
    assert client.post(f"/v1/care/reminders/{mid}/done", headers=h).status_code == 404


# ── voice preferences ────────────────────────────────────────────────────────

def test_prefs_default_and_partial_update(client, user):
    h = user["headers"]
    assert client.get("/v1/prefs", headers=h).json()["voice"] == "Aira warm"

    r = client.put("/v1/prefs", json={"voice": "Aira gentle"}, headers=h)
    assert r.status_code == 200, r.text
    assert r.json()["voice"] == "Aira gentle"
    # It actually persists — this is the whole point versus the old toast.
    assert client.get("/v1/prefs", headers=h).json()["voice"] == "Aira gentle"
    # A partial write leaves untouched keys alone.
    assert client.get("/v1/prefs", headers=h).json()["spoken_replies"] is False


def test_unknown_voice_is_rejected(client, user):
    r = client.put("/v1/prefs", json={"voice": "Morgan Freeman"}, headers=user["headers"])
    assert r.status_code == 400


def test_prefs_are_scoped_per_user(client, user):
    client.put("/v1/prefs", json={"voice": "Text only"}, headers=user["headers"])
    assert client.get("/v1/prefs", headers=_register(client)).json()["voice"] == "Aira warm"


# ── partner invites ──────────────────────────────────────────────────────────

def test_invite_accept_and_scoped_share(client, user):
    owner = user["headers"]
    client.post("/v1/care/appointments", json={"doctor": "Dr Mehta", "place": "City Clinic"},
                headers=owner)
    client.post("/v1/care/symptom", json={"what": "headache"}, headers=owner)

    inv = client.post("/v1/partner/invite",
                      json={"appointments": True, "reminders": False,
                            "health_details": False}, headers=owner).json()
    assert inv["code"] and inv["scopes"]["health_details"] is False

    partner_h = _register(client)
    acc = client.post("/v1/partner/accept", json={"code": inv["code"]}, headers=partner_h)
    assert acc.status_code == 200, acc.text

    shared = client.get("/v1/partner/shared", headers=partner_h).json()["items"]
    assert len(shared) == 1
    data = shared[0]["data"]
    assert data["appointments"][0]["doctor"] == "Dr Mehta"
    # Ungranted scopes are absent entirely, not empty-but-present.
    assert "reminders" not in data
    assert "symptom_count" not in data


def test_shared_rows_carry_kind_so_clients_can_type_them(client, user):
    """`kind` is the item's type, not its content, and the clients key their icon
    and title-flattening off it. The scope filter dropped it, so every shared row
    arrived untyped and rendered identically whatever it was."""
    owner = user["headers"]
    client.post("/v1/care/appointments", json={"doctor": "Dr Rao"}, headers=owner)
    client.post("/v1/care/medicines", json={"name": "Iron"}, headers=owner)
    client.post("/v1/care/reminders", json={"title": "Walk"}, headers=owner)
    inv = client.post("/v1/partner/invite",
                      json={"appointments": True, "reminders": True}, headers=owner).json()
    partner_h = _register(client)
    client.post("/v1/partner/accept", json={"code": inv["code"]}, headers=partner_h)

    data = client.get("/v1/partner/shared", headers=partner_h).json()["items"][0]["data"]
    assert data["appointments"][0]["kind"] == "appointment"
    assert data["medicines"][0]["kind"] == "medicine"
    assert data["reminders"][0]["kind"] == "reminder"


def test_health_details_scope_yields_counts_not_text(client, user):
    owner = user["headers"]
    client.post("/v1/care/symptom", json={"what": "a very private symptom note"},
                headers=owner)
    inv = client.post("/v1/partner/invite", json={"health_details": True},
                      headers=owner).json()
    partner_h = _register(client)
    client.post("/v1/partner/accept", json={"code": inv["code"]}, headers=partner_h)

    body = client.get("/v1/partner/shared", headers=partner_h).text
    assert "symptom_count" in body
    assert "a very private symptom note" not in body


def test_invite_is_single_use(client, user):
    inv = client.post("/v1/partner/invite", json={}, headers=user["headers"]).json()
    client.post("/v1/partner/accept", json={"code": inv["code"]}, headers=_register(client))
    second = client.post("/v1/partner/accept", json={"code": inv["code"]},
                         headers=_register(client))
    assert second.status_code == 404


def test_cannot_accept_your_own_invite(client, user):
    inv = client.post("/v1/partner/invite", json={}, headers=user["headers"]).json()
    r = client.post("/v1/partner/accept", json={"code": inv["code"]}, headers=user["headers"])
    assert r.status_code == 400


def test_bad_code_is_rejected(client, user):
    assert client.post("/v1/partner/accept", json={"code": "nope-not-a-code"},
                       headers=user["headers"]).status_code == 404


def test_revocation_cuts_off_an_accepted_partner(client, user):
    owner = user["headers"]
    client.post("/v1/care/appointments", json={"doctor": "Dr Rao"}, headers=owner)
    inv = client.post("/v1/partner/invite", json={"appointments": True},
                      headers=owner).json()
    partner_h = _register(client)
    client.post("/v1/partner/accept", json={"code": inv["code"]}, headers=partner_h)
    assert client.get("/v1/partner/shared", headers=partner_h).json()["items"]

    r = client.post(f"/v1/partner/invites/{inv['id']}/revoke", headers=owner)
    assert r.status_code == 200, r.text
    # Revocation means revocation, not just "no new sign-ups".
    assert client.get("/v1/partner/shared", headers=partner_h).json()["items"] == []


def test_cannot_revoke_someone_elses_invite(client, user):
    inv = client.post("/v1/partner/invite", json={}, headers=user["headers"]).json()
    r = client.post(f"/v1/partner/invites/{inv['id']}/revoke", headers=_register(client))
    assert r.status_code == 404


def test_spent_code_is_not_echoed_back_to_the_owner(client, user):
    owner = user["headers"]
    inv = client.post("/v1/partner/invite", json={}, headers=owner).json()
    client.post("/v1/partner/accept", json={"code": inv["code"]}, headers=_register(client))
    listed = client.get("/v1/partner/invites", headers=owner).json()["items"]
    row = next(i for i in listed if i["id"] == inv["id"])
    assert row["state"] == "accepted"
    assert "code" not in row


def test_shared_is_empty_not_an_error_for_a_normal_user(client, user):
    r = client.get("/v1/partner/shared", headers=user["headers"])
    assert r.status_code == 200
    assert r.json()["items"] == []


# ── the new tables honour export + delete ────────────────────────────────────

def test_export_includes_prefs_and_partner(client, user):
    h = user["headers"]
    client.put("/v1/prefs", json={"voice": "Aira gentle"}, headers=h)
    client.post("/v1/partner/invite", json={}, headers=h)
    data = client.get("/v1/account/export", headers=h).json()["data"]
    assert data["prefs"]["voice"] == "Aira gentle"
    assert len(data["partner"]["invites_issued"]) == 1


def test_delete_removes_prefs_and_revokes_issued_invites(client):
    import partner as partner_mod
    import prefs as prefs_mod

    owner_reg = client.post("/device/register").json()
    owner = {"Authorization": f"Bearer {owner_reg['token']}"}
    uid = owner_reg["user_id"]
    client.put("/v1/prefs", json={"voice": "Text only"}, headers=owner)
    inv = client.post("/v1/partner/invite", json={"appointments": True},
                      headers=owner).json()
    partner_h = _register(client)
    client.post("/v1/partner/accept", json={"code": inv["code"]}, headers=partner_h)

    r = client.post("/v1/account/delete", json={"confirm": "DELETE MY DATA"}, headers=owner)
    assert r.status_code == 200, r.text

    assert prefs_mod.export_user(uid) == prefs_mod.DEFAULTS
    assert partner_mod.export_user(uid)["invites_issued"] == []
    # The partner's view of a deleted account goes away with it.
    assert client.get("/v1/partner/shared", headers=partner_h).json()["items"] == []
