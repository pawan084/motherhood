"""The safety gate's DECISION — screens a message and returns a level.

Two layers, combined toward caution:

 1. A deterministic keyword pre-filter (`_keyword_level`). Always runs, needs no
    network, and catches the obvious emergencies even when the LLM is
    misconfigured or down. This is the fail-safe floor.
 2. The Gemini classifier (prompts `aira.safety_classifier`). Catches nuance the
    keywords miss.

The final level is the MORE urgent of the two. A `red` result short-circuits the
turn (chat.py never sends it to the reply model) and routes the client to the
urgent-care handoff.

This module holds NO database. Deciding whether somebody is in danger and
recording that we decided it are different jobs, and only the second one can
fail on a disk. `screen()` is a pure function of its arguments plus the prompt
registry, so it can be exercised exhaustively with no fixtures — see
`flags.py` for the persistence half, which is what the admin console reviews.

The gate lives server-side on purpose: the web and native prototypes each
shipped only half of the safety flow, and drifted. One gate here means every
client routes urgent input identically and correctly.
"""
import logging
import re

from app import prompts
from app.core import llm

log = logging.getLogger("aira.safety")

GREEN, AMBER, RED = "green", "amber", "red"
_RANK = {GREEN: 0, AMBER: 1, RED: 2}


def worse(a: str, b: str) -> str:
    return a if _RANK.get(a, 0) >= _RANK.get(b, 0) else b


# Curated phrase lists, matched against a NORMALISED message — see _normalise.
# English plus common Hindi/Hinglish transliterations, since Aira's audience
# uses all three.
#
# Every entry here is written without apostrophes because the message has had
# them stripped before matching. That one step closes a whole class of misses:
# the list previously held "can't breathe" and a user typing "i cant breathe"
# — which is how most people type on a phone — was screened GREEN and handed a
# normal AI reply.
# The languages this floor can actually read.
#
# This is a safety boundary, not a feature list. RED_PHRASES and _RED_PATTERNS
# below are English plus common Hindi/Hinglish transliterations, so a message in
# any other language reaches the deterministic floor and passes straight
# through. Offering a language the floor cannot read invites somebody to write
# in it — and when the LLM classifier is unavailable, which is the fallback
# state the floor exists for, nothing else is looking.
#
# The web client offered Spanish. Verified before this was added: "mi bebe no se
# mueve" — my baby isn't moving, the single most important signal here —
# screened GREEN, as did "sangrado abundante y no puedo respirar".
#
# Adding a language means adding its red-flag vocabulary here FIRST, checked by
# someone who speaks it. `test_language_coverage.py` holds this list to that.
SUPPORTED_LANGUAGES = ("English", "Hindi", "Hinglish")

RED_PHRASES = (
    "heavy bleeding", "bleeding heavily", "wont stop bleeding", "cannot stop bleeding",
    "gushing blood", "lot of blood", "so much blood", "severe bleeding",
    "soaking a pad", "soaking pads", "filling a pad", "bahut khoon", "khoon bah raha",
    "chest pain", "chest hurts", "cant breathe", "cannot breathe", "trouble breathing",
    "struggling to breathe", "saans nahi",
    "severe pain", "unbearable pain", "worst headache", "vision blur", "blurred vision",
    "blurry vision", "seeing spots", "flashing lights", "fainted", "passed out",
    "seizure", "convulsion",
    "water broke", "water breaking",
    "kill myself", "end my life", "end it all", "suicidal", "want to die",
    "hurt myself", "harm myself", "hurting myself", "harming myself",
    "harm my baby", "hurt my baby", "hurt the baby", "overdose", "cant go on",
    "dont want to be here", "no reason to live",
)

