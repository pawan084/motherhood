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
behind it. Catching the rest means asserting against a rendered tree, which is
an instrumentation test, not a grep.

That test now exists: apps/android/app/src/androidTest/.../ChatBubbleRenderTest.kt
covers the three fields that were real bugs, and was confirmed to fail when
they are reintroduced. The two files answer different questions and neither
replaces the other — this one asks whether a field escapes the network layer,
that one asks whether it reaches a person's eyes. `trust_label` passed the
first and failed the second, which is the whole reason both exist.

── The field list is only as good as the fixture ──

An empty list has no fields, and a null object has no fields, so anything the
seeding below fails to produce is silently not checked at all. This was not a
theoretical concern: `/v1/care` was walked on an account with no care in it, so
every field of every reminder, appointment and medicine went unchecked — which
is how `private_label` reached both clients unread, and how Android came to
have no GET for the contraction pattern it told people to read out loud.

The count is measurable, and was 13 before the seeding was filled in. Two
remain, both deliberate:

    action_card       null unless the LLM proposes one, and this app ships with
                      no classifier configured. The KEY is still checked — a
                      client dropping `action_card` fails this file, which is a
                      bug it has already caught once. What is unchecked is the
                      shape INSIDE the card. Fabricating one would make the
                      fixture disagree with the server, which is the property
                      that makes this file worth having.
    media_url         null unless AIRA_DEMO_MEDIA is set. Same reasoning: the
                      key is checked, and it is a string, so there is no nested
                      shape being missed.

