package com.aira.companion

import com.aira.companion.ui.AiraViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What happens to a message that never reaches the server.
 *
 * It used to answer as though it had. The app appended "I've understood that.
 * I can help organise the next step..." carrying `trustLabel = "wellness"` — a
 * reply to something Aira never saw, with a label asserting a screening that
 * never ran — and dropped the message with no way to send it. Someone typing
 * "I've been bleeding since this morning" on a bad connection got a calm
 * acknowledgement and no delivery.
 *
 * A null Context is the offline path this ViewModel is built to take in tests,
 * so these run the same code a lost connection does.
 */
class FailedSendTest {

    private fun send(text: String): AiraViewModel {
        val vm = AiraViewModel()
        vm.updateDraft(text)
        vm.sendMessage(null)
        return vm
    }

    @Test
    fun theMessageIsKeptAndMarkedUnsent() {
        val vm = send("Is this cramping normal")
        val mine = vm.uiState.value.messages.filterNot { it.fromAira }
        assertEquals(1, mine.size)
        assertEquals("Is this cramping normal", mine.first().text)
        assertTrue("a message that did not send must say so", mine.first().failed)
    }

    @Test
    fun airaDoesNotAnswerSomethingItNeverReceived() {
        // The heart of it. Any Aira bubble here is a reply to a message the
        // server never saw.
        val vm = send("Is this cramping normal")
        val fromAira = vm.uiState.value.messages.filter { it.fromAira }
        assertTrue(
            "Aira replied to a message that was never delivered: " +
                fromAira.joinToString { it.text },
            fromAira.isEmpty(),
        )
    }

    @Test
    fun nothingClaimsToHaveBeenSafetyChecked() {
        // The old fallback carried trustLabel = "wellness", which is the badge
        // that means the server screened this turn.
        val vm = send("Is this cramping normal")
        assertTrue(vm.uiState.value.messages.all { it.trustLabel == null })
        assertTrue("the header must admit screening is degraded",
            vm.uiState.value.screeningDegraded)
    }

    @Test
    fun theUrgentFloorStillFiresWithNoServer() {
        // The one thing that must work without a connection. The keyword floor
        // is the whole reason it is on the device.
        val vm = send("I have been bleeding heavily since this morning")
        assertTrue(vm.uiState.value.urgentHelpOpen)
        assertTrue(vm.uiState.value.messages.first { !it.fromAira }.failed)
    }

    @Test
    fun anOrdinaryQuestionDoesNotOpenUrgentHelp() {
        val vm = send("how do I plan my meals this week")
        assertFalse(vm.uiState.value.urgentHelpOpen)
    }

    @Test
    fun resendingClearsTheUnsentMarkWhileItIsInFlight() {
        val vm = send("Is this cramping normal")
        val id = vm.uiState.value.messages.first { !it.fromAira }.id

        vm.resendMessage(id, null)

        // Offline again, so it comes back marked — but as the SAME message, not
        // a second copy of what they typed.
        val mine = vm.uiState.value.messages.filterNot { it.fromAira }
        assertEquals("resending must not duplicate the message", 1, mine.size)
        assertEquals(id, mine.first().id)
        assertTrue(mine.first().failed)
    }

    @Test
    fun aFailedMessageIsNotSentAsHistory() {
        // History is what Aira is told was already said. A message that never
        // arrived was not said to anyone, and replaying it later as context
        // would put words into a conversation that never had them.
        val vm = send("Is this cramping normal")
        vm.updateDraft("and now this")
        vm.sendMessage(null)
        assertEquals(2, vm.uiState.value.messages.count { !it.fromAira })
        assertTrue(vm.uiState.value.messages.all { it.fromAira || it.failed })
    }
}
