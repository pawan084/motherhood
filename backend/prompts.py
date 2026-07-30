"""Admin-editable prompt registry.

Every LLM prompt Aira sends has an in-code default (the literals below) AND a
DB row that an admin can override from the console without a deploy. Call sites
use `resolve(key, DEFAULT)` so a missing/blank row always falls back to the
reviewed in-code text — an admin can never blank out a safety-critical prompt.

`fill(template, **kw)` does a forgiving `{name}` substitution (a stray brace or a
missing key is left as-is rather than raising), so an admin edit that drops a
placeholder degrades to literal text instead of a 500.
"""
import logging
import string

import db

log = logging.getLogger("aira.prompts")
_conn = None


# ── In-code defaults (the reviewed source of truth) ──────────────────────────

# Aira's reply persona. Deliberately conservative: wellness support, never
# diagnosis, always a visible care-team handoff. The safety LEVEL is decided by
# the classifier (safety.py) BEFORE this prompt runs; a red turn never reaches
# the model at all, so this prompt only ever produces green/amber replies.
AIRA_SYSTEM = (
    "You are Aira, a warm, calm maternal-wellness companion for someone who is "
    "{journey_phrase}. You are NOT a doctor and you never diagnose, prescribe, or "
    "tell someone to start/stop/change any medication. You give gentle, everyday "
    "wellness support and you always keep the person's care team in view.\n"
    "The person's name is {name}. Address them warmly by name when it feels "
    "natural. Their preferred language is {language} — reply in that language.\n"
    "SAFETY TONE for this turn is {trust_label}. If it is 'watchful', gently name "
    "that this is worth raising with their care team and offer to help them "
    "prepare, without alarming them. Never present your words as medical advice.\n"
    "Keep replies short (2-4 sentences), specific, and kind. Do not produce lists "
    "or clinical jargon. When a concrete next step would genuinely help, describe "
    "ONE action card (see the schema) — never more than one."
)

# The single free-form reply schema. The classifier already set safety, so the
# model returns the reply text, an optional single action card, and nothing that
# could be read as a prescription.
AIRA_STREAM_SCHEMA = (
    "Write your message to the person first, as plain prose — no JSON, no "
    "markdown fences, nothing before it.\n"
    "Then, on its own line, write exactly ---AIRA--- and after it a JSON object "
    'with these keys: "action_card": either null, or an object {"tool": one of '
    '["checkin","reminder","appointment","upload","wellness","symptom","careplan","support"], '
    '"title": a short label, "detail": one short line} describing the ONE next '
    "step that would help — use null when no action is warranted. "
    '"disclaimer_needed": true if this reply touches anything the person should '
    "confirm with their care team, else false."
)

# The prose comes first on purpose: it is what the person is waiting for, and it
# can start rendering before the model has decided anything about a card. A
# schema that put the JSON first would stream a wall of syntax and then the
# answer, which is slower to read than not streaming at all.
STREAM_DELIMITER = "---AIRA---"

AIRA_REPLY_SCHEMA = (
    "Respond with ONLY a JSON object (no markdown fences) with these keys: "
    '"reply": your message to the person as a plain string. '
    '"action_card": either null, or an object {"tool": one of '
    '["checkin","reminder","appointment","upload","wellness","symptom","careplan","support"], '
    '"title": a short label, "detail": one short line} describing the ONE next '
    "step that would help — use null when no action is warranted. "
    '"disclaimer_needed": true if this reply touches anything the person should '
    "confirm with their care team, else false."
)

# The safety classifier. Runs on EVERY inbound message before any reply. Its
# whole job is to sort input into the green/amber/red scale from the product's
# safety section. RED short-circuits the turn into the urgent-care handoff.
SAFETY_CLASSIFIER = (
    "You are the safety gate for Aira, a maternal-wellness companion (users are "
    "trying to conceive, pregnant, or postpartum). Read the person's message and "
    "classify the level of care it needs. Be cautious: when unsure between two "
    "levels, choose the MORE urgent one.\n"
    "LEVELS:\n"
    "- green: everyday wellness, questions, feelings, logistics. Safe for Aira to "
    "answer with gentle support.\n"
    "- amber: a symptom or situation that warrants watchful guidance and prompting "
    "the person to contact their care team soon (e.g. persistent headache, "
    "reduced fetal movement worry, low mood, moderate pain).\n"
    "- red: a possible emergency or crisis where the person must contact their "
    "care team or emergency services NOW and should not wait for an AI reply — "
    "e.g. heavy bleeding, severe abdominal/chest pain, trouble breathing, "
    "fainting, seizure, thoughts of self-harm or harming the baby, signs of "
    "severe pre-eclampsia (severe headache with vision changes), water breaking "
    "with concern, no fetal movement.\n"
    "Respond with ONLY a JSON object with keys: "
    '"level": "green" | "amber" | "red"; '
    '"reason": a short internal note (never shown to the user); '
    '"categories": an array of short tags (e.g. ["bleeding"], ["self_harm"], []).'
)

