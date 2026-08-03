"""The clinical safety gate. Screens EVERY user input before any reply is shown.

Layered:
  1. Deterministic rules (safety_taxonomy.py) — microseconds, offline, and a
     clinician-reviewable data file.
  2. A Gemini classifier — nuance the rules can't do: negation, reported
     speech, questions-about vs. reports-of, typos, code-switching.

Decision vocabulary (the gate's public contract, used by P3 and the client):
  urgent   route to Urgent Help. No AI reply.
  caution  reply proceeds, but leads with support and surfaces the care team.
  ok       reply proceeds normally.
  error    the gate could not verify safety (provider down/timeout on a clean
           input). NO reply may be generated. The client shows a safe error
           with the ever-present Urgent Help affordance — honest, without
           full urgent framing on what is probably a benign message.

Decision matrix — the deterministic floors are the point:

  rule hits                LLM says          decision
  ─────────────────────    ──────────────    ─────────────────────────────
  self-harm (non-neg.)     (not consulted)   urgent          ← rules alone
  urgent rule (non-neg.)   urgent            urgent
  urgent rule (non-neg.)   caution/ok        caution         ← FLOOR: a real
                                             symptom mention never renders
                                             as a plain reply
  urgent rule (non-neg.)   error/timeout     urgent          ← fail closed
  negated/uncertain hit    any               follow LLM
  negated/uncertain hit    error/timeout     urgent          ← fail closed
  caution rule             ok                caution         ← floor
  caution rule             urgent            urgent
  caution rule             error/timeout     caution
  none                     any               follow LLM
  none                     error/timeout     error           ← no unverified reply

Every screen() writes an audit row — input, context, decision, rule hits,
source, latency — so a reviewer can reconstruct why any turn did or didn't
escalate (and so the P10 admin review queue has something to read).
"""
import json
import logging
import re
import secrets
import time
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FutureTimeout
from dataclasses import dataclass, field

import config
import db
import safety_taxonomy as taxonomy

log = logging.getLogger("aira.safety")

_conn = None
# The LLM call runs in a worker thread so we can enforce a hard timeout budget;
# the gate is on the critical path of every turn.
_executor = ThreadPoolExecutor(max_workers=8, thread_name_prefix="safety-llm")

# How many whitespace-separated tokens on EITHER side of a match may hold a
# negation marker ("no bleeding" / "khoon nahi aa raha" — Hindi negates after
# the noun).
_NEGATION_WINDOW = 4


@dataclass
class GateResult:
    decision: str                    # urgent | caution | ok | error
    source: str                      # rules | llm | rules+llm | error
    matched: list = field(default_factory=list)   # rule ids that hit
    reason: str = ""
    latency_ms: int = 0


def init() -> None:
    global _conn
    if _conn is not None:
        return
    c = db.connect()
    c.execute("CREATE TABLE IF NOT EXISTS safety_audit ("
              " id TEXT PRIMARY KEY,"
              " learner_id TEXT,"
              " ts REAL,"
              " input TEXT,"
              " stage TEXT,"
              " week INTEGER,"
              " decision TEXT,"
              " source TEXT,"
              " matched TEXT,"
              " reason TEXT,"
              " latency_ms INTEGER)")
    c.execute("CREATE INDEX IF NOT EXISTS idx_safety_audit_learner"
              " ON safety_audit (learner_id, ts)")
    c.commit()
    _conn = c


# --- deterministic layer -----------------------------------------------------

def _compile_term(term: str) -> re.Pattern:
    # Word-boundary the edges so "khun" doesn't fire inside "khunkhar", but
    # allow the term's own internal punctuation/spacing verbatim.
    return re.compile(rf"(?<!\w){re.escape(term)}(?!\w)", re.IGNORECASE)


_COMPILED: dict[str, list] = {}


def _compiled(kind: str, rules) -> list:
    if kind not in _COMPILED:
        _COMPILED[kind] = [
            (rule,
             [_compile_term(t) for t in rule["terms"]],
             [_compile_term(t) for t in rule.get("denial_terms", ())])
            for rule in rules
        ]
    return _COMPILED[kind]


