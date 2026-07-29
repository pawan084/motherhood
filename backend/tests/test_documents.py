"""The Care Vault actually holding files.

Uploads used to be read and discarded: the row carried the filename and the
byte count, and the bytes themselves were gone. The vault listed a 20-week scan
with the right size next to a promise that it was "stored privately", and there
was no way for the user to find out otherwise until they went looking for it.
"""
import io
import os

import care


def _register(client):
    r = client.post("/device/register")
    return {"Authorization": f"Bearer {r.json()['token']}"}


def _upload(client, headers, name="scan.pdf", content=b"%PDF-1.4 pretend scan",
            content_type="application/pdf", kind="Scan"):
    return client.post(
        "/v1/care/documents",
        files={"file": (name, io.BytesIO(content), content_type)},
        data={"kind": kind},
        headers=headers,
    )


def test_an_uploaded_document_can_be_read_back(client, user):
    h = user["headers"]
    body = b"%PDF-1.4 the actual bytes"
    iid = _upload(client, h, content=body).json()["id"]

    r = client.get(f"/v1/care/documents/{iid}/file", headers=h)

    assert r.status_code == 200, r.text
    assert r.content == body


def test_a_document_is_served_as_an_attachment(client, user):
    """Never inline. These are user-supplied bytes, and a document rendered in
    place is a way to run someone else's content in this origin."""
    h = user["headers"]
    iid = _upload(client, h).json()["id"]

    r = client.get(f"/v1/care/documents/{iid}/file", headers=h)

    assert "attachment" in r.headers["content-disposition"]
    assert r.headers["content-type"].startswith("application/pdf")


def test_one_users_document_is_not_readable_by_another(client, user):
    iid = _upload(client, user["headers"]).json()["id"]
    other = _register(client)

    assert client.get(f"/v1/care/documents/{iid}/file", headers=other).status_code == 404


def test_an_executable_is_refused_rather_than_stored(client, user):
    """Enforced here, not only in the picker: the picker is a suggestion and
    this is the door."""
    r = _upload(client, user["headers"], name="x.html",
                content=b"<script>alert(1)</script>", content_type="text/html")

    assert r.status_code == 415


def test_deleting_the_item_removes_the_file(client, user):
    h = user["headers"]
    iid = _upload(client, h).json()["id"]
    path = care._document_path(user["id"], iid)
    assert os.path.exists(path)

    client.delete(f"/v1/care/items/{iid}", headers=h)

    assert not os.path.exists(path)
    assert client.get(f"/v1/care/documents/{iid}/file", headers=h).status_code == 404


def test_deleting_the_account_removes_every_file(client, user):
    """"Delete all my data" leaving someone's scans on disk is the most
    consequential possible version of a control that says one thing and does
    another."""
    h = user["headers"]
    _upload(client, h, name="a.pdf")
    _upload(client, h, name="b.pdf")
    user_dir = os.path.join(care.FILES_DIR, user["id"])
    assert os.path.isdir(user_dir)

    care.delete_user(user["id"])

    assert not os.path.exists(user_dir)


def test_a_missing_file_says_so_rather_than_serving_nothing(client, user):
    """Documents uploaded before files were stored still have rows. An empty
    200 would look like a corrupt scan; 410 says what happened."""
    h = user["headers"]
    iid = _upload(client, h).json()["id"]
    os.remove(care._document_path(user["id"], iid))

    assert client.get(f"/v1/care/documents/{iid}/file", headers=h).status_code == 410
