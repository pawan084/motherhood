package com.aira.companion.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The app's vocabulary of touch.
 *
 * There was no haptic feedback anywhere in this app — marking a medicine taken,
 * sending a message, and opening the urgent handoff were all silent. On a phone
 * that reads as a tap that might not have registered, and the usual response is
 * to press again. For a dose you have already logged, pressing again is exactly
 * the wrong outcome.
 *
 * Wrapped rather than called inline so the meanings stay consistent: the same
 * action should feel the same everywhere, and that only holds if there is one
 * place that decides. Three meanings is deliberately few — a phone that buzzes
 * at everything is noise, and noise is worse than silence for someone using
 * this at 3am next to a sleeping baby.
 */
class AiraHaptics(private val hf: HapticFeedback) {

    /** A choice registered: a tab, a chip, a selection. The lightest thing available. */
    fun select() = hf.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** Something is now true that was not before: taken, saved, sent, done. */
    fun confirm() = hf.performHapticFeedback(HapticFeedbackType.Confirm)

    /**
     * Weight, not alarm. Used for the urgent handoff and for deletion — moments
     * that deserve to feel different from a tick, without the app buzzing
     * urgently at someone who may already be frightened.
     */
    fun weighty() = hf.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberAiraHaptics(): AiraHaptics {
    val hf = LocalHapticFeedback.current
    return remember(hf) { AiraHaptics(hf) }
}
