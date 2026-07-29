"""Deletion, checked against the schema rather than against the list.

`privacy._SOURCES` is ten modules someone remembered to add. The screen promises
something wider — "it removes your profile, conversations, care items, memory,
consent history and safety records" — and the way that promise breaks is not a
module doing its job badly. It is a new table, added months later, whose author
never edited `_SOURCES` and had no reason to think about it. Nothing fails; the
data simply stays.

So this walks every table in the live schema and every column in it, looking for
the user's id anywhere at all. A new table holding user rows is covered by this
test the day it is created, without anyone remembering to extend it.

The uploaded file on disk is checked too. Bytes on a filesystem are the part a
SQL-shaped review never looks at, and they are the most personal thing here — a
scan is not a row.
"""
import os

import db
import privacy


def _tables(conn):
    return sorted(r[0] for r in conn.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"))


def _rows_mentioning(conn, uid):
    """{table: count} for every table with any column containing the uid."""
    found = {}
    for t in _tables(conn):
        cols = [r[1] for r in conn.execute(f"PRAGMA table_info({t})")]
        if not cols:
            continue
        where = " OR ".join(f"CAST({c} AS TEXT) LIKE ?" for c in cols)
        n = conn.execute(f"SELECT COUNT(*) FROM {t} WHERE {where}",   # noqa: S608
                         tuple([f"%{uid}%"] * len(cols))).fetchone()[0]
        if n:
            found[t] = n
    return found


def _files_for(uid):
    root = os.environ["AIRA_FILES_DIR"]
    hits = []
    for base, _dirs, names in os.walk(root):
        hits += [os.path.join(base, n) for n in names if uid in os.path.join(base, n)]
    return hits


def _fill_account(client, headers):
    """Touch every surface that writes a user-owned row."""
    ok = []

    def post(path, **kw):
        r = client.post(path, headers=headers, **kw)
        ok.append((path, r.status_code))
        return r

    client.patch("/account/profile", json={"name": "Priya", "journey": "pregnant"},
                 headers=headers)
    post("/v1/onboarding", json={"journey": "pregnant", "name": "Priya",
                                 "language": "English", "priorities": ["sleep"],
                                 "weeks": 20})
    client.patch("/v1/care/context", json={"weeks": 21}, headers=headers)
    post("/v1/care/reminders", json={"title": "Iron tablet", "detail": "8:00 PM"})
    post("/v1/care/medicines", json={"name": "Folic acid", "dose": "5mg",
                                     "schedule": "Daily", "time": "8:00 PM"})
    post("/v1/care/appointments", json={"doctor": "Dr Rao", "place": "City Hospital",
                                        "when": "14 Aug"})
    post("/v1/care/checkin", json={"mood": "low", "note": "tired today"})
    post("/v1/care/symptom", json={"what": "Headache", "severity": "mild"})
    client.put("/v1/emergency-profile",
               json={"blood_group": "O+", "contact_name": "Ravi", "contact_phone": "555"},
               headers=headers)
    post("/v1/care/documents",
         files={"file": ("scan.pdf", b"%PDF-1.4 not a real scan", "application/pdf")},
         data={"title": "20wk scan"})
    post("/v1/memory", json={"label": "Diet", "value": "vegetarian"})
    post("/v1/consent", json={"feature": "personalization", "granted": False})
    post("/v1/chat/turn", json={"message": "how do I sleep better?", "history": []})
    post("/v1/chat/turn", json={"message": "I am bleeding heavily", "history": []})
    post("/v1/consent", json={"feature": "partner_access", "granted": True})
    post("/v1/partner/invite", json={"appointments": True, "reminders": True})
    client.put("/v1/prefs", json={"voice": "Aira gentle", "spoken_replies": True},
               headers=headers)
    post("/v1/feedback", json={"rating": 4, "message": "helpful"})

    bad = [(p, s) for p, s in ok if s >= 300]
    assert not bad, f"setup calls failed, so deletion would pass vacuously: {bad}"


def test_deleting_an_account_leaves_nothing_in_any_table(client, user):
    uid, headers = user["id"], user["headers"]
    _fill_account(client, headers)

    conn = db.connect()
    before = _rows_mentioning(conn, uid)
    # Guard against the whole test passing because nothing was ever written.
    # A deletion test that deletes nothing is the easiest green in the file.
    assert len(before) >= 8, f"expected the account to span the schema, got {before}"
    assert _files_for(uid), "no uploaded file to delete"

    r = client.post("/v1/account/delete", json={"confirm": privacy.DELETE_CONFIRMATION},
                    headers=headers)
    assert r.status_code == 200, r.text

    left = _rows_mentioning(db.connect(), uid)
    assert not left, (
        f"rows survived deletion: {left}. If this is a new table, add its module to "
        f"privacy._SOURCES — the screen tells people deletion removes everything.")
    assert not _files_for(uid), "uploaded files survived deletion"


def test_analytics_events_are_deleted_too(client, user):
    """Written directly: no route records events today, but the table exists and
    privacy._SOURCES claims it, so the claim is worth holding to."""
    import analytics_store
    uid, headers = user["id"], user["headers"]
    analytics_store.record_event(uid, "screen_view", {"screen": "today"})
    assert _rows_mentioning(db.connect(), uid).get("events") == 1

    r = client.post("/v1/account/delete", json={"confirm": privacy.DELETE_CONFIRMATION},
                    headers=headers)
    assert r.status_code == 200, r.text
    assert "events" not in _rows_mentioning(db.connect(), uid)


def test_deleting_a_partner_does_not_delete_the_owners_invite(client, user):
    """The other half of the promise: erasing you must not erase someone else.

    The invite belongs to the owner, not to the partner who accepted it, so it
    survives — marked revoked rather than returned to pending, because a pending
    invite still shows a code a stranger could then accept.
    """
    owner, partner = user["headers"], None
    r = client.post("/device/register")
    partner_id = r.json()["user_id"]
    partner = {"Authorization": f"Bearer {r.json()['token']}"}

    client.post("/v1/consent", json={"feature": "partner_access", "granted": True},
                headers=owner)
    code = client.post("/v1/partner/invite", json={"appointments": True},
                       headers=owner).json()["code"]
    assert client.post("/v1/partner/accept", json={"code": code},
                       headers=partner).status_code == 200

    r = client.post("/v1/account/delete", json={"confirm": privacy.DELETE_CONFIRMATION},
                    headers=partner)
    assert r.status_code == 200, r.text
    assert not _rows_mentioning(db.connect(), partner_id)

    invites = client.get("/v1/partner/invites", headers=owner).json()["items"]
    assert len(invites) == 1
    assert invites[0]["state"] == "revoked"


def test_a_deleted_owner_leaves_their_partner_reading_nothing(client, user):
    """And the reverse: the partner's view empties rather than erroring or —
    far worse — still listing a deleted person's care."""
    owner = user["headers"]
    r = client.post("/device/register")
    partner = {"Authorization": f"Bearer {r.json()['token']}"}

    client.post("/v1/consent", json={"feature": "partner_access", "granted": True},
                headers=owner)
    client.post("/v1/care/reminders", json={"title": "Iron tablet", "detail": "8 PM"},
                headers=owner)
    code = client.post("/v1/partner/invite", json={"appointments": True, "reminders": True},
                       headers=owner).json()["code"]
    client.post("/v1/partner/accept", json={"code": code}, headers=partner)
    assert client.get("/v1/partner/shared", headers=partner).json()["items"]

    client.post("/v1/account/delete", json={"confirm": privacy.DELETE_CONFIRMATION},
                headers=owner)

    r = client.get("/v1/partner/shared", headers=partner)
    assert r.status_code == 200, r.text
    assert r.json()["items"] == []
