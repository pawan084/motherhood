package com.aira.companion

import com.aira.companion.model.JourneyType
import com.aira.companion.model.onboardingPromptsFor
import com.aira.companion.ui.screens.ONBOARDING_INTRO
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The opening line of onboarding must not promise a number of questions.
 *
 * Found on a device run, not by reading the code. The intro bubble interpolated
 * `prompts.size`, which was written to keep it accurate. It did the opposite:
 * the first screen said "4 short questions", and the moment "Pregnant" was
 * chosen — adding the pregnancy-week question — the same bubble, already sitting
 * several messages up the transcript, silently became "5 short questions".
 *
 * A transcript is a record of what was said. Earlier messages changing after the
 * fact is the thing that makes one untrustworthy, and it is worse here than in a
 * normal chat: this screen is where someone decides whether to tell the app they
 * are pregnant.
 */
class OnboardingIntroTest {

    @Test
    fun `the question count really does change mid-flow`() {
        // This is the hazard the copy has to survive. If these ever become equal,
        // the interpolation would have been safe — and this test should be the
        // thing that tells you so, rather than a count quietly drifting again.
        assertNotEquals(
            "pregnancy adds the weeks question, so the set is not a fixed size",
            onboardingPromptsFor(JourneyType.Pregnant).size,
            onboardingPromptsFor(JourneyType.Exploring).size,
        )
    }

    @Test
    fun `the intro states no count`() {
        assertFalse(
            "intro must not quantify what it cannot know yet: $ONBOARDING_INTRO",
            ONBOARDING_INTRO.any { it.isDigit() },
        )
        for (word in listOf("three", "four", "five", "six")) {
            assertFalse(
                "intro spells out a count ($word): $ONBOARDING_INTRO",
                ONBOARDING_INTRO.contains(word, ignoreCase = true),
            )
        }
    }
}