# Some red flags are a shape, not a phrase. Fetal movement is the clearest
# case: "baby isnt moving", "havent felt the baby move", "no kicks since last
# night" and "the baby stopped moving" all describe the same emergency and
# share almost no words. Substring matching cannot express that, and reduced
# fetal movement is among the most important things this floor has to catch.
_RED_PATTERNS = (
    re.compile(r"\b(baby|bub|bump|little one)\b[^.?!]{0,30}"
               r"\b(isnt|is not|hasnt|has not|not|stopped|arent|are not)\b"
               r"[^.?!]{0,15}\b(mov\w*|kick\w*|active)\b"),
    re.compile(r"\b(no|any|fewer|less|reduced|hardly any)\b[^.?!]{0,15}"
               r"\b(kicks?|movements?|movement)\b"),
    re.compile(r"\b(havent|have not|hasnt|has not|not)\b[^.?!]{0,20}"
               r"\bfelt\b[^.?!]{0,20}\b(baby|move|moving|kick|kicks)\b"),
    # Self-harm phrased as intent rather than with the listed nouns.
    re.compile(r"\b(want|going|thinking|plan|planning)\b[^.?!]{0,20}"
               r"\b(end|kill|hurt|harm)\b[^.?!]{0,12}"
               r"\b(it|me|myself|my life|things|it all)\b"),
)

_AMBER_PHRASES = (
    "bleeding", "spotting", "cramping", "cramps", "contractions", "headache",
    "dizzy", "dizziness", "swelling", "swollen", "fever", "vomiting", "throwing up",
    "reduced movement", "less movement", "leaking", "blurry", "anxious", "panic",
    "depressed", "hopeless", "cant sleep", "so tired", "burning pee",
    "pain when", "hurts a lot", "dard", "bukhar", "chakkar",
)

# Apostrophes (straight and curly) are removed rather than replaced, so
# "isn't" becomes "isnt" and matches an entry written that way. Everything else
# non-alphanumeric collapses to a space so punctuation can't split a phrase.
_APOSTROPHES = str.maketrans("", "", "'’ʼ`")


def _normalise(message: str) -> str:
    lowered = (message or "").lower().translate(_APOSTROPHES)
    return re.sub(r"[^a-z0-9]+", " ", lowered).strip()


def _keyword_level(message: str) -> tuple[str, list[str]]:
    """Deterministic floor. Returns (level, matched_categories).

    This is the ONLY screening when no classifier is configured — which is the
    state this app currently ships in — so a miss here is not a degraded
    experience, it is no screening at all for that message.
    """
    m = _normalise(message)
    red = [p for p in RED_PHRASES if p in m]
    red += [p.pattern[:28] for p in _RED_PATTERNS if p.search(m)]
    if red:
        return RED, red
    amber = [p for p in _AMBER_PHRASES if p in m]
    if amber:
        return AMBER, amber
    return GREEN, []


def _llm_level(message: str, history: list[dict]) -> dict | None:
    """The classifier's verdict, or None when the LLM is unavailable/unparseable
    (so the caller falls back to the keyword floor rather than failing open)."""
    if not llm.configured():
        return None
    system = prompts.resolve("aira.safety_classifier", prompts.SAFETY_CLASSIFIER)
    convo = "\n".join(f"{h['role']}: {h['content']}" for h in (history or [])[-6:])
    user = (f"Recent conversation:\n{convo}\n\n" if convo else "") + f"Latest message:\n{message}"
    try:
        data = llm.gemini_json(system, user, temperature=0.0,
                                    model=llm.SAFETY_MODEL)
    except Exception as e:  # noqa: BLE001 — never let the classifier crash a turn
        log.warning("safety classifier call failed: %s", e)
        return None
    level = str(data.get("level", "")).strip().lower()
    if level not in _RANK:
        return None
    cats = data.get("categories")
    cats = [str(c) for c in cats] if isinstance(cats, list) else []
    return {"level": level, "categories": cats, "reason": str(data.get("reason", ""))}


def screen(message: str, history: list[dict] | None = None,
           ctx: dict | None = None) -> dict:
    """Screen one message. Returns:
        {level, categories[], reason, degraded, keyword_level, llm_level}
    `degraded` is True when the LLM layer was unavailable and only the keyword
    floor applied — the client should still honor `level`, but the admin console
    surfaces it so a silent classifier outage is visible."""
    kw_level, kw_cats = _keyword_level(message)
    llm = _llm_level(message, history or [])

    if llm is None:
        return {"level": kw_level, "categories": kw_cats,
                "reason": "keyword-only (classifier unavailable)",
                "degraded": True, "keyword_level": kw_level, "llm_level": None}

    final = worse(kw_level, llm["level"])
    cats = sorted(set(kw_cats) | set(llm["categories"]))
    return {"level": final, "categories": cats, "reason": llm["reason"],
            "degraded": False, "keyword_level": kw_level, "llm_level": llm["level"]}


