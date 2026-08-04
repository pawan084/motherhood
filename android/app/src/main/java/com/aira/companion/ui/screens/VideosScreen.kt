package com.aira.companion.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
import com.aira.companion.ui.theme.Amber
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Lilac
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.Plum

/** Play a catalog entry: own hosted videos open the IN-APP player overlay
 * (review #31); legacy YouTube entries stay external. Shared by MeScreen. */
fun playVideo(
    context: Context,
    video: com.aira.companion.model.VideoItem,
    onOpenPlayer: (com.aira.companion.model.VideoItem) -> Unit,
) {
    if (video.streamPath != null) {
        onOpenPlayer(video)
        return
    }
    runCatching {
        if (video.youtubeId != null) {
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

private val LikeRose = Color(0xFFE57387)

private fun stageLabel(stage: String): String = when (stage) {
    "pregnant" -> "Pregnancy"
    "postpartum" -> "Postpartum"
    "trying_to_conceive" -> "Conceiving"
    else -> stage
}

/** A thumbnail that reads as VIDEO: a play button, a duration pill, a watched
 * badge, and a branded fallback while/if the image is missing. Shared by the
 * Videos tab and the Me "today's video" card. */
@Composable
fun VideoThumb(
    url: String,
    contentDescription: String?,
    durationMinutes: Int?,
    watched: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Lilac),
    ) {
        RemoteImage(
            url = url,
            contentDescription = contentDescription,
            modifier = Modifier.matchParentSize(),
        )
        // Scrim for overlay legibility; deeper when watched (dims the tile).
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = if (watched) 0.42f else 0.16f)),
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        durationMinutes?.let { min ->
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
            ) {
                Text(
                    text = "$min:00",
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
        if (watched) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Watched", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
    }
}

/** Star rating as filled stars (#8) — honest: shows "Be the first to rate"
 * until someone actually has. */
@Composable
private fun StarRow(avg: Double?, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val filled = avg?.let { Math.round(it).toInt() } ?: 0
        repeat(5) { i ->
            Icon(
                imageVector = if (i < filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (i < filled) Amber else InkMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(13.dp),
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = if (avg != null) "$avg ($count)" else "Not yet rated",
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            maxLines = 1,
        )
    }
}

/** The Videos tab: the real backend catalog with thumbnails, the caller's
 * stage ordered first server-side. Lazy-loaded on first visit. */
@Composable
fun VideosScreen(
    videos: List<VideoItem>,
    loading: Boolean,
    onOpenPlayer: (VideoItem) -> Unit,
    onLike: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    // Local filter: the catalog is small and already fetched — instant
    // results beat a network search round-trip.
    val shown = if (query.isBlank()) videos else videos.filter {
        it.title.contains(query, ignoreCase = true) ||
            it.topic.contains(query, ignoreCase = true) ||
            it.stage.contains(query, ignoreCase = true)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionLabel("Guides for your stage")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search videos…", color = InkMuted) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = InkMuted) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, "Clear search", tint = InkMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(4.dp))
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    text = when {
                        loading -> "Finding videos for you…"
                        query.isNotBlank() -> "Nothing matches \"$query\"."
                        else -> "No videos yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
            }
        }
        items(shown, key = { it.id }) { video ->
            AiraCard {
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
                            .width(104.dp)
                            .height(72.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            video.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (video.watched) InkMuted else Ink,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(5.dp))
                        Surface(color = LilacMist, shape = CircleShape) {
                            Text(
                                text = video.topic.takeIf { it.isNotBlank() }
                                    ?: stageLabel(video.stage),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Plum,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                        StarRow(video.avgStars, video.ratingCount)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { onLike(video) }) {
                            Icon(
                                imageVector = if (video.myLike) {
                                    Icons.Filled.Favorite
                                } else Icons.Filled.FavoriteBorder,
                                contentDescription = if (video.myLike) {
                                    "Unlike ${video.title}"
                                } else "Like ${video.title}",
                                tint = if (video.myLike) LikeRose else InkMuted,
                            )
                        }
                        Text(
                            text = "${video.likeCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted,
                        )
                    }
                }
            }
        }
    }
}
