"""Per-user video progress for the player screen."""
from app import main
from app.domains import accounts, care, videos


def _boot():
    main._startup()


def _uid():
    reg = accounts.device_register()
    uid = reg["user_id"]
    accounts.update_profile(uid, journey="pregnant", onboarded=True)
    care.update_care_context(care.CareContextIn(weeks=24), uid=uid)
    return uid


def _first_video(uid: str) -> str:
    body = videos.list_videos(uid=uid)
    return body["items"][0]["id"]


def test_progress_is_saved_and_returned_on_video_payloads():
    _boot()
    uid = _uid()
    vid = _first_video(uid)

    saved = videos.save_progress(
        vid, videos.ProgressIn(progress_seconds=35, duration_seconds=100), uid=uid)
    assert saved["progress_seconds"] == 35
    assert saved["progress_percent"] == 35
    assert saved["watched"] is False

    item = videos.get_video(vid, uid=uid)
    assert item["progress_seconds"] == 35
    assert item["progress_percent"] == 35
    assert item["watched"] is False


def test_completion_marks_video_watched_and_exported():
    _boot()
    uid = _uid()
    vid = _first_video(uid)

    done = videos.complete_video(vid, uid=uid)
    assert done["watched"] is True
    assert done["progress_percent"] == 100

    exp = videos.export_user(uid)
    assert exp["progress"][vid]["watched"] is True

    assert videos.delete_user(uid) >= 1
    assert videos.export_user(uid)["progress"] == {}


def test_progress_at_ninety_percent_counts_as_watched():
    _boot()
    uid = _uid()
    vid = _first_video(uid)
    result = videos.save_progress(
        vid, videos.ProgressIn(progress_seconds=90, duration_seconds=100), uid=uid)
    assert result["watched"] is True


def test_transcript_is_honest_when_catalog_has_none():
    _boot()
    uid = _uid()
    vid = _first_video(uid)
    transcript = videos.video_transcript(vid, uid=uid)
    assert transcript == {"video_id": vid, "available": False, "items": []}


def test_next_video_skips_the_current_video_and_prefers_unwatched():
    _boot()
    uid = _uid()
    first = _first_video(uid)
    next_one = videos.next_video(first, uid=uid)["video"]
    assert next_one is not None
    assert next_one["id"] != first
    videos.complete_video(next_one["id"], uid=uid)
    after = videos.next_video(first, uid=uid)["video"]
    assert after is not None
    assert after["id"] != first
    assert after["id"] != next_one["id"] or len(videos.list_videos(uid=uid)["items"]) <= 2


def test_unknown_video_progress_routes_404():
    _boot()
    uid = _uid()
    for fn in (
        lambda: videos.save_progress("nope", videos.ProgressIn(progress_seconds=1), uid=uid),
        lambda: videos.complete_video("nope", uid=uid),
        lambda: videos.video_transcript("nope", uid=uid),
        lambda: videos.next_video("nope", uid=uid),
    ):
        try:
            fn()
        except Exception as e:
            assert getattr(e, "status_code", None) == 404
        else:
            raise AssertionError("unknown video should 404")

