package com.aira.companion

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The snooze window, as arithmetic.
 *
 * AppPrefs needs a Context, so the storage is not what is tested here — the rule
 * is. "Snoozed today" has to mean today and only today, because both ways of
 * getting it wrong are bad in a health app: leaking into tomorrow silently mutes
 * a medicine schedule, and expiring early makes the control look broken on the
 * one day somebody needed it.
 */
class SnoozeWindowTest {

    /** The comparison AppPrefs.remindersSnoozedToday performs. */
    private fun snoozedToday(storedEpochDay: Long, today: LocalDate): Boolean =
        storedEpochDay == today.toEpochDay()

    private val today: LocalDate = LocalDate.of(2026, 8, 17)

    @Test
    fun neverSnoozedIsNotSnoozed() {
        assertFalse(snoozedToday(Long.MIN_VALUE, today))
    }

    @Test
    fun snoozedTodayHoldsForToday() {
        assertTrue(snoozedToday(today.toEpochDay(), today))
    }

    @Test
    fun yesterdaysSnoozeDoesNotSilenceToday() {
        // The expiry is the whole point: the alternative people reach for on a
        // bad day is switching reminders off, and an off switch has no end date.
        assertFalse(snoozedToday(today.minusDays(1).toEpochDay(), today))
    }

    @Test
    fun aSnoozeDoesNotCarryAcrossAMonthBoundary() {
        // Epoch days rather than day-of-month, so the 1st does not match the 1st
        // of the previous month.
        val firstOfMonth = LocalDate.of(2026, 9, 1)
        assertFalse(snoozedToday(LocalDate.of(2026, 8, 1).toEpochDay(), firstOfMonth))
        assertTrue(snoozedToday(firstOfMonth.toEpochDay(), firstOfMonth))
    }

    @Test
    fun aFutureDatedSnoozeIsNotHonouredToday() {
        // Clock changes can leave a stored day ahead of today. Treating that as
        // "snoozed" would mute reminders on a day the user never chose.
        assertFalse(snoozedToday(today.plusDays(1).toEpochDay(), today))
    }
}
