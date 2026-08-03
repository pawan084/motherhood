package com.aira.companion.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.aira.companion.model.VideoItem
import com.aira.companion.net.AiraApi
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.RemoteImage
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.components.youtubeThumbnailUrl
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted

/** Play a catalog entry. Own hosted videos stream from the API (ACTION_VIEW
 * with an explicit video/mp4 type — the system player handles HTTP range
 * streaming); legacy entries fall back to YouTube. Shared by MeScreen. */
fun playVideo(context: Context, video: com.aira.companion.model.VideoItem) {
    runCatching {
        val stream = video.streamPath
        if (stream != null) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType((AiraApi.baseUrl + stream).toUri(), "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        } else if (video.youtubeId != null) {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    ("https://www.youtube.com/watch?v=" + video.youtubeId).toUri(),
                ),
            )
        }
    }
}

/** Thumbnail URL for a catalog entry: own hosted thumb, else YouTube's. */
fun videoThumbUrl(video: com.aira.companion.model.VideoItem): String? = when {
    video.thumbPath != null -> AiraApi.baseUrl + video.thumbPath
    video.youtubeId != null -> youtubeThumbnailUrl(video.youtubeId)
    else -> null
}

private fun stageLabel(stage: String): String = when (stage) {
    "pregnant" -> "Pregnancy"
    "postpartum" -> "Postpartum"
    "trying_to_conceive" -> "Conceiving"
    else -> stage
}

/** The Videos tab: the real backend catalog with thumbnails, the caller's
 * stage ordered first server-side. Lazy-loaded on first visit. */
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
                            .width(132.dp)
                            .height(74.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            video.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = listOfNotNull(
                                video.topic.takeIf { it.isNotBlank() },
                                stageLabel(video.stage),
                                video.weekBand?.let { "weeks $it" },
                                video.durationMinutes?.let { "$it min" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                }
            }
        }
    }
}
