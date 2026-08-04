package com.aira.companion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.aira.companion.MainActivity
import com.aira.companion.R

/**
 * Minimal home-screen widget (review #55): week · day + today's water,
 * rendered from a prefs cache the ViewModel writes on every Me refresh —
 * the widget itself never touches the network. Tap opens the app.
 */
class AiraWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        ids.forEach { id -> manager.updateAppWidget(id, build(context)) }
    }

    companion object {
        private const val PREFS = "aira_net"

        fun pushUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, AiraWidgetProvider::class.java),
            )
            if (ids.isNotEmpty()) {
                ids.forEach { manager.updateAppWidget(it, build(context)) }
            }
        }

        private fun build(context: Context): RemoteViews {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val week = prefs.getInt("widget_week", -1)
            val day = prefs.getInt("widget_day", -1)
            val water = prefs.getInt("widget_water", -1)
            val target = prefs.getInt("widget_target", -1)
            val views = RemoteViews(context.packageName, R.layout.widget_aira)
            views.setTextViewText(
                R.id.widget_title,
                when {
                    week > 0 && day > 0 -> "Week $week · Day $day"
                    week > 0 -> "Week $week"
                    else -> "Aira"
                },
            )
            val hasWater = water >= 0 && target > 0
            views.setTextViewText(
                R.id.widget_subtitle,
                if (hasWater) "💧 $water of $target today" else "Open to check in",
            )
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            // Quick action: tick water straight from the home screen. Only a
            // broadcast (no app open) when there's a cached reminder to tick;
            // otherwise the button opens the app so one can be established.
            if (hasWater && water < target) {
                views.setTextViewText(R.id.widget_action, "💧  Log water")
                views.setOnClickPendingIntent(
                    R.id.widget_action,
                    PendingIntent.getBroadcast(
                        context, 4300,
                        Intent(context, WidgetActionReceiver::class.java)
                            .setAction(WidgetActionReceiver.ACTION_TICK_WATER),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            } else {
                views.setTextViewText(
                    R.id.widget_action,
                    if (hasWater) "✓ Water done" else "Open Aira",
                )
                views.setOnClickPendingIntent(
                    R.id.widget_action,
                    PendingIntent.getActivity(
                        context, 4301,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            return views
        }
    }
}
