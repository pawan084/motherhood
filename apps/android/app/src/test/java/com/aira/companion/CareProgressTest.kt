package com.aira.companion

import com.aira.companion.data.CareData
import com.aira.companion.data.CareItem
import com.aira.companion.ui.components.careProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CareProgressTest {

    private fun reminder(done: Boolean) = CareItem(
        id = "r${done}${System.nanoTime()}",
        kind = "reminder",
        done = done,
        title = "Water",
        subtitle = "",
    )

    private fun medicine(taken: Boolean) = CareItem(
        id = "m${taken}${System.nanoTime()}",
        kind = "medicine",
        done = false,
        title = "Prenatal vitamin",
        subtitle = "",
        takenToday = taken,
    )

    private fun appointment() = CareItem(
        id = "a${System.nanoTime()}",
        kind = "appointment",
        done = false,
        title = "Anomaly scan",
        subtitle = "",
    )

    @Test
    fun nothingLoadedIsNotZeroOfSomething() {
        val p = careProgress(null)
        assertEquals(0, p.total)
        assertTrue(p.isEmpty)
        // Not "all done": an empty card must not congratulate somebody for a
        // list the app has not managed to load.
        assertFalse(p.allDone)
    }

    @Test
    fun remindersAndMedicinesBothCount() {
        val p = careProgress(
            CareData(
                reminders = listOf(reminder(true), reminder(false)),
                medicines = listOf(medicine(true)),
            ),
        )
        assertEquals(2, p.done)
        assertEquals(3, p.total)
    }

    @Test
    fun appointmentsAreNotCountedAsTasks() {
        // An appointment is attended, not ticked. Counting it would leave
        // "1 of 2" on screen all day with no way to reach 2.
        val p = careProgress(
            CareData(
                reminders = listOf(reminder(true)),
                appointments = listOf(appointment(), appointment()),
            ),
        )
        assertEquals(1, p.done)
        assertEquals(1, p.total)
        assertTrue(p.allDone)
    }

    @Test
    fun takingTheLastDoseAdvancesRatherThanShrinking() {
        // The bug this guards: counting medicinesDue instead of medicines means
        // the denominator falls as doses are taken, so finishing everything
        // reads "0 of 0" rather than "2 of 2".
        val before = careProgress(
            CareData(medicines = listOf(medicine(false), medicine(false))),
        )
        val after = careProgress(
            CareData(
                medicines = listOf(medicine(true), medicine(true)),
                medicinesDue = emptyList(),
            ),
        )
        assertEquals(0, before.done)
        assertEquals(2, before.total)
        assertEquals(2, after.done)
        assertEquals(2, after.total)
        assertTrue(after.allDone)
    }

    @Test
    fun anEmptyDayIsEmptyRatherThanComplete() {
        val p = careProgress(CareData())
        assertTrue(p.isEmpty)
        assertFalse(p.allDone)
    }
}
