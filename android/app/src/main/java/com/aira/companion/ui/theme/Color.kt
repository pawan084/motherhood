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

// ── Rose second accent (Dawn move 3) ────────────────────────────────────────
// A warm counterpoint to plum: section eyebrows, tool tiles, category chips.
// Rose is flavour, never status — sage keeps meaning "done / safe".
val Rose: Color get() = c(0xFFB4596B, 0xFFDD93A3)
val RoseMist: Color get() = c(0xFFFAE9EA, 0xFF39222B)

// Gradient tail for the primary button (Dawn move 6): plum → warm plum.
val PlumGradEnd: Color get() = c(0xFF8A4A78, 0xFFB893C4)

// ── Hero panel ──────────────────────────────────────────────────────────────
// Dawn redesign (docs/design/redesign.html, move 1): in the light theme the
// hero is a sunrise gradient (peach → lilac) with plum ink — the page's one
// bold moment now glows instead of anchoring dark. The dark theme keeps its
// deep aubergine hero: at 3 AM a glowing peach panel would be the wrong call.
val HeroTop: Color get() = c(0xFFFBE3D3, 0xFF3A1C3C)
val HeroBottom: Color get() = c(0xFFE2CFEF, 0xFF1F0D25)
val HeroInk: Color get() = c(0xFF3B1C3F, 0xFFF2E7F3)
val HeroInkMuted: Color get() = c(0xFF7A5680, 0xFFBBA1BE)
val HeroAccent: Color get() = c(0xFF5A2B5C, 0xFFE7C9ED)

// ── Floating bottom-nav pill ────────────────────────────────────────────────
// Dawn move 4: light theme goes frosted — translucent white capsule, solid
// plum active tab, content glowing through beneath. Dark theme keeps its
// dark capsule. Nav ink is its own pair so it always reads against the pill.
val NavPill: Color get() = c(0xF2FFFFFF, 0xFF130F19)
val NavActive: Color get() = c(0xFF5A2B5C, 0xFF453450)
val NavInk: Color get() = c(0xFFFFFCF8, 0xFFF2E7F3)
val NavInkMuted: Color get() = c(0xFF8A6390, 0xFFBBA1BE)
