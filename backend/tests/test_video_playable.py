"""`playable` has to mean a video exists, not that the paperwork is done.

It was `status == "published" and clinical_review.status == "approved"` — two
moderation states — while nothing in the catalogue schema carried a media URL.
So the first topic an admin published and approved would have reported
`playable: true` with no file anywhere, and any client honouring the flag would
have drawn a play button that could only fail.

That was not hypothetical: both clients were about to start honouring it. Web
rendered a play glyph on every card regardless, and Android rendered none —
opposite bugs from the same ignored field. Fixing them meant fixing what the
field means first.

Three conditions now, all necessary: produced, cleared by a clinician, and
actually somewhere.
"""
from app.domains import videos


def _topic(**over):
    base = {
        "id": "t1", "title": "A topic", "category": "nutrition",
        "status": "published",
        "clinical_review": {"required": True, "status": "approved"},
    }
    base.update(over)
    return videos._normalise(base)


def test_published_and_approved_is_not_enough_without_media():
    t = _topic()

    assert t["media_url"] is None
    assert t["playable"] is False


def test_all_three_conditions_make_it_playable():
    t = _topic(media_url="https://cdn.example/aira/t1.mp4")

    assert t["playable"] is True
    assert t["media_url"] == "https://cdn.example/aira/t1.mp4"


def test_media_without_review_is_not_playable():
    """A produced video that no clinician has read is exactly what the review
    gate exists to hold back."""
    t = _topic(clinical_review={"required": True, "status": "pending"},
               media_url="https://cdn.example/aira/t1.mp4")

    assert t["playable"] is False


def test_media_without_publishing_is_not_playable():
    t = _topic(status="draft", media_url="https://cdn.example/aira/t1.mp4")

    assert t["playable"] is False


def test_a_blank_url_counts_as_no_url():
    """An empty string in a catalogue field is a typo, not a video."""
    t = _topic(media_url="   ")

    assert t["media_url"] is None
    assert t["playable"] is False


def test_the_shipped_catalogue_has_nothing_playable():
    """None of the topics are filmed. If this ever fails, media has arrived and
    the clients' 'in production' copy needs to go with it."""
    videos._load_catalog()

    playable = [t["id"] for t in videos._TOPICS if t["playable"]]

    assert playable == [], f"these now claim to be playable: {playable}"


def test_the_admin_overlay_applies_the_same_rule(client):
    """The console is where this would have gone wrong: publishing is a
    moderation act and nothing about it produces a file."""
    seed = _topic()
    reviews = {"t1": {"status": "published", "review_status": "approved",
                      "reviewed_by": "owner@test.local", "reviewed_at": 1.0,
                      "note": ""}}

    resolved = videos._resolved(seed, reviews)

    assert resolved["status"] == "published"
    assert resolved["clinical_review"]["status"] == "approved"
    assert resolved["playable"] is False, "approved paperwork made a missing video playable"


def test_the_overlay_does_allow_a_real_video():
    seed = _topic(status="draft", media_url="https://cdn.example/aira/t1.mp4")
    reviews = {"t1": {"status": "published", "review_status": "approved",
                      "reviewed_by": "owner@test.local", "reviewed_at": 1.0,
                      "note": ""}}

    assert videos._resolved(seed, reviews)["playable"] is True


# ── the demo placeholder ─────────────────────────────────────────────────────

def test_demo_media_is_off_unless_asked_for():
    """The most important assertion about the placeholder.

    A stand-in that ships by accident is worse than the honest "in production"
    state it replaces, because it is the same claim in its most convincing form:
    a play button under a clinical title that opens something generic.
    """
    assert videos.DEMO_MEDIA is False, "AIRA_DEMO_MEDIA is set in this environment"


def test_no_placeholder_url_while_demo_media_is_off():
    assert videos._demo_media_url({"id": "preg-week-24"}) is None
    assert videos._normalise({"id": "x", "title": "t", "category": "nutrition"})["media_url"] is None


def test_the_placeholder_page_is_absent_without_demo_media(client, user):
    """Not merely unlinked — absent. A production build has no such page even if
    a client asks for one by hand."""
    r = client.get("/v1/videos/preg-week-24/placeholder", headers=user["headers"])

    assert r.status_code == 404


def test_demo_media_still_respects_the_review_gate(monkeypatch):
    """The placeholder may stand in for a file. It may not stand in for a
    clinician having read the thing."""
    monkeypatch.setattr(videos, "DEMO_MEDIA", True)

    unapproved = videos._normalise({
        "id": "t1", "title": "t", "category": "nutrition", "status": "published",
        "clinical_review": {"status": "pending"},
    })

    assert unapproved["media_url"] is not None, "demo media should still be offered"
    assert unapproved["playable"] is False, "an unreviewed topic became playable"


def test_demo_media_marks_itself_as_a_placeholder(monkeypatch):
    """So a client can label it. A stand-in the UI presents as the real video is
    exactly the failure this whole switch is guarded for."""
    monkeypatch.setattr(videos, "DEMO_MEDIA", True)

    t = videos._normalise({
        "id": "t1", "title": "t", "category": "nutrition", "status": "published",
        "clinical_review": {"status": "approved"},
    })

    assert t["playable"] is True
    assert t["media_is_placeholder"] is True


def test_real_media_is_never_marked_a_placeholder(monkeypatch):
    monkeypatch.setattr(videos, "DEMO_MEDIA", True)

    t = videos._normalise({
        "id": "t1", "title": "t", "category": "nutrition", "status": "published",
        "clinical_review": {"status": "approved"},
        "media_url": "https://cdn.example/real.mp4",
    })

    assert t["media_url"] == "https://cdn.example/real.mp4"
    assert t["media_is_placeholder"] is False
