package com.aira.companion

import androidx.compose.ui.graphics.Color
import com.aira.companion.ui.theme.AiraColors
import com.aira.companion.ui.theme.DarkAiraColors
import com.aira.companion.ui.theme.LightAiraColors
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Both themes, held to a contrast minimum — but not the same one.
 *
 * The dark palette clears 4.5:1 on every pair the app renders and is held to
 * exactly that. The light palette cannot: adopting the Dawn reference palette
 * from ref/complete.html verbatim put eight pairs between 3.54:1 and 4.48:1.
 * Three of those were previously passing on darkened values (#6A636A, #B7332D,
 * #8E5C1D) and regressed when the reference won; the other five arrived with
 * the reference's new rose accent and hero gradient.
 *
 * Those eight are listed in [lightThemeExemptions] with their measured ratios
 * rather than deleted from [pairs]. An exemption you can read is worth more
 * than a check that quietly disappeared, and it makes the cost of matching the
 * reference legible to whoever reads this next. They are still held to a 3:1
 * floor by [theExemptedPairsAreStillHeldToAFloor], so they cannot drift further.
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
        // Dawn redesign surfaces: the hero gradient, the frosted nav pill and
        // the rose accent are all new places text sits on a tinted ground. The
        // hero is checked at both ends of its gradient — the far end is the
        // darker lilac, and supporting copy runs the full height of the panel.
        Triple("hero headline", c.heroInk, c.heroTop),
        Triple("hero headline at the gradient's far end", c.heroInk, c.heroBottom),
        Triple("hero supporting text", c.heroInkMuted, c.heroTop),
        Triple("hero supporting text at the gradient's far end", c.heroInkMuted, c.heroBottom),
        Triple("active nav tab", c.navInk, c.navActive),
        Triple("inactive nav tab", c.navInkMuted, c.navPill),
        Triple("section eyebrow", c.rose, c.ivory),
        Triple("section eyebrow on a card", c.rose, c.paper),
        Triple("tool tile label", c.rose, c.roseMist),
    )

    /**
     * Light-theme pairs the Dawn reference puts below 4.5:1, with the ratio each
     * measures today. The dark palette clears all of them and is granted no
     * exemptions at all.
     */
    private val lightThemeExemptions = mapOf(
        "supporting text on sage" to 4.39,
        "urgent pill" to 4.21,
        "urgent text on the page" to 4.47,
        "offline notice" to 3.54,
        "hero supporting text at the gradient's far end" to 4.15,
        "section eyebrow" to 4.18,
        "section eyebrow on a card" to 4.48,
        "tool tile label" to 3.91,
    )

    private fun assertAllPass(name: String, c: AiraColors, exempt: Set<String>) {
        val failures = pairs(c)
            .filterNot { (what, _, _) -> what in exempt }
            .map { (what, fg, bg) -> what to ratio(fg, bg) }
            .filter { (_, r) -> r < 4.5 }
        assertTrue(
            "$name theme below 4.5:1 — " +
                failures.joinToString { (what, r) -> "$what ${"%.2f".format(r)}" },
            failures.isEmpty(),
        )
    }

    @Test
    fun theLightThemeMeetsTheContrastMinimum() =
        assertAllPass("light", LightAiraColors, lightThemeExemptions.keys)

    @Test
    fun theDarkThemeMeetsTheContrastMinimum() =
        assertAllPass("dark", DarkAiraColors, emptySet())

    @Test
    fun theExemptedPairsAreStillHeldToAFloor() {
        // The exemption buys the reference's exact hex, not a licence to drift.
        // 3:1 is the AA minimum for large text and non-text contrast, and the
        // worst of the eight sits at 3.54 — so this fails on the next edit that
        // makes them worse, not on the one that introduced them.
        val tooFar = pairs(LightAiraColors)
            .filter { (what, _, _) -> what in lightThemeExemptions }
            .map { (what, fg, bg) -> what to ratio(fg, bg) }
            .filter { (_, r) -> r < 3.0 }
        assertTrue(
            "exempted light pair below the 3:1 floor — " +
                tooFar.joinToString { (what, r) -> "$what ${"%.2f".format(r)}" },
            tooFar.isEmpty(),
        )
    }

    @Test
    fun everyExemptionIsStillEarned() {
        // The counterpart to the floor: an exemption that starts passing 4.5:1
        // is dead weight that hides a real regression later. If a future palette
        // edit lifts one of these over the line, this fails and tells you to
        // delete the entry rather than leave the pair permanently unchecked.
        val nowPassing = pairs(LightAiraColors)
            .filter { (what, _, _) -> what in lightThemeExemptions }
            .map { (what, fg, bg) -> what to ratio(fg, bg) }
            .filter { (_, r) -> r >= 4.5 }
        assertTrue(
            "exemption no longer needed, remove it — " +
                nowPassing.joinToString { (what, r) -> "$what ${"%.2f".format(r)}" },
            nowPassing.isEmpty(),
        )
    }

    @Test
    fun theRecordedRatiosMatchTheRealOnes() {
        // The numbers in lightThemeExemptions are documentation, and stale
        // documentation about accessibility is worse than none. Pin them.
        val drifted = pairs(LightAiraColors)
            .mapNotNull { (what, fg, bg) ->
                lightThemeExemptions[what]?.let { recorded ->
                    val actual = ratio(fg, bg)
                    if (kotlin.math.abs(actual - recorded) > 0.01) {
                        "$what recorded $recorded but measures ${"%.2f".format(actual)}"
                    } else {
                        null
                    }
                }
            }
        assertTrue("exemption ratios are out of date — ${drifted.joinToString()}", drifted.isEmpty())
    }

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

    @Test
    fun theHeroIsLegibleAcrossItsWholeGradient() {
        // The hero is the one place a foreground sits on a moving background,
        // so checking only the top would miss the end that actually fails.
        // Headline ink must clear AA at both ends in both themes.
        listOf("light" to LightAiraColors, "dark" to DarkAiraColors).forEach { (name, c) ->
            assertTrue(
                "$name hero headline fails at the top of the gradient",
                ratio(c.heroInk, c.heroTop) >= 4.5,
            )
            assertTrue(
                "$name hero headline fails at the bottom of the gradient",
                ratio(c.heroInk, c.heroBottom) >= 4.5,
            )
        }
    }
}
