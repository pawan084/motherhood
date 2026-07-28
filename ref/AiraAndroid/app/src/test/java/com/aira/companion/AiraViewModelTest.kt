package com.aira.companion

import com.aira.companion.model.AiraTool
import com.aira.companion.model.AppStage
import com.aira.companion.model.JourneyType
import com.aira.companion.model.MainDestination
import com.aira.companion.ui.AiraViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiraViewModelTest {
    @Test
    fun onboardingBuildsCareContextAndOpensToday() {
        val viewModel = AiraViewModel()

        viewModel.startOnboarding()
        viewModel.answerOnboarding("Pregnant")
        viewModel.answerOnboarding("Hinglish")
        viewModel.answerOnboarding("Prepare for a visit")
        viewModel.answerOnboarding("Talking avatar")
        viewModel.finishOnboarding()

        val state = viewModel.uiState.value
        assertEquals(AppStage.Main, state.stage)
        assertEquals(MainDestination.Today, state.destination)
        assertEquals(JourneyType.Pregnant, state.journey)
        assertEquals("Hinglish", state.language)
        assertEquals("Talking avatar", state.companionPreference)
        assertEquals(4, state.onboardingAnswers.size)
    }

    @Test
    fun toolTrayAndToolSheetHaveExclusiveState() {
        val viewModel = AiraViewModel()

        viewModel.openTools()
        assertTrue(viewModel.uiState.value.toolsOpen)

        viewModel.openTool(AiraTool.CareVault)
        assertFalse(viewModel.uiState.value.toolsOpen)
        assertEquals(AiraTool.CareVault, viewModel.uiState.value.activeTool)

        viewModel.closeTool()
        assertNull(viewModel.uiState.value.activeTool)
    }

    @Test
    fun urgentHelpClosesOtherOverlays() {
        val viewModel = AiraViewModel()

        viewModel.openTools()
        viewModel.openUrgentHelp()

        val state = viewModel.uiState.value
        assertTrue(state.urgentHelpOpen)
        assertFalse(state.toolsOpen)
        assertNull(state.activeTool)
    }
}
