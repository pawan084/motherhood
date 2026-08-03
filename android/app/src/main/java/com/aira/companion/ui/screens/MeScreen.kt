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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aira.companion.model.AiraUiState
import com.aira.companion.model.DetailPage
import com.aira.companion.model.Reminder
import com.aira.companion.model.TodayFocus
import com.aira.companion.model.moodEmoji
import com.aira.companion.model.moodOptions
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.GradientHeroSurface
import com.aira.companion.ui.components.RemoteImage
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

/** "🩺 First scan · Week 12 · 4 weeks away" — the hero's CTA line; null
 * without journey data (the hero falls back to the generic guide link). */
private fun journeyNext(state: AiraUiState): String? {
    val journey = state.journeyContent ?: return null
    val week = journey.currentWeek ?: return null
    val next = journey.milestones.firstOrNull { it.week >= week } ?: return null
    val daysTo = (next.week - week) * 7 - ((state.todayFeed?.dayInWeek ?: 1) - 1)
    val distance = when {
        next.week == week -> "this week"
        daysTo in 1..10 -> "in $daysTo day${if (daysTo == 1) "" else "s"}"
        next.week - week == 1 -> "next week"
        else -> "${next.week - week} weeks away"
    }
    return "${next.emoji} ${next.label} · Week ${next.week} · $distance"
}

/** Where the droplet row SHOULD be by this hour (7am-9pm pace) — the same
 * formula the server's water_pace nudge uses. */
