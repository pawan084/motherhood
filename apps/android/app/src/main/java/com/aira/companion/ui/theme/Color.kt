package com.aira.companion.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The palette, in two themes.
 *
 * Every colour here used to be a top-level `val` — one fixed value per name —
 * which is why the app had no dark mode: 525 call sites named a light ivory
 * directly, so a dark colour scheme could only ever have recoloured the handful
 * of Material components and left the rest of the app glowing.
 *
 * The names are unchanged and still read identically at every call site
 * (`color = Ivory`), but they are now composable properties resolving against
 * whichever palette is in scope. The screens didn't change; the decision lives
 * in one file.
 *
 * The dark palette is not an inversion. Pure black with white text is harsh at
 * 3am, which is a normal hour for this app, so the surfaces are warm near-blacks
 * carrying the same plum cast as the light theme. The accents are lifted rather
 * than reused: a plum that reads as considered on ivory reads as mud on
 * charcoal. Every foreground/background pair is held to the same 4.5:1 minimum
 * as the light theme — see AiraColorsTest.
 */
class AiraColors(
    val ivory: Color,
    val ivoryDeep: Color,
    val paper: Color,
    val plum: Color,
    val plumDeep: Color,
    val plumSoft: Color,
    val lilac: Color,
    val lilacMist: Color,
    val sage: Color,
    val sageDeep: Color,
    val sageMist: Color,
    val ink: Color,
    val inkMuted: Color,
    val outlineSoft: Color,
    val urgent: Color,
    val urgentMist: Color,
    val amber: Color,
    val amberMist: Color,
    val isDark: Boolean,
)

val LightAiraColors = AiraColors(
    ivory = Color(0xFFF8F4EE),
    ivoryDeep = Color(0xFFF0EAE2),
    paper = Color(0xFFFFFCF8),
    plum = Color(0xFF4A234B),
    plumDeep = Color(0xFF311733),
    plumSoft = Color(0xFF755176),
    lilac = Color(0xFFE9DDEA),
    lilacMist = Color(0xFFF3ECF3),
    sage = Color(0xFF8FA58E),
    sageDeep = Color(0xFF49634F),
    sageMist = Color(0xFFE5EDE3),
    ink = Color(0xFF211D20),
    // Measured, not eyeballed: #716A70 cleared 4.5:1 on the page and on cards
    // but managed only 4.39:1 on the sage panels, where a good deal of the
    // supporting copy sits.
    inkMuted = Color(0xFF6A636A),
    outlineSoft = Color(0xFFE4DDD7),
    // #CC3D36 on its own mist was 4.21:1 — the urgent pill, the delete
    // confirmation and the safety copy.
    urgent = Color(0xFFB7332D),
    urgentMist = Color(0xFFFFE9E6),
    // #AE7423 on its mist was 3.54:1 — the offline notice, read by someone
    // already having trouble.
    amber = Color(0xFF8E5C1D),
    amberMist = Color(0xFFFFF1D8),
    isDark = false,
)

val DarkAiraColors = AiraColors(
    // `ivory` is the page and `paper` is the card sitting on it. In the dark
    // theme the card is LIGHTER than the page — that inversion is how elevation
    // reads when there are no usable shadows.
    ivory = Color(0xFF17141A),
    ivoryDeep = Color(0xFF120F15),
    paper = Color(0xFF221E27),
    plum = Color(0xFFD9BCE0),
    plumDeep = Color(0xFFEBDCEF),
    plumSoft = Color(0xFFC0A6C6),
    lilac = Color(0xFF3A2F42),
    lilacMist = Color(0xFF2A2431),
    sage = Color(0xFF9DB79C),
    sageDeep = Color(0xFFA8C6A9),
    sageMist = Color(0xFF232C25),
    ink = Color(0xFFF2EDF2),
    inkMuted = Color(0xFFB9B0BA),
    outlineSoft = Color(0xFF3A3340),
    urgent = Color(0xFFF39189),
    urgentMist = Color(0xFF3A211F),
    amber = Color(0xFFE8BC80),
    amberMist = Color(0xFF33261A),
    isDark = true,
)

/** The palette in scope. Static because switching theme recomposes everything
 *  anyway, so there is nothing to gain from tracking reads individually. */
val LocalAiraColors = staticCompositionLocalOf { LightAiraColors }

// ── The names the screens use ────────────────────────────────────────────────
//
// Deliberately the same identifiers as the old top-level vals, so not one of
// the 525 call sites had to change. Each is a @ReadOnlyComposable getter:
// readable anywhere inside a composable, including default parameter values,
// and a compile error anywhere it isn't — which is what made a refactor this
// wide safe to do at all. The compiler found every place that read a colour
// outside composition.

val Ivory: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.ivory
val IvoryDeep: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.ivoryDeep
val Paper: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.paper
val Plum: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.plum
val PlumDeep: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.plumDeep
val PlumSoft: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.plumSoft
val Lilac: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.lilac
val LilacMist: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.lilacMist
val Sage: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.sage
val SageDeep: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.sageDeep
val SageMist: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.sageMist
val Ink: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.ink
val InkMuted: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.inkMuted
val OutlineSoft: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.outlineSoft
val Urgent: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.urgent
val UrgentMist: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.urgentMist
val Amber: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.amber
val AmberMist: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.amberMist
