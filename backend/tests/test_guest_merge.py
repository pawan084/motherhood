"""Explicit review before anonymous device history joins a signed-in account."""
from app import main
from app.domains import accounts, care


def _boot():
    main._startup()


def _device():
    reg = accounts.device_register()
    return reg["user_id"], reg["token"]


def _account(email: str):
    body = accounts.SignupIn(email=email, password="a-long-enough-passphrase")
    return accounts.account_signup(body)


def test_merge_preview_reports_mood_and_reminder_conflicts():
    _boot()
    device_uid, device_token = _device()
    account = _account("merge-preview@example.com")
    account_uid = account["user_id"]

    care.add_checkin(care.CheckinIn(feeling="Tired"), uid=device_uid)
    care.add_checkin(care.CheckinIn(feeling="Okay"), uid=account_uid)
    care.add_reminder(care.ReminderIn(title="Vitamin", time="09:00"), uid=device_uid)
    care.add_reminder(care.ReminderIn(title="vitamin", time="08:30"), uid=account_uid)

    preview = accounts.account_merge_conflicts(device_token=device_token, uid=account_uid)
    assert preview["merge_required"] is True
    assert {c["kind"] for c in preview["conflicts"]} == {"checkin", "reminder"}


def test_merge_applies_choices_and_moves_non_conflicting_history():
    _boot()
    device_uid, device_token = _device()
    account = _account("merge-apply@example.com")
    account_uid = account["user_id"]

    care.add_checkin(care.CheckinIn(feeling="Tired"), uid=device_uid)
    care.add_checkin(care.CheckinIn(feeling="Okay"), uid=account_uid)
    care.add_reminder(care.ReminderIn(title="Vitamin", time="09:00"), uid=device_uid)
    care.add_reminder(care.ReminderIn(title="vitamin", time="08:30"), uid=account_uid)
    care.add_symptom(care.SymptomIn(what="headache"), uid=device_uid)

    preview = accounts.account_merge_conflicts(device_token=device_token, uid=account_uid)
    choices = [
        accounts.MergeChoiceIn(conflict_id=c["id"],
                               keep="device" if c["kind"] == "checkin" else "account")
        for c in preview["conflicts"]
    ]
    result = accounts.account_merge(
        accounts.MergeIn(device_token=device_token, choices=choices),
        uid=account_uid,
    )
    assert result["ok"] is True
    assert result["conflicts"] == 2

    timeline = care.timeline(uid=account_uid)["items"]
    assert any(i.get("feeling") == "Tired" for i in timeline)
    assert not any(i.get("feeling") == "Okay" for i in timeline)
    assert any(i.get("what") == "headache" for i in timeline)

    reminders = care.get_reminders(uid=account_uid)["items"]
    assert [r.get("time") for r in reminders if r.get("title", "").lower() == "vitamin"] \
        == ["08:30"]
    assert accounts.get_user(device_uid) is None


def test_cancel_merge_keeps_the_device_history_separate():
    _boot()
    device_uid, device_token = _device()
    account = _account("merge-cancel@example.com")

    care.add_checkin(care.CheckinIn(feeling="Tired"), uid=device_uid)
    result = accounts.account_merge_cancel(
        accounts.MergeIn(device_token=device_token), uid=account["user_id"])
    assert result == {"ok": True, "merged": False}
    assert accounts.get_user(device_uid) is not None
    assert care.timeline(uid=device_uid)["items"][0]["feeling"] == "Tired"