# Journey → a natural-language phrase spliced into the system prompt so the
# reply is grounded in the right life stage (fixes the prototype's "everyone is
# 24-weeks-pregnant" bug). Kept in the registry so copy is admin-tunable.
JOURNEY_PHRASE_TRYING = "trying to conceive"
JOURNEY_PHRASE_PREGNANT = "pregnant"
JOURNEY_PHRASE_POSTPARTUM = "in the postpartum period, recovering and caring for a newborn"
JOURNEY_PHRASE_EXPLORING = "exploring maternal-wellness support"


_DEFAULTS = {
    "aira.system": AIRA_SYSTEM,
    "aira.reply_schema": AIRA_REPLY_SCHEMA,
    "aira.safety_classifier": SAFETY_CLASSIFIER,
    "aira.journey.trying": JOURNEY_PHRASE_TRYING,
    "aira.journey.pregnant": JOURNEY_PHRASE_PREGNANT,
    "aira.journey.postpartum": JOURNEY_PHRASE_POSTPARTUM,
    "aira.journey.exploring": JOURNEY_PHRASE_EXPLORING,
}


# ── Registry storage ─────────────────────────────────────────────────────────

def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS prompts ("
              " key TEXT PRIMARY KEY, text TEXT NOT NULL, model TEXT DEFAULT '',"
              " updated REAL, updated_by TEXT DEFAULT '')")
    c.commit()
    _conn = c


def seed_defaults() -> None:
    """Insert any missing default rows. Idempotent and never clobbers an admin
    edit (ON CONFLICT DO NOTHING), so the in-code defaults only fill gaps."""
    init()
    import time
    for key, text in _DEFAULTS.items():
        _conn.execute("INSERT INTO prompts (key, text, updated) VALUES (?,?,?) "
                      "ON CONFLICT(key) DO NOTHING", (key, text, time.time()))
    _conn.commit()


def resolve(key: str, default: str) -> str:
    """The admin-overridden text for `key`, or the in-code `default` when there's
    no row or the row is blank. Reads live so an admin edit applies with no
    restart. Falls back to `default` if the registry isn't initialized yet."""
    if _conn is None:
        return default
    try:
        row = _conn.execute("SELECT text FROM prompts WHERE key=?", (key,)).fetchone()
    except Exception as e:  # noqa: BLE001 — never let a prompt lookup break a turn
        log.warning("prompt resolve(%s) failed: %s", key, e)
        return default
    text = (row[0] if row else "") or ""
    return text.strip() or default


class _SafeDict(dict):
    def __missing__(self, key):
        return "{" + key + "}"


def fill(template: str, **kw) -> str:
    """Forgiving `{name}` substitution: an unknown placeholder or a stray brace is
    left literal rather than raising, so an admin edit can't 500 a turn."""
    try:
        return string.Formatter().vformat(template, (), _SafeDict(**kw))
    except (ValueError, IndexError, KeyError):
        return template


def all_rows() -> list[dict]:
    """Every registry row (admin listing), annotated with whether it still
    matches the in-code default."""
    init()
    out = []
    for key, text, model, updated, by in _conn.execute(
            "SELECT key, text, model, updated, updated_by FROM prompts ORDER BY key"):
        out.append({"key": key, "text": text, "model": model or "",
                    "updated": updated, "updated_by": by or "",
                    "is_default": text == _DEFAULTS.get(key),
                    "has_default": key in _DEFAULTS})
    return out


def set_row(key: str, text: str, actor: str = "") -> None:
    init()
    import time
    _conn.execute("INSERT INTO prompts (key, text, updated, updated_by) VALUES (?,?,?,?) "
                  "ON CONFLICT(key) DO UPDATE SET text=excluded.text, "
                  "updated=excluded.updated, updated_by=excluded.updated_by",
                  (key, text, time.time(), actor))
    _conn.commit()


def default_for(key: str) -> str | None:
    return _DEFAULTS.get(key)
