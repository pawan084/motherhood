package com.aira.companion.notify

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aira.companion.MainActivity
import com.aira.companion.R

/** Fires for each scheduled care-reminder time; tapping opens the app on Me
 * (the default landing for returning users). Content is deliberately generic —
 * no health details on the lock screen. */
class CareReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!CareReminders.isEnabled(context)) return
        postNudge(context)
    }
}

/** Shared by every scheduled alarm and the enable-time preview — one posting
 * path, so what the user sees on schedule is exactly what they saw when opting
 * in. The line is lightly personalised from the widget cache (no network here):
 * if water is behind target it nudges that, otherwise it stays generic. */
fun postNudge(context: Context) {
    CareReminders.ensureChannel(context)
    val open = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val prefs = context.getSharedPreferences("aira_net", Context.MODE_PRIVATE)
    val water = prefs.getInt("widget_water", -1)
    val target = prefs.getInt("widget_target", -1)
    val text = if (water in 0 until target) {
        "💧 $water of $target today — a good moment for a glass of water."
    } else {
        "How's today going? Your care check-ins are waiting."
    }
    val notification = Notification.Builder(context, CareReminders.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("A moment for you")
        .setContentText(text)
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    context.getSystemService(NotificationManager::class.java)
        .notify(CareReminders.NOTIFICATION_ID, notification)
}
