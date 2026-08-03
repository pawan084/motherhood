package com.aira.companion.ui.screens

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aira.companion.model.VideoItem
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.components.ToolListRow
import com.aira.companion.ui.theme.InkMuted

/** Open a catalog entry in the YouTube app (or browser fallback — ACTION_VIEW
 * on a watch URL resolves either way). Shared by MeScreen's suggested card. */
fun openYouTube(context: Context, youtubeId: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=$youtubeId".toUri()),
        )
    }
}

private fun stageLabel(stage: String): String = when (stage) {
    "pregnant" -> "Pregnancy"
    "postpartum" -> "Postpartum"
    "trying_to_conceive" -> "Conceiving"
    else -> stage
}

/** The Videos tab: the backend catalog, the caller's stage ordered first
 * server-side. Lazy-loaded on first visit (ensureVideos()). */
@Composable
fun VideosScreen(
    videos: List<VideoItem>,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionLabel("Guides for your stage")
            Spacer(Modifier.height(4.dp))
        }
        if (videos.isEmpty()) {
            item {
                Text(
                    text = if (loading) "Finding videos for you…" else "No videos yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
            }
        }
        items(videos, key = { it.id }) { video ->
            AiraCard {
                ToolListRow(
                    icon = Icons.Outlined.PlayCircle,
                    title = video.title,
                    subtitle = listOfNotNull(
                        video.topic.takeIf { it.isNotBlank() },
                        stageLabel(video.stage),
                        video.weekBand?.let { "weeks $it" },
                        video.durationMinutes?.let { "$it min" },
                    ).joinToString(" · "),
                    onClick = { openYouTube(context, video.youtubeId) },
                )
            }
        }
    }
}
