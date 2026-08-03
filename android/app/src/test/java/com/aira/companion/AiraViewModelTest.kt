package com.aira.companion

import android.app.Application
import com.aira.companion.model.AiraTool
import com.aira.companion.model.AppStage
import com.aira.companion.model.JourneyType
import com.aira.companion.ui.AiraViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure state-transition tests. Paths that launch coroutines against the
 * backend (finishOnboarding's context push, deliver()) are exercised by the
 * on-device flow against a live backend, not here — this ViewModel has no
 * injected API seam yet (TODO if unit coverage of turns becomes worth the
 * refactor).
 */
class AiraViewModelTest {
    private fun vm() = AiraViewModel(Application())

    @Test
    fun pregnantJourneyAppendsAnchorPrompt() {
        val viewModel = vm()
        viewModel.startOnboarding()
        assertEquals(4, viewModel.uiState.value.prompts.size)

        viewModel.answerOnboarding("Pregnant")
        val state = viewModel.uiState.value
        assertEquals(JourneyType.Pregnant, state.journey)
        assertEquals(5, state.prompts.size)
        assertEquals("About how far along are you?", state.prompts.last().question)

        viewModel.answerOnboarding("Hinglish")
        viewModel.answerOnboarding("Prepare for a visit")
        viewModel.answerOnboarding("Talking avatar")
        viewModel.answerOnboarding("~16 weeks")
        assertEquals("~16 weeks", viewModel.uiState.value.anchorChoice)
        assertEquals("Hinglish", viewModel.uiState.value.language)
    }

    @Test
    fun tryingJourneyHasNoAnchorPrompt() {
        val viewModel = vm()
        viewModel.startOnboarding()
        viewModel.answerOnboarding("Trying to conceive")
        assertEquals(4, viewModel.uiState.value.prompts.size)
    }

    @Test
    fun toolsAreExclusive() {
        val viewModel = vm()
        viewModel.openTool(AiraTool.Reminder)
        assertEquals(AiraTool.Reminder, viewModel.uiState.value.activeTool)
        assertFalse(viewModel.uiState.value.toolsOpen)

        viewModel.openUrgentHelp()
        assertTrue(viewModel.uiState.value.urgentHelpOpen)
        assertNull(viewModel.uiState.value.activeTool)

        viewModel.closeUrgentHelp()
        assertFalse(viewModel.uiState.value.urgentHelpOpen)
    }

    @Test
    fun blankDraftDoesNotSend() {
        val viewModel = vm()
        viewModel.updateDraft("   ")
        viewModel.sendMessage() // must return before any coroutine launches
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertFalse(viewModel.uiState.value.sending)
    }

    @Test
    fun welcomeToOnboarding() {
        val viewModel = vm()
        assertEquals(AppStage.Welcome, viewModel.uiState.value.stage)
        viewModel.startOnboarding()
        assertEquals(AppStage.Onboarding, viewModel.uiState.value.stage)
    }
}
