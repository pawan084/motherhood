package com.aira.companion.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.material.icons.outlined.Sick
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single source of truth for how a mood looks across Me and the Moods detail:
 * a refined outline icon (no emoji), a calm distinguishing colour (a gentle
 * spectrum — never red/green traffic lights, which read as judgement), and a
 * spoken label so screen readers announce the feeling, not just its shape.
 *
 * "Anxious" is included deliberately: it is one of the most common perinatal
 * states and previously could not be expressed at all.
 */
data class MoodStyle(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val color: Color,
)

/**
 * The spectrum from ref/complete.html's Bloom 2.0 block (--m-great … --m-unwell
 * at line 470), not the earlier palette it supersedes.
 *
 * Two of these are decorative-only by necessity. Against a white card, "great"
 * measures 2.78:1 and "anxious" 2.92:1 — below the 3:1 WCAG floor for non-text
 * contrast — so a mood is never distinguished by its colour alone: every entry
 * pairs the colour with a distinct icon shape and a spoken [label], and the
 * selected state fills the tile and switches to white text rather than relying
 * on the hue. AiraColorsTest records the two measurements.
 */
val moodStyles: List<MoodStyle> = listOf(
    MoodStyle("great", "Great", Icons.Outlined.SentimentVerySatisfied, Color(0xFF4FAD72)),
    MoodStyle("okay", "Okay", Icons.Outlined.SentimentSatisfied, Color(0xFF7527F5)),
    MoodStyle("tired", "Tired", Icons.Outlined.Bedtime, Color(0xFF6579B7)),
    MoodStyle("anxious", "Anxious", Icons.Outlined.Psychology, Color(0xFFC98B20)),
    MoodStyle("low", "Low", Icons.Outlined.SentimentDissatisfied, Color(0xFF8855A6)),
    MoodStyle("unwell", "Unwell", Icons.Outlined.Sick, Color(0xFFC45F5C)),
)

private val moodByKey = moodStyles.associateBy { it.key }

/** Never null: an unknown key falls back to a neutral style rather than crashing. */
fun moodStyle(key: String?): MoodStyle =
    moodByKey[key] ?: MoodStyle(key ?: "okay", (key ?: "okay").replaceFirstChar(Char::uppercase),
        Icons.Outlined.SentimentSatisfied, Color(0xFF7527F5))
