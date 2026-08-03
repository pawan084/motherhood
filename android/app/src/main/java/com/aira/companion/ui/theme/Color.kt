package com.aira.companion.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Switchable palette (review #46 — dark mode for the 3am feeds): every
 * screen keeps importing the same names, but each name now RESOLVES against
 * the active palette. The getters read a Compose state, so flipping
 * [AiraPalette.dark] recomposes everything that used a color — no per-screen
 * changes, no second import path.
 */
object AiraPalette {
    var dark by mutableStateOf(false)
}

private fun c(light: Long, darkVariant: Long): Color =
    if (AiraPalette.dark) Color(darkVariant) else Color(light)

val Ivory: Color get() = c(0xFFF8F4EE, 0xFF16121A)
val IvoryDeep: Color get() = c(0xFFF0EAE2, 0xFF1D1822)
val Paper: Color get() = c(0xFFFFFCF8, 0xFF231D28)
val Plum: Color get() = c(0xFF4A234B, 0xFFD3B4DC)
val PlumDeep: Color get() = c(0xFF311733, 0xFFE7D3EC)
val PlumSoft: Color get() = c(0xFF755176, 0xFFB093B6)
val Lilac: Color get() = c(0xFFE9DDEA, 0xFF483C50)
val LilacMist: Color get() = c(0xFFF3ECF3, 0xFF2E2635)
val Sage: Color get() = c(0xFF8FA58E, 0xFF9DBB9C)
val SageDeep: Color get() = c(0xFF49634F, 0xFFB7D0BC)
val SageMist: Color get() = c(0xFFE5EDE3, 0xFF242B26)
val Ink: Color get() = c(0xFF211D20, 0xFFEFE8F0)
val InkMuted: Color get() = c(0xFF716A70, 0xFFA79FAB)
val OutlineSoft: Color get() = c(0xFFE4DDD7, 0xFF3B3440)
val Urgent: Color get() = c(0xFFCC3D36, 0xFFE57A72)
val UrgentMist: Color get() = c(0xFFFFE9E6, 0xFF3A2422)
val Amber: Color get() = c(0xFFAE7423, 0xFFE0A65A)
val AmberMist: Color get() = c(0xFFFFF1D8, 0xFF352A18)
