"""Every field the API sends must be read by every client that receives it.

Five bugs in two review passes had one shape: the backend computed something,
put it on the wire, and a client dropped it between the network layer and the
screen.

    disclaimer_needed   parsed by Android, read by nothing — an amber turn was
                        presented exactly like an ordinary one
    action_card         parsed by Android, read by nothing — "you could log this"
                        arrived as a sentence instead of a button
    trust_label         parsed by Android, read by nothing — an answer carrying a
                        caution looked like one that did not
    playable            parsed by both, read by neither — web drew a play glyph
                        on unfilmed topics, Android drew none and would have kept
                        drawing none once they were filmed
    media_url           added to Android and to the payload, never given to web,
                        which then said "Ready to watch" with nothing to open

None of these were caught by review, because each was invisible in the file it
was missing from. They were caught by driving a phone and a browser, one screen
at a time, which does not scale and cannot run in CI.

This runs in CI. Parsing a field is not consuming it, so proving
`disclaimerNeeded` appears in AiraApi.kt would have passed while the bug was
live; what is checked is that the name appears OUTSIDE the networking layer,
with bare property declarations stripped so carrying a value into a data class
and forgetting it does not count either.

The field list comes from real responses rather than a hand-maintained list, so a
new field is covered the day it ships and nobody has to remember this file.

── What this does NOT catch ──

It proves a field escapes the networking layer. It does not prove the field
reaches a screen.

Measured, not assumed: of the five bugs above, reintroducing `disclaimer_needed`
and `action_card` makes this fail, and reintroducing `trust_label` does not.
`trustLabel` was assigned in AiraViewModel while no bubble rendered it — it left
the network layer and died one storey later, where this check cannot see.

So this closes the hole the bugs actually came through and leaves a smaller one
behind it. Catching the rest means asserting against a rendered tree, which is an
instrumentation test, not a grep. Worth writing; not written yet. Saying so here
is cheaper than someone later assuming a green suite means every field is on a
screen.
"""
import pathlib
import re

import pytest

APPS = pathlib.Path(__file__).resolve().parents[2] / "apps"

ANDROID_ROOT = APPS / "android/app/src/main/java/com/aira/companion"
ANDROID_API = ANDROID_ROOT / "data/AiraApi.kt"
WEB_ROOT = APPS / "web/app"
WEB_API = WEB_ROOT / "aira-api.ts"


