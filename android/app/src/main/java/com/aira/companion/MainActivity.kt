package com.aira.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aira.companion.ui.AiraApp
import com.aira.companion.ui.theme.AiraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.aira.companion.ui.theme.ThemeMode.load(this)
        // Re-arm care reminders — AlarmManager alarms don't survive a reboot, so
        // re-schedule whenever the app is opened.
        if (com.aira.companion.notify.CareReminders.isEnabled(this)) {
            com.aira.companion.notify.CareReminders.scheduleAll(this)
        }
        // Ask for the post-notifications permission once (Android 13+) so the
        // onboarding-default reminder can actually appear.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val prefs = getSharedPreferences("aira_net", MODE_PRIVATE)
            val granted = checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted && !prefs.getBoolean("notif_perm_asked", false)) {
                prefs.edit().putBoolean("notif_perm_asked", true).apply()
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0x9A1,
                )
            }
        }
        enableEdgeToEdge()
        setContent {
            AiraTheme {
                AiraApp()
            }
        }
    }
}