def _is_negated(text: str, start: int, end: int) -> bool:
    """True when a negation marker sits within the window before OR after the
    match. Deliberately does NOT scan inside the match: taxonomy terms may
    contain negation words that ARE the danger sign ("no kicks", "can't feel
    the baby" — absence is the symptom). Denial phrasings that contain their
    own negation ("khoon nahi") are modeled as `denial_terms` in the taxonomy
    instead. Downgrades to UNCERTAIN (LLM decides) — never clears outright."""
    before = text[:start].split()[-_NEGATION_WINDOW:]
    after = text[end:].split()[:_NEGATION_WINDOW]
    for token in (*before, *after):
        stripped = token.strip(".,!?;:'\"()").lower()
        for marker in taxonomy.NEGATION_MARKERS:
            if stripped == marker or (marker == "n't" and stripped.endswith("n't")):
                return True
    return False


def match_rules(text: str, stage: str | None) -> dict:
    """Run the taxonomy against one input.

    Returns {"urgent": [ids], "uncertain": [ids], "caution": [ids]} where
    `uncertain` holds urgent-rule hits that appeared in a negated context.
    An unknown/missing stage applies EVERY rule — missing context widens the
    net, never narrows it.
    """
    hits = {"urgent": [], "uncertain": [], "caution": []}
    lowered = text.lower()
    for rule, patterns, denial_patterns in _compiled("urgent", taxonomy.URGENT_RULES):
        if stage in config.JOURNEY_STAGES and stage not in rule["stages"]:
            continue
        for pat in patterns:
            m = pat.search(lowered)
            if not m:
                continue
            bucket = "uncertain" if _is_negated(lowered, m.start(), m.end()) else "urgent"
            hits[bucket].append(rule["id"])
            break
        else:
            # Denial phrasings ("khoon nahi") carry their own negation: they go
            # straight to UNCERTAIN for the LLM to adjudicate, never cleared.
            if any(p.search(lowered) for p in denial_patterns):
                hits["uncertain"].append(rule["id"])
    for rule, patterns, _denials in _compiled("caution", taxonomy.CAUTION_RULES):
        if stage in config.JOURNEY_STAGES and stage not in rule["stages"]:
            continue
        if any(p.search(lowered) for p in patterns):
            hits["caution"].append(rule["id"])
    return hits


# --- LLM layer ---------------------------------------------------------------

_CLASSIFIER_PROMPT = """\
You are the safety triage layer of a maternal-health companion app. Classify ONE
user message. The user is {stage_desc}.

Reply with JSON only: {{"decision": "...", "reason": "..."}}

decision must be exactly one of:
- "urgent": the message reports a CURRENT symptom or situation that maternal-health
  guidance treats as a danger sign: vaginal bleeding, severe headache or vision
  changes, reduced or absent fetal movement, severe abdominal pain, high fever,
  trouble breathing or chest pain, waters breaking early, seizure or fainting,
  thoughts of self-harm or harming the baby. When genuinely unsure whether the
  person is reporting a danger sign RIGHT NOW, choose "urgent".
- "caution": emotional distress, possible depression or anxiety signals, a mild or
  ambiguous physical complaint, or a worried question about a danger-sign topic
  that does NOT report the symptom as currently happening.
- "ok": everything else — general questions, chit-chat, logging routine facts.

Notes:
- Questions ABOUT a symptom ("what should I do if I ever have bleeding?") are not
  reports OF the symptom. Classify "caution" if worried in tone, else "ok".
- Denials ("no bleeding, feeling fine") are "ok" unless something else worries you.
- Reports about a third party ("my friend had bleeding") are not the user's own
  danger sign; usually "ok".
- The message may be in English, Hindi, or romanized Hinglish. Typos are common —
  read for meaning.
- reason: one short sentence, in English.

User message:
{text}
"""

_STAGE_DESC = {
    "pregnant": "pregnant (gestational week {week})",
    "postpartum": "postpartum (week {week} after birth)",
    "trying_to_conceive": "trying to conceive",
}


