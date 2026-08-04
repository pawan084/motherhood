package com.aira.companion.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.aira.companion.model.Reminder
import java.util.Calendar

/**
 * Per-event care reminders (Phase 1). Reads each reminder's notify schedule and
 * sets one inexact daily AlarmManager alarm per time, firing an event-specific
 * notification ("Time to take your prenatal vitamin", "Water break") through
 * [ReminderNotifyReceiver]. Complements the general daily nudge in
 * [CareReminders] — this layer is about a specific thing at a specific time.
 *
 * Alarms don't survive a reboot, so [sync] is called on every Me refresh
 * (AiraViewModel) with the freshest reminder list. Capped at [MAX_ALARMS] to
 * stay battery-friendly; anything over the cap is dropped (logged by count).
 */
object ReminderScheduler {
    private const val REQUEST_BASE = 5200
    const val MAX_ALARMS = 16
    private const val PREFS = "aira_net"
    private const val KEY_COUNT = "reminder_alarm_count"
    private const val ACTION_PREFIX = "com.aira.companion.reminder."

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Re-arm all per-reminder alarms from the current reminder list. */
    fun sync(context: Context, reminders: List<Reminder>) {
        CareReminders.ensureChannel(context)
        cancelAll(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        var index = 0
        for (reminder in reminders) {
            if (!reminder.notifyEnabled) continue
            for (time in reminder.notifyTimes) {
                if (index >= MAX_ALARMS) break
                val minutes = parseHhMm(time) ?: continue
                val at = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, minutes / 60)
                    set(Calendar.MINUTE, minutes % 60)
                    set(Calendar.SECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                }
                alarms.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP, at.timeInMillis,
                    AlarmManager.INTERVAL_DAY, pending(context, index, reminder),
                )
                index++
            }
        }
        prefs(context).edit().putInt(KEY_COUNT, index).apply()
    }

    fun cancelAll(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        for (i in 0 until MAX_ALARMS) {
            alarms.cancel(
                PendingIntent.getBroadcast(
                    context, REQUEST_BASE + i,
                    Intent(context, ReminderNotifyReceiver::class.java)
                        .setAction(ACTION_PREFIX + (REQUEST_BASE + i)),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
    }

    private fun pending(context: Context, index: Int, reminder: Reminder): PendingIntent {
        val code = REQUEST_BASE + index
        return PendingIntent.getBroadcast(
            context, code,
            Intent(context, ReminderNotifyReceiver::class.java)
                .setAction(ACTION_PREFIX + code)
                .putExtra("title", reminder.title)
                .putExtra("kind", reminder.kind)
                .putExtra("notif_id", code),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun parseHhMm(s: String): Int? {
        val parts = s.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }
}
