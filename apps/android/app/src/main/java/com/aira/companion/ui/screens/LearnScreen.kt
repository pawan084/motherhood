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
import com.aira.companion.model.VideoTopic
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SectionLabel
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
    categories: List<VideoCategory> = emptyList(),
    savedIds: Set<String> = emptySet(),
    loading: Boolean = false,
    onLoad: () -> Unit = {},
    onToggleSave: (String) -> Unit = {},
    onUrgentHelp: () -> Unit = {},
) {
    // Load on first entry, matching how the other tabs fetch when opened.
    LaunchedEffect(Unit) { onLoad() }

    var category by remember { mutableStateOf("all") }
    val shown = videos.filter { category == "all" || it.category == category }

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
                PrimaryButton(
                    label = if (savedIds.contains(weekVideo.id)) "Saved for later" else "Save for later",
                    onClick = { onToggleSave(weekVideo.id) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = null,
                )
            }
        }

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
            Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        }

        shown.forEachIndexed { index, video ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            VideoCard(
                video = video,
                saved = savedIds.contains(video.id),
                onToggleSave = onToggleSave,
                onUrgentHelp = onUrgentHelp,
            )
        }
        if (!loading && shown.isEmpty()) {
            Text("No topics yet.", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
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
            PrimaryButton(
                label = if (saved) "Saved" else "Save for later",
                onClick = { onToggleSave(video.id) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = null,
            )
        }
    }
}
