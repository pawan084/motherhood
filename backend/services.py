"""Gemini glue: reply generation (blocking + streaming) and card suggestion.

Every LLM call in the product goes through this module or safety.py, nowhere
else. There is no fallback provider by design — a Gemini failure surfaces as an
explicit error, never a silent degradation (see PLAN.md non-negotiables).

Card suggestion runs on the small/fast model (the safety classifier's model):
it is a cheap post-processing step and must never delay or break a reply — any
failure returns [] and the reply stands alone.
"""
import json
import logging
import os
import threading
from collections import OrderedDict

import httpx

import config
import prompts

log = logging.getLogger("aira.services")

# ElevenLabs TTS. One warm voice for Aira (env-overridable); multilingual
# model so en/hi/Hinglish replies all speak.
ELEVEN_TTS_MODEL = os.environ.get("ELEVEN_TTS_MODEL", "eleven_multilingual_v2")
ELEVENLABS_VOICE_ID = os.environ.get("ELEVENLABS_VOICE_ID", "21m00Tcm4TlvDq8ikWAM")


_genai_client = None
_genai_lock = threading.Lock()


def _client():
    """One shared genai client for the whole process (safety.py uses it too).

    Found live, not in tests: constructing a Client per call intermittently
    dies with "Cannot send a request, as the client has been closed" under the
    server's threading — a GC'd Client tears down transport state a new one
    is still using. A process-wide singleton is also simply cheaper.
    """
    global _genai_client
    with _genai_lock:
        if _genai_client is None:
            from google import genai
            _genai_client = genai.Client(api_key=config.GEMINI_API_KEY)
        return _genai_client


def _to_contents(history: list[dict], text: str):
    """Map sanitized {role, content} history + the new message into genai
    contents. `assistant` becomes `model`; history is pre-sanitized by
    security.sanitize_history (system roles already stripped)."""
    from google.genai import types
    contents = []
    for turn in history:
        role = "model" if turn["role"] == "assistant" else "user"
        contents.append(types.Content(role=role, parts=[types.Part.from_text(text=turn["content"])]))
    contents.append(types.Content(role="user", parts=[types.Part.from_text(text=text)]))
    return contents


def generate_reply(system: str, history: list[dict], text: str) -> str:
    """One blocking reply. Raises on provider failure — the route decides how
    that surfaces (it never silently swallows)."""
    from google.genai import types
    resp = _client().models.generate_content(
        model=config.GEMINI_MODEL,
        contents=_to_contents(history, text),
        config=types.GenerateContentConfig(system_instruction=system, temperature=0.7),
    )
    reply = (resp.text or "").strip()
    if not reply:
        raise RuntimeError("empty reply from model")
    return reply


def stream_reply(system: str, history: list[dict], text: str):
    """Yield reply text chunks as the model produces them. Raises mid-stream on
    provider failure; the SSE route converts that into an error event."""
    from google.genai import types
    stream = _client().models.generate_content_stream(
        model=config.GEMINI_MODEL,
        contents=_to_contents(history, text),
        config=types.GenerateContentConfig(system_instruction=system, temperature=0.7),
    )
    for chunk in stream:
        if chunk.text:
            yield chunk.text


def transcribe(audio: bytes, mime: str) -> str:
    """Gemini STT: one multimodal call, transcript only. Raises on provider
    failure (the route owns the honest-error posture); an empty transcript
    returns "" — silence is not an error.

    Deliberately a SEPARATE call from reply generation, unlike sayli's
    one-call audio->reply turn: Aira's gate must screen the transcript BEFORE
    any reply exists, so transcription cannot be fused with generation.
    """
    from google.genai import types
    resp = _client().models.generate_content(
        model=config.GEMINI_SAFETY_MODEL,
        contents=[types.Content(role="user", parts=[
            types.Part.from_bytes(data=audio, mime_type=mime),
            types.Part.from_text(text=prompts.TRANSCRIBE_PROMPT),
        ])],
        config=types.GenerateContentConfig(temperature=0.0),
    )
    text = (resp.text or "").strip()
    # The model is told to answer NONE for silence/noise; normalize to "".
    return "" if text == "NONE" else text


