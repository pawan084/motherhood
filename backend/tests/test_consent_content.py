"""Consent is enforced (not just recorded), and admin-published content is what
users actually receive.

Both were previously write-only: the ledger had no reader, and `journey_content`
read the in-code seed while the console wrote to a table nobody queried.
"""
import pytest

import consent
import content
import memory
from conftest import admin_login


@pytest.fixture(autouse=True)
def restore_content(client):
    """These tests edit the shared content registry. Snapshot and restore it so
    the module is hermetic and cannot leak an edited title into another test."""
    content.init()
    cols = "key, title, body, status"
    before = content._conn.execute(f"SELECT {cols} FROM content_entries").fetchall()
    yield
    for key, title, body, status in before:
        content._conn.execute(
            "UPDATE content_entries SET title=?, body=?, status=? WHERE key=?",
            (title, body, status, key))
    content._conn.commit()


# ── 2.1 the personalisation consent gates personalisation ────────────────────

def test_memory_feeds_the_prompt_when_consent_is_granted(client, user):
    uid = user["id"]
    client.post("/v1/memory", json={"label": "Craving", "value": "salty food"},
                headers=user["headers"])
    assert consent.is_granted(uid, "personalization") is True   # default
    assert "salty food" in memory.context_summary(uid)


def test_revoking_personalisation_stops_memory_reaching_the_prompt(client, user):
    uid = user["id"]
    client.post("/v1/memory", json={"label": "Craving", "value": "salty food"},
                headers=user["headers"])
    assert memory.context_summary(uid)

    r = client.post("/v1/consent", json={"feature": "personalization", "granted": False},
                    headers=user["headers"])
    assert r.status_code == 200
    assert memory.context_summary(uid) == ""      # the switch is real

    # Revoking does not destroy anything the user can still review or forget.
    items = client.get("/v1/memory", headers=user["headers"]).json()["items"]
    assert any(i["value"] == "salty food" for i in items)

    # ...and re-granting restores it.
    client.post("/v1/consent", json={"feature": "personalization", "granted": True},
                headers=user["headers"])
    assert "salty food" in memory.context_summary(uid)


def test_per_item_approval_still_applies_independently(client, user):
    uid = user["id"]
    r = client.post("/v1/memory", json={"label": "Note", "value": "keep private"},
                    headers=user["headers"])
    mid = r.json()["id"]
    assert "keep private" in memory.context_summary(uid)
    client.patch(f"/v1/memory/{mid}", json={"approved": False}, headers=user["headers"])
    assert "keep private" not in memory.context_summary(uid)


def test_require_consent_dependency_blocks_and_allows(client, user):
    """`require_consent` is the gate for features that collect data only after
    agreement. Exercised directly since no shipped feature is gated on it yet."""
    import fastapi
    uid = user["id"]
    dep = consent.require_consent("future_baby_story")

    assert consent.is_granted(uid, "future_baby_story") is False   # default off
    try:
        dep(uid)
        raise AssertionError("expected a 403 while consent is withheld")
    except fastapi.HTTPException as e:
        assert e.status_code == 403

    client.post("/v1/consent", json={"feature": "future_baby_story", "granted": True},
                headers=user["headers"])
    assert dep(uid) == uid


# ── 2.2 published content reaches users ──────────────────────────────────────

def _journey_for(client, headers, journey, weeks=None):
    body = {"journey": journey}
    if weeks is not None:
        body["weeks"] = weeks
    client.post("/v1/onboarding", json=body, headers=headers)
    return client.get("/v1/journey", headers=headers).json()


def test_published_content_edit_reaches_the_user(client, user):
    before = _journey_for(client, user["headers"], "postpartum")
    assert before["title"] == "Postpartum recovery"

    csrf = admin_login(client)
    r = client.patch("/admin/content/journey.postpartum",
                     json={"title": "Recovery, your way",
                           "body": "Edited by a clinician.", "status": "published"},
                     headers=csrf)
    assert r.status_code == 200, r.text

    after = client.get("/v1/journey", headers=user["headers"]).json()
    assert after["title"] == "Recovery, your way"
    assert after["body"] == "Edited by a clinician."


def test_draft_status_is_never_served(client, user):
    csrf = admin_login(client)
    client.patch("/admin/content/journey.trying",
                 json={"title": "Unreviewed draft copy", "status": "draft"}, headers=csrf)

    j = _journey_for(client, user["headers"], "trying")
    assert j["title"] == "Trying to conceive"        # the in-code seed, not the draft
    assert "Unreviewed" not in j["title"]


def test_blank_field_falls_back_to_the_in_code_default(client, user):
    csrf = admin_login(client)
    client.patch("/admin/content/journey.exploring",
                 json={"title": "Temporarily renamed", "status": "published"}, headers=csrf)
    j = _journey_for(client, user["headers"], "exploring")
    assert j["title"] == "Temporarily renamed"

    client.patch("/admin/content/journey.exploring", json={"title": ""}, headers=csrf)
    j = client.get("/v1/journey", headers=user["headers"]).json()
    assert j["title"] == "Exploring with Aira"       # cleared -> seed restored


def test_pregnancy_week_banding_survives_a_title_edit(client, user):
    csrf = admin_login(client)
    client.patch("/admin/content/journey.pregnant",
                 json={"title": "Your pregnancy, gently", "body": "", "status": "published"},
                 headers=csrf)

    j = _journey_for(client, user["headers"], "pregnant", weeks=30)
    assert j["title"] == "Your pregnancy, gently"    # admin title applies
    assert j["weeks"] == 30
    assert j["this_week"] == "Third trimester"       # ...week band still drives this
    assert "Rest matters more now" in j["body"]      # ...and the banded body


def test_published_body_overrides_the_week_band(client, user):
    csrf = admin_login(client)
    client.patch("/admin/content/journey.pregnant",
                 json={"body": "One clinician-approved line for every week.",
                       "status": "published"}, headers=csrf)
    j = _journey_for(client, user["headers"], "pregnant", weeks=30)
    assert j["body"] == "One clinician-approved line for every week."
    assert j["this_week"] == "Third trimester"       # headline stays week-derived


def test_band_boundaries(client):
    assert content._band(1)[0] == "Early days"
    assert content._band(12)[0] == "Early days"
    assert content._band(13)[0] == "Second trimester begins"
    assert content._band(36)[0] == "Third trimester"
    assert content._band(37)[0] == "Nearly there"
    assert content._band(42)[0] == "Nearly there"
    assert content._band(0)[0] == "Early days"       # never indexes out of range
