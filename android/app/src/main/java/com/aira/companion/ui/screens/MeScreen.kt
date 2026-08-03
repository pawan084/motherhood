package com.aira.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aira.companion.model.AiraTool
import com.aira.companion.model.AiraUiState
import com.aira.companion.model.MoodEntry
import com.aira.companion.model.Reminder
import com.aira.companion.model.moodOptions
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.ChoiceChips
import com.aira.companion.ui.components.GradientHeroSurface
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.components.ToolListRow
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.OutlineSoft
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.Sage
import com.aira.companion.ui.theme.SageMist
import java.time.LocalDate

private fun stageLabel(stage: String): String = when (stage) {
    "pregnant" -> "Pregnant"
    "postpartum" -> "Postpartum"
    "trying_to_conceive" -> "Trying to conceive"
    else -> "Your journey"
}

/** The Me tab: real journey summary, mood check-in, today's reminders, and a
 * suggested video — all from the backend (loaded by MainExperience's
 * LaunchedEffect via refreshMe()). */
@Composable
fun MeScreen(
    state: AiraUiState,
    onSetMood: (String) -> Unit,
    onTick: (Reminder) -> Unit,
    onOpenTool: (AiraTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val today = LocalDate.now().toString()
    val todayMood = state.moods.lastOrNull { it.day == today }?.mood

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // ── Journey ────────────────────────────────────────────────────────
        GradientHeroSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                val care = state.careSummary
                if (care != null) {
                    Text(
                        text = if (care.displayName.isNotBlank()) "Hi ${care.displayName}" else "Hi there",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = care.week?.let { "Week $it" } ?: stageLabel(care.stage),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Ink,
                    )
                    if (care.week != null) {
                        Text(
                            text = stageLabel(care.stage),
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted,
                        )
                    }
                } else {
                    Text(
                        text = "Your journey, your pace",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ink,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (state.meLoading) "Syncing your care context…"
                        else "Tell Aira where you are in your journey — just say it in Chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                    )
                }
            }
        }

        // ── Mood check-in ──────────────────────────────────────────────────
        AiraCard {
            SectionLabel("How are you today?")
            Spacer(Modifier.height(12.dp))
            ChoiceChips(
                options = moodOptions.map { it.replaceFirstChar(Char::uppercase) },
                selected = todayMood?.replaceFirstChar(Char::uppercase) ?: "",
                onSelect = { onSetMood(it.lowercase()) },
            )
            if (state.moods.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                RecentMoodsStrip(state.moods)
            }
        }

        // ── Reminders ──────────────────────────────────────────────────────
        AiraCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Today's care", modifier = Modifier.weight(1f))
                TextButton(onClick = { onOpenTool(AiraTool.Medicines) }) {
                    Text("Manage", style = MaterialTheme.typography.labelMedium, color = Plum)
                }
            }
            if (state.reminders.isEmpty()) {
                Text(
                    text = if (state.meLoading) "Syncing…" else "No reminders yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.reminders.forEach { reminder ->
                    ReminderRow(reminder, onTick)
                }
            }
        }

        // ── Suggested video ────────────────────────────────────────────────
        state.suggestedVideo?.let { video ->
            AiraCard(containerColor = LilacMist) {
                SectionLabel("For you this week")
                Spacer(Modifier.height(4.dp))
                ToolListRow(
                    icon = Icons.Outlined.PlayCircle,
                    title = video.title,
                    subtitle = listOfNotNull(
                        video.topic.takeIf { it.isNotBlank() },
                        video.durationMinutes?.let { "$it min" },
                    ).joinToString(" · "),
                    onClick = { openYouTube(context, video.youtubeId) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    onTick: (Reminder) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (reminder.kind) {
                "water" -> Icons.Outlined.WaterDrop
                "exercise" -> Icons.Outlined.DirectionsWalk
                "medicine" -> Icons.Outlined.Medication
                else -> Icons.Outlined.TaskAlt
            },
            contentDescription = null,
            tint = Plum,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(reminder.title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(
                text = if (reminder.targetPerDay > 1) {
                    "${reminder.ticksToday} of ${reminder.targetPerDay} today"
                } else if (reminder.doneToday) "Done today" else "Not yet today",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        }
        Surface(
            onClick = { onTick(reminder) },
            enabled = !reminder.doneToday,
            shape = RoundedCornerShape(12.dp),
            color = if (reminder.doneToday) SageMist else Paper,
            contentColor = if (reminder.doneToday) Sage else Plum,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, if (reminder.doneToday) SageMist else OutlineSoft,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = if (reminder.doneToday) "${reminder.title} done" else "Tick ${reminder.title}",
                modifier = Modifier.padding(8.dp).size(18.dp),
            )
        }
    }
}

@Composable
private fun RecentMoodsStrip(moods: List<MoodEntry>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        moods.takeLast(7).forEach { entry ->
            Surface(shape = RoundedCornerShape(10.dp), color = LilacMist) {
                Column(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        // "Mon" from the ISO date; parse failures just show the day number.
                        text = runCatching {
                            LocalDate.parse(entry.day).dayOfWeek.name.take(3).lowercase()
                                .replaceFirstChar(Char::uppercase)
                        }.getOrDefault(entry.day.takeLast(2)),
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                    )
                    Text(
                        text = entry.mood.replaceFirstChar(Char::uppercase),
                        style = MaterialTheme.typography.labelMedium,
                        color = Ink,
                    )
                }
            }
        }
    }
}
