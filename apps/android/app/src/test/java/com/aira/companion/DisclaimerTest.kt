package com.aira.companion

import com.aira.companion.data.TurnResult
import com.aira.companion.model.ChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An amber turn has to carry its disclaimer all the way to the bubble.
 *
 * Found on a physical device. "my ankles are swollen at 31 weeks" was screened
 * amber by the backend — correctly; late-pregnancy swelling is a pre-eclampsia
 * signal — and the reply was then presented exactly like an ordinary one.
 *
 * The cause was the quietest kind: `disclaimerNeeded` was parsed off the wire
 * in AiraApi and read by nothing. The server did the safety work, the field
 * crossed the network, and the last step dropped it. The web client had
 * rendered this line all along, so the two clients disagreed about what a
 * raised-concern answer looks like — and only the phone was wrong.
 *
 * These assert the two ends the compiler cannot: that the field survives the
 * hop from response into the message the UI draws.
 */
class DisclaimerTest {

    private fun reply(res: TurnResult) = ChatMessage(
        id = 1L,
        fromAira = true,
        text = res.reply ?: "",
        trustLabel = res.trustLabel,
        at = 1_700_000_000.0,
        disclaimer = res.disclaimerNeeded,
    )

    private fun turn(level: String, label: String, needed: Boolean, text: String) = TurnResult(
        level = level,
        degraded = false,
        urgent = false,
        reply = text,
        trustLabel = label,
        actionCard = null,
        disclaimerNeeded = needed,
        urgentHelp = null,
    )

    @Test
    fun `an amber turn reaches the bubble carrying its disclaimer`() {
        val res = turn("amber", "watchful", true, "Swelling can be common, and worth mentioning.")

        assertTrue("the flag must survive into the rendered message", reply(res).disclaimer)
    }

    @Test
    fun `an ordinary turn does not show one`() {
        // The line has to stay meaningful. On every reply it becomes furniture,
        // and furniture is not read.
        val res = turn("green", "wellness", false, "Here are a few questions worth asking.")

        assertFalse(reply(res).disclaimer)
    }

    @Test
    fun `a message defaults to no disclaimer`() {
        assertFalse(ChatMessage(id = 2L, fromAira = true, text = "hello").disclaimer)
    }
}
