package com.aira.companion

import com.aira.companion.data.SafetyKeywords
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offline fallback, checked against the same phrasings as the server.
 *
 * This list only runs when the request itself failed — which is exactly when
 * nobody can fix it remotely. It was a hand-copied subset that had drifted:
 * "cant breathe" and every natural phrasing of reduced fetal movement passed
 * straight through to a generic reply while the phone was offline.
 *
 * The list is now generated from backend/safety.py; these tests check the
 * matching logic that consumes it.
 */
class OfflineSafetyTest {

    @Test
    fun apostropheLessTypingStillReachesUrgent() {
        // How people type on a phone, and what the old list missed.
        assertTrue(SafetyKeywords.looksUrgent("i cant breathe"))
        assertTrue(SafetyKeywords.looksUrgent("I can't breathe"))
        assertTrue(SafetyKeywords.looksUrgent("i can\u2019t breathe"))
    }

    @Test
    fun describedHaemorrhageReachesUrgent() {
        assertTrue(SafetyKeywords.looksUrgent("there is so much blood"))
        assertTrue(SafetyKeywords.looksUrgent("I'm soaking a pad every hour"))
    }

    @Test
    fun selfHarmReachesUrgent() {
        assertTrue(SafetyKeywords.looksUrgent("thinking of hurting myself"))
        assertTrue(SafetyKeywords.looksUrgent("I want to kill myself"))
    }

    @Test
    fun punctuationDoesNotSplitAPhrase() {
        assertTrue(SafetyKeywords.looksUrgent("heavy, bleeding!"))
    }

    @Test
    fun ordinaryMessagesDoNotTriggerUrgentHelp() {
        // Offline the fallback errs toward urgent, but not so far that ordinary
        // questions send a frightened person to an emergency screen.
        assertFalse(SafetyKeywords.looksUrgent("how do I plan my meals"))
        assertFalse(SafetyKeywords.looksUrgent("what should I ask at my next visit"))
        assertFalse(SafetyKeywords.looksUrgent("the baby is moving a lot today"))
    }

    @Test
    fun theListIsNotEmpty() {
        // A generator failure that produced an empty list would disable the
        // fallback silently.
        assertTrue(SafetyKeywords.OFFLINE_RED_PHRASES.size > 20)
    }
}
