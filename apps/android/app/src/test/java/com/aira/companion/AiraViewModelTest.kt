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
        viewModel.finishOnboarding()

        val state = viewModel.uiState.value
        assertEquals(AppStage.Main, state.stage)
        assertEquals(MainDestination.Today, state.destination)
        assertEquals(JourneyType.Pregnant, state.journey)
        assertEquals("Priya", state.name)
        assertEquals(24, state.weeks)
        assertEquals("Hinglish", state.language)
        // Five, not six: the "How would you like Aira to be present?" question
        // is gone. Two of its three options described features this build
        // doesn't have, and the answer was never sent anywhere.
        assertEquals(5, state.onboardingAnswers.size)
    }

    @Test
    fun nonPregnantOnboardingSkipsTheWeeksQuestion() {
        val viewModel = AiraViewModel()

        viewModel.startOnboarding()
        viewModel.answerOnboarding("Postpartum")
        viewModel.answerOnboarding("Maya")             // name
        viewModel.answerOnboarding("Hindi")            // straight to language
        viewModel.answerOnboarding("Feel calmer")
        viewModel.finishOnboarding()

        val state = viewModel.uiState.value
        assertEquals(JourneyType.Postpartum, state.journey)
        assertEquals("Maya", state.name)
        // Asking a postpartum user how many weeks pregnant they are is the same
        // class of error as the old hardcoded "Week 24".
        assertNull(state.weeks)
        assertEquals("Hindi", state.language)
        assertEquals(4, state.onboardingAnswers.size)
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

    // ── first run ───────────────────────────────────────────────────────────

    @Test
    fun theAppStartsOnStartingSoTheSplashHasSomethingToHold() {
        // MainActivity keeps the system splash on screen for exactly this stage.
        // If the initial stage ever changed, the splash would vanish instantly
        // and the first frame would be whatever resolved first.
        assertEquals(AppStage.Starting, AiraViewModel().uiState.value.stage)
    }

    @Test
    fun finishingTheTutorialLeadsToWelcome() {
        val viewModel = AiraViewModel()

        // Null context is the offline/test path: the seen-flag can't be written,
        // but the stage must still advance rather than trapping the user on the
        // tutorial with a button that appears to do nothing.
        viewModel.finishTutorial(null)

        assertEquals(AppStage.Welcome, viewModel.uiState.value.stage)
    }

    // ── the actions that used to be toasts ──────────────────────────────────
    //
    // With no Context there is no backend, and the honest result is to say
    // nothing was saved. Reporting success offline is the same failure these
    // actions had when they were toasts: the user is told something happened
    // that didn't.

    @Test
    fun voicePreferenceDefaultsToWarmAndIsNotSpoken() {
        val prefs = AiraViewModel().uiState.value.voicePrefs
        assertEquals("Aira warm", prefs.voice)
        // No speech synthesis in this build — the UI must not imply otherwise.
        assertFalse(prefs.spokenReplies)
    }

    @Test
    fun savingVoiceOfflineSaysNothingWasSaved() {
        val viewModel = AiraViewModel()

        viewModel.setVoice(null, "Aira gentle")

        assertEquals("Not connected — nothing was saved.", viewModel.uiState.value.snackbarMessage)
        // And the choice is NOT applied locally, so the sheet can't show a
        // selection the server never received.
        assertEquals("Aira warm", viewModel.uiState.value.voicePrefs.voice)
    }

    @Test
    fun creatingAPartnerInviteOfflineProducesNoInvite() {
        val viewModel = AiraViewModel()

        viewModel.createPartnerInvite(null, appointments = true, reminders = true, healthDetails = false)

        assertEquals("Not connected — nothing was saved.", viewModel.uiState.value.snackbarMessage)
        assertNull(viewModel.uiState.value.partnerInvite)
    }

    // No equivalent test for uploadDocument: it takes an android.net.Uri, and
    // android.jar is stubbed on the unit-test classpath, so even Uri.EMPTY
    // throws "Stub!" before the assertion runs. Covering it would mean enabling
    // returnDefaultValues for the whole module, which would silently weaken
    // every other test here. It is exercised on-device instead.

    @Test
    fun reminderCompletionOfflineSaysNothingWasSaved() {
        val viewModel = AiraViewModel()

        viewModel.setReminderDone(null, "rem_123", done = true)

        assertEquals("Not connected — nothing was saved.", viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun clearingThePartnerInviteDropsTheSingleUseCode() {
        val viewModel = AiraViewModel()

        viewModel.clearPartnerInvite()

        assertNull(viewModel.uiState.value.partnerInvite)
    }
}
