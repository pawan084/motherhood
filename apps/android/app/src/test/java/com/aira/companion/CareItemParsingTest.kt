package com.aira.companion

import com.aira.companion.data.toCareItems
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The care list flattens six differently-shaped kinds into one row type, so the
 * mapping is where a row silently loses its subject. A check-in that renders as
 * "Checkin" is exactly as useless as not showing the timeline at all, which is
 * what the app did before.
 */
class CareItemParsingTest {
    private fun items(json: String) = JSONArray(json).toCareItems()

    @Test
    fun checkInIsTitledByHowThePersonFelt() {
        val item = items(
            """[{"id":"che_1","kind":"checkin","feeling":"Tired","sleep_hours":6.0,
                 "note":"woke at 3am"}]""",
        ).single()

        assertEquals("Tired", item.title)
        assertTrue(item.subtitle, item.subtitle.contains("6h sleep"))
        assertTrue(item.subtitle, item.subtitle.contains("woke at 3am"))
    }

    @Test
    fun symptomKeepsSeverityAndOnsetInTheSubtitle() {
        val item = items(
            """[{"id":"sym_1","kind":"symptom","what":"Headache","severity":"mild",
                 "started":"yesterday"}]""",
        ).single()

        assertEquals("Headache", item.title)
        assertEquals("mild · yesterday", item.subtitle)
    }

    @Test
    fun documentShowsItsFilenameAndType() {
        val item = items(
            """[{"id":"doc_1","kind":"document","name":"scan.pdf","type":"Scan"}]""",
        ).single()

        assertEquals("scan.pdf", item.title)
        assertEquals("Scan", item.subtitle)
    }

    @Test
    fun aKindWithNoRecognisedLabelStillNamesItself() {
        // Better a capitalised kind than an empty row: an unlabelled line in a
        // care list reads as data loss.
        val item = items("""[{"id":"x_1","kind":"appointment"}]""").single()

        assertEquals("Appointment", item.title)
        assertEquals("", item.subtitle)
    }

    @Test
    fun wholeNumberSleepHoursDropTheDecimal() {
        val item = items("""[{"id":"c","kind":"checkin","feeling":"Ok","sleep_hours":8}]""").single()

        assertEquals("8h sleep", item.subtitle)
    }
}
