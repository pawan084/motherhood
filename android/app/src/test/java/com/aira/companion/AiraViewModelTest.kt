package com.aira.companion

import android.app.Application
import com.aira.companion.model.AiraTool
import com.aira.companion.model.AppStage
import com.aira.companion.model.JourneyType
import com.aira.companion.model.MainDestination
import com.aira.companion.model.MoodEntry
import com.aira.companion.model.Reminder
import com.aira.companion.model.applyTick
import com.aira.companion.model.upsertMood
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

    @Test
    fun defaultDestinationIsChatAndEnumHasThreeTabs() {
        val viewModel = vm()
        assertEquals(MainDestination.Chat, viewModel.uiState.value.destination)
        // Guards the destinationIcons map's getValue contract.
        assertEquals(3, MainDestination.entries.size)
    }

    @Test
    fun settingsOpensAndClosesAndClosesTray() {
        val viewModel = vm()
        viewModel.openTools()
        viewModel.openSettings()
        assertTrue(viewModel.uiState.value.settingsOpen)
        assertFalse(viewModel.uiState.value.toolsOpen)
        viewModel.closeSettings()
        assertFalse(viewModel.uiState.value.settingsOpen)
    }
}

/** Pure reducer tests — the optimistic-update logic without any network. */
class WellnessReducerTest {
    private fun water(ticks: Int) =
        Reminder("r1", "Drink water", "water", targetPerDay = 8,
                 ticksToday = ticks, doneToday = ticks >= 8)

    private fun medicine(done: Boolean) =
        Reminder("m1", "Prenatal vitamin", "medicine", targetPerDay = 1,
                 ticksToday = if (done) 1 else 0, doneToday = done)

    @Test
    fun upsertMoodReplacesTodaysEntry() {
        val moods = listOf(MoodEntry("2026-08-02", "tired"), MoodEntry("2026-08-03", "low"))
        val next = upsertMood(moods, "2026-08-03", "okay")
        assertEquals(2, next.size)
        assertEquals("okay", next.last { it.day == "2026-08-03" }.mood)
    }

    @Test
    fun upsertMoodAppendsNewDay() {
        val next = upsertMood(listOf(MoodEntry("2026-08-02", "great")), "2026-08-03", "low")
        assertEquals(2, next.size)
    }

    @Test
    fun applyTickIncrementsAndCompletesAtTarget() {
        val step = applyTick(listOf(water(6)), "r1").first()
        assertEquals(7, step.ticksToday)
        assertFalse(step.doneToday)
        val done = applyTick(listOf(water(7)), "r1").first()
        assertEquals(8, done.ticksToday)
        assertTrue(done.doneToday)
        // Past done: no-op, never 9/8.
        assertEquals(8, applyTick(listOf(done), "r1").first().ticksToday)
    }

    @Test
    fun applyTickClampsMedicineAtOne() {
        val ticked = applyTick(listOf(medicine(false)), "m1").first()
        assertEquals(1, ticked.ticksToday)
        assertTrue(ticked.doneToday)
    }

    @Test
    fun applyTickIgnoresUnknownId() {
        val before = listOf(water(3))
        assertEquals(before, applyTick(before, "nope"))
    }
}
