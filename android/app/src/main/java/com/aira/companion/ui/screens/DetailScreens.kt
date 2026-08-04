package com.aira.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aira.companion.model.JourneyContent
import com.aira.companion.model.MoodEntry
import com.aira.companion.model.Reminder
import com.aira.companion.model.WellnessReport
import com.aira.companion.ui.components.moodStyle
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.components.softSurface
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
                "Set your journey to see your weekly guide",
                style = MaterialTheme.typography.titleMedium, color = Ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tell Aira your stage on the Me tab, and your week-by-week path appears here.",
                style = MaterialTheme.typography.bodyMedium, color = InkMuted,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onClose) { Text("Back to Me", color = Plum) }
            return@DetailScaffold
        }
        val shown = content.shownWeek
        val total = content.totalWeeks

        // ── Hero: progress + baby size ─────────────────────────────────────
        AiraCard(containerColor = LilacMist) {
            if (shown != null && total != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Week $shown of $total" +
                                if (shown == content.currentWeek) " · you are here" else "",
                            style = MaterialTheme.typography.labelMedium, color = InkMuted,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            content.title,
                            style = MaterialTheme.typography.headlineSmall, color = Ink,
                        )
                    }
                    if (content.sizeEmoji != null) {
                        Text(content.sizeEmoji, style = MaterialTheme.typography.displaySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Progress bar: how far along the whole journey.
                Surface(
                    shape = RoundedCornerShape(99.dp), color = Paper,
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                ) {
                    Row {
                        Surface(
                            shape = RoundedCornerShape(99.dp), color = Plum,
                            modifier = Modifier
                                .weight(shown.toFloat().coerceAtLeast(0.5f))
                                .height(10.dp),
                        ) {}
                        if (shown < total) Spacer(Modifier.weight((total - shown).toFloat()))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    if (content.sizeLabel != null) {
                        Text(
                            "Baby is about the size of ${content.sizeLabel}",
                            style = MaterialTheme.typography.bodyMedium, color = Ink,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Text(
                        "${(total - shown).coerceAtLeast(0)} to go",
                        style = MaterialTheme.typography.bodySmall, color = InkMuted,
                    )
                }
            } else {
                Text(content.title, style = MaterialTheme.typography.headlineSmall, color = Ink)
            }
        }
        Spacer(Modifier.height(14.dp))

        if (shown != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Your path", modifier = Modifier.weight(1f))
                // Fine-grained week stepper (the path's nodes are milestones).
                IconButton(onClick = { if (shown > 1) onBrowseWeek(shown - 1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous week",
                         tint = Plum, modifier = Modifier.size(18.dp))
                }
                Text("Week $shown", style = MaterialTheme.typography.labelMedium, color = Ink)
                IconButton(onClick = { if (shown < (total ?: 42)) onBrowseWeek(shown + 1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next week",
                         tint = Plum, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            JourneyPathTimeline(
                milestones = content.milestones,
                currentWeek = content.currentWeek,
                shownWeek = shown,
                sizeEmoji = content.sizeEmoji,
                onSelect = onBrowseWeek,
            )
            Spacer(Modifier.height(16.dp))
        }
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

// ── Moods ────────────────────────────────────────────────────────────────────

/** Mood reports: this week day-by-day, the month's distribution, and the
 * full day list. */
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
        val byDay = history.associateBy { it.day }

        SectionLabel("This week")
        Spacer(Modifier.height(8.dp))
        AiraCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                (6 downTo 0).forEach { back ->
                    val date = LocalDate.now().minusDays(back.toLong())
                    val entry = byDay[date.toString()]
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            date.dayOfWeek.name.take(1),
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted,
                        )
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (entry != null) moodStyle(entry.mood).color.copy(alpha = 0.16f) else Paper,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, if (back == 0) Plum else OutlineSoft,
                            ),
                        ) {
                            if (entry != null) {
                                Icon(
                                    moodStyle(entry.mood).icon,
                                    contentDescription = moodStyle(entry.mood).label,
                                    tint = moodStyle(entry.mood).color,
                                    modifier = Modifier.padding(7.dp).size(18.dp),
                                )
                            } else {
                                Spacer(Modifier.padding(7.dp).size(18.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionLabel("This month")
        Spacer(Modifier.height(8.dp))
        AiraCard {
            val counts = history.groupingBy { it.mood }.eachCount()
            val max = (counts.values.maxOrNull() ?: 1).coerceAtLeast(1)
            counts.entries.sortedByDescending { it.value }.forEach { (mood, count) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 5.dp),
                ) {
                    Icon(
                        moodStyle(mood).icon,
                        contentDescription = null,
                        tint = moodStyle(mood).color,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        mood.replaceFirstChar(Char::uppercase),
                        style = MaterialTheme.typography.labelMedium,
                        color = Ink,
                        modifier = Modifier.width(64.dp),
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = moodStyle(mood).color.copy(alpha = 0.75f),
                        modifier = Modifier
                            .weight(count.toFloat() / max)
                            .height(14.dp),
                    ) {}
                    if (count < max) Spacer(Modifier.weight((max - count).toFloat() / max))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "$count", style = MaterialTheme.typography.titleSmall, color = Ink,
                    )
                }
            }
            Text(
                "${history.size} check-ins in the last 30 days",
                style = MaterialTheme.typography.bodySmall, color = InkMuted,
            )
        }
        Spacer(Modifier.height(14.dp))

        SectionLabel("Day by day")
        Spacer(Modifier.height(8.dp))
        history.sortedByDescending { it.day }.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    moodStyle(entry.mood).icon,
                    contentDescription = moodStyle(entry.mood).label,
                    tint = moodStyle(entry.mood).color,
                    modifier = Modifier.size(24.dp),
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

/** Per-reminder notification schedule (Phase 1): a toggle + editable times
 * ("HH:mm") for water/exercise/custom; medicines show their derived time
 * read-only (they're managed in the Medicines sheet). */
@Composable
private fun ReminderNotifyEditor(
    reminder: Reminder,
    onSetNotify: (Reminder, Boolean, List<String>) -> Unit,
) {
    Spacer(Modifier.height(10.dp))
    HorizontalDivider(color = OutlineSoft)
    Spacer(Modifier.height(8.dp))
    if (reminder.kind == "medicine") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Notifications, null, tint = Plum, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Reminds you at " +
                    reminder.notifyTimes.joinToString(", ").ifBlank { "its scheduled time" },
                style = MaterialTheme.typography.bodySmall, color = InkMuted,
            )
        }
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Notifications, null,
            tint = if (reminder.notifyEnabled) Plum else InkMuted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Reminders",
            style = MaterialTheme.typography.titleSmall, color = Ink,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = reminder.notifyEnabled,
            onCheckedChange = { on ->
                onSetNotify(reminder, on, reminder.notifyTimes.ifEmpty { listOf("10:00") })
            },
        )
    }
    if (reminder.notifyEnabled) {
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            reminder.notifyTimes.forEach { t ->
                Surface(
                    shape = CircleShape, color = LilacMist,
                    onClick = { onSetNotify(reminder, true, reminder.notifyTimes - t) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t, style = MaterialTheme.typography.labelMedium, color = Plum)
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Filled.Close, "Remove $t", tint = Plum, modifier = Modifier.size(13.dp))
                    }
                }
            }
            Surface(
                modifier = Modifier.softSurface(CircleShape, 3.dp),
                shape = CircleShape, color = Paper,
                onClick = {
                    android.app.TimePickerDialog(
                        context,
                        { _, h, m ->
                            val nt = String.format("%02d:%02d", h, m)
                            onSetNotify(reminder, true, (reminder.notifyTimes + nt).distinct().sorted())
                        },
                        10, 0, false,
                    ).show()
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, null, tint = Plum, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add time", style = MaterialTheme.typography.labelMedium, color = Plum)
                }
            }
        }
    }
}

