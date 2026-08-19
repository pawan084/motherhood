"""Which pregnancy weeks have a week-by-week video, and which do not.

The weekly card is the one thing in Learn that changes on its own, so a week
with no topic behind it is a card that silently disappears. That is worst at
exactly the weeks it happens: someone at 41 weeks is overdue, anxious and
checking daily, and an app that showed a weekly card every week until their due
date and then stopped reads as having run out of things to say to them.

The client now says so rather than rendering nothing. This pins the catalogue
side: the covered range is asserted, and the known holes are listed by number so
that adding a topic for one of them fails here and makes somebody delete the
line — rather than the gap quietly closing and nobody noticing the copy is now
unreachable.
"""
from app.domains import videos


def _covered_weeks() -> set[int]:
    videos.init()
    weeks: set[int] = set()
    for topic in videos._TOPICS:
        timing = topic["timing"]
        if timing["type"] != "gestational_week" or timing["start_week"] is None:
            continue
        end = timing["end_week"] or timing["start_week"]
        weeks.update(range(timing["start_week"], end + 1))
    return weeks


def test_every_week_from_four_to_forty_has_a_topic():
    """The stretch people actually spend a pregnancy in, with no holes.

    A gap in the middle would be a card that vanishes for a week and comes back
    the next, which reads as a bug rather than as an absence.
    """
    covered = _covered_weeks()
    missing = [w for w in range(4, 41) if w not in covered]
    assert not missing, f"weeks with no week-by-week topic: {missing}"


def test_the_known_gaps_are_only_the_ones_we_know_about():
    covered = _covered_weeks()
    gaps = [w for w in range(1, 43) if w not in covered]
    # 1-3: most people do not know yet. 41-42: overdue, and the reason the
    # client grew an honest empty state instead of showing nothing.
    assert gaps == [1, 2, 3, 41, 42], f"the catalogue's gaps have changed: {gaps}"


def test_a_pregnant_caller_past_forty_gets_nothing_rather_than_the_wrong_week():
    """No falling back to week 40's topic.

    Stretching the last band over 41 and 42 would be the easy fix and a clinical
    claim we are not entitled to make — that what is true at 40 is true at 42.
    Returning None is what lets the client say "no video yet" honestly.
    """
    assert videos.video_for_week("pregnant", 41) is None
    assert videos.video_for_week("pregnant", 42) is None
    assert videos.video_for_week("pregnant", 24) is not None


def test_no_weekly_card_for_a_journey_that_is_not_measured_in_weeks():
    for journey in ("postpartum", "trying", "exploring", None):
        assert videos.video_for_week(journey, 24) is None, journey
    assert videos.video_for_week("pregnant", None) is None