fun expectedWaterByNow(target: Int): Int {
    val hour = java.time.LocalTime.now().hour
    if (hour < 7) return 0
    return Math.round(target * minOf(1f, (hour - 7) / 14f))
}

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
    onSaveMoodNote: (String, String) -> Unit,
    onTick: (Reminder) -> Unit,
    onUntick: (Reminder) -> Unit,
    onOpenDetail: (DetailPage) -> Unit,
    onTalkToAira: () -> Unit,
    onAckWeekFlip: () -> Unit,
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
                    // Recomputed on recomposition (every refresh), never cached:
                    // a home resumed at 18:00 must not greet the morning.
                    val hour = java.time.LocalTime.now().hour
                    val greeting = when {
                        hour < 12 -> "Good morning"
                        hour < 17 -> "Good afternoon"
                        hour < 22 -> "Good evening"
                        else -> "Good night"
                    }
                    Text(
                        text = if (care.displayName.isNotBlank()) "$greeting, ${care.displayName}" else greeting,
                        style = MaterialTheme.typography.titleMedium,
                        color = InkMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = care.week?.let { week ->
                                state.todayFeed?.dayInWeek?.let { d -> "Week $week · Day $d" }
                                    ?: "Week $week"
                            } ?: stageLabel(care.stage),
                            style = MaterialTheme.typography.headlineLarge,
                            color = Ink,
                            modifier = Modifier.weight(1f),
                        )
                        state.journeyContent?.sizeEmoji?.let {
                            Text(it, style = MaterialTheme.typography.headlineLarge)
                        }
                    }
                    Text(
                        text = listOfNotNull(
                            state.journeyContent?.title,
                            state.todayFeed?.daysToGo?.let { days ->
                                // Days feel abstract until the third trimester;
                                // weeks read as progress, days as a countdown.
                                if ((care.week ?: 0) < 28 && days > 70) {
                                    "${(days + 6) / 7} weeks to go"
                                } else "$days days to go"
                            },
                        ).joinToString(" · ").ifBlank { stageLabel(care.stage) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    // The journey CTA doubles as the next-milestone preview —
                    // one journey surface on Me, not two.
                    val next = journeyNext(state)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = next ?: "See your weekly guide",
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

        // ── Week-flip celebration: shown once per new week (review #8) ─────
        state.weekJustFlipped?.let { newWeek ->
            AiraCard(containerColor = LilacMist) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Week $newWeek begins 🎉",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink,
                        )
                        Text(
                            "A new week on your path — see what's changing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                    TextButton(onClick = {
                        onAckWeekFlip()
                        onOpenDetail(DetailPage.Journey)
                    }) {
                        Text("See what's new", color = Plum)
                    }
                    IconButton(onClick = onAckWeekFlip) {
                        Icon(Icons.Filled.Close, "Dismiss", tint = InkMuted)
                    }
                }
            }
        }

        // ── Today: mood + nudges + care in one interactive card ────────────
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
            // A note is where the real signal lives (review #20): optional,
            // shown once today's mood exists; posts via the same upsert.
            if (todayMood != null) {
                var noteOpen by remember { mutableStateOf(false) }
                var noteText by remember { mutableStateOf("") }
                if (!noteOpen) {
                    TextButton(onClick = { noteOpen = true }) {
                        Text("Add a note", style = MaterialTheme.typography.labelMedium, color = Plum)
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Anything behind it? (private)") },
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    onSaveMoodNote(todayMood, noteText.trim())
                                    noteOpen = false
                                },
                                enabled = noteText.isNotBlank(),
                            ) { Text("Save") }
                        },
                    )
                }
                // The page answers a hard day (review #21) instead of
                // carrying on as if nothing was said.
                if (todayMood == "low" || todayMood == "unwell") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Be gentle with yourself today.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onTalkToAira) {
                            Text("Talk to Aira", color = Plum)
                        }
                    }
                }
            }

            // Care lives in the same card: mood + check-offs are the page's
            // two interactive dailies — one "Today" surface, not two cards.
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = OutlineSoft)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Today's care", modifier = Modifier.weight(1f))
                TextButton(onClick = { onOpenDetail(DetailPage.Care) }) {
                    Text("Details", style = MaterialTheme.typography.labelMedium, color = Plum)
                }
            }
            val done = state.reminders.count { it.doneToday }
            if (state.reminders.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (done == state.reminders.size) {
                            "All done for today 🎉"
                        } else "$done of ${state.reminders.size} done",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (done == state.reminders.size) Sage else InkMuted,
                        modifier = Modifier.weight(1f),
                    )
                    val streak = state.todayFeed?.streak ?: 0
                    if (streak >= 2) {
                        Text(
                            "🔥 $streak-day streak",
                            style = MaterialTheme.typography.labelMedium,
                            color = Plum,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            } else {
                Text(
                    text = if (state.meLoading) "Syncing…" else "No reminders yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
            }
            // Server-ranked nudges live INSIDE the care section (formerly a
            // separate "Right now" card): most of them point here anyway.
            // Nudges that duplicate a control on THIS screen are dropped —
            // mood_checkin (the picker is directly above) and water_pace
            // (the droplet row is directly below). Chat keeps both: nothing
            // there shows mood or water state.
            val focusItems = state.todayFeed?.focus
                ?.filter { it.kind != "mood_checkin" && it.kind != "water_pace" }
                .orEmpty()
            if (focusItems.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    focusItems.forEach { focus ->
                        FocusNudge(focus) {
                            when (focus.kind) {
                                "milestone_week" -> onOpenDetail(DetailPage.Journey)
                                else -> onOpenDetail(DetailPage.Care)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.reminders.forEach { reminder ->
                    ReminderRow(reminder, onTick, onUntick)
                }
            }
        }

        // ── Daily for you: the tip + the day's video in ONE card — both
        // rotate at midnight, so they share the "changes every day" slot.
        // Deliberately BELOW mood/care: act before browse — the surfaces a
        // user touches several times a day outrank read-only content. ─────
        if (state.todayFeed?.tipText != null || state.suggestedVideo != null) {
            AiraCard(containerColor = SageMist) {
                SectionLabel("Daily for you")
                state.todayFeed?.tipText?.let { tip ->
                    Spacer(Modifier.height(6.dp))
                    Text(tip, style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
                state.suggestedVideo?.let { video ->
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = OutlineSoft)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { playVideo(context, video) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RemoteImage(
                            url = videoThumbUrl(video) ?: "",
                            contentDescription = video.title,
                            modifier = Modifier
                                .width(108.dp)
                                .height(61.dp)
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
                                    video.durationMinutes?.let { "$it min" },
                                    "Aira video",
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight, null,
                            tint = Plum, modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FocusNudge(focus: TodayFocus, onTap: () -> Unit) {
    Surface(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        color = LilacMist,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(focus.title, style = MaterialTheme.typography.titleSmall, color = Ink)
                if (focus.body.isNotBlank()) {
                    Text(
                        focus.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight, null,
                tint = Plum, modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    onTick: (Reminder) -> Unit,
    onUntick: (Reminder) -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
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
                    text = reminder.detail ?: if (reminder.targetPerDay > 1) {
                        "${reminder.ticksToday} of ${reminder.targetPerDay} today"
                    } else if (reminder.doneToday) "Done today" else "Not yet today",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            // Done rows stay tappable: tapping again UNDOES the last tick
            // (review #10 — an accidental tap was permanent for the day).
            Surface(
                onClick = {
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                    )
                    if (reminder.doneToday) onUntick(reminder) else onTick(reminder)
                },
                shape = RoundedCornerShape(12.dp),
                color = if (reminder.doneToday) SageMist else Paper,
                contentColor = if (reminder.doneToday) Sage else Plum,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, if (reminder.doneToday) SageMist else OutlineSoft,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = if (reminder.doneToday) {
                        "${reminder.title} done — tap to undo"
                    } else "Tick ${reminder.title}",
                    modifier = Modifier.padding(8.dp).size(18.dp),
                )
            }
        }
        if (reminder.kind == "water" && reminder.targetPerDay > 1) {
            Spacer(Modifier.height(8.dp))
            WaterDroplets(
                reminder.ticksToday, reminder.targetPerDay,
                expectedNow = expectedWaterByNow(reminder.targetPerDay),
            )
        }
    }
}
