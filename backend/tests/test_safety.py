"""P2: the safety gate's adversarial suite.

The LLM layer is mocked — these tests pin the DETERMINISTIC guarantees, which
must hold no matter what the model does: the floors, the fail-closed paths, the
negation handling, and the audit trail. False negatives are the dangerous
direction, so most cases here assert that something does NOT quietly render as
a plain reply.

(Live classifier behaviour gets its own recorded-fixture evals once a Gemini
key exists — tracked in TODO.md.)
"""
import importlib
import sys
import time

import pytest


@pytest.fixture()
def gate(tmp_path, monkeypatch):
    monkeypatch.setenv("SQLITE_PATH", str(tmp_path / "safety.db"))
    monkeypatch.setenv("SAFETY_LLM_TIMEOUT_MS", "400")
    monkeypatch.delenv("DATABASE_URL", raising=False)
    for mod in ("safety", "safety_taxonomy", "db", "config"):
        sys.modules.pop(mod, None)
    safety = importlib.import_module("safety")
    safety.init()
    return safety


class LLMStub:
    """Replaces safety._classify_llm; records whether it was consulted."""

    def __init__(self, decision="ok", reason="stub", error: Exception | None = None,
                 delay: float = 0.0):
        self.decision, self.reason, self.error, self.delay = decision, reason, error, delay
        self.calls = 0

    def __call__(self, text, stage, week):
        self.calls += 1
        if self.delay:
            time.sleep(self.delay)
        if self.error:
            raise self.error
        return {"decision": self.decision, "reason": self.reason}


def _stub(gate, monkeypatch, **kw) -> LLMStub:
    stub = LLMStub(**kw)
    monkeypatch.setattr(gate, "_classify_llm", stub)
    return stub


# --- self-harm: deterministic, immediate, offline ----------------------------

@pytest.mark.parametrize("text", [
    "I want to kill myself",
    "some days I just want to die",
    "i'm scared I might hurt the baby",
    "जान दे दूंगी अब",
    "bas marna chahti hoon",
])
def test_self_harm_is_urgent_without_consulting_the_llm(gate, monkeypatch, text):
    stub = _stub(gate, monkeypatch, decision="ok")  # even a broken LLM saying ok
    r = gate.screen("L1", text, "postpartum", 3)
    assert r.decision == "urgent"
    assert r.source == "rules"
    assert stub.calls == 0  # no network on this path


# --- the caution floor -------------------------------------------------------

def test_symptom_report_llm_urgent_is_urgent(gate, monkeypatch):
    _stub(gate, monkeypatch, decision="urgent", reason="active bleeding")
    r = gate.screen("L1", "I am bleeding quite a lot", "pregnant", 24)
    assert r.decision == "urgent"
    assert "vaginal_bleeding" in r.matched


def test_symptom_mention_never_renders_plain_even_if_llm_says_ok(gate, monkeypatch):
    """THE floor: a non-negated urgent-rule hit + LLM 'ok' → caution, not ok."""
    _stub(gate, monkeypatch, decision="ok")
    r = gate.screen("L1", "what should I do if I have bleeding?", "pregnant", 24)
    assert r.decision == "caution"
    assert r.source == "rules+llm"


@pytest.mark.parametrize("text,stage", [
    ("baby stopped moving since last night", "pregnant"),
    ("बच्चा हिल नहीं रहा है", "pregnant"),
    ("saans nahi aa rahi hai mujhe", "postpartum"),
    ("mera paani nikal gaya abhi 30 weeks", "pregnant"),
    ("worst headache of my life and seeing spots", "pregnant"),
])
def test_danger_signs_across_languages_hit_rules(gate, monkeypatch, text, stage):
    _stub(gate, monkeypatch, decision="urgent")
    r = gate.screen("L1", text, stage, 30)
    assert r.decision == "urgent"
    assert r.matched  # the deterministic layer saw it too


def test_caution_rule_floors_at_caution(gate, monkeypatch):
    _stub(gate, monkeypatch, decision="ok")
    r = gate.screen("L1", "I have been crying all the time lately", "postpartum", 2)
    assert r.decision == "caution"


def test_caution_rule_llm_can_raise_to_urgent(gate, monkeypatch):
    _stub(gate, monkeypatch, decision="urgent", reason="ppd escalation")
    r = gate.screen("L1", "crying all the time, can't do this anymore", "postpartum", 2)
    assert r.decision == "urgent"


# --- negation: downgraded to the LLM, never silently cleared -----------------

def test_negated_symptom_llm_clears_to_ok(gate, monkeypatch):
    _stub(gate, monkeypatch, decision="ok", reason="denial of symptom")
    r = gate.screen("L1", "no bleeding today, feeling good!", "pregnant", 24)
    assert r.decision == "ok"
    assert r.source == "llm"


