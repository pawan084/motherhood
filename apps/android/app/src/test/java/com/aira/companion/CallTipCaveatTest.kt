package com.aira.companion

import com.aira.companion.model.callTipCaveat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The strongest claim in the product, and the one nothing was checking.
 *
 * A "when to call" tip lists symptoms that warrant phoning a midwife. It reads
 * as clinical whether or not a clinician has seen it, so the ONLY thing
 * separating "checked by a professional" from "seed copy in content.py" is the
 * caveat line underneath.
 *
 * The backend is careful here — it refuses to serve a draft edit and refuses to
 * mark the seed reviewed — and both were tested. What nothing tested is the
 * last step: whether the client, handed an honest `reviewed = false`, says so.
 * That is the same gap trust_label fell through, on the screen where being
 * wrongly trusted costs the most.
 */
class CallTipCaveatTest {

    @Test
    fun unreviewedCopyAdmitsItIsUnreviewed() {
        val caveat = callTipCaveat(reviewed = false)

        assertTrue("unreviewed copy must say so", caveat.contains("not reviewed"))
        assertTrue(
            "and must point at the people who can actually advise",
            caveat.contains("care team", ignoreCase = true),
        )
    }

    @Test
    fun unreviewedCopyNeverBorrowsClinicalAuthority() {
        // The assertion this file exists for. Any phrasing that implies a
        // professional signed this off is false while `reviewed` is false —
        // including a careless edit that reuses the reviewed wording.
        //
        // Negations are removed BEFORE looking. The first version of this test
        // failed on the correct string, because "not reviewed by a clinician"
        // contains "reviewed by": a substring search cannot tell a claim from
        // its denial, and the denial is the whole point of the sentence.
        val caveat = callTipCaveat(reviewed = false).lowercase()
        val bare = caveat.replace(
            Regex("""(not|never|isn't|is not)\s+(reviewed|checked|approved|verified)\s+by"""),
            "",
        )

        for (claim in listOf("reviewed by", "checked by", "approved by",
                             "clinician-reviewed", "verified by")) {
            assertFalse(
                "unreviewed copy claims a professional stands behind it: \"$claim\"",
                bare.contains(claim),
            )
        }
    }

    @Test
    fun reviewedCopySaysWhoStandsBehindIt() {
        val caveat = callTipCaveat(reviewed = true)

        assertTrue(caveat.contains("Reviewed", ignoreCase = true))
        assertFalse(
            "reviewed copy must not also disclaim itself",
            caveat.contains("not reviewed", ignoreCase = true),
        )
    }

    @Test
    fun theTwoAreNeverTheSameSentence() {
        // A refactor that collapses the branch — or inverts it — is caught here
        // rather than on a phone.
        assertTrue(callTipCaveat(true) != callTipCaveat(false))
    }
}
