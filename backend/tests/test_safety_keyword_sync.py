"""The clients' offline lists must match the server's.

There were three copies of the red-flag list — server, web, Android — and the
clients' were made once and never revisited. Widening the server's left both
clients screening the old, narrower set whenever they were offline, which is
the only time their list is used at all. Same defect, two more places.

The clients cannot call the API in the situation their list exists for, so they
have to ship one. This test makes that copy a build artifact: if safety.py
changes and the generator hasn't been re-run, this fails and says so.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "tools"))

import export_safety_keywords as gen  # noqa: E402


def test_generated_client_lists_are_up_to_date():
    stale = []
    for path, build in gen.TARGETS.items():
        current = path.read_text(encoding="utf-8") if path.exists() else ""
        if current != build():
            stale.append(str(path.relative_to(gen.ROOT)))
    assert not stale, (
        "offline safety lists have drifted from backend/safety.py: "
        + ", ".join(stale)
        + " — run: python backend/tools/export_safety_keywords.py"
    )


def test_every_exported_phrase_actually_screens_red():
    """A phrase that doesn't reach RED on the server has no business being in a
    client's urgent list."""
    import safety

    for phrase in safety.RED_PHRASES:
        level, _ = safety._keyword_level(phrase)
        assert level == safety.RED, f"{phrase!r} does not screen red on the server"
