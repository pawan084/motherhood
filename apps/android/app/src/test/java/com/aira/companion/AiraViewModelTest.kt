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
    fun pregnantOnboardingCapturesNameAndWeeks() {
        val viewModel = AiraViewModel()

        viewModel.startOnboarding()
        viewModel.answerOnboarding("Pregnant")
        viewModel.answerOnboarding("Priya")            // name
        viewModel.answerOnboarding("24")               // weeks — pregnancy only
        viewModel.answerOnboarding("Hinglish")
        viewModel.answerOnboarding("Prepare for a visit")
        viewModel.answerOnboarding("Talking avatar")
        viewModel.finishOnboarding()

        val state = viewModel.uiState.value
        assertEquals(AppStage.Main, state.stage)
        assertEquals(MainDestination.Today, state.destination)
        assertEquals(JourneyType.Pregnant, state.journey)
        assertEquals("Priya", state.name)
        assertEquals(24, state.weeks)
        assertEquals("Hinglish", state.language)
        assertEquals("Talking avatar", state.companionPreference)
        assertEquals(6, state.onboardingAnswers.size)
    }

    @Test
    fun nonPregnantOnboardingSkipsTheWeeksQuestion() {
        val viewModel = AiraViewModel()

        viewModel.startOnboarding()
        viewModel.answerOnboarding("Postpartum")
        viewModel.answerOnboarding("Maya")             // name
        viewModel.answerOnboarding("Hindi")            // straight to language
        viewModel.answerOnboarding("Feel calmer")
        viewModel.answerOnboarding("Chat only")
        viewModel.finishOnboarding()

        val state = viewModel.uiState.value
        assertEquals(JourneyType.Postpartum, state.journey)
        assertEquals("Maya", state.name)
        // Asking a postpartum user how many weeks pregnant they are is the same
        // class of error as the old hardcoded "Week 24".
        assertNull(state.weeks)
        assertEquals("Hindi", state.language)
        assertEquals("Chat only", state.companionPreference)
        assertEquals(5, state.onboardingAnswers.size)
    }

    @Test
    fun nameAndWeeksCanBeSkipped() {
        val viewModel = AiraViewModel()

        viewModel.startOnboarding()
        viewModel.answerOnboarding("Pregnant")
        viewModel.answerOnboarding("")                 // skip name
        viewModel.answerOnboarding("")                 // skip weeks
        viewModel.answerOnboarding("English")
        viewModel.answerOnboarding("Feel calmer")
        viewModel.answerOnboarding("Chat only")

        val state = viewModel.uiState.value
        assertEquals("", state.name)
        assertNull(state.weeks)
        assertEquals("Skipped", state.onboardingAnswers[1].answer)
        assertEquals("English", state.language)
    }

    @Test
    fun implausibleWeekIsTreatedAsSkipped() {
        val viewModel = AiraViewModel()

        viewModel.startOnboarding()
        viewModel.answerOnboarding("Pregnant")
        viewModel.answerOnboarding("Priya")
        viewModel.answerOnboarding("99")               // not a pregnancy week

        assertNull(viewModel.uiState.value.weeks)
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
