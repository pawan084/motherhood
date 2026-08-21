"""Notification preferences.

Android/iOS permission is granted or denied on the device. The backend stores
the user's product choice around reminder delivery and cadence so Settings can
sync across sessions without pretending it can flip OS permission remotely.
"""
import json
import re
import time

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from app.core import db
from app.core import security
from app.domains.accounts import current_user

router = APIRouter(prefix="/v1", tags=["notifications"],
                   dependencies=[Depends(security.require_app_token)])
_conn = None

PERMISSION_STATUSES = {"unknown", "granted", "denied"}
DELIVERY_MODES = {"device", "in_app_only"}
CADENCES = {"1x_day": 1, "2x_day": 2, "3x_day": 3}
TIME_RE = re.compile(r"^([01]\d|2[0-3]):[0-5]\d$")

DEFAULTS = {
    "permission_status": "unknown",
    "delivery_mode": "device",
    "care_reminders_enabled": True,
    "cadence": "2x_day",
    "times": ["09:00", "20:00"],
    "updated": None,
}


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS notification_prefs ("
              " user_id TEXT PRIMARY KEY, data TEXT NOT NULL, updated REAL)")
    c.commit()
    _conn = c


def _read(uid: str) -> dict:
    init()
    row = _conn.execute("SELECT data, updated FROM notification_prefs WHERE user_id=?",
                        (uid,)).fetchone()
    if not row:
        return dict(DEFAULTS)
    data = {**DEFAULTS, **json.loads(row[0] or "{}")}
    data["updated"] = row[1]
    return data


def _validate_times(times: list[str], cadence: str) -> list[str]:
    if len(times) != CADENCES[cadence]:
        raise HTTPException(status_code=400, detail=f"{cadence} requires {CADENCES[cadence]} time(s)")
    cleaned = []
    for t in times:
        s = str(t or "").strip()
        if not TIME_RE.match(s):
            raise HTTPException(status_code=400, detail="times must use HH:MM 24-hour format")
        cleaned.append(s)
    return cleaned


class NotificationPrefsIn(BaseModel):
    permission_status: str | None = None
    delivery_mode: str | None = None
    care_reminders_enabled: bool | None = None
    cadence: str | None = None
    times: list[str] | None = None


@router.get("/notifications/preferences")
def read_preferences(uid: str = Depends(current_user)):
    return _read(uid)


@router.put("/notifications/preferences")
def write_preferences(body: NotificationPrefsIn, uid: str = Depends(current_user)):
    current = _read(uid)
    patch = {k: v for k, v in body.model_dump().items() if v is not None}
    if "permission_status" in patch and patch["permission_status"] not in PERMISSION_STATUSES:
        raise HTTPException(status_code=400, detail="invalid permission status")
    if "delivery_mode" in patch and patch["delivery_mode"] not in DELIVERY_MODES:
        raise HTTPException(status_code=400, detail="invalid delivery mode")
    if "cadence" in patch and patch["cadence"] not in CADENCES:
        raise HTTPException(status_code=400, detail="invalid reminder cadence")

    merged = {**current, **patch}
    if merged["permission_status"] == "denied":
        merged["delivery_mode"] = "in_app_only"
    merged["times"] = _validate_times(merged["times"], merged["cadence"])
    now = time.time()
    stored = {k: v for k, v in merged.items() if k != "updated"}
    init()
    _conn.execute("INSERT INTO notification_prefs (user_id, data, updated) VALUES (?,?,?) "
                  "ON CONFLICT(user_id) DO UPDATE SET data=excluded.data, updated=excluded.updated",
                  (uid, json.dumps(stored), now))
    _conn.commit()
    return _read(uid)


def export_user(uid: str) -> dict:
    return _read(uid)


def delete_user(uid: str) -> int:
    init()
    cur = _conn.execute("DELETE FROM notification_prefs WHERE user_id=?", (uid,))
    _conn.commit()
    return getattr(cur, "rowcount", 0) or 0

