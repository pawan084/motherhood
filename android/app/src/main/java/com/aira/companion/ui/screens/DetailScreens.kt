package com.aira.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.aira.companion.model.JourneyContent
import com.aira.companion.model.MoodEntry
import com.aira.companion.model.Reminder
import com.aira.companion.model.moodEmoji
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SectionLabel
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

@Composable
private fun DetailScaffold(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 6.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

// ── Journey ──────────────────────────────────────────────────────────────────

/** Week-by-week guide: a horizontal week timeline + the band's three
 * sections. Browsing weeks never mutates the stored care context. */
@Composable
fun JourneyDetailScreen(
    content: JourneyContent?,
    onBrowseWeek: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailScaffold(title = "Your journey", onClose = onClose, modifier = modifier) {
        if (content == null) {
            Text(
                "Loading this week's guide…",
                style = MaterialTheme.typography.bodyMedium, color = InkMuted,
            )
            return@DetailScaffold
        }
        val shown = content.shownWeek
        if (shown != null) {
            SectionLabel("Weekly timeline")
            Spacer(Modifier.height(8.dp))
            WeekTimeline(
                shownWeek = shown,
                currentWeek = content.currentWeek,
                onSelect = onBrowseWeek,
            )
            Spacer(Modifier.height(16.dp))
        }
        AiraCard(containerColor = LilacMist) {
            if (shown != null) {
                Text(
                    text = "Week $shown" + if (shown == content.currentWeek) " · you are here" else "",
                    style = MaterialTheme.typography.labelMedium, color = InkMuted,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(content.title, style = MaterialTheme.typography.headlineSmall, color = Ink)
        }
        Spacer(Modifier.height(12.dp))
        JourneySection("Your body", content.yourself)
        JourneySection("Your baby", content.baby)
        JourneySection("Prepare for your visit", content.prepare)
        if (content.disclaimer.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                content.disclaimer,
                style = MaterialTheme.typography.bodySmall, color = InkMuted,
            )
        }
    }
}

@Composable
private fun JourneySection(label: String, body: String) {
    if (body.isBlank()) return
    AiraCard {
        SectionLabel(label)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun WeekTimeline(
    shownWeek: Int,
    currentWeek: Int?,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (1..42).forEach { week ->
            val selected = week == shownWeek
            val isCurrent = week == currentWeek
            Surface(
                onClick = { onSelect(week) },
                shape = CircleShape,
                color = when {
                    selected -> Plum
                    isCurrent -> Lilac
                    else -> Paper
                },
                contentColor = if (selected) Paper else Ink,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, if (selected || isCurrent) Plum else OutlineSoft,
                ),
            ) {
                Text(
                    text = "$week",
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// ── Moods ────────────────────────────────────────────────────────────────────

/** 30-day mood history: a count summary + one row per day, newest first. */
@Composable
fun MoodDetailScreen(
    history: List<MoodEntry>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailScaffold(title = "Your moods", onClose = onClose, modifier = modifier) {
        if (history.isEmpty()) {
            Text(
                "No check-ins yet — say how you feel on the Me tab and it lands here.",
                style = MaterialTheme.typography.bodyMedium, color = InkMuted,
            )
            return@DetailScaffold
        }
        SectionLabel("Last 30 days")
        Spacer(Modifier.height(8.dp))
        AiraCard {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                history.groupingBy { it.mood }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .forEach { (mood, count) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                moodEmoji[mood] ?: "·",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                "$count",
                                style = MaterialTheme.typography.titleSmall, color = Ink,
                            )
                            Text(
                                mood.replaceFirstChar(Char::uppercase),
                                style = MaterialTheme.typography.labelSmall, color = InkMuted,
                            )
                        }
                    }
            }
        }
        Spacer(Modifier.height(12.dp))
        history.sortedByDescending { it.day }.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    moodEmoji[entry.mood] ?: "·",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.mood.replaceFirstChar(Char::uppercase),
                        style = MaterialTheme.typography.titleSmall, color = Ink,
                    )
                    Text(
                        formatDay(entry.day),
                        style = MaterialTheme.typography.bodySmall, color = InkMuted,
                    )
                }
            }
        }
    }
}

