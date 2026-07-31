package com.aira.companion

import com.aira.companion.data.ActionCard
import com.aira.companion.model.AiraTool
import com.aira.companion.model.ChatMessage
import com.aira.companion.model.toolForActionCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The action card, and the rule that it must go somewhere.
 *
 * `action_card` was parsed off the wire and read by nothing — the third field in
 * a row to be dropped between the network layer and the screen, after
 * `disclaimer_needed` and `trust_label`. So Aira decided "this answer should
 * offer to log a symptom", said so on the wire, and the phone drew a paragraph
 * while the web client had shown a tappable card the whole time.
 *
 * The tool name comes from a language model. It is asked for one of a fixed
 * vocabulary and will mostly comply, but "mostly" is the operative word, so an
 * unrecognised name has to produce no card at all rather than one that does
 * nothing when pressed.
 */
class ActionCardTest {

    @Test
    fun `every tool the backend may send resolves to a sheet`() {
        // Exactly the vocabulary in prompts.py's AIRA_REPLY_SCHEMA. If that list
        // grows, this test is where the client finds out.
        val fromSchema = listOf(
            "checkin", "reminder", "appointment", "upload",
            "wellness", "symptom", "careplan", "support",
        )

        for (tool in fromSchema) {
            assertNotNull("no sheet for \"$tool\", so its card would not be shown",
                          toolForActionCard(tool))
        }
    }

    @Test
    fun `the names map to the sheet a person would expect`() {
        assertEquals(AiraTool.Symptom, toolForActionCard("symptom"))
        assertEquals(AiraTool.Reminder, toolForActionCard("reminder"))
        assertEquals(AiraTool.Appointment, toolForActionCard("appointment"))
        assertEquals(AiraTool.CareVault, toolForActionCard("upload"))
        assertEquals(AiraTool.Reset, toolForActionCard("wellness"))
    }

    @Test
    fun `case and stray whitespace do not lose the card`() {
        assertEquals(AiraTool.Reminder, toolForActionCard(" Reminder "))
        assertEquals(AiraTool.CheckIn, toolForActionCard("CHECKIN"))
    }

    @Test
    fun `an unknown tool resolves to nothing rather than a dead card`() {
        assertNull(toolForActionCard("teleport"))
        assertNull(toolForActionCard(""))
    }

    @Test
    fun `the card survives from response into the rendered message`() {
        val card = ActionCard("symptom", "Log this symptom", "So a pattern is visible")

        val message = ChatMessage(id = 1L, fromAira = true, text = "…", card = card)

        assertEquals(card, message.card)
        assertEquals(AiraTool.Symptom, toolForActionCard(message.card!!.tool))
    }

    @Test
    fun `a message defaults to carrying no card`() {
        assertNull(ChatMessage(id = 2L, fromAira = true, text = "hello").card)
    }
}
