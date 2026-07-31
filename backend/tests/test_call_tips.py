"""The "when to call" tips, and the claims they are not allowed to make.

This is the most consequential copy in the product: it tells a pregnant person
which signals mean ring your care team now. Three properties matter more than
the wording, and none of them can be held by review alone.

  1. It only reaches someone whose week is known. A "when to call" line is
     meaningless without knowing which signals apply, and the app has already
     shipped one hardcoded "Week 24" shown to strangers.
  2. It never claims review it has not had. The seeds in content.py were drafted
     from the app's own red-flag vocabulary, not written by a clinician, and the
     payload has to say so until somebody publishes.
  3. Publishing it is an act, not a default — so the tips seed as drafts.
"""
import content


def test_a_pregnant_user_with_a_known_week_gets_a_tip(client, user):
    d = content.journey_content("pregnant", 31)

    assert "call_tip" in d
    assert d["call_tip"]["title"]
    assert d["call_tip"]["body"]


def test_no_tip_without_a_week(client, user):
    """Better silence than guessing which signals apply to someone."""
    assert "call_tip" not in content.journey_content("pregnant", None)
    assert "call_tip" not in content.journey_content("pregnant", 0)


def test_no_pregnancy_tip_for_a_journey_that_is_not_pregnancy(client, user):
    for journey in ("postpartum", "trying", "exploring"):
        assert "call_tip" not in content.journey_content(journey, 31), journey


def test_the_band_moves_with_the_week(client, user):
    """Reduced fetal movement only becomes the headline signal once someone can
    feel a pattern to compare against."""
    early = content.journey_content("pregnant", 8)["call_tip"]["body"]
    late = content.journey_content("pregnant", 31)["call_tip"]["body"]

    assert early != late
    assert "move" in late.lower()


def test_the_seed_does_not_claim_to_be_reviewed(client, user):
    """The single most important assertion in this file.

    Nobody qualified has read the in-code seeds. Until an admin publishes a row,
    the payload must say so, so a client cannot present them as clinician-backed.
    """
    assert content.journey_content("pregnant", 31)["call_tip"]["reviewed"] is False


def test_publishing_an_edit_marks_it_reviewed(client, user):
    band_key = content.call_tip_key(28)
    content.update_entry(band_key, title="When to call",
                         body="Ring the day unit on any of these, at any hour.",
                         status="published", actor="midwife@clinic.example")

    tip = content.journey_content("pregnant", 31)["call_tip"]

    assert tip["body"] == "Ring the day unit on any of these, at any hour."
    assert tip["reviewed"] is True


def test_a_draft_edit_is_never_served(client, user):
    """Same rule the rest of the content registry follows: drafts are for the
    console, not for users."""
    band_key = content.call_tip_key(28)
    content.update_entry(band_key, body="half-written, not for anyone yet",
                         status="draft", actor="editor@clinic.example")

    tip = content.journey_content("pregnant", 31)["call_tip"]

    assert "half-written" not in tip["body"]
    assert tip["reviewed"] is False


def test_the_tips_are_editable_from_the_console(client, user):
    """update_entry raises KeyError for a key with no row, so the tips have to
    be seeded or the console cannot touch them."""
    keys = {e["key"] for e in content.list_entries()}

    for band_start in (1, 13, 20, 28, 37):
        assert content.call_tip_key(band_start) in keys


def test_they_seed_as_drafts(client, user):
    entries = {e["key"]: e for e in content.list_entries()}

    for band_start in (1, 13, 20, 28, 37):
        entry = entries[content.call_tip_key(band_start)]
        assert entry["status"] == "draft", f"band {band_start} seeded as published"
