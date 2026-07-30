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

    // ── appointments ────────────────────────────────────────────────────────
    //
    // A visit is the one thing here with a fixed date and a real cost to
    // missing it: a scan is rebooked weeks out, not tomorrow.

    private fun epochOf(y: Int, m: Int, d: Int): Long =
        java.time.LocalDate.of(y, m, d)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toEpochSecond()

    @Test
    fun aVisitIsFlaggedTheEveningBefore() {
        // The evening before, not the morning of: what a reminder buys you is
        // time to arrange the lift or the childcare, and at 8am on the day that
        // is already too late.
        val at = ReminderScheduler.appointmentReminderAt(
            epochOf(2026, 8, 14),
            now = LocalDateTime.of(2026, 8, 1, 9, 0),
        )
        assertEquals(LocalDateTime.of(2026, 8, 13, 18, 0), at)
    }

    @Test
    fun aVisitTomorrowIsNotFlaggedRetroactively() {
        // The nudge window has passed. Firing immediately for something the
        // user just typed is noise, not a reminder.
        assertNull(
            ReminderScheduler.appointmentReminderAt(
                epochOf(2026, 8, 2),
                now = LocalDateTime.of(2026, 8, 1, 21, 0),
            ),
        )
    }

    @Test
    fun aPastVisitIsNeverFlagged() {
        assertNull(
            ReminderScheduler.appointmentReminderAt(
                epochOf(2026, 7, 1),
                now = LocalDateTime.of(2026, 8, 1, 9, 0),
            ),
        )
    }

    @Test
    fun theEveningBeforeStillCountsUntilSixOClock() {
        // Booked for tomorrow, and it is 17:00 today — there is still an
        // evening to have, so the nudge stands.
        val at = ReminderScheduler.appointmentReminderAt(
            epochOf(2026, 8, 2),
            now = LocalDateTime.of(2026, 8, 1, 17, 0),
        )
        assertEquals(LocalDateTime.of(2026, 8, 1, 18, 0), at)
    }
}

/**
 * Why a pending reminder has to be re-timed when the clock moves.
 *
 * WorkManager stores a DURATION, not a moment. These pin the arithmetic that
 * makes that a problem, and that recomputing is the fix — the behaviour lives
 * in TimeChangeReceiver, which needs a device to exercise, but the reasoning it
 * rests on does not.
 */
class ClockChangeTest {

    private val delhi = java.time.ZoneId.of("Asia/Kolkata")
    private val london = java.time.ZoneId.of("Europe/London")

    @Test
    fun aDelayComputedBeforeFlyingLandsAtTheWrongLocalTime() {
        // Scheduled at 10:00 in Delhi for the 20:00 tablet: ten hours.
        val scheduledAt = java.time.ZonedDateTime.of(2026, 8, 1, 10, 0, 0, 0, delhi)
        val target = ReminderScheduler.nextOccurrence("8:00 PM · Daily", scheduledAt.toLocalDateTime())!!
        val delay = java.time.Duration.between(scheduledAt.toLocalDateTime(), target)
        assertEquals(java.time.Duration.ofHours(10), delay)

        // The duration elapses exactly as promised. The wall clock does not.
        //
        // August, so London is on BST (UTC+1) and Delhi is UTC+5:30 — a gap of
        // four and a half hours, not five and a half. I asserted 14:30 first
        // and the test corrected me; the exact figure matters less than the
        // direction, but a test that documents an offset should have it right.
        val firesAt = scheduledAt.toInstant().plus(delay).atZone(london)
        assertEquals(15, firesAt.hour)
        assertEquals(30, firesAt.minute)
        // 15:30 is not 20:00, and nothing in WorkManager notices.
    }

    @Test
    fun recomputingAfterTheChangeGivesTheRightTimeAgain() {
        // What the receiver does: ask again, from where you now are.
        val nowInLondon = java.time.ZonedDateTime.of(2026, 8, 1, 14, 30, 0, 0, london)
        val target = ReminderScheduler.nextOccurrence("8:00 PM · Daily", nowInLondon.toLocalDateTime())!!
        assertEquals(20, target.hour)
        assertEquals(java.time.LocalDate.of(2026, 8, 1), target.toLocalDate())
    }

    @Test
    fun anHourLostToDaylightSavingIsAnHourOfDrift() {
        // Spring forward: 01:00 becomes 02:00. A reminder scheduled the evening
        // before counts down through a night that is an hour shorter than the
        // duration assumed, and arrives an hour late by the wall clock.
        val zone = java.time.ZoneId.of("Europe/London")
        val before = java.time.ZonedDateTime.of(2026, 3, 28, 22, 0, 0, 0, zone)
        val target = ReminderScheduler.nextOccurrence("8:00 AM · Daily", before.toLocalDateTime())!!
        val delay = java.time.Duration.between(before.toLocalDateTime(), target)
        val firesAt = before.toInstant().plus(delay).atZone(zone)
        assertEquals(9, firesAt.hour)   // meant 08:00, arrives at 09:00
    }
}
