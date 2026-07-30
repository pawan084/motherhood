package com.aira.companion.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aira.companion.data.AiraApi

/**
 * Re-time every reminder when the clock underneath them moves.
 *
 * WorkManager takes a DURATION, not a moment. "8:00 PM daily" is turned into
 * "fire in 9h 42m" at the instant it is scheduled, and that duration is all the
 * system keeps. Nothing re-derives it afterwards, so anything that changes what
 * the local clock reads leaves every pending reminder pointing at the wrong
 * time:
 *
 *  - Travel. Fly Delhi to London and an 8:00 PM tablet reminder arrives at
 *    14:30 local, because the 9h 42m elapsed exactly as promised.
 *  - Daylight saving. An hour appears or disappears and every pending reminder
 *    is an hour out until it next fires.
 *  - Someone correcting a wrong device clock.
 *
 * It self-healed after one cycle — the worker re-books from the current local
 * time when it fires — which is precisely what made it easy to miss: the second
 * day was right, so only the first dose after a change was wrong. On a daily
 * medicine that is the dose you get wrong on the day you were travelling.
 *
 * The reminder list comes from the offline cache, not the network. This fires
 * while the app is closed, often mid-flight with no signal, and the cache added
 * for offline reading already holds the last known list — so re-timing needs
 * nothing that a plane can take away.
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            -> reschedule(context)
        }
    }

    private fun reschedule(context: Context) {
        val care = AiraApi.cachedCare(context)?.value ?: return
        Log.i(
            "AiraReminders",
            "clock changed — re-timing ${care.reminders.size} reminders " +
                "and ${care.appointments.size} appointments",
        )
        // syncAll cancels everything first, so this replaces the stale delays
        // rather than adding a second set alongside them.
        ReminderScheduler.syncAll(context, care.reminders, care.appointments)
    }
}
