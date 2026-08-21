"""Notification preference storage for Settings and permission recovery."""
from app import main
from app.domains import accounts, notifications


def _boot():
    main._startup()


def _uid():
    return accounts.device_register()["user_id"]


def test_notification_preferences_have_safe_defaults():
    _boot()
    prefs = notifications.read_preferences(uid=_uid())
    assert prefs["permission_status"] == "unknown"
    assert prefs["delivery_mode"] == "device"
    assert prefs["care_reminders_enabled"] is True
    assert prefs["cadence"] == "2x_day"
    assert prefs["times"] == ["09:00", "20:00"]


def test_denied_permission_forces_in_app_only_delivery():
    _boot()
    uid = _uid()
    prefs = notifications.write_preferences(
        notifications.NotificationPrefsIn(permission_status="denied", delivery_mode="device"),
        uid=uid,
    )
    assert prefs["permission_status"] == "denied"
    assert prefs["delivery_mode"] == "in_app_only"


def test_cadence_and_times_are_validated():
    _boot()
    uid = _uid()
    ok = notifications.write_preferences(
        notifications.NotificationPrefsIn(cadence="3x_day", times=["08:00", "14:00", "20:00"]),
        uid=uid,
    )
    assert ok["times"] == ["08:00", "14:00", "20:00"]

    for body in (
        notifications.NotificationPrefsIn(cadence="3x_day", times=["08:00"]),
        notifications.NotificationPrefsIn(times=["8am", "20:00"]),
        notifications.NotificationPrefsIn(cadence="every hour"),
    ):
        try:
            notifications.write_preferences(body, uid=uid)
        except Exception as e:
            assert getattr(e, "status_code", None) == 400
        else:
            raise AssertionError("invalid notification preference should fail")


def test_notification_preferences_export_and_delete():
    _boot()
    uid = _uid()
    notifications.write_preferences(
        notifications.NotificationPrefsIn(permission_status="granted", cadence="1x_day", times=["09:00"]),
        uid=uid,
    )
    assert notifications.export_user(uid)["permission_status"] == "granted"
    assert notifications.delete_user(uid) == 1
    assert notifications.export_user(uid)["permission_status"] == "unknown"