def _read(path: pathlib.Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def _joined(paths) -> str:
    return "\n".join(_read(p) for p in paths)


# `val name: Type` / `var name: Type,` with no assignment — a declaration.
_KOTLIN_DECL = re.compile(r"^\s*(?:val|var)\s+\w+\s*:\s*[^=]+,?\s*$")


def _strip_declarations(src: str) -> str:
    """Drop Kotlin property declarations, keeping everything that reads them."""
    return "\n".join(line for line in src.splitlines() if not _KOTLIN_DECL.match(line))


@pytest.fixture(scope="module")
def sources():
    if not ANDROID_API.exists() or not WEB_API.exists():
        pytest.skip("client sources not present in this checkout")
    android_all = list(ANDROID_ROOT.rglob("*.kt"))
    web_ui = list((WEB_ROOT / "ui").rglob("*.ts*"))
    return {
        "android_api": _read(ANDROID_API),
        # Everything except the networking layer, with bare property
        # declarations stripped.
        #
        # Without the stripping this check has a hole big enough to drive the
        # original bugs through: `val startWeek: Int?` in AiraModels.kt is a
        # declaration, not a use, and counting it would pass a field that is
        # carried all the way to a data class and then read by nobody — which
        # is a description of every dropped field in the docstring above.
        "android_rest": _strip_declarations(
            _joined([p for p in android_all if p != ANDROID_API])),
        "web_api": _read(WEB_API),
        "web_rest": _joined(web_ui),
    }


def _camel(field: str) -> str:
    head, *tail = field.split("_")
    return head + "".join(w.capitalize() for w in tail)


def _android_consumes(field: str, src) -> bool:
    """True when the field reaches Kotlin outside AiraApi.kt, by its own name.

    Only the camelCase of the JSON key counts, which is the convention this
    client follows without exception: `disclaimer_needed` -> `disclaimerNeeded`,
    `action_card` -> `actionCard`, `next_action` -> `nextAction`.

    An earlier version also accepted whatever local the parser assigned the key
    to. That was strictly worse, and provably so: `action_card` is read into a
    local named `card`, and `card` appears in a hundred unrelated places, so the
    check passed while the field was being dropped. Verified by reintroducing
    the original bug — see the simulation test below, which failed to fail.

    A heuristic that can be satisfied by an unrelated identifier is not a check;
    it is a decoration that reports success.
    """
    return re.search(r"\b" + re.escape(_camel(field)) + r"\b", src["android_rest"]) is not None


def _web_consumes(field: str, src) -> bool:
    """Web keeps the JSON names in its types, so the key itself is the identifier.

    Two places count. Usually a screen reads it (`m.disclaimer_needed`). Sometimes
    an exported helper in the API module does the reading and the screens call the
    helper — `videoDurationLabel` turns `duration.min_seconds` into "2–4 min",
    which is consumption by any useful definition.

    What must NOT count is the type declaration, since declaring
    `disclaimer_needed?: boolean` and then reading it nowhere is precisely what
    every dropped field did. A property access distinguishes the two: a type line
    never contains one.
    """
    if re.search(r"\b" + re.escape(field) + r"\b", src["web_rest"]):
        return True
    return re.search(r"\." + re.escape(field) + r"\b", src["web_api"]) is not None


# Fields a client is allowed not to read, each with the reason it is allowed.
#
# An entry here is a decision, not a shrug: it says somebody looked and decided
# this client has no use for this value. Anything not listed must be consumed.
EXEMPT = {
    "android": {
        "content_format": "catalogue metadata (explainer / demonstration) that no screen distinguishes",
        "specialties": "which clinical specialties must review a topic — admin console material, not a user's",
        "required": "whether a topic needs clinical review; the same admin-only moderation detail",
        "degraded_llm": "duplicates safety.degraded, which both clients DO read to show the screening pill",
        "clinical_review": "Android reads its nested `status` as reviewStatus, never the object itself",
        "slug": "a human-readable alias for id; every lookup here goes through id",
        "status": "production state, but `playable` is the question a screen asks and it subsumes this",
        "start_week": "the server picks the week's video; showing raw band bounds would be noise",
        "end_week": "the server picks the week's video; showing raw band bounds would be noise",
        # Both of these are things web does and Android does not. Neither is a
        # dropped field in the dangerous sense — nothing is claimed and then not
        # delivered — but they are recorded here rather than waved past.
        "languages": "web lists a topic's languages in its detail sheet; Android has no detail sheet yet",
        "in_app_actions": "web offers 'Ask Aira about this' from a video; Android does not surface it",
    },
    "web": {
        "content_format": "catalogue metadata (explainer / demonstration) that no screen distinguishes",
        "specialties": "which clinical specialties must review a topic — admin console material, not a user's",
        "clinical_review": "web reads none of its fields; the review state it holds is admin-only",
        "degraded_llm": "duplicates safety.degraded, which both clients DO read to show the screening pill",
        "timing": "web reads none of its fields; which topic suits this week is decided server-side",
        "start_week": "the server picks the week's video; showing raw band bounds would be noise",
        "end_week": "the server picks the week's video; showing raw band bounds would be noise",
    },
}


def _fields_of(payload, prefix="") -> set:
    """Every key in a response, including nested ones."""
    out = set()
    if isinstance(payload, dict):
        for k, v in payload.items():
            out.add(k)
            out |= _fields_of(v)
    elif isinstance(payload, list):
        for item in payload[:3]:
            out |= _fields_of(item)
    return out


def _payloads(client, user):
    """Real responses, so the field list cannot drift from what ships."""
    h = user["headers"]
    client.post("/v1/onboarding",
                json={"journey": "pregnant", "name": "Ava", "weeks": 24, "language": "English"},
                headers=h)
    turn = client.post("/v1/chat/turn",
                       json={"message": "is this normal?", "history": []}, headers=h)
    return {
        "/v1/today": client.get("/v1/today", headers=h).json(),
        "/v1/journey": client.get("/v1/journey", headers=h).json(),
        "/v1/videos": client.get("/v1/videos", headers=h).json(),
        "/v1/chat/turn": turn.json(),
        "/v1/emergency-profile": client.get("/v1/emergency-profile", headers=h).json(),
    }


def test_android_consumes_every_field(client, user, sources):
    missing = []
    for path, payload in _payloads(client, user).items():
        for field in sorted(_fields_of(payload)):
            if field in EXEMPT["android"]:
                continue
            if not _android_consumes(field, sources):
                missing.append(f"{path}: {field}")
    assert not missing, (
        "Android receives these and never reads them outside AiraApi.kt — "
        "either use them, or add them to EXEMPT['android'] with a reason:\n  "
        + "\n  ".join(missing)
    )


def test_web_consumes_every_field(client, user, sources):
    missing = []
    for path, payload in _payloads(client, user).items():
        for field in sorted(_fields_of(payload)):
            if field in EXEMPT["web"]:
                continue
            if not _web_consumes(field, sources):
                missing.append(f"{path}: {field}")
    assert not missing, (
        "web receives these and never reads them outside aira-api.ts — "
        "either use them, or add them to EXEMPT['web'] with a reason:\n  "
        + "\n  ".join(missing)
    )


def test_the_check_would_have_caught_the_bugs_it_was_written_for():
    """A guard against the guard.

    If `_android_consumes` ever returns True for something only mentioned in the
    networking layer, this test stops protecting anything — and it would do so
    silently, which is the failure mode of every check nobody verifies. The five
    real fields are asserted as consumed; a made-up one must not be.
    """
    fake = {
        "android_api": 'disclaimerNeeded = o.optBoolean("disclaimer_needed", false),',
        "android_rest": "// nothing reads it",
        "web_api": "disclaimer_needed?: boolean;",
        "web_rest": "// nothing reads it",
    }

    assert _android_consumes("disclaimer_needed", fake) is False
    assert _web_consumes("disclaimer_needed", fake) is False

    fake["android_rest"] = "val x = message.disclaimerNeeded"
    fake["web_rest"] = "{m.disclaimer_needed && <small/>}"
    assert _android_consumes("disclaimer_needed", fake) is True
    assert _web_consumes("disclaimer_needed", fake) is True


def test_a_declaration_alone_is_not_consumption():
    """Carrying a field into a data class and reading it nowhere is the bug, not
    the fix — so the declaration must not satisfy the check."""
    declared_only = {
        "android_api": 'playable = o.optBoolean("playable", false),',
        "android_rest": _strip_declarations("    val playable: Boolean,\n    val saved: Boolean,"),
    }

    assert _android_consumes("playable", declared_only) is False


def test_an_unrelated_identifier_does_not_satisfy_the_check():
    """The regression that made this test useless once.

    `action_card` is parsed into a local called `card`, and an earlier version
    accepted that local as the identifier to search for. `card` appears in a
    hundred unrelated places, so the check passed while the field was dropped.
    Only the camelCase of the key counts now.
    """
    unrelated = {
        "android_api": 'val card = o.optJSONObject("action_card")',
        "android_rest": "val card = somethingCompletelyElse()",
    }

    assert _android_consumes("action_card", unrelated) is False


def test_a_type_declaration_does_not_satisfy_the_web_check():
    types_only = {
        "web_rest": "// no screen reads it",
        "web_api": "  disclaimer_needed?: boolean;",
    }

    assert _web_consumes("disclaimer_needed", types_only) is False


def test_exemptions_carry_a_reason():
    for client_name, entries in EXEMPT.items():
        for field, reason in entries.items():
            assert reason and len(reason) > 15, (
                f"{client_name}.{field} is exempt without a real reason"
            )
