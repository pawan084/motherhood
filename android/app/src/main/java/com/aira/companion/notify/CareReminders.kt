package com.aira.companion.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Local care-reminder notifications — framework AlarmManager, no dependencies.
 *
 * The user picks how OFTEN (1–3 a day) and at WHAT times; each time is one
 * inexact daily alarm that posts a gentle, health-detail-free nudge. Scheduling
 * is on-device, so the preferences live on-device too (SharedPreferences).
 *
 * A sensible default (one nudge at 09:00) is enabled the first time onboarding
 * finishes; everything is adjustable in Settings and fully off with one switch.
 * AlarmManager alarms don't survive a reboot, so [scheduleAll] is also called on
 * every app launch (MainActivity) to re-arm them.
 */
object CareReminders {
    const val CHANNEL_ID = "care_reminders"
    const val NOTIFICATION_ID = 4201
    private const val REQUEST_BASE = 4201        // one request code per time slot
    const val MAX_TIMES = 3

    private const val PREFS = "aira_net"          // shared with the token + widget cache
    private const val KEY_ENABLED = "notif_enabled"
    private const val KEY_TIMES = "notif_times"           // CSV of "HH:mm"
    private const val KEY_ONBOARD_DEFAULT = "notif_onboard_default"
    private const val LEGACY_ENABLED = "care_reminder_enabled"

    // minutes-since-midnight; 09:00, one gentle morning nudge
    val DEFAULT_TIMES = listOf(9 * 60)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean {
        val p = prefs(context)
        // Honour the old single-toggle key so an existing opt-in isn't lost.
        return p.getBoolean(KEY_ENABLED, p.getBoolean(LEGACY_ENABLED, false))
    }

    /** Configured times as minutes-since-midnight — sorted, deduped, 1..MAX_TIMES. */
    fun times(context: Context): List<Int> {
        val raw = prefs(context).getString(KEY_TIMES, null)
        val parsed = raw?.split(",")?.mapNotNull { token ->
            val hm = token.trim().split(":")
            val h = hm.getOrNull(0)?.toIntOrNull()
            val m = hm.getOrNull(1)?.toIntOrNull()
            if (h != null && m != null) h * 60 + m else null
        }?.filter { it in 0 until 24 * 60 }
        return (parsed?.takeIf { it.isNotEmpty() } ?: DEFAULT_TIMES)
            .distinct().sorted().take(MAX_TIMES)
    }

    fun setTimes(context: Context, minutes: List<Int>) {
        val clean = minutes.filter { it in 0 until 24 * 60 }
            .distinct().sorted().take(MAX_TIMES)
            .ifEmpty { DEFAULT_TIMES }
        prefs(context).edit()
            .putString(KEY_TIMES, clean.joinToString(",") { fmt(it) })
            .apply()
        if (isEnabled(context)) scheduleAll(context)   // re-arm live
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            scheduleAll(context)
            // Enable-time preview: shows exactly what a nudge looks like AND
            // proves the posting path works on this device (some OEMs quietly
            // break scheduled notifications).
            postNudge(context)
        } else {
            cancelAll(context)
        }
    }

    /** Turn reminders on with a gentle default the first time onboarding finishes. */
    fun applyOnboardingDefault(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_ONBOARD_DEFAULT, false)) return   // only ever once
        p.edit().putBoolean(KEY_ONBOARD_DEFAULT, true).apply()
        if (!p.contains(KEY_TIMES)) {
            p.edit().putString(KEY_TIMES, DEFAULT_TIMES.joinToString(",") { fmt(it) }).apply()
        }
        setEnabled(context, true)
    }

    fun fmt(minutes: Int): String = String.format("%02d:%02d", minutes / 60, minutes % 60)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "Care reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Gentle nudges for your care routine" },
        )
    }

    private fun pending(context: Context, index: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_BASE + index,
            // Distinct action per slot so the PendingIntents don't collapse.
            Intent(context, CareReminderReceiver::class.java).setAction("$CHANNEL_ID.$index"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun scheduleAll(context: Context) {
        ensureChannel(context)
        cancelAll(context)                       // clear any stale slots first
        val alarms = context.getSystemService(AlarmManager::class.java)
        times(context).forEachIndexed { index, minutes ->
            val at = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minutes / 60)
                set(Calendar.MINUTE, minutes % 60)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            alarms.setInexactRepeating(
                AlarmManager.RTC_WAKEUP, at.timeInMillis,
                AlarmManager.INTERVAL_DAY, pending(context, index),
            )
        }
    }

    fun cancelAll(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        for (i in 0 until MAX_TIMES) alarms.cancel(pending(context, i))
    }
}
