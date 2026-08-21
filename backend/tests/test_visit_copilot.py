"""Visit Copilot API: appointment-aware question list and share payload."""
import time

from app import main
from app.domains import accounts, care, visit


def _boot():
    main._startup()


def _uid():
    return accounts.device_register()["user_id"]


def test_visit_copilot_empty_state_without_appointment():
    _boot()
    uid = _uid()
    data = visit.visit_copilot(uid=uid)
    assert data["has_appointment"] is False
    assert data["appointment"] is None
    assert data["empty"]["title"] == "No appointment added"
    assert data["count"] >= 1


def test_visit_copilot_uses_next_care_appointment():
    _boot()
    uid = _uid()
    care.add_appointment(
        care.AppointmentIn(
            doctor="Dr. Kapoor",
            place="Sunrise Clinic",
            when="Fri, 12 Sep",
            at=time.time() + 86400,
        ),
        uid=uid,
    )
    data = visit.visit_copilot(uid=uid)
    assert data["has_appointment"] is True
    assert data["appointment"]["doctor"] == "Dr. Kapoor"
    assert "Fri, 12 Sep" in data["appointment"]["title"]
    assert data["empty"] is None


def test_questions_can_be_added_checked_edited_and_deleted():
    _boot()
    uid = _uid()
    q = visit.add_question(visit.QuestionIn(text="Ask about swelling", source="chat"), uid=uid)
    assert q["source"] == "chat"

    updated = visit.update_question(
        q["id"], visit.QuestionPatchIn(text="Ask about ankle swelling", checked=True), uid=uid)
    assert updated["checked"] is True
    assert updated["text"] == "Ask about ankle swelling"

    data = visit.visit_copilot(uid=uid)
    assert any(item["id"] == q["id"] and item["checked"] for item in data["questions"])

    assert visit.delete_question(q["id"], uid=uid) == {"ok": True}
    assert not any(item["id"] == q["id"] for item in visit.visit_copilot(uid=uid)["questions"])


def test_share_visit_list_returns_reviewable_payload():
    _boot()
    uid = _uid()
    care.add_appointment(care.AppointmentIn(doctor="Dr. Kapoor"), uid=uid)
    visit.add_question(visit.QuestionIn(text="Confirm glucose test timing"), uid=uid)

    shared = visit.share_visit_list(visit.ShareIn(recipient="Dr. Kapoor"), uid=uid)
    assert shared["ok"] is True
    assert shared["recipient"] == "Dr. Kapoor"
    assert shared["appointment"]["doctor"] == "Dr. Kapoor"
    assert any("glucose" in q["text"].lower() for q in shared["questions"])
    assert "Nothing is sent" in shared["privacy_note"]