If you add an endpoint, check what the fixture actually produced for it before
concluding it is covered.
"""
import datetime
import pathlib
import re
import time

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
        "since_previous_seconds": "the gap before ONE contraction. Android sends it "
                                  "and reads back the summary instead — no screen "
                                  "lists contractions individually, and typical_gap "
                                  "is the number a midwife asks for",
        "last_taken": "when a dose was last taken; no screen shows a 'last taken at' "
                      "line, and takenToday answers the question the list actually asks",
        "content_format": "catalogue metadata (explainer / demonstration) that no screen distinguishes",
        "specialties": "which clinical specialties must review a topic — admin console material, not a user's",
        "required": "whether a topic needs clinical review; the same admin-only moderation detail",
        "degraded_llm": "duplicates safety.degraded, which both clients DO read to show the screening pill",
        "clinical_review": "Android reads its nested `status` as reviewStatus, never the object itself",
        # Consumed, but under names the camelCase rule cannot derive: the nested
        # care_plan object is flattened into planTotal / planOnTrack at the parse
        # site. Listed here so a reader does not conclude it is unused — the plan
        # sheet reads both and renders "N of M on track".
        "care_plan": "flattened at parse into planTotal/planOnTrack, both read by the care-plan sheet",
        "total": "part of care_plan; read as planTotal",
        "on_track": "part of care_plan; read as planOnTrack",
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
        "last_taken": "same as Android — nothing renders a 'last taken at' line",
        "taken_today": "web renders medicines_due, which the SERVER has already "
                       "filtered to doses not taken today, so the per-item flag "
                       "would be answering a question this client never asks",
        "private_label": "keeps a label off a LOCK SCREEN. A browser has no lock "
                         "screen and posts no notifications, so there is nothing "
                         "here for the flag to protect — Android honours it",
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
                json={"journey": "pregnant", "name": "Ava", "weeks": 24,
                      "language": "English",
                      # Otherwise /v1/today.priorities is [] and whatever it
                      # carries is never looked at.
                      "priorities": ["Prepare for a visit", "Feel calmer"]},
                headers=h)
    # An AMBER message, not a neutral one. `safety.categories` is [] on a green
    # turn, so the list the clients render in the trust chip was never checked.
    # "cramping" is a keyword-floor match, which means this holds without a
    # classifier configured — the state this app currently ships in.
    turn = client.post("/v1/chat/turn",
                       json={"message": "I have cramping, is this normal?",
                             "history": []}, headers=h)
    # One of each care kind, because /v1/care on an empty account returns
    # `{"reminders": [], "appointments": [], ...}` and the checker then sees four
    # container names and not one field of the things inside them. The app's
    # most-used screen was its largest blind spot for that reason alone — it is
    # how `private_label` reached the wire, and both clients, unread.
    def seed(path, body):
        """POST setup data and REFUSE to continue if it did not land.

        The first version of this ignored the response, and posted an
        appointment as `{"title": ...}` when AppointmentIn requires `doctor`.
        It 422'd, nothing was created, `appointments` stayed empty, and the
        blind spot this seeding exists to close stayed open — while the code
        above it read as though an appointment had been made.

        A fixture that silently fails is worse than no fixture: the suite still
        passes, so the gap now has a comment claiming it is covered.
        """
        r = client.post(path, json=body, headers=h)
        assert r.status_code == 200, f"contract fixture failed to seed {path}: {r.text}"
        return r

    seed("/v1/care/reminders", {"title": "iron tablet", "time": "8:00 PM"})
    # `at` and `notes` given explicitly: both were None with only the free-text
    # `when`, so neither was ever checked.
    seed("/v1/care/appointments",
         {"doctor": "midwife", "place": "the surgery", "when": "next Tuesday",
          "at": time.time() + 86400 * 3, "notes": "bring the folder"})
    med = seed("/v1/care/medicines",
               {"name": "folic acid", "dose": "400mcg", "time": "9:00 AM"})
    # A dose marked taken, so `last_taken` and `taken_today` carry values
    # instead of being None on an untouched medicine.
    seed(f"/v1/care/medicines/{med.json()['id']}/taken", {"taken": True})
    # A SECOND medicine, left untaken. Marking the only one taken emptied
    # `medicines_due` — filling one blind spot opened another, which is the
    # argument for measuring this rather than reasoning about it.
    seed("/v1/care/medicines", {"name": "iron", "dose": "65mg", "time": "7:00 PM"})
    # Two of each, so the summary objects (`usual`, `recent`) are populated too:
    # both are None until there is enough to describe, and a None object has no
    # fields for the checker to look at.
    for _ in range(3):
        seed("/v1/care/movements",
             {"count": 10, "minutes": 25, "started": "after lunch"})
        seed("/v1/care/contractions", {"seconds": 45, "since_previous_seconds": 300})
    # A due date, so /v1/today.due_date is not None. Chosen to agree with the
    # week above rather than fight it — a due date wins over a reported week,
    # and silently moving the fixture to a different gestation would change
    # what the journey and video endpoints return.
    client.patch("/v1/care/context",
                 json={"due_date": (datetime.date.today()
                                    + datetime.timedelta(days=(40 - 24) * 7)).isoformat()},
                 headers=h)
    seed("/v1/videos/preg-week-24/save", {"saved": True})
    return {
        "/v1/today": client.get("/v1/today", headers=h).json(),
        "/v1/journey": client.get("/v1/journey", headers=h).json(),
        "/v1/videos": client.get("/v1/videos", headers=h).json(),
        "/v1/chat/turn": turn.json(),
        "/v1/emergency-profile": client.get("/v1/emergency-profile", headers=h).json(),
        "/v1/care": client.get("/v1/care", headers=h).json(),
        "/v1/care/movements": client.get("/v1/care/movements", headers=h).json(),
        "/v1/care/contractions": client.get("/v1/care/contractions", headers=h).json(),
    }
    # NOT /account/me. Adding it here was tried and reverted: the user object's
    # field names are `id`, `kind`, `email`, `name` — words that appear all over
    # both clients for unrelated reasons (`CareItem.kind`, the sign-in form's
    # `email`). The name search finds them and reports success no matter what the
    # user model actually holds. Measured, not assumed: with the endpoint listed
    # here and `kind`/`email` genuinely missing from Android's UserProfile, this
    # file passed. See test_the_user_object_is_modelled_by_both_clients, which
    # checks the models themselves instead.


# Endpoints a client does not call at all, and why.
#
# Exempting one line here rather than every field it returns. The distinction
# matters: a client that has never heard of an endpoint is a product decision,
# where a client that reads an endpoint and drops a field is the bug this file
# was written for. Recording it makes a deliberate asymmetry stay deliberate.
UNUSED_ENDPOINTS = {
    "android": {},
    "web": {
        "/v1/care/movements":
            "counting movements is a lying-still-with-a-phone task; a desktop "
            "browser is the wrong instrument and offering it there would be worse "
            "than not having it",
        "/v1/care/contractions":
            "same — timing contractions happens with a phone in hand, in labour, "
            "not at a laptop",
    },
}


def test_android_consumes_every_field(client, user, sources):
    missing = []
    for path, payload in _payloads(client, user).items():
        if path in UNUSED_ENDPOINTS["android"]:
            continue
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
        if path in UNUSED_ENDPOINTS["web"]:
            continue
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


def test_endpoint_exemptions_carry_a_reason_too():
    """Skipping a whole endpoint is a bigger claim than skipping a field, so it
    is held to the same standard rather than a looser one."""
    for client_name, entries in UNUSED_ENDPOINTS.items():
        for path, reason in entries.items():
            assert reason and len(reason) > 30, (
                f"{client_name} skips {path} without a real reason"
            )


def test_a_skipped_endpoint_really_is_skipped():
    """A guard on the skip itself.

    If UNUSED_ENDPOINTS ever names a path that is not in the checked payloads,
    it is dead configuration claiming to cover something — and the endpoint it
    was meant to exempt would be silently unchecked.
    """
    checked = {"/v1/today", "/v1/journey", "/v1/videos", "/v1/chat/turn",
               "/v1/emergency-profile", "/v1/care", "/v1/care/movements",
               "/v1/care/contractions"}

    for client_name, entries in UNUSED_ENDPOINTS.items():
        for path in entries:
            assert path in checked, (
                f"{client_name} exempts {path}, which nothing checks anyway"
            )


# ── the user object ──────────────────────────────────────────────────────────
#
# Checked structurally rather than by name search, for the reason recorded in
# _payloads: every key on this object is a common English word that occurs
# elsewhere in both clients, so a name search cannot distinguish "the model has
# this field" from "the word appears somewhere".

def _kotlin_user_fields() -> set[str]:
    """Property names declared on Android's UserProfile data class."""
    src = ANDROID_API.read_text(encoding="utf-8")
    body = re.search(r"data class UserProfile\((.*?)\n\)", src, re.S)
    assert body, "UserProfile data class not found — did it move or get renamed?"
    return set(re.findall(r"^\s*val\s+(\w+)\s*:", body.group(1), re.M))


