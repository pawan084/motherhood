package com.aira.companion.ui.components

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.OutlineSoft

/**
 * The shape of what is coming, while it is still coming.
 *
 * Screens used to go from blank — or the word "Loading…" — straight to full
 * content. Both leave you guessing whether anything is on its way; "Loading…"
 * in the place an empty state will occupy also looks, for a moment, exactly
 * like a section that has nothing in it.
 *
 * These are rows the size of the rows that will replace them, so the screen
 * does not jump when the real ones arrive.
 *
 * ── Motion ──
 *
 * The pulse is switched off when the system's animator scale is zero. That
 * setting is how someone turns animation off on Android, and it is set by
 * people who get motion sickness from it or who find movement hard to look
 * past — reasons that apply more, not less, to an app used during pregnancy
 * and after birth. The placeholder still appears; it simply holds still.
 */
@Composable
private fun animationsEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) {
        runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    fraction: Float = 1f,
) {
    val alpha = if (animationsEnabled()) {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val value by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "skeleton-alpha",
        )
        value
    } else {
        0.7f
    }
    Column(
        modifier = modifier
            .fillMaxWidth(fraction)
            .height(height)
            .background(
                color = if (alpha > 0.7f) LilacMist else OutlineSoft,
                shape = RoundedCornerShape(6.dp),
            ),
    ) {}
}

/**
 * A few placeholder rows for a list that has not arrived.
 *
 * One description for the group rather than one per bar: a screen reader
 * announcing five identical "loading" nodes is worse than silence, and what
 * someone needs to know is that this section is still coming.
 */
@Composable
fun SkeletonRows(
    count: Int = 3,
    modifier: Modifier = Modifier,
    label: String = "Loading",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label — still loading" },
    ) {
        repeat(count) { index ->
            SkeletonBlock(
                modifier = Modifier.padding(vertical = 9.dp),
                height = 16.dp,
                // Uneven widths, because a stack of identical bars reads as a
                // graphic rather than as text that has not arrived.
                fraction = when (index % 3) {
                    0 -> 0.82f
                    1 -> 0.64f
                    else -> 0.73f
                },
            )
        }
    }
}
