"""Weekly wellness report payload for the mobile report/share screens."""
import time

from app import main
from app.domains import accounts, care, wellness


def _boot():
    main._startup()


def _user():
    reg = accounts.device_register()
    uid = reg["user_id"]
    accounts.update_profile(uid, name="Pawan", journey="pregnant", onboarded=True)
    care.update_care_context(care.CareContextIn(weeks=24), uid=uid)
    return uid


def _set_created(item_id: str, uid: str, created: float):
    care._conn.execute("UPDATE care_items SET created=? WHERE id=? AND user_id=?",
                       (created, item_id, uid))
    care._conn.commit()


def test_weekly_report_counts_mood_vitamin_and_water_logs():
    _boot()
    uid = _user()
    now = time.time()

    checkin = care.add_checkin(care.CheckinIn(feeling="Okay"), uid=uid)
    _set_created(checkin["id"], uid, now)

    water = care.add_reminder(care.ReminderIn(title="Drink water"), uid=uid)
    _set_created(water["id"], uid, now)
    care.mark_reminder_done(water["id"], uid=uid)

    med = care.add_medicine(care.MedicineIn(name="Prenatal vitamin"), uid=uid)
    care.mark_taken(med["id"], uid=uid)

    report = wellness._report(uid, now=now)
    assert report["week"] == 24
    assert report["weeks_to_go"] == 16
    assert report["metrics"]["mood_checkins"]["count"] == 1
    assert report["metrics"]["water_goal_met"]["count"] == 1
    assert report["metrics"]["vitamin_taken"]["count"] == 1
    assert report["consistency"] > 0
    assert len(report["bars"]) == 7
    assert "not a medical assessment" in report["disclaimer"]


def test_share_preview_uses_report_data():
    _boot()
    uid = _user()
    preview = wellness.share_preview(uid=uid)
    assert preview["title"] == "Week 24 summary"
    assert preview["card"]["weeks_to_go"] == 16
    assert preview["included_defaults"]["mood_checkins"] is False


def test_share_requires_at_least_one_section():
    _boot()
    uid = _user()
    try:
        wellness.share_week(
            wellness.ShareIn(journey_week=False, care_consistency=False, mood_checkins=False),
            uid=uid,
        )
    except Exception as e:
        assert getattr(e, "status_code", None) == 400
    else:
        raise AssertionError("share without sections should fail")