def test_hindi_postfix_negation_is_detected(gate):
    """'khoon nahi aa raha' — Hindi negates AFTER the noun; the window must
    look both ways."""
    hits = gate.match_rules("khoon nahi aa raha, sab theek hai", "pregnant")
    assert hits["uncertain"] and not hits["urgent"]


def test_negated_symptom_with_llm_down_fails_closed(gate, monkeypatch):
    _stub(gate, monkeypatch, error=RuntimeError("gemini 500"))
    r = gate.screen("L1", "no bleeding I think??", "pregnant", 24)
    assert r.decision == "urgent"
    assert r.source == "error"


# --- fail-closed matrix ------------------------------------------------------

def test_llm_down_with_urgent_hit_is_urgent(gate, monkeypatch):
    _stub(gate, monkeypatch, error=RuntimeError("gemini 500"))
    r = gate.screen("L1", "severe headache and blurry vision", "pregnant", 35)
    assert r.decision == "urgent"


def test_llm_down_with_clean_text_is_error_not_ok(gate, monkeypatch):
    """A clean input the gate can't verify must NOT produce a reply."""
    _stub(gate, monkeypatch, error=RuntimeError("gemini 500"))
    r = gate.screen("L1", "which fruits are good in the second trimester?", "pregnant", 24)
    assert r.decision == "error"


def test_llm_down_with_caution_hit_keeps_caution_floor(gate, monkeypatch):
    _stub(gate, monkeypatch, error=RuntimeError("gemini 500"))
    r = gate.screen("L1", "feeling hopeless these days", "postpartum", 4)
    assert r.decision == "caution"


def test_llm_timeout_fails_closed(gate, monkeypatch):
    _stub(gate, monkeypatch, decision="ok", delay=1.0)  # budget is 400ms
    r = gate.screen("L1", "I am bleeding", "pregnant", 24)
    assert r.decision == "urgent"
    assert r.source == "error"


def test_llm_invalid_output_fails_closed(gate, monkeypatch):
    _stub(gate, monkeypatch, error=ValueError("invalid decision"))
    r = gate.screen("L1", "I am bleeding", "pregnant", 24)
    assert r.decision == "urgent"


# --- LLM-only path (no rule hits) --------------------------------------------

def test_llm_urgent_on_clean_text_is_honored(gate, monkeypatch):
    """Phrasing the rules can't see ('everything has gone dark since tuesday and
    I can barely stand') — the LLM's escalation must win."""
    _stub(gate, monkeypatch, decision="urgent", reason="possible crisis")
    r = gate.screen("L1", "everything has gone dark since tuesday and I can barely stand",
                    "postpartum", 1)
    assert r.decision == "urgent"
    assert r.source == "llm"


def test_benign_chat_is_ok(gate, monkeypatch):
    _stub(gate, monkeypatch, decision="ok")
    r = gate.screen("L1", "good morning aira!", "pregnant", 12)
    assert r.decision == "ok"


# --- stage scoping -----------------------------------------------------------

def test_stage_scoped_rule_skipped_for_other_stage(gate, monkeypatch):
    """'no kicks' is a pregnancy rule; postpartum it goes to the LLM alone."""
    stub = _stub(gate, monkeypatch, decision="ok")
    r = gate.screen("L1", "no kicks in the mobile yet", "postpartum", 6)
    assert r.decision == "ok"
    assert stub.calls == 1
    assert not r.matched


def test_unknown_stage_applies_every_rule(gate, monkeypatch):
    """Missing context widens the net: fetal-movement terms match even with no
    stage on file."""
    _stub(gate, monkeypatch, decision="ok")
    r = gate.screen("L1", "can't feel the baby today", None, None)
    assert r.decision == "caution"  # floored by the rule hit
    assert "reduced_fetal_movement" in r.matched


# --- audit trail -------------------------------------------------------------

def test_every_screen_writes_an_audit_row(gate, monkeypatch):
    _stub(gate, monkeypatch, decision="ok")
    gate.screen("L-audit", "hello!", "pregnant", 10)
    gate.screen("L-audit", "I am bleeding", "pregnant", 10)
    rows = gate._conn.execute(
        "SELECT decision, matched FROM safety_audit WHERE learner_id=? ORDER BY ts",
        ("L-audit",)).fetchall()
    assert len(rows) == 2
    assert rows[0][0] == "ok"
    assert rows[1][0] == "caution"          # floored
    assert "vaginal_bleeding" in rows[1][1]


def test_rules_only_path_is_fast(gate, monkeypatch):
    """The self-harm path must not block on anything — budget: 50ms."""
    _stub(gate, monkeypatch, decision="ok", delay=5.0)
    started = time.time()
    r = gate.screen("L1", "I want to kill myself", "postpartum", 2)
    assert (time.time() - started) < 0.05
    assert r.decision == "urgent"
