package com.aira.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.material.icons.outlined.Sick
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.aira.companion.ui.components.moodStyle
import com.aira.companion.ui.components.moodStyles
import com.aira.companion.ui.theme.Amber
import com.aira.companion.ui.theme.AmberMist
import com.aira.companion.ui.theme.HeroAccent
import com.aira.companion.ui.theme.HeroInk
import com.aira.companion.ui.theme.HeroInkMuted
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
    return "${next.label} · Week ${next.week} · $distance"
}

/** Nudges never duplicated on Me: each one's control is already on this
 * screen (mood picker above, droplet row below). Chat shows the full set. */
private val ME_SUPPRESSED_NUDGES = setOf("mood_checkin", "water_pace")

/** Where the droplet row SHOULD be by this hour (7am-9pm pace) — the same
 * formula the server's water_pace nudge uses. */
fun expectedWaterByNow(target: Int): Int {
    val hour = java.time.LocalTime.now().hour
    if (hour < 7) return 0
    return Math.round(target * minOf(1f, (hour - 7) / 14f))
}

/** Refined mood glyphs (replacing emoji): outline Material symbols so the
 * check-in reads calm and grown-up rather than playful. */
private val moodIcon: Map<String, ImageVector> = mapOf(
    "great" to Icons.Outlined.SentimentVerySatisfied,
    "okay" to Icons.Outlined.SentimentSatisfied,
    "tired" to Icons.Outlined.Bedtime,
    "low" to Icons.Outlined.SentimentDissatisfied,
    "unwell" to Icons.Outlined.Sick,
)

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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
       androidx.compose.foundation.ExperimentalFoundationApi::class)