# --- TTS (ElevenLabs) with a small LRU cache ---------------------------------
# Greetings and short replies repeat; every repeat re-bills ElevenLabs.
# In-process LRU keyed by text — entries are MP3 bytes, so the cap stays low.
# Per-worker by design; fold into Redis if multi-instance (same note as the
# rate limiter).

_TTS_CACHE_MAX = int(os.environ.get("AIRA_TTS_CACHE_SIZE", "64"))
_tts_cache: "OrderedDict[str, bytes]" = OrderedDict()
_tts_lock = threading.Lock()


def tts_cached(text: str) -> bytes:
    """MP3 bytes for `text`. Raises on provider failure."""
    key = text
    with _tts_lock:
        if key in _tts_cache:
            _tts_cache.move_to_end(key)
            return _tts_cache[key]
    r = httpx.post(
        f"https://api.elevenlabs.io/v1/text-to-speech/{ELEVENLABS_VOICE_ID}",
        params={"output_format": "mp3_44100_128"},
        headers={"xi-api-key": config.ELEVENLABS_API_KEY,
                 "Content-Type": "application/json"},
        json={"text": text, "model_id": ELEVEN_TTS_MODEL},
        timeout=60,
    )
    r.raise_for_status()
    audio = r.content
    with _tts_lock:
        _tts_cache[key] = audio
        _tts_cache.move_to_end(key)
        while len(_tts_cache) > _TTS_CACHE_MAX:
            _tts_cache.popitem(last=False)
    return audio


def extract_memory(user_text: str, ctx: dict | None) -> list[dict]:
    """0-4 memory items from the learner's OWN message. NEVER raises — memory
    is enrichment, and a failure must not affect the turn. Kind validation is
    duplicated in memory.remember (defense in depth: this bound is the
    model's, that one is the store's)."""
    try:
        from google.genai import types
        resp = _client().models.generate_content(
            model=config.GEMINI_SAFETY_MODEL,
            contents=prompts.MEMORY_EXTRACTION_PROMPT.format(user_text=user_text[:2000]),
            config=types.GenerateContentConfig(
                response_mime_type="application/json", temperature=0.0),
        )
        data = json.loads(resp.text)
        items = []
        for item in data.get("items", [])[:4]:
            if not isinstance(item, dict):
                continue
            if item.get("kind") not in ("fact", "concern", "symptom", "preference"):
                continue
            content = str(item.get("content", "")).strip()
            if content:
                items.append({"kind": item["kind"], "content": content[:200]})
        return items
    except Exception as e:  # noqa: BLE001
        log.warning("memory extraction failed (turn unaffected): %s", e)
        return []


def suggest_cards(user_text: str, reply: str, ctx: dict | None) -> list[dict]:
    """0-3 typed action cards for this turn. NEVER raises: cards are garnish,
    and a failure here must not cost the user their reply."""
    try:
        from google.genai import types
        catalog = "\n".join(f"- {k}: {v}" for k, v in prompts.CARD_TYPES.items())
        resp = _client().models.generate_content(
            model=config.GEMINI_SAFETY_MODEL,
            contents=prompts.CARD_SUGGESTION_PROMPT.format(
                catalog=catalog, user_text=user_text[:2000], reply=reply[:2000]),
            config=types.GenerateContentConfig(
                response_mime_type="application/json", temperature=0.0),
        )
        data = json.loads(resp.text)
        cards = []
        for card in data.get("cards", [])[:3]:
            if not isinstance(card, dict) or card.get("type") not in prompts.CARD_TYPES:
                continue
            cards.append({
                "type": card["type"],
                "title": str(card.get("title", ""))[:60],
                "subtitle": str(card.get("subtitle", ""))[:120],
            })
        return cards
    except Exception as e:  # noqa: BLE001
        log.warning("card suggestion failed (reply stands alone): %s", e)
        return []
