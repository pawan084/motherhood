"""Voice endpoint contract while transcription is not configured."""
import asyncio

from fastapi import HTTPException

from app import main
from app.domains import accounts, voice


class _Upload:
    def __init__(self, data: bytes, content_type: str):
        self._data = data
        self._sent = False
        self.content_type = content_type

    async def read(self, _n: int):
        if self._sent:
            return b""
        self._sent = True
        return self._data


def _boot():
    main._startup()


def _uid():
    return accounts.device_register()["user_id"]


def test_voice_transcribe_fails_closed_without_provider():
    _boot()
    uid = _uid()
    try:
        asyncio.run(voice.transcribe_voice(_Upload(b"pretend audio", "audio/m4a"), uid=uid))
    except HTTPException as e:
        assert e.status_code == 503
        assert e.detail["error"] == "voice transcription is not configured"
        assert e.detail["audio_deleted"] is True
    else:
        raise AssertionError("transcription should fail closed without a provider")


def test_voice_refuses_non_audio_and_empty_audio():
    _boot()
    uid = _uid()
    for upload, status in (
        (_Upload(b"<html></html>", "text/html"), 415),
        (_Upload(b"", "audio/m4a"), 400),
    ):
        try:
            asyncio.run(voice.transcribe_voice(upload, uid=uid))
        except HTTPException as e:
            assert e.status_code == status
        else:
            raise AssertionError(f"expected {status}")


def test_discard_is_idempotent_and_export_says_no_raw_audio():
    _boot()
    uid = _uid()
    assert voice.discard_voice(uid=uid) == {"ok": True, "audio_deleted": True}
    assert voice.export_user(uid) == {"raw_audio_stored": False}
    assert voice.delete_user(uid) == 0

