"""Voice input contract.

The mobile UI has a listening and transcript-review path. What this backend does
today is define the safe contract: accept only audio, read it under the upload
cap, store nothing, and fail closed until a real speech-to-text provider is
configured.
"""
import logging
import time

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile

from app.core import security
from app.domains.accounts import current_user

log = logging.getLogger("aira.voice")
router = APIRouter(prefix="/v1", tags=["voice"],
                   dependencies=[Depends(security.require_app_token)])

ALLOWED_AUDIO_TYPES = {
    "audio/mpeg",
    "audio/mp3",
    "audio/mp4",
    "audio/m4a",
    "audio/aac",
    "audio/wav",
    "audio/x-wav",
    "audio/webm",
    "audio/ogg",
}


def init() -> None:
    # No table: raw audio is not stored in this build.
    return None


def _content_type(upload: UploadFile) -> str:
    return (upload.content_type or "").split(";")[0].strip().lower()


def _transcription_configured() -> bool:
    return False


@router.post("/voice/transcribe")
async def transcribe_voice(file: UploadFile = File(...), uid: str = Depends(current_user)):
    content_type = _content_type(file)
    if content_type not in ALLOWED_AUDIO_TYPES:
        raise HTTPException(status_code=415, detail="only audio files can be transcribed")
    data = await security.read_capped(file)
    if not data:
        raise HTTPException(status_code=400, detail="audio file is empty")

    if not _transcription_configured():
        log.info("voice transcription requested by %s but no provider is configured", uid)
        raise HTTPException(
            status_code=503,
            detail={
                "error": "voice transcription is not configured",
                "audio_deleted": True,
                "retryable": False,
            },
        )

    # Future provider integration returns this shape:
    return {  # pragma: no cover
        "transcript": "",
        "language": "English",
        "confidence": "Low",
        "audio_deleted": True,
        "processed_at": time.time(),
    }


@router.post("/voice/discard")
def discard_voice(uid: str = Depends(current_user)):
    """Explicitly discard an in-progress recording.

    Since this build never stores raw audio, discarding is idempotent.
    """
    return {"ok": True, "audio_deleted": True}


def export_user(uid: str) -> dict:
    return {"raw_audio_stored": False}


def delete_user(uid: str) -> int:
    return 0

