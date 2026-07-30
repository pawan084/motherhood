package com.aira.companion

import com.aira.companion.ui.components.dayLabel
import com.aira.companion.ui.components.dayOf
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * How a day is written, and which day two entries belong to.
 *
 * Shared by the chat and the care timeline. They are read one after another, and
 * a record saying "Tuesday" beside a conversation saying "yesterday" about the
 * same afternoon is a record you stop trusting — so there is one implementation
 * and these tests sit on it.
 */
class DayLabelTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 7, 30)

    private fun at(y: Int, m: Int, d: Int, hour: Int = 12): Double =
        ZonedDateTime.of(y, m, d, hour, 0, 0, 0, zone).toEpochSecond().toDouble()

    @Test
    fun theNearestTwoDaysAreNamedNotDated() {
        assertEquals("Today", dayLabel(at(2026, 7, 30), today, zone))
        assertEquals("Yesterday", dayLabel(at(2026, 7, 29), today, zone))
    }

    @Test
    fun olderEntriesGetARealDate() {
        // Absolute, not "3 days ago". The useful question about a symptom logged
        // at 3am is which night it was, and relative phrasing makes that the
        // reader's arithmetic.
        assertEquals("Sunday 26 July", dayLabel(at(2026, 7, 26), today, zone))
    }

    @Test
    fun lastYearKeepsItsYear() {
        // Without this, January's entry and last January's sit under the same
        // heading — in a record kept across a pregnancy and beyond.
        assertEquals("14 January 2025", dayLabel(at(2025, 1, 14), today, zone))
    }

    @Test
    fun theEarlyHoursBelongToTheNightTheyHappenedIn() {
        // 3am on the 30th is "Today" once the 30th has begun — the entry sorts
        // and groups by the calendar day it was recorded in, which is what the
        // heading above it claims.
        assertEquals("Today", dayLabel(at(2026, 7, 30, hour = 3), today, zone))
        assertEquals("Yesterday", dayLabel(at(2026, 7, 29, hour = 23), today, zone))
    }

    @Test
    fun groupingentriesAndLabellingThemUseTheSameDay() {
        // The heading is derived from the same value the grouping compares, so
        // a run of entries cannot end up under a heading that names another day.
        val lateNight = at(2026, 7, 29, hour = 23)
        val earlyMorning = at(2026, 7, 30, hour = 1)
        assertNotEquals(dayOf(lateNight, zone), dayOf(earlyMorning, zone))
        assertEquals("Yesterday", dayLabel(lateNight, today, zone))
        assertEquals("Today", dayLabel(earlyMorning, today, zone))
    }
}
