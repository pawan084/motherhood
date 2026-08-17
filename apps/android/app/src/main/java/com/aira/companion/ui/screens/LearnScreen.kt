package com.aira.companion.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aira.companion.model.VideoCategory
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import com.aira.companion.ui.components.EmptyState
import com.aira.companion.ui.components.SecondaryButton
import com.aira.companion.model.VideoTopic
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.components.SkeletonRows
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.OutlineSoft
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.Urgent
import com.aira.companion.ui.theme.UrgentMist
import kotlin.math.roundToInt

/**
 * Learn — Aira's educational video library, served by GET /v1/videos. The server
 * resolves the caller's journey + gestational week, so `weekVideo` is the pregnant
 * caller's current week-by-week topic, shown as a suggest-only card. No media is
 * produced yet, so playback is not offered here; you can browse, filter and save.
 *
 * Safety: an `urgent` topic is about knowing when to get help — it routes to the
 * care team rather than offering AI reassurance.
 */
@Composable
fun LearnScreen(
    modifier: Modifier = Modifier,
    videos: List<VideoTopic> = emptyList(),
    weekVideo: VideoTopic? = null,
    /** Set when this is a pregnant caller with a known week and the catalogue
     *  has nothing for it. The card used to vanish silently — see below. */
    weekWithoutVideo: Int? = null,
    categories: List<VideoCategory> = emptyList(),
    savedIds: Set<String> = emptySet(),
    loading: Boolean = false,
    onLoad: () -> Unit = {},
    onToggleSave: (String) -> Unit = {},
    onUrgentHelp: () -> Unit = {},
    /** Opens a produced video. Never called while the catalogue has no media. */
    onWatch: (VideoTopic) -> Unit = {},
    /** The language chosen in onboarding, for the availability notice. */
    language: String = "English",
    /** Takes an unanswered search into Chat, where a person can ask it. */
    onAskAira: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    // Load on first entry, matching how the other tabs fetch when opened.
    LaunchedEffect(Unit) { onLoad() }

    var category by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    val trimmedQuery = query.trim()

    val byCategory = videos.filter { category == "all" || it.category == category }
    val shown = if (trimmedQuery.isBlank()) {
        byCategory
    } else {
        byCategory.filter { video ->
            // Title, summary and category all searched: someone typing
            // "postpartum" is naming a category, and someone typing "sleep" is
            // naming a subject. Matching only titles would miss both.
            listOf(video.title, video.description, video.categoryLabel)
                .any { it.contains(trimmedQuery, ignoreCase = true) }
        }
    }

    // Whether anything in the current view exists in the chosen language.
    // Distinct from "no results": the topics are there, they are just not in
    // the language onboarding promised, and saying "nothing found" would hide
    // that difference behind an unrelated answer.
    val languageGap = trimmedQuery.isBlank() &&
        byCategory.isNotEmpty() &&
        !language.equals("English", ignoreCase = true) &&
        byCategory.none { it.languages.any { l -> l.equals(language, ignoreCase = true) } }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Ivory)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 28.dp),
    ) {
        Text("Short guided videos", style = MaterialTheme.typography.headlineLarge, color = Ink)
        Text(
            "Clinician-reviewed topics for where you are — in production now. " +
                "Save any for when they're ready.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )

        // Weeks 41 and 42 have no week-by-week topic, and neither do 1 to 3.
        //
        // The card simply disappeared, which is worst exactly where it matters:
        // someone overdue is anxious and checking daily, and an app that showed
        // them a weekly card every week until their due date and then quietly
        // stopped reads as having nothing left to say to them. Say what is
        // happening instead.
        if (weekVideo == null && weekWithoutVideo != null) {
            Spacer(Modifier.height(20.dp))
            AiraCard(containerColor = LilacMist) {
                SectionLabel("Your week with Aira")
                Text(
                    "No week $weekWithoutVideo video yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                Text(
                    "The topics below still apply. Anything that worries you is " +
                        "worth taking to your care team rather than waiting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
        }

        if (weekVideo != null) {
            Spacer(Modifier.height(20.dp))
            AiraCard(containerColor = LilacMist) {
                SectionLabel("Your week with Aira")
                Text(weekVideo.title, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    "${weekVideo.description} · ${durationLabel(weekVideo)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
                Spacer(Modifier.height(12.dp))
                // The same readiness state as every other card. This one is the
                // most prominent thing on the screen, and it was the only one
                // that said nothing about whether it could be watched.
                if (weekVideo.playable && !weekVideo.mediaUrl.isNullOrBlank()) {
                    PrimaryButton(
                        label = if (weekVideo.mediaIsPlaceholder) "Open placeholder" else "Watch",
                        onClick = { onWatch(weekVideo) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = null,
                    )
                    if (weekVideo.mediaIsPlaceholder) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Not the real video — a stand-in while this is filmed.",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text(
                        text = "In production — we'll tell you when it's ready.",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                PrimaryButton(
                    label = if (savedIds.contains(weekVideo.id)) "Saved for later" else "Save for later",
                    onClick = { onToggleSave(weekVideo.id) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = null,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search videos…", color = InkMuted) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = InkMuted,
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear search",
                            tint = InkMuted,
                        )
                    }
                }
            } else {
                null
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )

        if (categories.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryChip("All", category == "all") { category = "all" }
                categories.forEach { c ->
                    CategoryChip(c.label, category == c.key) { category = c.key }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (loading && videos.isEmpty()) {
            // Card-shaped placeholders rather than the word "Loading…", so the
            // page does not jump when the real cards arrive.
            SkeletonRows(count = 3, label = "Videos")
        }

        // The language gap is checked before the empty state, because the two
        // answer different questions and the wrong one is actively misleading:
        // "nothing found" about a catalogue that is full, only in English.
        if (languageGap) {
            EmptyState(
                icon = Icons.Outlined.Language,
                title = "Not available in $language yet",
                body = "These topics aren't dubbed into $language yet — the English " +
                    "videos carry $language subtitles in the meantime. We're adding " +
                    "more each month.",
            )
            SecondaryButton(
                label = "Change language in Settings",
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            shown.forEachIndexed { index, video ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                VideoCard(
                    video = video,
                    saved = savedIds.contains(video.id),
                    onToggleSave = onToggleSave,
                    onUrgentHelp = onUrgentHelp,
                    onWatch = onWatch,
                )
            }
        }

        if (!loading && !languageGap && shown.isEmpty()) {
            // A dead-end list with no way out reads as a bug. The query is
            // quoted back so it is obvious what was searched — a filter left on
            // from earlier is the usual reason a search "fails" — and the way
            // out is the one place that can answer anything.
            if (trimmedQuery.isNotBlank()) {
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = "No videos match yet",
                    body = "Nothing for “$trimmedQuery” so far. Asking Aira in Chat " +
                        "works for anything the catalogue doesn't cover yet — and it " +
                        "tells us what to make next.",
                )
                PrimaryButton(
                    label = "Ask Aira instead",
                    onClick = { onAskAira(trimmedQuery) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (category != "all") {
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(
                        label = "Search all categories",
                        onClick = { category = "all" },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = "Nothing in this category yet",
                    body = "This shelf is still being filled. The other categories " +
                        "have topics ready now.",
                )
                SecondaryButton(
                    label = "Show all videos",
                    onClick = { category = "all" },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "Every video is clinician-reviewed before it's published. Aira is wellness " +
                "support, not diagnosis or emergency care.",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
        )
    }
}

/** "2–4 min" from a topic's recommended duration range. */
private fun durationLabel(v: VideoTopic): String {
    val lo = maxOf(1, (v.minSeconds / 60f).roundToInt())
    val hi = maxOf(lo, (v.maxSeconds / 60f).roundToInt())
    return if (lo == hi) "$lo min" else "$lo–$hi min"
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Plum else Paper,
        contentColor = if (selected) Paper else InkMuted,
        shape = RoundedCornerShape(99.dp),
        border = BorderStroke(1.dp, if (selected) Plum else OutlineSoft),
        onClick = onClick,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun VideoCard(
    video: VideoTopic,
    saved: Boolean,
    onToggleSave: (String) -> Unit,
    onWatch: (VideoTopic) -> Unit,
    onUrgentHelp: () -> Unit,
) {
    AiraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(video.categoryLabel)
            Spacer(Modifier.weight(1f))
            Text(
                text = durationLabel(video),
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(video.title, style = MaterialTheme.typography.titleMedium, color = Ink)
        Text(video.description, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        Spacer(Modifier.height(12.dp))
        if (video.safetyLevel == "urgent") {
            Surface(
                color = UrgentMist,
                contentColor = Urgent,
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "This one is about knowing when to get help — not something to " +
                            "watch and wait on.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        label = "Get urgent help",
                        onClick = onUrgentHelp,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = null,
                    )
                }
            }
        } else {
            // Watch appears only when there is something to watch.
            //
            // `playable` is published AND clinically approved AND a media URL,
            // and no topic in the catalogue has the third, so this is Save for
            // everyone today. The screen said "in production" once, at the top,
            // and then gave every card the same button regardless — so a topic
            // that became ready looked identical to one that had not.
            if (video.playable && !video.mediaUrl.isNullOrBlank()) {
                PrimaryButton(
                    // The label distinguishes the two, because the button does
                    // not. Opening a stand-in under a clinical title while the
                    // control says plain "Watch" would be the most convincing
                    // false claim in the app.
                    label = if (video.mediaIsPlaceholder) "Open placeholder" else "Watch",
                    onClick = { onWatch(video) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = null,
                )
                if (video.mediaIsPlaceholder) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Not the real video — a stand-in while this is filmed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                    )
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Text(
                    text = "In production — we'll tell you when it's ready.",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                )
                Spacer(Modifier.height(10.dp))
            }
            PrimaryButton(
                label = if (saved) "Saved" else "Save for later",
                onClick = { onToggleSave(video.id) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = null,
            )
        }
    }
}
