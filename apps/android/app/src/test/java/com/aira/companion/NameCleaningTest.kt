package com.aira.companion

import com.aira.companion.ui.AiraViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tidying a typed name.
 *
 * This exists because of a real one: on the test phone, autocorrect turned
 * "Riya" into "Rita I" as the field lost focus, and the app stored it verbatim
 * — so every screen greeted her as "Rita I". Using someone's name wrongly is
 * worse than not using it at all, and a stray committed suggestion is the most
 * likely way it happens on a phone keyboard.
 */
class NameCleaningTest {
    private val vm = AiraViewModel()

    @Test
    fun aStraySingleLetterFromAutocorrectIsDropped() {
        assertEquals("Rita", vm.cleanName("Rita I"))
        assertEquals("Priya", vm.cleanName("Priya k"))
    }

    @Test
    fun realSingleWordNamesSurvive() {
        assertEquals("Riya", vm.cleanName("Riya"))
        // A one-letter name on its own is left alone: the rule is about a
        // fragment appended to something, not about short names.
        assertEquals("A", vm.cleanName("A"))
    }

    @Test
    fun genuineMultiWordNamesAreKept() {
        assertEquals("Mary Anne", vm.cleanName("Mary Anne"))
        assertEquals("Ana Maria Costa", vm.cleanName("Ana Maria Costa"))
    }

    @Test
    fun whitespaceIsCollapsedAndTrimmed() {
        assertEquals("Mary Anne", vm.cleanName("  Mary    Anne  "))
        assertEquals("", vm.cleanName("   "))
    }

    @Test
    fun aVeryLongNameIsCapped() {
        assertEquals(60, vm.cleanName("a".repeat(200)).length)
    }

    @Test
    fun trailingInitialsWithAStopAreNotTreatedAsStrays() {
        // "J." is punctuation, not a bare letter — someone writing their name
        // that way meant it.
        assertEquals("Mary J.", vm.cleanName("Mary J."))
    }
}
