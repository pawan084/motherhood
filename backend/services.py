"""Gemini access. One provider does every LLM call — the safety classifier and
the Aira reply. There is no second provider to fall back to, so callers wrap
these in try/except and surface a clean per-request error.

Kept deliberately small: two entry points (`gemini_json`, `gemini_text`) plus a
health probe for the admin console. The prompts themselves live in the
admin-editable registry (prompts.py); this module only talks to the API.
"""
import json
import logging
import os
import threading
import time

log = logging.getLogger("aira.services")

GEMINI_KEY = os.environ.get("GEMINI_API_KEY", "")
GEMINI_KEY_NAME = "GEMINI_API_KEY"  # one name the admin "is an LLM configured?" guard reads
GEMINI_MODEL = os.environ.get("AIRA_GEMINI_MODEL", "gemini-2.5-flash")
SAFETY_MODEL = os.environ.get("AIRA_SAFETY_MODEL", GEMINI_MODEL)
_MAX_TOKENS = int(os.environ.get("AIRA_GEMINI_MAX_TOKENS", "640"))

# Warn (don't crash) on a missing key so /health stays up for platform probes
# and the failure surfaces as a clean per-request error.
if not GEMINI_KEY:
    log.warning("GEMINI_API_KEY is not set — chat and safety calls will fail until it is.")

_client_singleton = None
_lock = threading.Lock()


def _client():
    """Lazily build a shared Gemini client. Kept lazy (not module-level) so
    importing services without GEMINI_API_KEY never fails at import time."""
    global _client_singleton
    if _client_singleton is None:
        with _lock:
            if _client_singleton is None:
                if not GEMINI_KEY:
                    raise RuntimeError("GEMINI_API_KEY not set")
                from google import genai  # lazy import: only needed when a call runs
                _client_singleton = genai.Client(api_key=GEMINI_KEY)
    return _client_singleton


def _generate(system: str, user: str, temperature: float, max_tokens: int,
              model: str | None, json_out: bool) -> str:
    from google.genai import types  # lazy
    cfg: dict = {"temperature": temperature, "max_output_tokens": max_tokens,
                 # Thinking off: measurably faster time-to-first-token, and these
                 # are structured/classification calls that don't need it.
                 "thinking_config": types.ThinkingConfig(thinking_budget=0)}
    if json_out:
        cfg["response_mime_type"] = "application/json"
    # Gemini has no separate system role, so system + user are concatenated.
    resp = _client().models.generate_content(
        model=model or GEMINI_MODEL,
        contents=[f"{system}\n\n{user}"],
        config=types.GenerateContentConfig(**cfg))
    return (resp.text or "").strip()


def gemini_json(system: str, user: str, temperature: float = 0.4,
                max_tokens: int | None = None, model: str | None = None) -> dict:
    """One-shot Gemini completion parsed as a JSON object. Returns {} when the
    model returns anything that isn't a JSON object — callers treat that as
    "no structured result" and fall back."""
    raw = _generate(system, user, temperature, max_tokens or _MAX_TOKENS, model, json_out=True)
    try:
        data = json.loads(raw)
    except (json.JSONDecodeError, ValueError, TypeError):
        log.warning("Gemini returned non-JSON: %r", raw[:120])
        return {}
    return data if isinstance(data, dict) else {}


def gemini_stream(system: str, user: str, temperature: float = 0.6,
                  max_tokens: int | None = None, model: str | None = None):
    """Yield the model's answer as it arrives.

    Used only for the conversational reply. Everything else here stays one-shot:
    the safety classifier is a decision, not a performance, and streaming a
    decision would mean acting on half of one.
    """
    from google.genai import types  # lazy, as above
    cfg = types.GenerateContentConfig(
        temperature=temperature,
        max_output_tokens=max_tokens or _MAX_TOKENS,
        thinking_config=types.ThinkingConfig(thinking_budget=0),
    )
    for chunk in _client().models.generate_content_stream(
            model=model or GEMINI_MODEL,
            contents=[f"{system}\n\n{user}"],
            config=cfg):
        text = getattr(chunk, "text", None)
        if text:
            yield text


def gemini_text(system: str, user: str, temperature: float = 0.6,
                max_tokens: int | None = None, model: str | None = None) -> str:
    return _generate(system, user, temperature, max_tokens or _MAX_TOKENS, model, json_out=False)


def configured() -> bool:
    return bool(GEMINI_KEY)


def provider_health() -> dict:
    """A cheap liveness probe for the admin System page: is a key present, and
    does a tiny generate call succeed? Never raises — reports the error instead."""
    if not GEMINI_KEY:
        return {"name": "gemini", "status": "unconfigured", "ms": None,
                "error": f"{GEMINI_KEY_NAME} not set"}
    t0 = time.time()
    try:
        _generate("You are a health probe.", 'Reply with {"ok":true}', 0.0, 16,
                  SAFETY_MODEL, json_out=True)
        return {"name": "gemini", "status": "ok",
                "ms": int((time.time() - t0) * 1000)}
    except Exception as e:  # noqa: BLE001
        return {"name": "gemini", "status": "error",
                "ms": int((time.time() - t0) * 1000), "error": str(e)[:200]}
