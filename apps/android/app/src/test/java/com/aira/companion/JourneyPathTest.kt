package com.aira.companion

import com.aira.companion.ui.screens.PathPosition
import com.aira.companion.ui.screens.positionOf
import com.aira.companion.ui.screens.pregnancyMilestones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which part of the path a week falls in.
 *
 * The visual is borrowed from a learning app; the meaning is not. There is no
 * "completed" state here and nothing is locked, so this only ever answers
 * behind / here / coming — and these tests exist mostly to keep it that way.
 */
class JourneyPathTest {

    private val milestones = pregnancyMilestones()

    private fun positionAt(week: Int): List<PathPosition> =
        milestones.mapIndexed { i, m -> positionOf(m, week, milestones.getOrNull(i + 1)) }

    @Test
    fun exactlyOneMilestoneIsEverTheCurrentOne() {
        // Two "you are here" markers would be two answers to the one question
        // this screen exists to answer.
        for (week in 1..42) {
            val here = positionAt(week).count { it == PathPosition.HERE }
            assertEquals("week $week", 1, here)
        }
    }

    @Test
    fun aTrimesterBoundaryMovesYouOnTheDayItBegins() {
        // Week 13 is still the first trimester; week 14 is the second.
        assertEquals(PathPosition.HERE, positionAt(13)[0])
        assertEquals(PathPosition.HERE, positionAt(14)[1])
        assertEquals(PathPosition.HERE, positionAt(28)[2])
    }

    @Test
    fun whatIsPassedIsBehindAndWhatIsNotIsComing() {
        val at24 = positionAt(24)
        assertEquals(PathPosition.BEHIND, at24[0])   // first trimester
        assertEquals(PathPosition.HERE, at24[1])     // second — week 24 sits here
        assertEquals(PathPosition.AHEAD, at24[2])    // third
        assertEquals(PathPosition.AHEAD, at24[3])    // full term
    }

    @Test
    fun nothingAheadIsEverLockedOrCompleted() {
        // Stated as a test because it is the whole reason this is not a copy of
        // the pattern it borrows from: a week you have not reached is not an
        // achievement to unlock, and someone at 24 weeks may need to read what
        // week 30 says tonight. PathPosition has no such states, and this fails
        // the day somebody adds one.
        assertEquals(3, PathPosition.entries.size)
        assertTrue(
            PathPosition.entries.map { it.name }
                .containsAll(listOf("BEHIND", "HERE", "AHEAD")),
        )
    }

    @Test
    fun theWholeRoadIsShownNotJustTheNextStep() {
        // A path you can only see one step of is a queue. Every milestone is
        // rendered at every week; only its state changes.
        assertEquals(milestones.size, positionAt(8).size)
        assertEquals(milestones.size, positionAt(40).size)
    }

    @Test
    fun pastTheDueDateYouAreStillOnTheLastNodeNotOffTheEnd() {
        // Weeks 40, 41, 42 all sit on the final milestone rather than falling
        // off a path that has run out — and care.MAX_TRACKED_WEEK stops the
        // week being asserted at all beyond that.
        assertEquals(PathPosition.HERE, positionAt(40).last())
        assertEquals(PathPosition.HERE, positionAt(42).last())
    }
}
