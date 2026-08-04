package com.aira.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Emergency
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aira.companion.model.AiraTool
import com.aira.companion.model.CareSummary
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.components.ToolListRow
import com.aira.companion.ui.components.softSurface
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.OutlineSoft
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Lilac
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.SageDeep
import com.aira.companion.ui.theme.SageMist
import com.aira.companion.ui.theme.Urgent

/** Settings: opened from the top-bar gear as a full-screen overlay. Absorbs
 * the old You tab plus the care tools orphaned by the 3-tab restructure. */
@Composable
fun SettingsScreen(
    careSummary: CareSummary?,
    onOpenTool: (AiraTool) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var personalisationEnabled by remember { mutableStateOf(true) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Ivory)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 6.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close settings",
                    tint = Ink,
                )
            }
            Text("Settings", style = MaterialTheme.typography.titleMedium, color = Ink)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(64.dp)
                        .background(Lilac, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (careSummary?.displayName?.take(1) ?: "A").uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Plum,
                )
            }
            Spacer(modifier = Modifier.width(15.dp))
            Column {
                Text(
                    text = careSummary?.displayName?.takeIf { it.isNotBlank() } ?: "Your profile",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink,
                )
                Text(
                    text = listOfNotNull(
                        careSummary?.week?.let { "Week $it" },
                        when (careSummary?.language) {
                            "hi" -> "Hindi"
                            "hi-Latn" -> "Hinglish"
                            else -> "English"
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel("Your care, your control")

        ToolListRow(
            icon = Icons.Outlined.Lock,
            title = "Privacy & consent",
            subtitle = "Data, permissions and export",
            onClick = { onOpenTool(AiraTool.Privacy) },
        )
        ToolListRow(
            icon = Icons.Outlined.Memory,
            title = "What Aira remembers",
            subtitle = "Review or forget care context",
            onClick = { onOpenTool(AiraTool.Memory) },
        )
        ToolListRow(
            icon = Icons.Outlined.Language,
            title = "Voice & language",
            subtitle = "English · Aira warm voice",
            onClick = { onOpenTool(AiraTool.Voice) },
        )
        ToolListRow(
            icon = Icons.Outlined.RecordVoiceOver,
            title = "Companion mode",
            subtitle = "Text, voice or talking avatar",
            onClick = { onOpenTool(AiraTool.Companion) },
        )
        ToolListRow(
            icon = Icons.Outlined.Group,
            title = "Partner access",
            subtitle = "Practical tasks only",
            onClick = { onOpenTool(AiraTool.Partner) },
            accent = SageDeep,
        )
        ToolListRow(
            icon = Icons.Outlined.SupportAgent,
            title = "Help & human support",
            subtitle = "Care navigator, FAQs and feedback",
            onClick = { onOpenTool(AiraTool.Support) },
            accent = SageDeep,
        )

        Spacer(modifier = Modifier.height(18.dp))
        SectionLabel("Care tools")

        ToolListRow(
            icon = Icons.Outlined.Emergency,
            title = "Emergency profile",
            subtitle = "Available offline",
            onClick = { onOpenTool(AiraTool.Emergency) },
            accent = Urgent,
        )
        ToolListRow(
            icon = Icons.Outlined.Medication,
            title = "Medicines",
            subtitle = "Your care routine",
            onClick = { onOpenTool(AiraTool.Medicines) },
        )
        ToolListRow(
            icon = Icons.Outlined.CalendarMonth,
            title = "Visit copilot",
            subtitle = "Prepare questions for appointments",
            onClick = { onOpenTool(AiraTool.Appointment) },
        )
        ToolListRow(
            icon = Icons.Outlined.Checklist,
            title = "Care plan",
            subtitle = "Your current priorities",
            onClick = { onOpenTool(AiraTool.CarePlan) },
        )

        Spacer(modifier = Modifier.height(18.dp))

        AiraCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .background(SageMist, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = SageDeep,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI personalisation",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                    )
                    Text(
                        text = "Use only approved context in answers",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
                Switch(
                    checked = personalisationEnabled,
                    onCheckedChange = { personalisationEnabled = it },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Appearance (review #46): system-follow, light, or dark ─────────
        AiraCard {
            val context = androidx.compose.ui.platform.LocalContext.current
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
            )
            Text(
                text = "Dark mode is easier on 3 AM eyes",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    com.aira.companion.ui.theme.ThemeMode.SYSTEM to "System",
                    com.aira.companion.ui.theme.ThemeMode.LIGHT to "Light",
                    com.aira.companion.ui.theme.ThemeMode.DARK to "Dark",
                ).forEach { (value, label) ->
                    val selected = com.aira.companion.ui.theme.ThemeMode.mode == value
                    Surface(
                        onClick = {
                            com.aira.companion.ui.theme.ThemeMode.save(context, value)
                        },
                        shape = CircleShape,
                        color = if (selected) Plum else Paper,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) Plum else OutlineSoft,
                        ),
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Paper else Ink,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Care reminders: on by default from onboarding; the user controls
        //    how often (1–3×/day) and at what times. Local AlarmManager. ─────
        AiraCard {
            val context = androidx.compose.ui.platform.LocalContext.current
            val reminders = com.aira.companion.notify.CareReminders
            var on by remember { mutableStateOf(reminders.isEnabled(context)) }
            var times by remember { mutableStateOf(reminders.times(context)) }
            val permissionLauncher =
                androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts
                        .RequestPermission(),
                ) { granted ->
                    if (granted) { reminders.setEnabled(context, true); on = true }
                }
            fun turnOn() {
                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    reminders.setEnabled(context, true); on = true
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Care reminders",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                    )
                    Text(
                        text = if (on) {
                            "${times.size}× a day · " + times.joinToString(", ") { reminders.fmt(it) }
                        } else {
                            "Gentle nudges for your care routine"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
                Switch(
                    checked = on,
                    onCheckedChange = { want ->
                        if (!want) { reminders.setEnabled(context, false); on = false } else turnOn()
                    },
                )
            }
            if (on) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "How often",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        1 to listOf(9 * 60),
                        2 to listOf(9 * 60, 20 * 60),
                        3 to listOf(9 * 60, 14 * 60, 20 * 60),
                    ).forEach { (count, defaults) ->
                        val selected = times.size == count
                        Surface(
                            onClick = { times = defaults; reminders.setTimes(context, defaults) },
                            shape = CircleShape,
                            color = if (selected) Plum else Paper,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, if (selected) Plum else OutlineSoft,
                            ),
                        ) {
                            Text(
                                text = "${count}× a day",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Paper else Ink,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "At",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted,
                )
                Spacer(modifier = Modifier.height(4.dp))
                times.forEachIndexed { index, minutes ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    ) {
                        Text(
                            text = "Reminder ${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            onClick = {
                                android.app.TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        val updated = times.toMutableList()
                                            .also { it[index] = hour * 60 + minute }
                                            .distinct().sorted()
                                        times = updated
                                        reminders.setTimes(context, updated)
                                    },
                                    minutes / 60, minutes % 60, false,
                                ).show()
                            },
                            modifier = Modifier.softSurface(RoundedCornerShape(12.dp), 3.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Paper,
                        ) {
                            Text(
                                text = reminders.fmt(minutes),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = Ink,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AiraCard(containerColor = SageMist) {
            Text(
                text = "Never used for advertising",
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
            )
            Text(
                text = "Your sensitive health data is not an ad product.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        }
    }
}
