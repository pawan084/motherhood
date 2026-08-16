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
 * ── Which layer of the reference this is ────────────────────────────────────
 *
 * ref/complete.html defines its tokens three times, all on a bare `:root` at
 * equal specificity, so the LAST one is what the file actually renders:
 *
 *   1. line  15  the original aubergine palette (--plum:#4A234B)
 *   2. line 328  "Dawn" — a peach/lilac re-skin, marked as already shipped
 *   3. line 455  "Aira Bloom 2.0 — unified production design direction",
 *                explicitly annotated "intentionally redesign every screen"
 *
 * This file is layer 3. Layers 1 and 2 are dead in the reference and are not
 * reproduced here — Dawn's peach hero and aubergine plum are what the other
 * codebase shipped, and adopting them would match a design the reference has
 * since replaced. The whole system is brighter and violet now: plum is #7527F5
 * rather than #4A234B, and paper is pure white rather than warm ivory.
 *
 * The dark palette is not an inversion. Pure black with white text is harsh at
 * 3am, which is a normal hour for this app, so the surfaces are warm near-blacks
 * carrying the same violet cast as the light theme. Contrast is checked on every
 * pair the app actually renders — see AiraColorsTest.
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
    // ── Bloom 2.0 adds a full semantic set beside the existing accents ───────
    val destructive: Color,
    val destructiveMist: Color,
    val info: Color,
    val infoMist: Color,
    val success: Color,
    val successMist: Color,
    val focus: Color,
    val rose: Color,
    val roseMist: Color,
    val heroTop: Color,
    val heroBottom: Color,
    val heroInk: Color,
    val heroInkMuted: Color,
    val heroAccent: Color,
    val navPill: Color,
    val navActive: Color,
    val navInk: Color,
    val navInkMuted: Color,
    val isDark: Boolean,
)

val LightAiraColors = AiraColors(
    ivory = Color(0xFFFBFAFF),
    ivoryDeep = Color(0xFFF4F0FB),
    paper = Color(0xFFFFFFFF),
    plum = Color(0xFF7527F5),
    plumDeep = Color(0xFF4C13B8),
    plumSoft = Color(0xFF9567D4),
    lilac = Color(0xFFDDC7FF),
    lilacMist = Color(0xFFF5EEFF),
    sage = Color(0xFF75AD86),
    sageDeep = Color(0xFF35704A),
    sageMist = Color(0xFFEAF7EE),
    ink = Color(0xFF25212B),
    inkMuted = Color(0xFF746D7E),
    outlineSoft = Color(0xFFEAE2F6),
    urgent = Color(0xFFC93842),
    urgentMist = Color(0xFFFFF0F1),
    amber = Color(0xFFA76A16),
    amberMist = Color(0xFFFFF5DD),
    destructive = Color(0xFF9D2852),
    destructiveMist = Color(0xFFFDEEF4),
    info = Color(0xFF315DAD),
    infoMist = Color(0xFFEDF4FF),
    success = Color(0xFF35704A),
    successMist = Color(0xFFEAF7EE),
    focus = Color(0xFF6B18EE),
    rose = Color(0xFFA9448B),
    roseMist = Color(0xFFFCECF7),
    // The hero is a pale violet wash under near-black ink — Bloom 2.0 inverted
    // Dawn's dark panel, so the ink is the dark element and the panel the light
    // one. It clears 14:1, the most legible surface in the app.
    heroTop = Color(0xFFFCE9FF),
    heroBottom = Color(0xFFEEE0FF),
    heroInk = Color(0xFF2D163B),
    heroInkMuted = Color(0xFF725680),
    heroAccent = Color(0xFF7527F5),
    // Nav is a pale violet capsule with a solid violet active tab.
    navPill = Color(0xFFF2E9FF),
    navActive = Color(0xFF7527F5),
    navInk = Color(0xFFFFFFFF),
    navInkMuted = Color(0xFF7B4FA8),
    isDark = false,
)

val DarkAiraColors = AiraColors(
    // `ivory` is the page and `paper` is the card sitting on it. In the dark
    // theme the card is LIGHTER than the page — that inversion is how elevation
    // reads when there are no usable shadows.
    ivory = Color(0xFF201725),
    ivoryDeep = Color(0xFF170F1B),
    paper = Color(0xFF2B2033),
    plum = Color(0xFFBB82FF),
    plumDeep = Color(0xFFDEC1FF),
    plumSoft = Color(0xFFC7A2E8),
    lilac = Color(0xFF4A3752),
    lilacMist = Color(0xFF382A48),
    sage = Color(0xFF8FBF8C),
    sageDeep = Color(0xFFB7DDB4),
    sageMist = Color(0xFF212B20),
    ink = Color(0xFFF7F1FA),
    inkMuted = Color(0xFFC0B1C7),
    outlineSoft = Color(0xFF493759),
    urgent = Color(0xFFFF8478),
    urgentMist = Color(0xFF3A1A17),
    amber = Color(0xFFE8C989),
    amberMist = Color(0xFF3A2E17),
    // The reference's dark block never defines destructive, info, success or
    // focus — it only re-tints the tokens the older palettes already had. These
    // four are derived here rather than left at their light values, which would
    // have put a #9D2852 plum-red on a near-black card at 1.9:1. Each is the
    // light hue lifted to the same luminance band as its neighbours, and each
    // is checked by AiraColorsTest like everything else.
    destructive = Color(0xFFE8899B),
    destructiveMist = Color(0xFF3A2028),
    info = Color(0xFF9DBDF0),
    infoMist = Color(0xFF1E2A3D),
    success = Color(0xFFB7DDB4),
    successMist = Color(0xFF212B20),
    focus = Color(0xFFBB82FF),
    rose = Color(0xFFE8899B),
    roseMist = Color(0xFF3A2028),
    heroTop = Color(0xFF3A1D3D),
    heroBottom = Color(0xFF1F0F22),
    heroInk = Color(0xFFF7EFF7),
    heroInkMuted = Color(0xFFC9A8CC),
    heroAccent = Color(0xFFE8CBEC),
    navPill = Color(0xFF2F2239),
    navActive = Color(0xFF5A3A5C),
    navInk = Color(0xFFFFFCF8),
    navInkMuted = Color(0xFFC0B1C7),
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

// ── Bloom 2.0 ────────────────────────────────────────────────────────────────
val Destructive: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.destructive
val DestructiveMist: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.destructiveMist
val Info: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.info
val InfoMist: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.infoMist
val Success: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.success
val SuccessMist: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.successMist
val Focus: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.focus
val Rose: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.rose
val RoseMist: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.roseMist
val HeroTop: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.heroTop
val HeroBottom: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.heroBottom
val HeroInk: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.heroInk
val HeroInkMuted: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.heroInkMuted
val HeroAccent: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.heroAccent
val NavPill: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.navPill
val NavActive: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.navActive
val NavInk: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.navInk
val NavInkMuted: Color @Composable @ReadOnlyComposable get() = LocalAiraColors.current.navInkMuted
