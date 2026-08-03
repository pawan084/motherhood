package com.aira.companion.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Medication
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aira.companion.model.AiraUiState
import com.aira.companion.model.DetailPage
import com.aira.companion.model.Reminder
import com.aira.companion.model.moodEmoji
import com.aira.companion.model.moodOptions
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.GradientHeroSurface
import com.aira.companion.ui.components.RemoteImage
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.components.youtubeThumbnailUrl
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Lilac
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

/** The Me tab: journey hero (tap -> weekly timeline detail), emoji mood
 * check-in (tap history -> mood detail), graphical Today's care (tap ->
 * care detail), and the suggested video with thumbnail + week context. */
@Composable
fun MeScreen(
    state: AiraUiState,
    onSetMood: (String) -> Unit,
    onTick: (Reminder) -> Unit,
    onOpenDetail: (DetailPage) -> Unit,
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
        // ── Journey hero -> weekly timeline detail ─────────────────────────
        GradientHeroSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenDetail(DetailPage.Journey) },
        ) {
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
                    // This week's headline — the "relevant information" line.
                    Text(
                        text = state.journeyContent?.title ?: stageLabel(care.stage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "See your weekly guide",
                            style = MaterialTheme.typography.labelMedium,
                            color = Plum,
                        )
                        Icon(
                            Icons.Filled.ChevronRight, null,
                            tint = Plum, modifier = Modifier.size(16.dp),
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

        // ── Mood check-in (emoji picker) + history link ────────────────────
        AiraCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("How are you today?", modifier = Modifier.weight(1f))
                TextButton(onClick = { onOpenDetail(DetailPage.Moods) }) {
                    Text("History", style = MaterialTheme.typography.labelMedium, color = Plum)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                moodOptions.forEach { mood ->
                    val selected = mood == todayMood
                    Surface(
                        onClick = { onSetMood(mood) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) Plum else Paper,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, if (selected) Plum else OutlineSoft,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                moodEmoji[mood] ?: "·",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                mood.replaceFirstChar(Char::uppercase),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Paper else InkMuted,
                            )
                        }
                    }
                }
            }
        }

        // ── Today's care (graphical) -> care detail ────────────────────────
        AiraCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Today's care", modifier = Modifier.weight(1f))
                TextButton(onClick = { onOpenDetail(DetailPage.Care) }) {
                    Text("Details", style = MaterialTheme.typography.labelMedium, color = Plum)
                }
            }
            val done = state.reminders.count { it.doneToday }
            if (state.reminders.isNotEmpty()) {
                Text(
                    text = "$done of ${state.reminders.size} done",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (done == state.reminders.size) Sage else InkMuted,
                )
                Spacer(Modifier.height(10.dp))
            } else {
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

        // ── Suggested video (thumbnail + context) ──────────────────────────
        state.suggestedVideo?.let { video ->
            AiraCard(containerColor = LilacMist) {
                SectionLabel("For you this week")
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openYouTube(context, video.youtubeId) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RemoteImage(
                        url = youtubeThumbnailUrl(video.youtubeId),
                        contentDescription = video.title,
                        modifier = Modifier
                            .width(124.dp)
                            .height(70.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            video.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                        )
                        Text(
                            text = listOfNotNull(
                                video.topic.takeIf { it.isNotBlank() },
                                video.weekBand?.let { "weeks $it" },
                                video.durationMinutes?.let { "$it min" },
                            ).joinToString(" · ") + " · YouTube",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                }
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
    Column {
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
        if (reminder.kind == "water" && reminder.targetPerDay > 1) {
            Spacer(Modifier.height(8.dp))
            WaterDroplets(reminder.ticksToday, reminder.targetPerDay)
        }
    }
}