fun MeScreen(
    state: AiraUiState,
    onNudgeTapped: (String) -> Unit,
    onOpenPlayer: (com.aira.companion.model.VideoItem) -> Unit,
    onSetMood: (String) -> Unit,
    onSaveMoodNote: (String, String) -> Unit,
    onTick: (Reminder) -> Unit,
    onUntick: (Reminder) -> Unit,
    onOpenDetail: (DetailPage) -> Unit,
    onOpenTool: (com.aira.companion.model.AiraTool) -> Unit,
    onTalkToAira: () -> Unit,
    onAckWeekFlip: () -> Unit,
    onTipFeedback: (String, Boolean) -> Unit,
    onSaveName: (String) -> Unit,
    onDismissName: () -> Unit,
    onRefresh: () -> Unit,
    onSetStage: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val today = LocalDate.now().toString()
    val todayMood = state.moods.lastOrNull { it.day == today }?.mood
    var showStagePicker by remember { mutableStateOf(false) }
    if (showStagePicker) {
        StagePickerDialog(
            onDismiss = { showStagePicker = false },
            onSave = { stage, anchor -> onSetStage(stage, anchor); showStagePicker = false },
        )
    }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = state.meLoading,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // ── Honest staleness: a failed refresh over old data says so ───────
        if (state.meStale) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SageMist,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    "Offline — showing your last update. Pull to retry.",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
        }
        // ── Journey hero -> weekly timeline detail ─────────────────────────
        GradientHeroSurface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (state.careSummary == null) showStagePicker = true
                        else onOpenDetail(DetailPage.Journey)
                    },
                    onLongClick = {
                        // Share the week (review #36) as a drawn image card —
                        // the system sheet lets the user pick the destination.
                        val care = state.careSummary ?: return@combinedClickable
                        runCatching {
                            com.aira.companion.util.ShareCard.shareWeek(
                                context,
                                headline = care.week?.let { w ->
                                    state.todayFeed?.dayInWeek?.let { d ->
                                        "Week $w · Day $d"
                                    } ?: "Week $w"
                                } ?: "My journey",
                                emoji = state.journeyContent?.sizeEmoji,
                                subline = state.todayFeed?.daysToGo?.let {
                                    "$it days to go"
                                },
                            )
                        }
                    },
                ),
        ) {
            Column {
                val care = state.careSummary
                if (care != null) {
                    // Recomputed on recomposition (every refresh), never cached:
                    // a home resumed at 18:00 must not greet the morning.
                    val hour = java.time.LocalTime.now().hour
                    val greeting = when {
                        hour < 5 -> "Good night"    // the 3 AM feeds
                        hour < 12 -> "Good morning"
                        hour < 17 -> "Good afternoon"
                        hour < 22 -> "Good evening"
                        else -> "Good night"
                    }
                    Text(
                        text = if (care.displayName.isNotBlank()) "$greeting, ${care.displayName}" else greeting,
                        style = MaterialTheme.typography.titleMedium,
                        color = HeroInkMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    // The week as the page's one big, confident number — an
                    // editorial serif numeral on the dark panel, with a stacked
                    // WEEK / Day eyebrow. This is the hero's whole job.
                    val week = care.week
                    if (week != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = week.toString(),
                                style = TextStyle(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 64.sp,
                                    lineHeight = 62.sp,
                                    letterSpacing = (-2).sp,
                                ),
                                color = HeroInk,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.padding(bottom = 10.dp)) {
                                Text(
                                    text = if (care.stage == "postpartum") "PP WEEK" else "WEEK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HeroAccent,
                                )
                                state.todayFeed?.dayInWeek?.let { d ->
                                    Text(
                                        text = "Day $d",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = HeroInk,
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = stageLabel(care.stage),
                            style = MaterialTheme.typography.headlineLarge,
                            color = HeroInk,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = listOfNotNull(
                            state.journeyContent?.title,
                            state.todayFeed?.lengthCm?.let { "~$it cm" },
                            state.todayFeed?.daysToGo?.let { days ->
                                // Days feel abstract until the third trimester;
                                // weeks read as progress, days as a countdown.
                                if ((care.week ?: 0) < 28 && days > 70) {
                                    "${(days + 6) / 7} weeks to go"
                                } else "$days days to go"
                            },
                        ).joinToString(" · ").ifBlank { stageLabel(care.stage) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = HeroInkMuted,
                    )
                    // Signature: a slim progress bar filling across the 40 weeks
                    // toward the due date — the journey made tangible.
                    if (week != null && care.stage == "pregnant") {
                        Spacer(Modifier.height(16.dp))
                        val frac = (week.toFloat() / 40f).coerceIn(0.03f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(HeroInk.copy(alpha = 0.18f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(frac)
                                    .height(5.dp)
                                    .clip(CircleShape)
                                    .background(HeroAccent),
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    // The journey CTA doubles as the next-milestone preview — a
                    // soft pill so it reads as the tappable way into the path
                    // (the whole hero opens it; this is the visible affordance).
                    val next = journeyNext(state)
                    Box(
                        modifier = Modifier
                            .background(HeroInk.copy(alpha = 0.12f), CircleShape)
                            .padding(start = 15.dp, end = 9.dp, top = 9.dp, bottom = 9.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = next ?: androidx.compose.ui.res.stringResource(com.aira.companion.R.string.me_weekly_guide),
                                style = MaterialTheme.typography.labelLarge,
                                color = HeroInk,
                            )
                            Spacer(Modifier.width(5.dp))
                            Icon(
                                Icons.Filled.ChevronRight, null,
                                tint = HeroAccent, modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Your journey, your pace",
                        style = MaterialTheme.typography.headlineMedium,
                        color = HeroInk,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (state.meLoading) "Syncing your care context…"
                        else "Set where you are and Aira shapes your week, videos, and guidance.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HeroInkMuted,
                    )
                    if (!state.meLoading) {
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Set your journey",
                                style = MaterialTheme.typography.labelLarge,
                                color = HeroAccent,
                            )
                            Icon(
                                Icons.Filled.ChevronRight, null,
                                tint = HeroAccent, modifier = Modifier.size(18.dp),
                            )
                        }
                    }
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

        // ── First run: ask the name once (review #1); greeting personalises
        // the moment it's saved. Dismissable, never nags again. ────────────
        val care = state.careSummary
        if (care != null && care.displayName.isBlank() && !state.namePromptDismissed) {
            AiraCard(containerColor = LilacMist) {
                Text(
                    "What should Aira call you?",
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                )
                Spacer(Modifier.height(8.dp))
                var name by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Your name (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Row {
                    TextButton(onClick = onDismissName) {
                        Text("Not now", color = InkMuted)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            onSaveName(name.trim())
                            onDismissName()
                        },
                        enabled = name.isNotBlank(),
                    ) { Text("Save", color = Plum) }
                }
            }
        }

        // ── Sunday recap (review #35): the weekly digest, feed-driven ──────
        state.todayFeed?.recapMoods?.let { moodCount ->
            AiraCard(containerColor = LilacMist) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel(androidx.compose.ui.res.stringResource(com.aira.companion.R.string.me_week_header))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = listOfNotNull(
                                "$moodCount mood check-in${if (moodCount == 1) "" else "s"}",
                                state.todayFeed?.recapWaterAvg?.let {
                                    "~$it glasses of water a day"
                                },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink,
                        )
                    }
                    TextButton(onClick = { onOpenDetail(DetailPage.Moods) }) {
                        Text("Reports", color = Plum)
                    }
                }
            }
        }

        // ── Today: mood + nudges + care in one interactive card ────────────
        AiraCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(
                    text = if (state.careSummary?.stage == "postpartum") {
                        if (java.time.LocalTime.now().hour < 15) {
                            androidx.compose.ui.res.stringResource(com.aira.companion.R.string.me_mood_morning)
                        } else androidx.compose.ui.res.stringResource(com.aira.companion.R.string.me_mood_evening)
                    } else androidx.compose.ui.res.stringResource(com.aira.companion.R.string.me_mood_header),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onOpenDetail(DetailPage.Moods) }) {
                    Text(androidx.compose.ui.res.stringResource(com.aira.companion.R.string.me_history), style = MaterialTheme.typography.labelMedium, color = Plum)
                }
            }
            Spacer(Modifier.height(8.dp))
            // Mood picker: a refined icon + a calm colour per feeling (#2/#14).
            // The selected chip fills with its own colour; each carries a spoken
            // label so a screen reader announces the feeling, not just its shape
            // (#18), and a tap gives a small haptic (#15).
            val moodHaptics = androidx.compose.ui.platform.LocalHapticFeedback.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                moodStyles.forEach { m ->
                    val selected = m.key == todayMood
                    Surface(
                        onClick = {
                            moodHaptics.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                            )
                            onSetMood(m.key)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Feeling ${m.label}" },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) m.color else Paper,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, if (selected) m.color else OutlineSoft,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = m.icon,
                                contentDescription = null,
                                tint = if (selected) Paper else m.color,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                m.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp, letterSpacing = 0.sp,
                                ),
                                color = if (selected) Paper else InkMuted,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

            // 7-day mood strip (#11): the check-in visibly builds a picture — a
            // coloured dot per day (grey = not logged); tap opens the history.
            Spacer(Modifier.height(14.dp))
            val stripToday = LocalDate.now()
            val moodByDay = state.moods.associateBy { it.day }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDetail(DetailPage.Moods) },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for (i in 6 downTo 0) {
                    val day = stripToday.minusDays(i.toLong())
                    val mood = moodByDay[day.toString()]?.mood
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (mood != null) moodStyle(mood).color
                                    else OutlineSoft.copy(alpha = 0.7f),
                                ),
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            day.dayOfWeek.getDisplayName(
                                java.time.format.TextStyle.NARROW,
                                java.util.Locale.getDefault(),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (i == 0) Plum else InkMuted,
                        )
                    }
                }
            }

            // A note is where the real signal lives: optional, private.
            if (todayMood != null) {
                var noteOpen by remember { mutableStateOf(false) }
                var noteText by remember { mutableStateOf("") }
                Spacer(Modifier.height(6.dp))
                if (!noteOpen) {
                    TextButton(onClick = { noteOpen = true }) {
                        Text(androidx.compose.ui.res.stringResource(com.aira.companion.R.string.me_add_note), style = MaterialTheme.typography.labelMedium, color = Plum)
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
                // Mood-specific follow-up (#3) + time-aware (#4): meet the
                // feeling with the right words and the right next step, instead
                // of one generic line for every hard day.
                val hourNow = java.time.LocalTime.now().hour
                data class FollowUp(val line: String, val action: String, val symptom: Boolean = false)
                val followUp = when (todayMood) {
                    "anxious" -> FollowUp("A racing mind is common right now — a slow minute of breathing can steady it.", "Talk to Aira")
                    "low" -> FollowUp(
                        if (hourNow < 5) "The night feeds are hard. Be gentle with yourself." else "Be gentle with yourself today.",
                        "Talk to Aira",
                    )
                    "unwell" -> FollowUp("Sorry you're not feeling well. If anything worries you, tell your care team.", "Log a symptom", symptom = true)
                    "tired" -> FollowUp("Rest counts as care — even ten minutes helps.", "Talk to Aira")
                    else -> null
                }
                followUp?.let { fu ->
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fu.line, style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            if (fu.symptom) onOpenTool(com.aira.companion.model.AiraTool.Symptom) else onTalkToAira()
                        }) { Text(fu.action, color = Plum) }
                    }
                }
            }

            // Trend escalation (#5): several tender days (anxious/low/unwell) in
            // a week surface a gentle, non-alarming check-in with a path to the
            // care team. Read from the mood history — never a diagnosis.
            val weekAgo = LocalDate.now().minusDays(6).toString()
            val tenderCount = state.moods.count {
                it.day >= weekAgo && it.mood in com.aira.companion.model.tenderMoods
            }
            if (tenderCount >= 4) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = AmberMist,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("A harder stretch", style = MaterialTheme.typography.titleSmall, color = Ink)
                            Text(
                                "You've logged several harder days this week. That matters — your care team is there for the low weeks too.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        TextButton(onClick = onTalkToAira) { Text("Reach out", color = Amber) }
                    }
                }
            }

            // Care lives in the same card: mood + check-offs are the page's
            // two interactive dailies — one "Today" surface, not two cards.
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = OutlineSoft)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(androidx.compose.ui.res.stringResource(com.aira.companion.R.string.me_care_header), modifier = Modifier.weight(1f))
                TextButton(onClick = { onOpenDetail(DetailPage.Care) }) {
                    Text(androidx.compose.ui.res.stringResource(com.aira.companion.R.string.me_details), style = MaterialTheme.typography.labelMedium, color = Plum)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.LocalFireDepartment, null,
                                tint = Plum, modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "$streak-day streak",
                                style = MaterialTheme.typography.labelMedium,
                                color = Plum,
                            )
                        }
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
                ?.filter { it.kind !in ME_SUPPRESSED_NUDGES }.orEmpty()
            if (focusItems.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    focusItems.forEach { focus ->
                        FocusNudge(focus) {
                            onNudgeTapped(focus.kind)
                            when (focus.kind) {
                                "milestone_week" -> onOpenDetail(DetailPage.Journey)
                                // The Visit copilot holds the real appointment
                                // + question checklist — the natural landing.
                                "appointment_soon" ->
                                    onOpenTool(com.aira.companion.model.AiraTool.Appointment)
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
            AiraCard {
                SectionLabel(androidx.compose.ui.res.stringResource(com.aira.companion.R.string.me_daily_header))
                state.todayFeed?.tipText?.let { tip ->
                    Spacer(Modifier.height(6.dp))
                    Text(tip, style = MaterialTheme.typography.bodyMedium, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "General wellness guidance — not medical advice.",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                    )
                    // One thumb per tip (review #29) — the ack is local so
                    // the row doesn't beg twice in one sitting.
                    val tipId = state.todayFeed?.tipId
                    if (tipId != null) {
                        var acked by remember(tipId) { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!acked) {
                                Text(
                                    "Helpful?",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = InkMuted,
                                )
                                IconButton(onClick = {
                                    onTipFeedback(tipId, true); acked = true
                                }) {
                                    Icon(
                                        Icons.Outlined.ThumbUp, "Helpful",
                                        tint = Plum, modifier = Modifier.size(16.dp),
                                    )
                                }
                                IconButton(onClick = {
                                    onTipFeedback(tipId, false); acked = true
                                }) {
                                    Icon(
                                        Icons.Outlined.ThumbDown, "Not helpful",
                                        tint = InkMuted, modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                Text(
                                    "Thanks — noted.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = InkMuted,
                                )
                            }
                        }
                    }
                }
                state.suggestedVideo?.let { video ->
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = OutlineSoft)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { playVideo(context, video, onOpenPlayer) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VideoThumb(
                            url = videoThumbUrl(video) ?: "",
                            contentDescription = video.title,
                            durationMinutes = video.durationMinutes,
                            watched = video.watched,
                            modifier = Modifier
                                .width(108.dp)
                                .height(61.dp),
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

        // ── Data transparency on the home itself (review #54) ──────────────
        TextButton(
            onClick = { onOpenTool(com.aira.companion.model.AiraTool.Memory) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                androidx.compose.ui.res.stringResource(com.aira.companion.R.string.me_memory_link),
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted,
            )
        }

        Spacer(Modifier.height(96.dp)) // clearance for the floating nav pill
    }
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

/** Empty-hero stage picker: set the care stage (and an approximate anchor) in
 * two taps, no re-onboarding — so no one gets stranded without a journey. */
@Composable
private fun StagePickerDialog(
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    var stage by remember { mutableStateOf<String?>(null) }
    var anchor by remember { mutableStateOf<String?>(null) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Paper) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Where are you in your journey?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Aira uses this to shape your week, videos, and guidance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
                Spacer(Modifier.height(14.dp))
                listOf(
                    "pregnant" to "Pregnant",
                    "postpartum" to "Postpartum",
                    "trying_to_conceive" to "Trying to conceive",
                ).forEach { (key, label) ->
                    val selected = stage == key
                    Surface(
                        onClick = { stage = key; anchor = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) LilacMist else Paper,
                        border = androidx.compose.foundation.BorderStroke(
                            if (selected) 1.5.dp else 1.dp,
                            if (selected) Plum else OutlineSoft,
                        ),
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                val anchors = when (stage) {
                    "pregnant" -> listOf("~8 weeks", "~16 weeks", "~24 weeks", "~32 weeks")
                    "postpartum" -> listOf("Under 2 weeks", "About a month", "2–3 months", "4+ months")
                    else -> emptyList()
                }
                if (anchors.isNotEmpty()) {
                    Text(
                        text = if (stage == "pregnant") "About how far along?" else "How old is your baby?",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        anchors.forEach { option ->
                            val sel = anchor == option
                            Surface(
                                onClick = { anchor = option },
                                shape = CircleShape,
                                color = if (sel) Plum else Paper,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, if (sel) Plum else OutlineSoft,
                                ),
                            ) {
                                Text(
                                    option,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (sel) Paper else Ink,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = InkMuted) }
                    Spacer(Modifier.weight(1f))
                    val ready = stage != null && (anchors.isEmpty() || anchor != null)
                    TextButton(
                        onClick = { stage?.let { onSave(it, anchor) } },
                        enabled = ready,
                    ) { Text("Save", color = if (ready) Plum else InkMuted) }
                }
            }
        }
    }
}
