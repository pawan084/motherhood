package com.aira.companion.ui.screens

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Plum

/**
 * A scan, read inside Aira.
 *
 * Opening a document used to fire an ACTION_VIEW intent, which hands the file to
 * whatever app claims the type. That undoes the rest of this screen's
 * protections in one tap: the app lock does not apply to another app, neither
 * does FLAG_SECURE, and the receiving app is free to cache, index or back up
 * somebody's scan. It was also the default and only path, so nobody chose it.
 *
 * Rendered here instead. Handing it to another app is still possible — people do
 * genuinely want to email a report to a partner or a clinic — but it is now a
 * named, secondary action taken deliberately rather than the thing that happens
 * when you tap your own document.
 *
 * PDFs go through the platform's PdfRenderer, so no library and no upload to
 * anything is involved; JPEG and PNG decode directly. Those three are exactly
 * what the server accepts, so there is no fourth case to fall through.
 */
@Composable
fun DocumentViewer(
    file: File,
    title: String,
    contentType: String?,
    onClose: () -> Unit,
    onOpenExternally: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ivory)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    "Shown inside Aira",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close document", tint = Ink)
            }
        }

        val pages by produceState<List<ImageBitmap>?>(initialValue = null, file, contentType) {
            value = withContext(Dispatchers.IO) { renderPages(file, contentType) }
        }

        val rendered = pages
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                rendered == null -> Text(
                    "Opening…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
                rendered.isEmpty() -> Text(
                    // Honest rather than blank. The file is still there and can
                    // still be opened elsewhere from the button below.
                    "This file can't be shown here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
                else -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rendered.forEach { page ->
                        Image(
                            bitmap = page,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        TextButton(
            onClick = onOpenExternally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text("Open in another app", color = Plum)
        }
        Text(
            // Said plainly at the moment of choosing, not in a settings screen
            // nobody reads.
            text = "Another app can keep its own copy of anything you open there.",
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
        )
    }
}

/** Pages as bitmaps, or an empty list when the type is not one we render. */
private fun renderPages(file: File, contentType: String?): List<ImageBitmap> = runCatching {
    when {
        contentType?.contains("pdf", ignoreCase = true) == true -> {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    (0 until renderer.pageCount).map { index ->
                        renderer.openPage(index).use { page ->
                            // Rendered at twice the page's point size: a scan of
                            // a printed report is unreadable at 1x on a phone,
                            // and being unable to read it is the same as not
                            // having it.
                            val bitmap = android.graphics.Bitmap.createBitmap(
                                page.width * 2,
                                page.height * 2,
                                android.graphics.Bitmap.Config.ARGB_8888,
                            )
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(
                                bitmap, null, null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                            )
                            bitmap.asImageBitmap()
                        }
                    }
                }
            }
        }
        else -> listOfNotNull(
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap(),
        )
    }
}.getOrDefault(emptyList())
