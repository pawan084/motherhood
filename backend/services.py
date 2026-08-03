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

import config
import prompts

log = logging.getLogger("aira.services")


def _client():
    from google import genai
    return genai.Client(api_key=config.GEMINI_API_KEY)


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
