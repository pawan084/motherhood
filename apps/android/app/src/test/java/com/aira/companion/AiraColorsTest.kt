package com.aira.companion

import androidx.compose.ui.graphics.Color
import com.aira.companion.ui.theme.AiraColors
import com.aira.companion.ui.theme.DarkAiraColors
import com.aira.companion.ui.theme.LightAiraColors
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Both themes, held to the same contrast minimum.
 *
 * The light palette was measured by hand and three colours were found failing —
 * muted text on the sage panels, the urgent red on its own mist, and the amber
 * offline notice. A dark theme is exactly where that happens again, because a
 * value picked to look right against charcoal is easy to pick by eye and hard
 * to pick correctly. This runs the arithmetic on every pair the app actually
 * renders, in both themes, so a future palette edit can't quietly drop below
 * the line.
 */
class AiraColorsTest {

    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val (hi, lo) = if (la > lb) la to lb else lb to la
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Foreground/background pairs the screens actually put together. */
    private fun pairs(c: AiraColors) = listOf(
        Triple("body text on the page", c.ink, c.ivory),
        Triple("body text on a card", c.ink, c.paper),
        Triple("supporting text on the page", c.inkMuted, c.ivory),
        Triple("supporting text on a card", c.inkMuted, c.paper),
        Triple("supporting text on lilac", c.inkMuted, c.lilacMist),
        Triple("supporting text on sage", c.inkMuted, c.sageMist),
        Triple("section labels", c.plumSoft, c.ivory),
        Triple("section labels on lilac", c.plumSoft, c.lilacMist),
        Triple("links and buttons", c.plum, c.ivory),
        Triple("links and buttons on a card", c.plum, c.paper),
        Triple("sage panel text", c.sageDeep, c.sageMist),
        Triple("urgent pill", c.urgent, c.urgentMist),
        Triple("urgent text on the page", c.urgent, c.ivory),
        Triple("offline notice", c.amber, c.amberMist),
    )

    private fun assertAllPass(name: String, c: AiraColors) {
        val failures = pairs(c)
            .map { (what, fg, bg) -> what to ratio(fg, bg) }
            .filter { (_, r) -> r < 4.5 }
        assertTrue(
            "$name theme below 4.5:1 — " +
                failures.joinToString { (what, r) -> "$what ${"%.2f".format(r)}" },
            failures.isEmpty(),
        )
    }

    @Test
    fun theLightThemeMeetsTheContrastMinimum() = assertAllPass("light", LightAiraColors)

    @Test
    fun theDarkThemeMeetsTheContrastMinimum() = assertAllPass("dark", DarkAiraColors)

    @Test
    fun theDarkThemeIsActuallyDark() {
        // Guards against the palettes being wired up the wrong way round, which
        // would otherwise pass every contrast check above while showing an ivory
        // app to someone who asked for dark.
        assertTrue(luminance(DarkAiraColors.ivory) < 0.05)
        assertTrue(luminance(DarkAiraColors.ink) > 0.5)
    }

    @Test
    fun cardsSitAboveThePageInBothThemes() {
        // Elevation is carried by the paper/ivory relationship rather than by
        // shadows, so the direction of that difference is load-bearing: cards
        // are lighter than the page in dark, darker in light.
        assertTrue(luminance(LightAiraColors.paper) > luminance(LightAiraColors.ivory))
        assertTrue(luminance(DarkAiraColors.paper) > luminance(DarkAiraColors.ivory))
    }
}
