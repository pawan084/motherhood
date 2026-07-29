package com.aira.companion

import com.aira.companion.reminders.ReminderScheduler
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When a reminder next fires.
 *
 * Worth testing on its own because everything else about notifications is
 * observable — you either get one or you don't — while this is the part that
 * silently gets it wrong by twelve hours. The times come from the string the
 * care item already carries ("8:00 PM · Daily"), so the parsing has to survive
 * both clock conventions.
 */
class ReminderSchedulingTest {
    private val wednesdayNoon = LocalDateTime.of(2026, 7, 29, 12, 0)

    @Test
    fun aTimeLaterTodayFiresToday() {
        val next = ReminderScheduler.nextOccurrence("8:00 PM · Daily", wednesdayNoon)
        assertEquals(LocalDateTime.of(2026, 7, 29, 20, 0), next)
    }

    @Test
    fun aTimeAlreadyPastFiresTomorrow() {
        val next = ReminderScheduler.nextOccurrence("9:00 AM · Daily", wednesdayNoon)
        assertEquals(LocalDateTime.of(2026, 7, 30, 9, 0), next)
    }

    @Test
    fun middayAndMidnightAreNotSwapped() {
        // 12 PM is noon and 12 AM is midnight — the two cases a naive
        // "add twelve for PM" gets backwards, which would send a bedtime
        // medicine reminder at lunchtime.
        assertEquals(
            LocalDateTime.of(2026, 7, 29, 12, 30),
            ReminderScheduler.nextOccurrence("12:30 PM", LocalDateTime.of(2026, 7, 29, 9, 0)),
        )
        assertEquals(
            LocalDateTime.of(2026, 7, 30, 0, 30),
            ReminderScheduler.nextOccurrence("12:30 AM", LocalDateTime.of(2026, 7, 29, 9, 0)),
        )
    }

    @Test
    fun twentyFourHourTimesWork() {
        assertEquals(
            LocalDateTime.of(2026, 7, 29, 20, 15),
            ReminderScheduler.nextOccurrence("20:15 · Daily", wednesdayNoon),
        )
    }

    @Test
    fun aReminderWithNoTimeIsNeverScheduled() {
        // A reminder with no time is a note. Scheduling it for an invented hour
        // would be worse than leaving it alone — especially for medication.
        assertNull(ReminderScheduler.nextOccurrence("Daily", wednesdayNoon))
        assertNull(ReminderScheduler.nextOccurrence("", wednesdayNoon))
    }

    @Test
    fun nonsenseTimesAreRejectedRatherThanClamped() {
        assertNull(ReminderScheduler.nextOccurrence("99:99", wednesdayNoon))
    }
}
