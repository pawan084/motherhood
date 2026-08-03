package com.aira.companion.ui.screens

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aira.companion.model.VideoItem
import com.aira.companion.net.AiraApi
import com.aira.companion.ui.theme.Lilac

/**
 * In-app player for OWN hosted videos (review #31 + user ask) — framework
 * VideoView + MediaController, zero new dependencies. Completion fires the
 * watched signal that drives unwatched-first suggestions; the like button
 * and the 1-5 star row write REAL aggregates.
 */
@Composable
fun VideoPlayerScreen(
    video: VideoItem,
    onClose: () -> Unit,
    onCompleted: (VideoItem) -> Unit,
    onLike: (VideoItem) -> Unit,
    onRate: (VideoItem, Int) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close player",
                        tint = Color.White,
                    )
                }
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val streamUrl = AiraApi.baseUrl + (video.streamPath ?: "")
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(Uri.parse(streamUrl))
                            val controller = MediaController(ctx)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setOnCompletionListener { onCompleted(video) }
                            setOnPreparedListener { start() }
                        }
                    },
                )
            }

            // ── Social row: real like count + the caller's own star rating ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onLike(video) }) {
                    Icon(
                        imageVector = if (video.myLike) {
                            Icons.Filled.Favorite
                        } else Icons.Filled.FavoriteBorder,
                        contentDescription = if (video.myLike) "Unlike" else "Like",
                        tint = if (video.myLike) Color(0xFFE57387) else Color.White,
                    )
                }
                Text(
                    text = "${video.likeCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
                Spacer(Modifier.width(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (1..5).forEach { star ->
                        IconButton(
                            onClick = { onRate(video, star) },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                imageVector = if ((video.myStars ?: 0) >= star) {
                                    Icons.Filled.Star
                                } else Icons.Outlined.StarOutline,
                                contentDescription = "Rate $star star${if (star > 1) "s" else ""}",
                                tint = if ((video.myStars ?: 0) >= star) {
                                    Color(0xFFF2C14E)
                                } else Lilac,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                video.avgStars?.let { avg ->
                    Text(
                        text = "★ $avg (${video.ratingCount})",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
