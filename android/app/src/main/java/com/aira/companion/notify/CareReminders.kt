package com.aira.companion.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Opt-in daily care nudge (review #18): framework AlarmManager — inexact,
 * battery-friendly, no new dependencies. One notification a day at ~15:00,
 * the hour the water-pace rule starts caring. Deliberately minimal: the
 * proactive brain stays server-side; this only reminds the user it exists.
 */
object CareReminders {
    const val CHANNEL_ID = "care_reminders"
    private const val REQUEST_CODE = 4201
    private const val PREFS = "aira_net"          // same file as the token
    private const val KEY_ENABLED = "care_reminder_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            schedule(context)
            // Immediate preview: shows the user exactly what the daily
            // nudge looks like AND proves the posting path works on this
            // device (some OEMs quietly break scheduled notifications).
            postNudge(context)
        } else {
            cancel(context)
        }
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "Care reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "A gentle daily nudge for your care routine" },
        )
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, CareReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(context: Context) {
        ensureChannel(context)
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, at.timeInMillis,
            AlarmManager.INTERVAL_DAY, pending(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
    }
}