def _typescript_user_fields() -> set[str]:
    """Keys declared on web's User type."""
    src = WEB_API.read_text(encoding="utf-8")
    body = re.search(r"export type User = \{(.*?)\};", src, re.S)
    assert body, "web User type not found — did it move or get renamed?"
    return set(re.findall(r"(\w+)\s*[?]?\s*:", body.group(1)))


def test_the_user_object_is_modelled_by_both_clients():
    """Every key public_user() sends must exist on both client models.

    The bug: /account/me returns `kind`, Android's UserProfile did not have it,
    and `signedIn` was therefore set only by the act of signing in — never
    restored. One restart later the You screen told an account holder "You're
    using Aira without an account" and removed the Sign out button. The account
    was fine; the app had simply thrown away the only durable answer.

    `email` went the same way, which is why the screen could not name the
    account even when it knew there was one.
    """
    import accounts

    sent = set(accounts.public_user({
        "id": "usr_x", "kind": "account", "email": "a@b.com", "name": "Ava",
        "journey": "pregnant", "language": "English", "onboarded": 1,
    }))

    assert not sent - _kotlin_user_fields(), (
        "Android's UserProfile is missing fields the server sends: "
        f"{sorted(sent - _kotlin_user_fields())}"
    )
    assert not sent - _typescript_user_fields(), (
        "web's User type is missing fields the server sends: "
        f"{sorted(sent - _typescript_user_fields())}"
    )