private fun formatDay(iso: String): String = runCatching {
    val date = LocalDate.parse(iso)
    val dow = date.dayOfWeek.name.lowercase().replaceFirstChar(Char::uppercase).take(3)
    val month = date.month.name.lowercase().replaceFirstChar(Char::uppercase).take(3)
    "$dow, ${date.dayOfMonth} $month"
}.getOrDefault(iso)

// ── Care ─────────────────────────────────────────────────────────────────────

/** Today's care in full: water droplet progress, every reminder with its
 * graphic, add-a-reminder, and delete (never for medicine rows — those are
 * managed in the Medicines sheet). */
@Composable
fun CareDetailScreen(
    reminders: List<Reminder>,
    onTick: (Reminder) -> Unit,
    onAdd: (String, String, Int) -> Unit,
    onDelete: (Reminder) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailScaffold(title = "Today's care", onClose = onClose, modifier = modifier) {
        val done = reminders.count { it.doneToday }
        SectionLabel("Progress")
        Spacer(Modifier.height(8.dp))
        AiraCard(containerColor = if (done == reminders.size && reminders.isNotEmpty()) SageMist else Paper) {
            Text(
                text = if (reminders.isEmpty()) "Nothing to track yet."
                else "$done of ${reminders.size} done today",
                style = MaterialTheme.typography.headlineSmall, color = Ink,
            )
        }
        Spacer(Modifier.height(14.dp))

        reminders.forEach { reminder ->
            AiraCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (reminder.kind) {
                            "water" -> Icons.Outlined.WaterDrop
                            "exercise" -> Icons.Outlined.DirectionsWalk
                            "medicine" -> Icons.Outlined.Medication
                            else -> Icons.Outlined.TaskAlt
                        },
                        contentDescription = null, tint = Plum, modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(reminder.title, style = MaterialTheme.typography.titleSmall, color = Ink)
                        Text(
                            text = when {
                                reminder.targetPerDay > 1 ->
                                    "${reminder.ticksToday} of ${reminder.targetPerDay} today"
                                reminder.doneToday -> "Done today"
                                else -> "Not yet today"
                            },
                            style = MaterialTheme.typography.bodySmall, color = InkMuted,
                        )
                    }
                    if (reminder.kind != "medicine") {
                        IconButton(onClick = { onDelete(reminder) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                "Remove ${reminder.title}", tint = InkMuted,
                            )
                        }
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
                            Icons.Filled.Check,
                            contentDescription = "Tick ${reminder.title}",
                            modifier = Modifier.padding(8.dp).size(18.dp),
                        )
                    }
                }
                if (reminder.kind == "water") {
                    Spacer(Modifier.height(10.dp))
                    WaterDroplets(reminder.ticksToday, reminder.targetPerDay)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(8.dp))
        SectionLabel("Add a reminder")
        Spacer(Modifier.height(8.dp))
        var title by remember { mutableStateOf("") }
        AiraCard {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("e.g. Afternoon rest") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                label = "Add daily reminder",
                onClick = {
                    if (title.isNotBlank()) {
                        onAdd(title.trim(), "custom", 1)
                        title = ""
                    }
                },
                enabled = title.isNotBlank(),
                trailingIcon = Icons.Filled.Add,
            )
        }
    }
}

/** The water graphic: one droplet per target unit, filled as ticked. */
@Composable
fun WaterDroplets(ticks: Int, target: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(target.coerceAtMost(12)) { index ->
            Icon(
                imageVector = Icons.Outlined.WaterDrop,
                contentDescription = null,
                tint = if (index < ticks) Plum else Lilac,
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        if (index < ticks) LilacMist else Paper,
                        CircleShape,
                    )
                    .padding(2.dp),
            )
        }
    }
}
