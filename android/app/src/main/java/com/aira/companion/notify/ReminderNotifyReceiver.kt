package com.aira.companion.notify

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aira.companion.MainActivity
import com.aira.companion.R

/** Posts a per-event care reminder ("Take your prenatal vitamin", "Water
 * break") scheduled by [ReminderScheduler]. Content is specific to the event
 * kind; tapping opens the app on Me. No health details beyond the item name. */
class ReminderNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CareReminders.ensureChannel(context)
        val title = intent.getStringExtra("title")?.takeIf { it.isNotBlank() } ?: "Care reminder"
        val kind = intent.getStringExtra("kind") ?: "custom"
        val notifId = intent.getIntExtra("notif_id", 5200)

        val heading = if (kind == "medicine") "Medicine reminder" else title
        val text = when (kind) {
            "water" -> "Time for a glass of water."
            "exercise" -> "A little movement — even a short walk counts."
            "medicine" -> "Time to take $title."
            else -> "Reminder: $title."
        }

        val open = PendingIntent.getActivity(
            context, notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CareReminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(heading)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notifId, notification)
    }
}
