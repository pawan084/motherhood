package com.aira.companion.data

import android.content.Context

/**
 * Device-local UI state — things that are true of this install, not of an account.
 *
 * Kept in its own preferences file rather than alongside the session token in
 * [AiraApi], because the two have opposite lifetimes: the token is cleared on a
 * 401 or a sign-out, and this must survive both. Mixing them would mean a dead
 * token could reset the tutorial.
 *
 * Nothing here is sent to the server. "Have you seen the tutorial" has to work
 * before the user has an account at all, and is not worth a row in anyone's
 * health record.
 */
object AppPrefs {
    private const val PREFS = "aira_local"
    private const val KEY_TUTORIAL_SEEN = "tutorial_seen"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * True once the tutorial has been completed OR skipped. Skipping counts:
     * showing it again to someone who chose to skip is not a second chance to
     * explain, it is ignoring them.
     */
    fun tutorialSeen(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_TUTORIAL_SEEN, false)

    fun markTutorialSeen(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_TUTORIAL_SEEN, true).apply()
    }
}