def test_signed_in_state_is_derived_from_kind_not_from_having_just_signed_in():
    """The other half of the same bug, and the half a field check cannot see.

    Holding `kind` is necessary but not sufficient: what made the screen lie was
    computing signed-in state from the auth call rather than from the payload.
    A literal `signedIn = true` is that mistake written down, so it is refused
    here — the value has to come from what the server said.
    """
    vm = (ANDROID_ROOT / "ui/AiraViewModel.kt").read_text(encoding="utf-8")

    assert "signedIn = true" not in vm, (
        "signedIn is being asserted rather than derived. It must come from "
        'user.kind == "account", or a relaunch will disagree with the sign-in.'
    )
    assert vm.count('signedIn = user.kind == "account"') >= 2, (
        "both the authenticate() and restoreSession() paths must set signedIn "
        "from the payload, or the two disagree after a restart"
    )


# ── the fixture's own coverage ───────────────────────────────────────────────

_KNOWN_THIN = {
    ".action_card",           # needs an LLM; see the module docstring
    ".items[].media_url",     # needs AIRA_DEMO_MEDIA
    ".week_video.media_url",
}


def _thin_spots(node, path=""):
    """Places where the payload carries nothing, so nothing inside is checked.

    A list is examined across ALL of its items, not just the first. Looking only
    at [0] reported `medicines[0].last_taken` as uncovered purely because the
    untaken medicine happened to sort first — the taken one two places along
    carried it. A checker that depends on list order is reporting on the fixture's
    sort, not on coverage.
    """
    if isinstance(node, dict):
        out = []
        for k, v in node.items():
            out += _thin_spots(v, f"{path}.{k}")
        return out
    if isinstance(node, list):
        if not node:
            return [path]
        # Thin only where EVERY item is thin: one item carrying the field is
        # enough for its shape to be checked.
        per_item = [set(_thin_spots(i, f"{path}[]")) for i in node]
        return sorted(set.intersection(*per_item))
    return [path] if node is None else []


def test_the_fixture_is_not_thin(client, user):
    """Guards the guard, in the dimension that actually failed.

    Every field check above is derived from a real response, which means an
    empty list or a null object removes fields from the check silently — the
    suite stays green and the coverage quietly shrinks. Three real defects were
    found this way, all invisible because the test data was thinner than
    anything a person would have.

    So the seeding is asserted rather than trusted. Emptying a collection now
    fails here, with the name of what stopped being covered, instead of showing
    up as a bug on a phone months later.
    """
    thin = sorted(
        f"{endpoint}{spot}"
        for endpoint, payload in _payloads(client, user).items()
        for spot in _thin_spots(payload)
        if spot not in _KNOWN_THIN
    )

    assert not thin, (
        "these carry nothing, so no field inside them is checked by this file — "
        "seed them in _payloads, or add them to _KNOWN_THIN with a reason:\n  "
        + "\n  ".join(thin)
    )


