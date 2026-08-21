"""Direct tests for Care Vault status and share-link behavior."""
import os

from app import main
from app.domains import accounts, care


def _boot():
    main._startup()


def _doc(uid: str, name="scan.pdf", data=b"%PDF-1.4 scan"):
    item = care._add_item(uid, "document", {
        "name": name,
        "type": "Scan",
        "size": len(data),
        "content_type": "application/pdf",
        "scan_status": "ready",
        "scan_progress": 100,
        "scan_message": "Files are scanned before they enter your vault.",
        "use_in_answers": False,
    })
    care._store_document(uid, item["id"], data)
    return item


def test_document_status_preview_share_revoke_and_delete():
    _boot()
    uid = accounts.device_register()["user_id"]
    item = _doc(uid, data=b"%PDF-1.4 shared")

    status = care.get_document_status(item["id"], uid=uid)
    assert status["status"] == "ready"
    assert status["progress"] == 100

    preview = care.document_share_preview(item["id"], uid=uid)
    assert preview["document"]["name"] == "scan.pdf"
    assert [x["hours"] for x in preview["expiry_options"]] == [24, 168, 720]

    share = care.create_document_share(
        item["id"], care.DocumentShareIn(recipient="Dr Kapoor", expires_in_hours=24), uid=uid)
    assert share["recipient"] == "Dr Kapoor"
    assert share["token"]
    assert care.list_document_shares(uid=uid)["items"][0]["id"] == share["id"]

    assert care.revoke_document_share(share["id"], uid=uid) == {"ok": True}
    assert care.list_document_shares(uid=uid)["items"] == []

    fresh = care.create_document_share(
        item["id"], care.DocumentShareIn(expires_in_hours=24), uid=uid)
    care.delete_item(item["id"], uid=uid)
    assert not os.path.exists(care._document_path(uid, item["id"]))
    row = care._conn.execute("SELECT revoked FROM document_shares WHERE id=?",
                             (fresh["id"],)).fetchone()
    assert row and row[0] is not None


def test_invalid_share_expiry_is_refused():
    _boot()
    uid = accounts.device_register()["user_id"]
    item = _doc(uid)
    try:
        care.create_document_share(
            item["id"], care.DocumentShareIn(expires_in_hours=2), uid=uid)
    except Exception as e:
        assert getattr(e, "status_code", None) == 400
    else:
        raise AssertionError("invalid expiry should fail")

