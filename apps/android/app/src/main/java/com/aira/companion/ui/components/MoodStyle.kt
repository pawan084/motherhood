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

val moodStyles: List<MoodStyle> = listOf(
    MoodStyle("great", "Great", Icons.Outlined.SentimentVerySatisfied, Color(0xFF7C9E7A)),
    MoodStyle("okay", "Okay", Icons.Outlined.SentimentSatisfied, Color(0xFF9A86C4)),
    MoodStyle("tired", "Tired", Icons.Outlined.Bedtime, Color(0xFF7E8CA8)),
    MoodStyle("anxious", "Anxious", Icons.Outlined.Psychology, Color(0xFFC79233)),
    MoodStyle("low", "Low", Icons.Outlined.SentimentDissatisfied, Color(0xFF8A6A9C)),
    MoodStyle("unwell", "Unwell", Icons.Outlined.Sick, Color(0xFFC06B54)),
)

private val moodByKey = moodStyles.associateBy { it.key }

/** Never null: an unknown key falls back to a neutral style rather than crashing. */
fun moodStyle(key: String?): MoodStyle =
    moodByKey[key] ?: MoodStyle(key ?: "okay", (key ?: "okay").replaceFirstChar(Char::uppercase),
        Icons.Outlined.SentimentSatisfied, Color(0xFF9A86C4))
