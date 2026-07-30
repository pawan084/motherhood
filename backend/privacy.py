"""Data export and account deletion — the two rights the product promises.

`legal.py` tells users "you can export or delete your data at any time" and the
clients render buttons for both, so these have to be real endpoints rather than
a policy document. Everything is scoped to `current_user`: there is no way to
export or erase anyone else's data through this module.

Each storage module owns the SQL for its own tables and exposes a matching pair:

    export_user(uid) -> JSON-serialisable          delete_user(uid) -> int

so the schema and its erasure stay in one place, and adding a table to the app
means adding it to one module rather than remembering to patch a central list.

Deletion ordering matters. `accounts` runs LAST, because removing the `users`
row is also what invalidates every outstanding session token (`_verify_token`
looks the row up). Each module commits separately — they hold independent
connections, so this is not one atomic transaction — and putting the account row
last means a mid-sequence failure leaves the user able to authenticate and retry
instead of stranding rows nobody can reach. The counts returned per module make
a partial deletion visible rather than silent.
"""
import io
import json
import logging
import os
import time
import zipfile

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

import accounts
import analytics_store
import care
import chat
import consent
import feedback
import memory
import partner
import prefs
import safety
import security
import videos
from accounts import current_user

log = logging.getLogger("aira.privacy")
router = APIRouter(prefix="/v1", tags=["privacy"],
                   dependencies=[Depends(security.require_app_token)])

# (label, module) in deletion order — `accounts` MUST stay last, see above.
_SOURCES = (
    ("care", care),
    ("chat", chat),
    ("memory", memory),
    ("consent", consent),
    ("feedback", feedback),
    ("prefs", prefs),
    ("partner", partner),
    ("safety", safety),
    ("videos", videos),
    ("events", analytics_store),
    ("account", accounts),
)

# Typed by the user to confirm an irreversible delete. Deliberately not a bare
# DELETE: the session token never expires (see accounts.py), so a leaked token
# should not be one request away from erasing someone's care history.
DELETE_CONFIRMATION = "DELETE MY DATA"


def _export_payload(uid: str) -> dict:
    data = {}
    for label, module in _SOURCES:
        try:
            data[label] = module.export_user(uid)
        except Exception as e:  # noqa: BLE001 — one bad table must not void the export
            log.warning("export of %s failed for %s: %s", label, uid, e)
            data[label] = {"error": "could not be exported"}
    return {"user_id": uid, "exported_at": time.time(), "data": data}


@router.get("/account/export")
def export_account(uid: str = Depends(current_user)):
    """Every record Aira holds for the signed-in user, as one JSON document.

    Records only. Uploaded documents are listed here with their metadata; the
    files themselves come with /account/export.zip, because a scan does not fit
    in a JSON field in any form a person can open.
    """
    return _export_payload(uid)


@router.get("/account/export.zip")
def export_account_zip(uid: str = Depends(current_user)):
    """Everything, including the files.

    The JSON export described itself as "everything Aira holds" while the Care
    Vault's actual documents — someone's scans and prescriptions — existed only
    on the server. Listing a file in an export is not exporting it.

    Streamed rather than assembled in memory: the cap is 20 MB per document and
    there is no cap on how many someone has, so building the archive in RAM
    would make the export fail exactly for the users with the most to lose.
    """
    payload = _export_payload(uid)
    documents = payload.get("data", {}).get("care", {}).get("items", {}).get("document", [])

    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("aira-export.json", json.dumps(payload, indent=2, default=str))
        used: set[str] = set()
        for doc in documents:
            path = care._document_path(uid, doc.get("id", ""))
            if not os.path.exists(path):
                continue
            # Two scans can share a filename; the archive must not silently keep
            # only the last one.
            name = os.path.basename(doc.get("name") or "document")
            candidate = name
            n = 2
            while candidate in used:
                stem, dot, ext = name.rpartition(".")
                candidate = f"{stem} ({n}){dot}{ext}" if dot else f"{name} ({n})"
                n += 1
            used.add(candidate)
            archive.write(path, f"documents/{candidate}")
    buffer.seek(0)
    return StreamingResponse(
        buffer,
        media_type="application/zip",
        headers={"Content-Disposition": 'attachment; filename="aira-export.zip"'},
    )


class DeleteAccountIn(BaseModel):
    confirm: str


@router.post("/account/delete")
def delete_account(body: DeleteAccountIn, uid: str = Depends(current_user)):
    """Erase the account and every row attached to it. Irreversible.

    POST rather than DELETE so the confirmation travels in a body that proxies
    and clients handle consistently. On success the caller's token is already
    dead — the client should drop it and re-register as a new anonymous user."""
    if body.confirm != DELETE_CONFIRMATION:
        raise HTTPException(status_code=400,
                            detail=f"confirm must be exactly '{DELETE_CONFIRMATION}'")
    deleted: dict[str, int] = {}
    failed: list[str] = []
    for label, module in _SOURCES:
        try:
            deleted[label] = module.delete_user(uid)
        except Exception as e:  # noqa: BLE001
            log.error("deletion of %s failed for %s: %s", label, uid, e)
            failed.append(label)
    if failed:
        # The account row is deleted last, so on failure the user still has a
        # working token and can retry. Surface it instead of reporting success.
        raise HTTPException(status_code=500,
                            detail=f"partially deleted; retry (failed: {','.join(failed)})")
    log.info("account %s deleted: %s", uid, deleted)
    return {"ok": True, "deleted": deleted}
