"""Reminder pause and snooze-all state for Care detail."""
import time

from app import main
from app.domains import accounts, care


def _boot():
    main._startup()


def _uid():
    return accounts.device_register()["user_id"]


def test_pause_reminders_returns_state_in_care_payload():
    _boot()
    uid = _uid()
    state = care.pause_reminders(
        care.PauseRemindersIn(preset="3_days", reason="rough day"), uid=uid)
    assert state["paused"] is True
    assert state["pause_until"] > time.time()
    assert state["reason"] == "rough day"

    payload = care.care(uid=uid)
    assert payload["reminder_state"]["paused"] is True


def test_custom_pause_must_be_future_and_resume_clears_it():
    _boot()
    uid = _uid()
    try:
        care.pause_reminders(care.PauseRemindersIn(until=time.time() - 1), uid=uid)
    except Exception as e:
        assert getattr(e, "status_code", None) == 400
    else:
        raise AssertionError("past pause date should fail")

    care.pause_reminders(care.PauseRemindersIn(until=time.time() + 3600), uid=uid)
    resumed = care.resume_reminders(uid=uid)
    assert resumed["paused"] is False
    assert resumed["pause_until"] is None


def test_snooze_all_today_sets_snooze_until_midnight():
    _boot()
    uid = _uid()
    state = care.snooze_all_today(uid=uid)
    assert state["snoozed_today"] is True
    assert state["snooze_until"] > time.time()


def test_expired_pause_is_reported_as_inactive():
    _boot()
    uid = _uid()
    care.pause_reminders(care.PauseRemindersIn(until=time.time() + 3600), uid=uid)
    care._conn.execute("UPDATE care_reminder_state SET pause_until=? WHERE user_id=?",
                       (time.time() - 1, uid))
    care._conn.commit()
    assert care._reminder_state(uid)["paused"] is False


def test_reminder_state_exports_and_deletes():
    _boot()
    uid = _uid()
    care.snooze_all_today(uid=uid)
    assert care.export_user(uid)["reminder_state"]["snoozed_today"] is True
    assert care.delete_user(uid) >= 1
    assert care.export_user(uid)["reminder_state"]["snoozed_today"] is False

