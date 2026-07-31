"""A language may only be offered if the safety floor can read it.

The deterministic floor in safety.py is English plus common Hindi/Hinglish
transliterations. It is what catches an emergency when the LLM classifier is
unavailable — which is not a rare edge case but the state the whole floor exists
for, and the state this repo runs in with no API key.

The web client offered Spanish. Nothing validated it, `language` is passed to the
model as "reply in X", and the result was measured rather than guessed:

    "mi bebe no se mueve"                          -> green
    "sangrado abundante y no puedo respirar"       -> green
    "dolor de cabeza severo y vision borrosa"      -> green

The first of those is reduced fetal movement, the signal the floor's regex
patterns exist specifically to catch in English. A Spanish-speaking user was
invited into a language where nothing was watching.

These tests make the boundary structural: the offered list and the readable list
are the same list, and adding to it without adding vocabulary fails here.
"""
import pytest

import accounts
import safety


RED_BY_LANGUAGE = {
    "English": ["I have heavy bleeding", "I cannot breathe", "the baby stopped moving"],
    # Transliterated, as the audience actually types on a phone keyboard.
    "Hindi": ["bahut khoon bah raha hai", "saans nahi aa rahi"],
    "Hinglish": ["bahut khoon and severe pain", "baby is not moving since last night"],
}


def test_every_supported_language_is_covered_by_the_floor():
    """The point of the whole file: each language we offer has to actually
    screen red on its own emergency phrasing."""
    for language in safety.SUPPORTED_LANGUAGES:
        assert language in RED_BY_LANGUAGE, (
            f"{language} is offered but has no red-flag phrases proven here"
        )
        for phrase in RED_BY_LANGUAGE[language]:
            result = safety.screen(phrase, [], {"journey": "pregnant"})
            assert result["level"] == safety.RED, f"{language}: {phrase!r} screened {result['level']}"


def test_the_floor_does_not_read_spanish():
    """Not a wish — a record of why Spanish is not offered.

    If someone adds Spanish vocabulary to RED_PHRASES, this test fails and the
    language can then be added to SUPPORTED_LANGUAGES. That is the intended
    order, and this failing is the signal that the order was followed.
    """
    unread = safety.screen("mi bebe no se mueve", [], {"journey": "pregnant"})

    assert unread["level"] != safety.RED, (
        "Spanish now screens red — add it to SUPPORTED_LANGUAGES and to "
        "RED_BY_LANGUAGE above, then delete this test"
    )


def test_an_unsupported_language_is_refused(client, user):
    """The storage step is where an unsupported picker in one client became a
    real gap: `language` is handed to the model as "reply in X"."""
    from fastapi import HTTPException

    with pytest.raises(HTTPException) as excinfo:
        accounts.update_profile(user["id"], language="Spanish")

    assert excinfo.value.status_code == 400


def test_supported_languages_are_accepted(client, user):
    for language in safety.SUPPORTED_LANGUAGES:
        accounts.update_profile(user["id"], language=language)
        assert accounts.get_user(user["id"])["language"] == language


def test_onboarding_refuses_one_too(client, user):
    r = client.post("/v1/onboarding",
                    json={"journey": "pregnant", "language": "Spanish", "weeks": 20},
                    headers=user["headers"])

    assert r.status_code == 400, r.text


def test_a_blank_language_still_falls_back(client, user):
    """Empty is not a claim about a language, so it keeps the default rather
    than 400ing a client that simply did not ask."""
    accounts.update_profile(user["id"], language="")

    assert accounts.get_user(user["id"])["language"] == "English"