/** Today's care in full: water droplet progress, every reminder with its
 * graphic, add-a-reminder, and delete (never for medicine rows — those are
 * managed in the Medicines sheet). */
@Composable
fun CareDetailScreen(
    reminders: List<Reminder>,
    report: WellnessReport?,
    onTick: (Reminder) -> Unit,
    onUntick: (Reminder) -> Unit,
    onSetTarget: (Reminder, Int) -> Unit,
    onAdd: (String, String, Int) -> Unit,
    onDelete: (Reminder) -> Unit,
    onSetNotify: (Reminder, Boolean, List<String>) -> Unit,
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
                        onClick = {
                            if (reminder.doneToday) onUntick(reminder) else onTick(reminder)
                        },
                        shape = CircleShape,
                        color = if (reminder.doneToday) SageMist else LilacMist,
                        contentColor = if (reminder.doneToday) Sage else Plum,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = if (reminder.doneToday) {
                                "${reminder.title} done — tap to undo"
                            } else "Tick ${reminder.title}",
                            modifier = Modifier.padding(8.dp).size(18.dp),
                        )
                    }
                }
                if (reminder.kind == "water") {
                    Spacer(Modifier.height(10.dp))
                    WaterDroplets(
                        reminder.ticksToday, reminder.targetPerDay,
                        expectedNow = expectedWaterByNow(reminder.targetPerDay),
                    )
                    // Editable goal (review #24): a doctor's "10 glasses"
                    // shouldn't fight a hardcoded 8.
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Daily goal: ${reminder.targetPerDay} glasses",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { onSetTarget(reminder, reminder.targetPerDay - 1) },
                            enabled = reminder.targetPerDay > 4,
                        ) { Text("−", color = Plum) }
                        TextButton(
                            onClick = { onSetTarget(reminder, reminder.targetPerDay + 1) },
                            enabled = reminder.targetPerDay < 16,
                        ) { Text("+", color = Plum) }
                    }
                }
                val series = report?.reminders?.firstOrNull { it.id == reminder.id }
                if (series != null) {
                    val doneDays = series.days.associate { it.day to it.done }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            "This week",
                            style = MaterialTheme.typography.labelSmall, color = InkMuted,
                        )
                        Spacer(Modifier.width(3.dp))
                        (6 downTo 0).forEach { back ->
                            val date = LocalDate.now().minusDays(back.toLong()).toString()
                            Surface(
                                shape = CircleShape,
                                color = if (doneDays[date] == true) Sage else Paper,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (doneDays[date] == true) Sage else OutlineSoft,
                                ),
                                modifier = Modifier.size(13.dp),
                            ) {}
                        }
                    }
                    val monthDone = series.days.count { it.done }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$monthDone of ${report.days} days this month",
                        style = MaterialTheme.typography.bodySmall, color = InkMuted,
                    )
                }
                ReminderNotifyEditor(reminder, onSetNotify)
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

/** The water graphic: one droplet per target unit, filled as ticked.
 * `expectedNow` outlines the droplets that SHOULD be done by this hour —
 * the pace is visible without a single word (review #25). */
@Composable
fun WaterDroplets(ticks: Int, target: Int, expectedNow: Int = -1) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics {
            contentDescription = "$ticks of $target glasses today"
        },
    ) {
        repeat(target.coerceAtMost(12)) { index ->
            val behindPace = index >= ticks && index < expectedNow
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
                    .then(
                        if (behindPace) {
                            Modifier.border(
                                1.dp, Plum.copy(alpha = 0.45f), CircleShape,
                            )
                        } else Modifier,
                    )
                    .padding(2.dp),
            )
        }
    }
}
