package com.aira.companion

import com.aira.companion.ui.screens.minutesOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The order a day is read in.
 *
 * Care lists came back newest-created first, which is the order things were
 * typed and no order at all for reading a schedule: an 8am tablet sat below a
 * 10pm one because it was entered second. This is the key that sorting runs on,
 * read out of the same "8:00 PM · Daily" line the screen already shows — so the
 * order can never disagree with the text next to it.
 */
class CareSortTest {

    @Test
    fun readsBothClockConventions() {
        assertEquals(8 * 60, minutesOfDay("8:00 AM · Daily"))
        assertEquals(20 * 60, minutesOfDay("8:00 PM · Daily"))
        assertEquals(20 * 60 + 15, minutesOfDay("20:15 · Daily"))
    }

    @Test
    fun middayAndMidnightAreNotSwapped() {
        // The pair a naive "+12 for PM" gets backwards, which would file a
        // bedtime medicine first thing in the morning.
        assertEquals(0, minutesOfDay("12:00 AM"))
        assertEquals(12 * 60, minutesOfDay("12:00 PM"))
    }

    @Test
    fun aLineWithNoTimeSortsLast() {
        // A reminder with no time is a note. Notes do not belong in the middle
        // of a schedule, and null is what puts them at the end.
        assertNull(minutesOfDay("Daily"))
        assertNull(minutesOfDay(""))
        assertNull(minutesOfDay(null))
    }

    @Test
    fun nonsenseIsNotGuessedAt() {
        assertNull(minutesOfDay("99:99"))
        assertNull(minutesOfDay("25:00"))
    }

    @Test
    fun theMorningComesBeforeTheEvening() {
        // The property the screen actually depends on, stated directly.
        val morning = minutesOfDay("8:00 AM · Daily")!!
        val evening = minutesOfDay("10:00 PM · Daily")!!
        assertTrue("an 8am tablet must sort above a 10pm one", morning < evening)
    }
}
