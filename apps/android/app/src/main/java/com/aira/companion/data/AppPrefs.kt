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
    private const val KEY_ONBOARDED = "onboarded"

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

    /**
     * True once this install has completed onboarding on some account.
     *
     * Exists so that "the server didn't answer" and "you have no account" stop
     * being the same thing. Startup asked the server who the user was, and any
     * failure — no signal, aeroplane mode, a dead hotel wifi — dropped through
     * to the same branch as a fresh install, so a returning user with no
     * connection was shown the Welcome screen and the tutorial. Their care was
     * safe on the server the whole time; the app just told them, at the worst
     * possible moment, that it had never met them.
     */
    fun wasOnboarded(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ONBOARDED, false)

    fun markOnboarded(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    /** Sign-out returns this install to a genuinely unknown user. */
    fun clearOnboarded(ctx: Context) {
        prefs(ctx).edit().remove(KEY_ONBOARDED).apply()
    }
}
