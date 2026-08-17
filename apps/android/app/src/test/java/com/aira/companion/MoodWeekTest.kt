package com.aira.companion

import com.aira.companion.data.CareItem
import com.aira.companion.ui.components.MoodWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The mood week is arithmetic on somebody's own feelings, so it is tested
 * rather than eyeballed: the failure mode is showing a user a week that did not
 * happen, which is both wrong and unsettling in a way a layout bug is not.
 */
class MoodWeekTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 8, 17)

    private fun checkIn(mood: String, date: LocalDate, at: LocalTime = LocalTime.NOON) =
        CareItem(
            id = "$mood-$date-$at",
            kind = "checkin",
            done = false,
            title = mood,
            subtitle = "",
            created = date.atTime(at).atZone(zone).toEpochSecond().toDouble(),
        )

    @Test
    fun aWeekWithNothingLoggedIsSevenGaps() {
        val week = MoodWeek.lastDays(emptyList(), today, zone = zone)
        assertEquals(7, week.size)
        assertEquals(List(7) { null }, week)
    }

    @Test
    fun daysAreReturnedOldestFirstAndAlignedToToday() {
        val timeline = listOf(
            checkIn("great", today),
            checkIn("low", today.minusDays(6)),
        )
        val week = MoodWeek.lastDays(timeline, today, zone = zone)
        assertEquals("low", week.first())
        assertEquals("great", week.last())
    }

    @Test
    fun missedDaysStayGapsRatherThanCompacting() {
        // The bug this guards: dropping the empty days would slide Monday's
        // entry into Sunday's slot and show an unbroken week to someone who
        // logged twice.
        val timeline = listOf(
            checkIn("great", today),
            checkIn("tired", today.minusDays(3)),
        )
        val week = MoodWeek.lastDays(timeline, today, zone = zone)
        assertEquals(listOf(null, null, null, "tired", null, null, "great"), week)
    }

    @Test
    fun theLatestCheckInOfADayWins() {
        // Newest-first input. Someone who felt low in the morning and better by
        // evening should see the evening answer — it is their latest word.
        val timeline = listOf(
            checkIn("great", today, LocalTime.of(21, 0)),
            checkIn("low", today, LocalTime.of(7, 0)),
        )
        assertEquals("great", MoodWeek.today(timeline, today, zone))
    }

    @Test
    fun anythingOlderThanTheWindowIsIgnored() {
        val timeline = listOf(checkIn("unwell", today.minusDays(7)))
        assertEquals(List(7) { null }, MoodWeek.lastDays(timeline, today, zone = zone))
    }

    @Test
    fun aFutureCheckInIsIgnoredRatherThanClamped() {
        // Clock skew between phone and server can date a row tomorrow. Clamping
        // it into today would overwrite a real answer with one from a day that
        // has not happened.
        val timeline = listOf(checkIn("anxious", today.plusDays(1)))
        assertNull(MoodWeek.today(timeline, today, zone))
    }

    @Test
    fun nonCheckInRowsAreIgnored() {
        // The timeline carries symptom logs too, and a symptom called "tired"
        // is not a mood check-in.
        val timeline = listOf(
            CareItem(
                id = "s1",
                kind = "symptom",
                done = false,
                title = "tired",
                subtitle = "",
                created = today.atTime(LocalTime.NOON).atZone(zone).toEpochSecond().toDouble(),
            ),
        )
        assertNull(MoodWeek.today(timeline, today, zone))
    }

    @Test
    fun anUnknownFeelingIsNotInventedIntoAMood() {
        // Free-text check-ins exist. "meh" has no colour, no icon and no label,
        // so it must not be drawn as one — moodStyle() would otherwise fall back
        // to a neutral violet and quietly claim the day was logged as "Okay".
        val timeline = listOf(checkIn("meh", today))
        assertNull(MoodWeek.today(timeline, today, zone))
    }

    @Test
    fun rowsWithoutATimestampAreIgnored() {
        val timeline = listOf(
            CareItem(id = "x", kind = "checkin", done = false, title = "great", subtitle = ""),
        )
        assertNull(MoodWeek.today(timeline, today, zone))
    }

    @Test
    fun someoneWhoHasNeverCheckedInHasNoGap() {
        // Null, not a large number. A new user is not returning after a lapse,
        // and "it's been a few quiet days" would be the app inventing a history
        // they never had.
        assertNull(MoodWeek.daysSinceLastCheckIn(emptyList(), today, zone))
    }

    @Test
    fun theGapIsCountedInCalendarDays() {
        val timeline = listOf(checkIn("great", today.minusDays(3), LocalTime.of(23, 0)))
        assertEquals(3L, MoodWeek.daysSinceLastCheckIn(timeline, today, zone))
    }

    @Test
    fun aCheckInTodayIsAZeroGap() {
        val timeline = listOf(checkIn("okay", today, LocalTime.of(6, 0)))
        assertEquals(0L, MoodWeek.daysSinceLastCheckIn(timeline, today, zone))
    }

    @Test
    fun theGapUsesTheMostRecentCheckInNotTheFirstInTheList() {
        // The list is newest-first by contract, but nothing enforces it across a
        // cache merge, so the calculation takes the maximum rather than the head.
        val timeline = listOf(
            checkIn("low", today.minusDays(9)),
            checkIn("great", today.minusDays(2)),
        )
        assertEquals(2L, MoodWeek.daysSinceLastCheckIn(timeline, today, zone))
    }

    @Test
    fun aFutureDatedRowDoesNotSuppressTheCard() {
        // Clock skew dating a row tomorrow would otherwise read as "0 days ago"
        // and hide a re-engagement card the user should be seeing.
        val timeline = listOf(
            checkIn("great", today.plusDays(1)),
            checkIn("low", today.minusDays(5)),
        )
        assertEquals(5L, MoodWeek.daysSinceLastCheckIn(timeline, today, zone))
    }

    @Test
    fun dayInitialsAlignWithTheDots() {
        // Hardcoding "M T W T F S S" is only right on a Sunday. 2026-08-17 is a
        // Monday, so a week ending today starts on the previous Tuesday.
        val labels = MoodWeek.dayInitials(today)
        assertEquals(7, labels.size)
        assertEquals(
            java.time.DayOfWeek.TUESDAY,
            today.minusDays(6).dayOfWeek,
        )
        assertEquals(
            java.time.DayOfWeek.MONDAY.getDisplayName(
                java.time.format.TextStyle.NARROW,
                java.util.Locale.getDefault(),
            ),
            labels.last(),
        )
    }
}
