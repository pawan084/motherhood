package com.aira.companion

import com.aira.companion.model.trustLabelFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Restoring history must not relabel a cautioned answer as a reassuring one.
 *
 * The bug: /v1/chat/history carries `safety_level` ("green" / "amber" / "red"),
 * because the label itself is not stored. The restore assigned that raw level
 * straight to `trustLabel`, which ChatBubble compares against "watchful". So
 * "amber" was not "watchful", and the bubble fell through to its other branch
 * and drew **"Wellness guidance"** in green.
 *
 * That is worse than losing the chip. Yesterday's answer, screened as raised
 * concern and shown at the time as "Watchful · consider your care team", came
 * back after a restart wearing the reassuring label instead — and a red turn
 * did too.
 *
 * Neither existing check could see it. The contract test asks whether
 * `safety_level` escapes the network layer; it did. ChatBubbleRenderTest asks
 * whether ChatBubble draws a trustLabel correctly; it does. The defect was in
 * the caller, converting between two vocabularies without either end knowing
 * there were two.
 */
class TrustLabelHistoryTest {

    @Test
    fun amberBecomesWatchful() {
        assertEquals("watchful", trustLabelFor("amber"))
    }

    @Test
    fun greenBecomesWellness() {
        assertEquals("wellness", trustLabelFor("green"))
    }

    @Test
    fun theRawLevelIsNeverUsedAsALabel() {
        // The assertion this file exists for. Anything ChatBubble does not
        // recognise as "watchful" is drawn as "Wellness guidance", so passing a
        // level through unmapped is not a missing feature — it is an incorrect
        // reassurance on the exact turns that were flagged.
        for (level in listOf("amber", "red")) {
            val label = trustLabelFor(level)
            assertEquals(
                "the raw safety level reached the bubble, which reads anything " +
                    "that is not \"watchful\" as wellness",
                true,
                label == null || label == "watchful",
            )
        }
    }

    @Test
    fun redCarriesNoWellnessChip() {
        // A red turn is the urgent path; live, the server sends trust_label
        // null. History must agree — "Wellness guidance" over an emergency
        // answer is the worst version of this bug.
        assertNull(trustLabelFor("red"))
    }

    @Test
    fun anUnknownOrMissingLevelClaimsNothing() {
        // No chip is honest about not knowing. A default of "wellness" would
        // invent reassurance out of a parsing gap.
        assertNull(trustLabelFor(null))
        assertNull(trustLabelFor(""))
        assertNull(trustLabelFor("standard"))
    }

    @Test
    fun theMappingMatchesTheServers() {
        // backend/chat.py: _TRUST_LABEL = {"green": "wellness", "amber": "watchful"}
        // Two copies of one mapping is how they drift; this pins the client's.
        assertEquals("wellness", trustLabelFor("GREEN"))
        assertEquals("watchful", trustLabelFor("Amber"))
    }
}