def test_restored_history_maps_the_safety_level_to_a_label():
    """A field can arrive intact and still be wrong by the time it is drawn.

    /v1/chat/history carries `safety_level` — "green" / "amber" / "red" — while
    ChatBubble speaks "wellness" / "watchful". The restore assigned one straight
    to the other, and the bubble reads anything that is not "watchful" as
    wellness. So an amber turn came back from history captioned "Wellness
    guidance", and so did a red one: not a missing chip, an incorrect
    reassurance on precisely the turns that were flagged.

    Nothing else could catch it. This file's field check asks whether
    `safety_level` escapes the network layer, and it did. ChatBubbleRenderTest
    asks whether ChatBubble renders a trustLabel correctly, and it does. The
    defect lived between them, in a caller translating two vocabularies without
    either end knowing there were two — so the caller is what is asserted here.
    """
    vm = (ANDROID_ROOT / "ui/AiraViewModel.kt").read_text(encoding="utf-8")

    assert "trustLabel = t.safetyLevel" not in vm, (
        'the raw safety level is being used as a trust label again. ChatBubble '
        'draws anything that is not "watchful" as "Wellness guidance", so this '
        "mislabels amber and red turns as reassuring. Use trustLabelFor()."
    )
    assert "trustLabelFor(t.safetyLevel)" in vm, (
        "history restore must map the level through trustLabelFor()"
    )


# ── shared vocabularies ──────────────────────────────────────────────────────
#
# The trust_label defect was not a dropped field. `safety_level` arrived intact
# and was handed to a component that speaks a different set of words, and both
# ends were individually correct. That failure mode is invisible to every check
# above it, so the vocabularies themselves are pinned here.

def test_every_journey_the_server_accepts_is_known_to_both_clients():
    """A journey the client cannot name falls back to whatever was already
    selected, silently — so somebody who chose "After a loss" would keep being
    shown the previous journey's content, which for this particular value is
    the cruellest possible failure."""
    import accounts

    kotlin = (ANDROID_ROOT / "model/AiraModels.kt").read_text(encoding="utf-8")
    enum_body = re.search(r"enum class JourneyType\b.*?\n\}", kotlin, re.S)
    assert enum_body, "JourneyType not found — renamed or moved?"
    android = {m.lower() for m in re.findall(r"^\s{4}(\w+)\(", enum_body.group(0), re.M)}

    ts = WEB_API.read_text(encoding="utf-8")
    web_union = re.search(r"export type Journey\s*=\s*([^;]+);", ts)
    assert web_union, "web Journey union not found — renamed or moved?"
    web = set(re.findall(r'"(\w+)"', web_union.group(1)))

    assert accounts.VALID_JOURNEYS <= android, (
        f"Android cannot name: {sorted(accounts.VALID_JOURNEYS - android)}")
    assert accounts.VALID_JOURNEYS <= web, (
        f"web cannot name: {sorted(accounts.VALID_JOURNEYS - web)}")


def test_both_clients_map_a_stored_safety_level_the_same_way():
    """History stores the LEVEL; the chip is a LABEL. Each client converts, and
    a client that converts differently shows a different chip for the same turn.

    Android got this wrong by not converting at all — an amber turn came back
    from history captioned "Wellness guidance". Web had it right. Pinning both
    against chat._TRUST_LABEL keeps the three in step.
    """
    import chat

    assert chat._TRUST_LABEL == {"green": "wellness", "amber": "watchful"}, (
        "the server's mapping changed; both clients below need to change with it")

    kotlin = (ANDROID_ROOT / "model/AiraModels.kt").read_text(encoding="utf-8")
    mapper = re.search(r"fun trustLabelFor\(.*?\n\}", kotlin, re.S)
    assert mapper, "trustLabelFor not found — the mapping must live in one place"
    for level, label in chat._TRUST_LABEL.items():
        assert re.search(rf'"{level}"\s*->\s*"{label}"', mapper.group(0)), (
            f"Android does not map {level!r} to {label!r}")

    # Web maps inline in the history loader rather than in a named function.
    web_chat = (WEB_ROOT / "ui/chat.tsx").read_text(encoding="utf-8")
    for level, label in chat._TRUST_LABEL.items():
        assert re.search(rf'"{level}"\s*\?\s*"{label}"', web_chat), (
            f"web does not map {level!r} to {label!r}")
