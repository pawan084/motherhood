"""P6: care — medicines, documents, appointments, care plan.

The recurring properties, applied to four new resources: owner scoping,
validation, idempotency where it matters (taken-today), and the two identity
flows — sign-in merge and delete-cascade — extended to FILES on disk, because
deleting a document's row while orphaning its PDF would make "delete my
account" a lie.
"""
import os
import sys


def _register(client) -> dict:
    r = client.post("/device/register")
    return {"X-Device-Token": r.json()["device_token"]}


def _upload(client, h, name="report.pdf", kind="lab_report", content=b"%PDF-1.4 test"):
    return client.post("/documents", files={"file": (name, content, "application/pdf")},
                       data={"kind": kind}, headers=h)


# ── medicines ────────────────────────────────────────────────────────────────

def test_medicine_roundtrip_and_taken_today(client):
    h = _register(client)
    r = client.post("/medicines", json={"name": "Prenatal vitamin", "dose": "1 tablet",
                                        "time_of_day": "20:00"}, headers=h)
    mid = r.json()["id"]
    meds = client.get("/medicines", headers=h).json()["medicines"]
    assert meds[0]["name"] == "Prenatal vitamin" and meds[0]["taken_today"] is False

    # Idempotent per day.
    client.post(f"/medicines/{mid}/taken", headers=h)
    client.post(f"/medicines/{mid}/taken", headers=h)
    meds = client.get("/medicines", headers=h).json()["medicines"]
    assert meds[0]["taken_today"] is True


def test_medicine_validation(client):
    h = _register(client)
    assert client.post("/medicines", json={"name": "  "}, headers=h).status_code == 422
    assert client.post("/medicines", json={"name": "X", "time_of_day": "8pm"},
                       headers=h).status_code == 422
    assert client.post("/medicines", json={"name": "X", "time_of_day": "25:00"},
                       headers=h).status_code == 422


def test_medicines_owner_scoped(client):
    a, b = _register(client), _register(client)
    mid = client.post("/medicines", json={"name": "Iron"}, headers=a).json()["id"]
    assert client.get("/medicines", headers=b).json()["medicines"] == []
    assert client.post(f"/medicines/{mid}/taken", headers=b).status_code == 404
    assert client.delete(f"/medicines/{mid}", headers=b).status_code == 404


# ── documents ────────────────────────────────────────────────────────────────

def test_document_upload_download_delete(client):
    h = _register(client)
    r = _upload(client, h)
    assert r.status_code == 200
    doc_id = r.json()["id"]

    docs = client.get("/documents", headers=h).json()["documents"]
    assert docs[0]["kind"] == "lab_report" and docs[0]["filename"] == "report.pdf"

    dl = client.get(f"/documents/{doc_id}", headers=h)
    assert dl.status_code == 200
    assert dl.content == b"%PDF-1.4 test"
    assert dl.headers["content-type"].startswith("application/pdf")

    assert client.delete(f"/documents/{doc_id}", headers=h).status_code == 200
    assert client.get(f"/documents/{doc_id}", headers=h).status_code == 404


def test_document_type_whitelist(client):
    h = _register(client)
    r = _upload(client, h, name="malware.exe")
    assert r.status_code == 422
    r = _upload(client, h, name="noext")
    assert r.status_code == 422


def test_document_kind_validated(client):
    h = _register(client)
    assert _upload(client, h, kind="diary").status_code == 422


def test_document_size_cap(client, monkeypatch):
    security = sys.modules["security"]
    monkeypatch.setattr(security, "MAX_UPLOAD_BYTES", 10)
    h = _register(client)
    r = _upload(client, h, content=b"x" * 100)
    assert r.status_code == 413


def test_documents_owner_scoped(client):
    a, b = _register(client), _register(client)
    doc_id = _upload(client, a).json()["id"]
    assert client.get(f"/documents/{doc_id}", headers=b).status_code == 404
    assert client.delete(f"/documents/{doc_id}", headers=b).status_code == 404


def test_client_filename_never_touches_disk(client):
    """A hostile filename influences only the display name; the stored path is
    server-generated."""
    import config
    h = _register(client)
    r = _upload(client, h, name="../../../etc/passwd.pdf")
    assert r.status_code == 200
    # Nothing outside the upload dir, and no traversal inside it.
    for root, _dirs, files in os.walk(config.UPLOAD_DIR):
        for f in files:
            assert f.startswith("doc_") and f.endswith(".pdf")


# ── appointments + care plan ─────────────────────────────────────────────────

def test_appointment_roundtrip(client):
    h = _register(client)
    r = client.post("/appointments", json={
        "title": "Antenatal check", "when_ts": 1893456000.0,
        "location": "City clinic"}, headers=h)
    aid = r.json()["id"]
    apts = client.get("/appointments", headers=h).json()["appointments"]
    assert apts[0]["title"] == "Antenatal check"
    assert client.delete(f"/appointments/{aid}", headers=h).status_code == 200


def test_care_plan_toggle(client):
    h = _register(client)
    pid = client.post("/care-plan", json={"title": "Log fatigue for 3 days"},
                      headers=h).json()["id"]
    client.post(f"/care-plan/{pid}/toggle", headers=h)
    assert client.get("/care-plan", headers=h).json()["items"][0]["done"] is True
    client.post(f"/care-plan/{pid}/toggle", headers=h)
    assert client.get("/care-plan", headers=h).json()["items"][0]["done"] is False


# ── identity flows ───────────────────────────────────────────────────────────

def test_sign_in_merges_care_data_and_moves_files(client):
    import config
    h = _register(client)
    client.post("/medicines", json={"name": "Iron"}, headers=h)
    doc_id = _upload(client, h).json()["id"]

    r = client.post("/account/dev-login", json={"email": "m@x.com", "name": ""}, headers=h)
    session = {"Authorization": f"Bearer {r.json()['session']}"}
    uid = r.json()["user_id"]

    assert client.get("/medicines", headers=session).json()["medicines"][0]["name"] == "Iron"
    # The file physically moved to the account's directory and still downloads.
    assert os.path.exists(os.path.join(config.UPLOAD_DIR, uid, doc_id + ".pdf"))
    assert client.get(f"/documents/{doc_id}", headers=session).status_code == 200


def test_account_deletion_erases_care_data_and_files(client):
    import config
    h = _register(client)
    doc_id = _upload(client, h).json()["id"]
    client.post("/medicines", json={"name": "Iron"}, headers=h)

    r = client.post("/account/dev-login", json={"email": "m@x.com", "name": ""}, headers=h)
    session = {"Authorization": f"Bearer {r.json()['session']}"}
    uid = r.json()["user_id"]
    client.delete("/account", headers=session)

    assert client.get("/medicines", headers=h).json()["medicines"] == []
    assert client.get("/documents", headers=h).json()["documents"] == []
    assert not os.path.exists(os.path.join(config.UPLOAD_DIR, uid, doc_id + ".pdf"))