def _classify_llm(text: str, stage: str | None, week: int | None) -> dict:
    """One Gemini call → {"decision": ..., "reason": ...}. Raises on any failure;
    the caller owns fail-closed semantics. Uses the process-wide shared client
    (services._client) — per-call Clients intermittently die under threading."""
    from google.genai import types

    import services

    stage_desc = _STAGE_DESC.get(stage or "", "an expecting or new parent (stage unknown)")
    stage_desc = stage_desc.format(week=week if week is not None else "unknown")
    resp = services._client().models.generate_content(
        model=config.GEMINI_SAFETY_MODEL,
        contents=_CLASSIFIER_PROMPT.format(stage_desc=stage_desc, text=text[:4000]),
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            temperature=0.0,
        ),
    )
    data = json.loads(resp.text)
    if data.get("decision") not in ("urgent", "caution", "ok"):
        raise ValueError(f"classifier returned invalid decision: {data!r}")
    return {"decision": data["decision"], "reason": str(data.get("reason", ""))[:300]}


def _classify_with_timeout(text: str, stage: str | None, week: int | None) -> dict:
    future = _executor.submit(_classify_llm, text, stage, week)
    try:
        return future.result(timeout=config.SAFETY_LLM_TIMEOUT_MS / 1000)
    except FutureTimeout:
        future.cancel()
        raise TimeoutError(f"safety classifier exceeded {config.SAFETY_LLM_TIMEOUT_MS}ms")


# --- the gate ----------------------------------------------------------------

def screen(learner_id: str, text: str, stage: str | None = None,
           week: int | None = None) -> GateResult:
    """Screen one user input. Always audits; never raises."""
    started = time.time()
    hits = match_rules(text, stage)

    # Self-harm: deterministic, immediate, no network on the path.
    if "self_harm_risk" in hits["urgent"]:
        result = GateResult("urgent", "rules", hits["urgent"],
                            "self-harm risk terms matched")
        return _finish(learner_id, text, stage, week, result, started)

    try:
        llm = _classify_with_timeout(text, stage, week)
    except Exception as e:  # noqa: BLE001 — every failure mode fails closed below
        log.warning("safety classifier unavailable: %s", e)
        if hits["urgent"] or hits["uncertain"]:
            # A possible danger sign we couldn't adjudicate → escalate.
            result = GateResult("urgent", "error",
                                hits["urgent"] + hits["uncertain"],
                                f"classifier unavailable with rule hits ({e})")
        elif hits["caution"]:
            result = GateResult("caution", "error", hits["caution"],
                                f"classifier unavailable; caution floor stands ({e})")
        else:
            # Clean input we couldn't verify → no reply may be generated.
            result = GateResult("error", "error", [],
                                f"classifier unavailable ({e})")
        return _finish(learner_id, text, stage, week, result, started)

    matched = hits["urgent"] + hits["uncertain"] + hits["caution"]
    if hits["urgent"]:
        # Floor: a non-negated urgent-rule hit never renders as a plain reply.
        decision = "urgent" if llm["decision"] == "urgent" else "caution"
        source = "rules+llm"
    elif hits["caution"]:
        decision = "urgent" if llm["decision"] == "urgent" else "caution"
        source = "rules+llm"
    else:
        # No rule hits (or negated-only, which the LLM adjudicates freely).
        decision = llm["decision"]
        source = "llm"
    result = GateResult(decision, source, matched, llm["reason"])
    return _finish(learner_id, text, stage, week, result, started)


def _finish(learner_id: str, text: str, stage: str | None, week: int | None,
            result: GateResult, started: float) -> GateResult:
    result.latency_ms = int((time.time() - started) * 1000)
    try:
        _conn.execute(
            "INSERT INTO safety_audit (id, learner_id, ts, input, stage, week,"
            " decision, source, matched, reason, latency_ms)"
            " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            ("sa_" + secrets.token_hex(12), learner_id, time.time(), text,
             stage or "", week, result.decision, result.source,
             json.dumps(result.matched), result.reason, result.latency_ms))
        _conn.commit()
    except Exception:  # noqa: BLE001
        # An audit failure must not take down the decision path — but it is
        # loud: losing the audit trail is an incident, not a debug line.
        log.exception("safety audit write FAILED (decision=%s)", result.decision)
    return result
