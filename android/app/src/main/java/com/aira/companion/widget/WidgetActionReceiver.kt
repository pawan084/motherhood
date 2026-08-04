package com.aira.companion.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aira.companion.net.AiraApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home-screen quick action: log a glass of water without opening the app.
 *
 * Reads the water-reminder id the ViewModel cached on the last Me refresh, bumps
 * the widget optimistically so it responds instantly, then posts the tick to the
 * backend in the background (goAsync). If the tick fails the optimistic bump is
 * rolled back so the widget never lies. When there's no cached reminder yet the
 * widget shows an "Open Aira" button instead (handled in AiraWidgetProvider), so
 * this receiver only ever runs with a real id.
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TICK_WATER) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString("widget_water_id", null)
        val water = prefs.getInt("widget_water", -1)
        val target = prefs.getInt("widget_target", -1)
        if (id.isNullOrEmpty() || water < 0 || target <= 0) return
        if (water >= target) return   // goal already met today — nothing to do

        // Optimistic: bump the cache and repaint immediately.
        prefs.edit().putInt("widget_water", water + 1).apply()
        AiraWidgetProvider.pushUpdate(context)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AiraApi.tickReminder(context, id)
            } catch (_: Throwable) {
                // Roll back so the widget doesn't show a tick the server rejected.
                prefs.edit().putInt("widget_water", water).apply()
                AiraWidgetProvider.pushUpdate(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TICK_WATER = "com.aira.companion.widget.TICK_WATER"
        private const val PREFS = "aira_net"
    }
}
