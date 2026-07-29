package com.aira.companion

import com.aira.companion.data.TurnResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing of the safety-gated chat response.
 *
 * The case that matters most is a RED turn for someone who has not saved a
 * care-team number. The backend sends `"phone": null`, and org.json coerces a
 * JSON null to the string "null" — so a naive `optString(...).ifBlank { null }`
 * yields a four-character "phone number", the urgent screen offers "Call care
 * team", and the dialer opens on `tel:null`. That is the inert-button failure
 * the server-side handoff exists to prevent, so it gets a test.
 */
class TurnResultTest {

    private fun redTurn(phone: String) = JSONObject(
        """
        {
          "safety": {"level": "red", "categories": ["heavy bleeding"], "degraded": true},
          "urgent": true,
          "reply": null,
          "trust_label": null,
          "action_card": null,
          "urgent_help": {
            "headline": "Please contact your care team now.",
            "message": "Do not wait for an AI response.",
            "care_team": {"name": null, "phone": $phone},
            "emergency_contact": {"name": null, "phone": null}
          }
        }
        """.trimIndent(),
    )

    @Test
    fun redTurnWithNoCareTeamNumberYieldsNullPhone() {
        val result = TurnResult.from(redTurn("null"))

        assertTrue(result.urgent)
        assertEquals("red", result.level)
        assertNull("a red turn must never carry an AI reply", result.reply)
        // The important assertion: no phone, so the UI offers to ADD a number
        // rather than dialling the string "null".
        assertNull(result.urgentHelp?.careTeamPhone)
        assertNull(result.urgentHelp?.careTeamName)
        assertNull(result.urgentHelp?.emergencyContactPhone)
    }

    @Test
    fun redTurnWithASavedNumberKeepsIt() {
        val result = TurnResult.from(redTurn("\"+911140000000\""))

        assertTrue(result.urgent)
        assertEquals("+911140000000", result.urgentHelp?.careTeamPhone)
    }

    @Test
    fun greenTurnCarriesReplyAndTrustLabel() {
        val result = TurnResult.from(
            JSONObject(
                """
                {
                  "safety": {"level": "green", "categories": [], "degraded": false},
                  "urgent": false,
                  "reply": "I'm here with you.",
                  "trust_label": "wellness",
                  "action_card": {"tool": "checkin", "title": "Check in", "detail": "Two minutes"},
                  "disclaimer_needed": false
                }
                """.trimIndent(),
            ),
        )

        assertEquals(false, result.urgent)
        assertEquals("I'm here with you.", result.reply)
        assertEquals("wellness", result.trustLabel)
        assertEquals("checkin", result.actionCard?.tool)
        assertNull(result.urgentHelp)
    }
}
