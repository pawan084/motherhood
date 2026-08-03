package com.aira.companion.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.FileProvider
import java.io.File

/**
 * Week share card (review #36): a 1080² image drawn with framework Canvas —
 * no personal data beyond what the user chooses to share, handed to the
 * system share sheet where THEY pick the destination.
 */
object ShareCard {
    fun shareWeek(
        context: Context,
        headline: String,     // "Week 8 · Day 1"
        emoji: String?,       // size emoji
        subline: String?,     // "32 weeks to go"
    ) {
        val size = 1080
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F8F4EE"))

        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F3ECF3")
        }
        canvas.drawRoundRect(
            RectF(80f, 240f, size - 80f, size - 240f), 64f, 64f, card,
        )

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#211D20")
            textAlign = Paint.Align.CENTER
            textSize = 96f
            isFakeBoldText = true
        }
        canvas.drawText(headline, size / 2f, 480f, text)
        emoji?.let {
            text.textSize = 160f
            text.isFakeBoldText = false
            canvas.drawText(it, size / 2f, 660f, text)
        }
        subline?.let {
            text.textSize = 56f
            text.color = Color.parseColor("#716A70")
            canvas.drawText(it, size / 2f, 780f, text)
        }
        text.textSize = 44f
        text.color = Color.parseColor("#4A234B")
        canvas.drawText("tracked with Aira", size / 2f, size - 300f, text)

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "aira-week.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }
        val uri = FileProvider.getUriForFile(
            context, "com.aira.companion.fileprovider", file,
        )
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Share your week",
            ),
        )
    }
}
