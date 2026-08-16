package com.aira.companion.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.aira.companion.ui.theme.Lilac
import com.aira.companion.ui.theme.Plum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Zero-dependency remote image with a small in-memory LRU (video thumbnails
 * are ~20-60 KB; 24 entries ≈ a screenful of catalog). Degrades to a tinted
 * placeholder while loading or on failure — a missing thumbnail must never
 * break a row. For anything heavier than thumbnails, adopt Coil instead. */
private val cache = LruCache<String, Bitmap>(24)

/** Disk layer under the memory LRU: thumbs survive process death instead of
 * re-downloading every launch. Keyed by url hash in cacheDir (the OS may
 * clear it — that's the contract we want). */
private fun diskFile(context: android.content.Context, url: String): java.io.File =
    java.io.File(context.cacheDir, "thumb_" + url.hashCode().toUInt().toString(16))

private suspend fun fetchBitmap(context: android.content.Context, url: String): Bitmap? =
    withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }
        val disk = diskFile(context, url)
        if (disk.exists()) {
            runCatching { BitmapFactory.decodeFile(disk.path) }.getOrNull()?.let {
                cache.put(url, it)
                return@withContext it
            }
        }
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 6_000
            conn.readTimeout = 10_000
            conn.inputStream.use { it.readBytes() }
        }.getOrNull()?.let { bytes ->
            runCatching { disk.writeBytes(bytes) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?.also { cache.put(url, it) }
        }
    }

/** YouTube thumbnail for a video id (mqdefault = 320x180, plenty for rows). */
fun youtubeThumbnailUrl(youtubeId: String): String =
    "https://img.youtube.com/vi/$youtubeId/mqdefault.jpg"

@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = cache.get(url), url) {
        if (value == null) value = fetchBitmap(context.applicationContext, url)
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(Lilac.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayCircle,
                contentDescription = contentDescription,
                tint = Plum,
            )
        }
    }
}
