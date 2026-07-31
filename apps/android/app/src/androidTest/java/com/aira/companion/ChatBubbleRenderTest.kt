package com.aira.companion

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.aira.companion.data.ActionCard
import com.aira.companion.ui.components.ChatBubble
import com.aira.companion.ui.theme.AiraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The storey the contract test cannot see.
 *
 * backend/tests/test_client_contract.py proves a field escapes the networking
 * layer, and says plainly what it cannot prove:
 *
 *     "of the five bugs above, reintroducing disclaimer_needed and action_card
 *      makes this fail, and reintroducing trust_label does not. trustLabel was
 *      assigned in AiraViewModel while no bubble rendered it — it left the
 *      network layer and died one storey later, where this check cannot see.
 *      Catching the rest means asserting against a rendered tree, which is an
 *      instrumentation test, not a grep. Worth writing; not written yet."
 *
 * This is that test. It asserts against the tree Compose actually produces, so
 * a field carried faithfully into ChatBubble and then not drawn fails here —
 * which is precisely how trust_label failed, silently, past both review and CI.
 *
 * Scope is deliberately one composable. These three fields are checked because
 * each was a real bug, not because a bubble is the only thing worth rendering.
 */
class ChatBubbleRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val answer = "Mild swelling in the ankles is common around this stage."

    @Test
    fun anAmberTurnSaysItIsNotMedicalAdvice() {
        // disclaimer_needed: the backend screened the turn as raised concern
        // and asked for the qualification. Before it was drawn, an amber answer
        // was presented exactly like an ordinary one.
        compose.setContent {
            AiraTheme { ChatBubble(text = answer, fromAira = true, disclaimer = true) }
        }

        compose.onNodeWithText(answer).assertIsDisplayed()
        compose.onNode(hasText("medical advice", substring = true)).assertIsDisplayed()
    }

    @Test
    fun anOrdinaryTurnDoesNotCarryTheDisclaimer() {
        // The other half. A disclaimer on every answer is a disclaimer on none:
        // it stops meaning anything, and the amber turns it exists for stop
        // standing out.
        compose.setContent {
            AiraTheme { ChatBubble(text = answer, fromAira = true, disclaimer = false) }
        }

        assertTrue(
            "the disclaimer appeared on a turn that was not flagged",
            compose.onAllNodesWithText("medical advice", substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun aWatchfulTurnLooksDifferentFromAWellnessOne() {
        // trust_label — THE bug this file exists for. It reached the ViewModel
        // and no bubble rendered it, so an answer carrying a caution looked
        // exactly like one that did not.
        compose.setContent {
            AiraTheme { ChatBubble(text = answer, fromAira = true, trustLabel = "watchful") }
        }

        compose.onNode(hasText("care team", substring = true)).assertIsDisplayed()
    }

    @Test
    fun aWellnessTurnIsLabelledToo() {
        compose.setContent {
            AiraTheme { ChatBubble(text = answer, fromAira = true, trustLabel = "wellness") }
        }

        compose.onNode(hasText("Wellness", substring = true)).assertIsDisplayed()

        // And must NOT borrow the watchful wording, which is the distinction
        // the whole chip exists to make.
        assertTrue(
            "a wellness turn told someone to consider their care team",
            compose.onAllNodesWithText("care team", substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun anActionCardIsAPressableThingAndNotASentence() {
        // action_card: "you could log this" used to arrive as prose. A card is
        // only a card if it can be pressed, so the click action is asserted
        // rather than just the text.
        var pressed = false
        compose.setContent {
            AiraTheme {
                ChatBubble(
                    text = answer,
                    fromAira = true,
                    card = ActionCard(tool = "reminder", title = "Set a reminder",
                                      detail = "so you don't have to hold it"),
                    onCardClick = { pressed = true },
                )
            }
        }

        compose.onNodeWithText("Set a reminder").assertIsDisplayed()
        compose.onNode(hasText("Set a reminder", substring = true), useUnmergedTree = false)
            .assertHasClickAction()

        compose.onNodeWithText("Set a reminder").performClick()
        assertTrue("the action card was drawn but does nothing when pressed", pressed)
    }

    @Test
    fun aCardWithNowhereToGoIsNotDrawn() {
        // ChatBubble requires BOTH the card and a handler. An inert control that
        // looks pressable is the defect this codebase keeps removing, so the
        // rule is asserted rather than left as a comment.
        compose.setContent {
            AiraTheme {
                ChatBubble(
                    text = answer,
                    fromAira = true,
                    card = ActionCard(tool = "unknown-tool", title = "Set a reminder",
                                      detail = "nothing handles this"),
                    onCardClick = null,
                )
            }
        }

        assertTrue(
            "a card was drawn for a tool nothing can open",
            compose.onAllNodesWithText("Set a reminder").fetchSemanticsNodes().isEmpty(),
        )
    }
}
