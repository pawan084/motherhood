package com.aira.companion.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aira.companion.ui.theme.EyebrowInk
import com.aira.companion.ui.theme.HeroAccent
import com.aira.companion.ui.theme.HeroBottom
import com.aira.companion.ui.theme.HeroInk
import com.aira.companion.ui.theme.HeroInkMuted
import com.aira.companion.ui.theme.HeroTop
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.IvoryDeep
import com.aira.companion.ui.theme.Lilac
import com.aira.companion.ui.theme.NavActive
import com.aira.companion.ui.theme.NavInk
import com.aira.companion.ui.theme.NavInkMuted
import com.aira.companion.ui.theme.NavPill
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.OutlineSoft
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.PlumDeep

/**
 * The component vocabulary of ref/complete.html's Bloom 2.0 layer.
 *
 * These are the pieces the reference repeats across its 56 screens — the
 * onboarding step frame, the choice pill, the anchor chip, the segmented
 * control, the value card, the status note and the micro-tip. They live apart
 * from AiraComponents.kt because that file predates the reference and its
 * shapes answer to the old palette; mixing the two vocabularies in one file
 * would make it impossible to tell which of them a given screen is following.
 */

/**
 * `.eyebrow` — an uppercase section label.
 *
 * The uppercasing is visual only. TalkBack pronounces short all-caps strings as
 * initialisms ("S-T-E-P 1 O-F 4"), so the original casing is kept as the
 * content description and only the rendered glyphs are transformed. That keeps
 * the reference's look without handing a screen-reader user a spelling test.
 */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = EyebrowInk,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier.semantics { contentDescription = text },
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.08.em,
        ),
        color = color,
    )
}

/** `.obprog` — the four-step onboarding progress rule. */
@Composable
fun ObProgress(
    step: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val target = (step.coerceIn(0, total).toFloat() / total.toFloat())
    val fraction by animateFloatAsState(targetValue = target, label = "onboarding-progress")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(LilacMist)
            .semantics { contentDescription = "Step $step of $total" },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(Plum),
        )
    }
}

/**
 * `.opt` — a full-width choice pill.
 *
 * Selection is announced through [Role.RadioButton] and a selected/unselected
 * state rather than left to the fill colour, because the reference distinguishes
 * the two states largely by tint.
 */
@Composable
fun ChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) LilacMist else Paper,
        contentColor = if (selected) PlumDeep else Ink,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) Plum else OutlineSoft,
        ),
        shadowElevation = if (selected) 4.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** `.anchor .a` — a compact, wrapping chip used for timing anchors. */
@Composable
fun AnchorChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick),
        shape = CircleShape,
        color = if (selected) Plum else Paper,
        contentColor = if (selected) Paper else Ink,
        border = BorderStroke(1.dp, if (selected) Plum else OutlineSoft),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** `.seg` — an inset segmented control. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(LilacMist, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.RadioButton) { onSelect(index) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) Paper else Color.Transparent,
                contentColor = if (selected) PlumDeep else Ink,
                shadowElevation = if (selected) 2.dp else 0.dp,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** `.vcard` — an icon tile beside a title and a supporting line. */
@Composable
fun ValueCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(role = Role.RadioButton, onClick = onClick) else it },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) LilacMist else Paper,
        contentColor = Ink,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) Plum else OutlineSoft,
        ),
        shadowElevation = if (selected) 6.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(LilacMist, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Plum,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
        }
    }
}

/** `.status-note` — a calm, non-alarming explanatory panel. */
@Composable
fun StatusNote(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(LilacMist, RoundedCornerShape(15.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PlumDeep,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(9.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
            color = PlumDeep,
        )
    }
}

/** `.privacy-note` — centred fine print under a card or a control. */
@Composable
fun PrivacyNote(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
        color = InkMuted,
        textAlign = textAlign,
    )
}

/** `.text-cta` — a low-emphasis inline action with a real 48dp hit area. */
@Composable
fun TextCta(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Plum,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        contentColor = color,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                ),
            )
        }
    }
}

/** `.splash-dots` — three quiet loading dots. */
@Composable
fun SplashDots(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { contentDescription = "Loading" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(0.35f, 0.65f, 1f).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(Plum.copy(alpha = alpha), CircleShape),
            )
        }
    }
}

/** `.timechip` — a small time or value affordance. */
@Composable
fun TimeChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tinted: Boolean = true,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it },
        shape = CircleShape,
        color = if (tinted) LilacMist else Ivory,
        contentColor = Plum,
        border = if (tinted) null else BorderStroke(1.dp, OutlineSoft),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/**
 * The frame every dedicated onboarding step shares (`.obstep`).
 *
 * [fill] is the reference's flexible spacer holding the faint decorative glyph:
 * it absorbs the leftover height so the primary action stays pinned to the
 * bottom on every screen size instead of floating mid-page, which is the
 * specific complaint the reference records against the old verify-code screen.
 */
@Composable
fun OnboardingStep(
    eyebrow: String,
    title: String,
    lede: String?,
    onBack: (() -> Unit)?,
    onSkip: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    fill: @Composable (() -> Unit)? = null,
    cta: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 26.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                BackButton(onClick = onBack)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (onSkip != null) {
                TextCta(label = "Skip", onClick = onSkip, color = Plum)
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Eyebrow(text = eyebrow)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = Ink,
        )
        if (lede != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = lede,
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
        }
        content()
        if (fill != null) {
            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) { fill() }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        cta()
    }
}

/**
 * `.backbtn` — the back arrow with a real target.
 *
 * A bare glyph has only its own inline box to hit, roughly 14×20dp, on the most
 * frequently used control in the app. This is 40dp with the glyph centred.
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(40.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = CircleShape,
        color = Paper,
        contentColor = Ink,
        border = BorderStroke(1.dp, OutlineSoft),
        shadowElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * `.hero` — the week panel that opens Me.
 *
 * Bloom 2.0 inverted this: it was light ink on a deep aubergine panel and is now
 * near-black ink on a pale violet wash, which is why the hero tokens carry their
 * own ink pair rather than borrowing the page's.
 *
 * [dayInWeek] is the reference's "Day 5". It is only ever passed when it can be
 * derived from a real due date — the number is a claim about someone's
 * pregnancy, and a decorative one would be a confident lie on the largest text
 * on the screen. When it is unknown the week stands alone.
 */
@Composable
fun WeekHero(
    greeting: String,
    week: Int?,
    dayInWeek: Int?,
    subtitle: String?,
    progress: Float?,
    modifier: Modifier = Modifier,
    weekRail: List<Int> = emptyList(),
    onSelectWeek: ((Int) -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, HeroTop),
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(HeroTop, HeroBottom)))
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = HeroInk,
            )
            if (week != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = week.toString(),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 58.sp,
                        ),
                        color = HeroAccent,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Eyebrow(text = "Week", color = HeroAccent)
                        if (dayInWeek != null) {
                            Text(
                                text = "Day $dayInWeek",
                                style = MaterialTheme.typography.titleSmall,
                                color = HeroInk,
                            )
                        }
                    }
                }
            }
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeroInkMuted,
                )
            }
            if (progress != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Paper, CircleShape),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(
                                Brush.horizontalGradient(listOf(Plum, HeroAccent)),
                                CircleShape,
                            ),
                    )
                }
            }
            if (weekRail.isNotEmpty() && week != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Paper.copy(alpha = 0.85f), RoundedCornerShape(22.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    weekRail.forEach { w ->
                        val current = w == week
                        Box(
                            modifier = Modifier
                                .size(if (current) 46.dp else 34.dp)
                                .background(
                                    if (current) {
                                        Brush.linearGradient(listOf(Plum, HeroAccent))
                                    } else {
                                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                    },
                                    CircleShape,
                                )
                                .let {
                                    if (onSelectWeek != null) {
                                        it.clickable(role = Role.Button) { onSelectWeek(w) }
                                    } else {
                                        it
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = w.toString(),
                                style = if (current) {
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                    )
                                } else {
                                    MaterialTheme.typography.bodySmall
                                },
                                color = if (current) Paper else HeroInkMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * `.moodrow` — the six-mood check-in, plus the week behind it.
 *
 * [recent] is the last seven days' check-ins, oldest first, with null for a day
 * that has none. A gap is drawn as a gap rather than skipped: a row of seven
 * dots that silently omits the days you missed would show an unbroken week to
 * someone who logged twice.
 *
 * Colour is never the only signal here — see MoodStyle. Each tile carries its
 * own icon and its label, and the selected tile fills and switches to white
 * rather than relying on a hue two of which fall below the non-text floor.
 */
@Composable
fun MoodCheckInCard(
    selectedKey: String?,
    recent: List<String?>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    dayLabels: List<String> = emptyList(),
    onHistory: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Paper,
        border = BorderStroke(1.dp, OutlineSoft),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(19.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(text = "How are you today?")
                Spacer(modifier = Modifier.weight(1f))
                if (onHistory != null) {
                    TextCta(label = "History", onClick = onHistory)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                moodStyles.forEach { mood ->
                    val selected = mood.key == selectedKey
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(role = Role.RadioButton) { onSelect(mood.key) }
                            .semantics {
                                contentDescription =
                                    if (selected) "${mood.label}, selected" else mood.label
                            },
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) mood.color else LilacMist,
                        shadowElevation = if (selected) 6.dp else 0.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = mood.icon,
                                contentDescription = null,
                                tint = if (selected) Paper else mood.color,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = mood.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (selected) Paper else InkMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            if (recent.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    recent.forEachIndexed { index, key ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        // An unlogged day is an outline, not a
                                        // colour — absence has to look like
                                        // absence, not like a seventh mood.
                                        color = key?.let { moodStyle(it).color }
                                            ?: OutlineSoft,
                                        shape = CircleShape,
                                    ),
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = dayLabels.getOrElse(index) { "" },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = InkMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Today's care, as a one-line summary with a way in.
 *
 * The reference draws "1 of 3 complete · View all". When nothing has loaded the
 * card says so rather than printing "0 of 0", which reads as an achievement.
 */
@Composable
fun TodayCareCard(
    progress: CareProgress,
    loaded: Boolean,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Paper,
        border = BorderStroke(1.dp, OutlineSoft),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow(text = "Today's care")
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = when {
                        !loaded -> "Not loaded yet."
                        progress.isEmpty -> "Nothing scheduled today."
                        progress.allDone -> "All ${progress.total} done."
                        else -> "${progress.done} of ${progress.total} complete"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            TextCta(label = "View all", onClick = onViewAll)
        }
    }
}

/**
 * `.watch-preview` — the week's video, on the home screen.
 *
 * [playable] is load-bearing rather than decorative. No topic in this catalog
 * has produced media yet, so drawing the reference's play button over every
 * thumbnail would promise something that does not exist and then do nothing
 * when tapped. An unplayable topic shows what it is and when it is coming.
 */
@Composable
fun WatchThisWeekCard(
    categoryLabel: String,
    title: String,
    description: String,
    duration: String?,
    playable: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen),
        shape = RoundedCornerShape(24.dp),
        color = Paper,
        border = BorderStroke(1.dp, OutlineSoft),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(19.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(text = "What to watch this week")
                Spacer(modifier = Modifier.weight(1f))
                if (onViewAll != null) {
                    TextCta(label = "View all", onClick = onViewAll)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 104.dp, height = 72.dp)
                        .background(
                            Brush.linearGradient(listOf(Plum, PlumDeep)),
                            RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (playable) {
                            Icons.Outlined.PlayCircle
                        } else {
                            Icons.Outlined.Schedule
                        },
                        contentDescription = null,
                        tint = Paper,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    CategoryChip(label = categoryLabel)
                    Spacer(modifier = Modifier.height(7.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                    if (duration != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = duration,
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted,
                        )
                    }
                }
            }
            if (!playable) {
                Spacer(modifier = Modifier.height(10.dp))
                StatusNote(
                    text = "In production — this topic is written and reviewed, " +
                        "but the video isn't filmed yet.",
                    icon = Icons.Outlined.Schedule,
                )
            }
        }
    }
}

/** `.chip-cat` — a category tag. */
@Composable
fun CategoryChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = LilacMist,
        contentColor = Plum,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        )
    }
}

/**
 * The re-engagement card, for someone coming back after a gap.
 *
 * The reference is explicit that this copy carries real tone risk and must not
 * default to streak-loss language. Nothing here counts what was missed, offers
 * to "catch up", or implies a broken run: the days are described as quiet, the
 * ask is ten seconds about today only, and the third option is to pause
 * reminders rather than to try harder.
 *
 * "Not now" is a real dismissal, not a delay disguised as one — a card that
 * reappears on the next open would be nagging somebody who already answered.
 */
@Composable
fun QuietDaysCard(
    onCheckIn: () -> Unit,
    onDismiss: () -> Unit,
    onPauseReminders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = LilacMist,
    ) {
        Column(modifier = Modifier.padding(19.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            Brush.radialGradient(listOf(Lilac, LilacMist)),
                            CircleShape,
                        ),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "It's been a few quiet days",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "No pressure — pregnancy has days like that.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Whenever you're ready, a 10-second check-in helps Aira keep your " +
                    "week accurate. Nothing to catch up on — just today.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
            Spacer(modifier = Modifier.height(14.dp))
            PrimaryButton(
                label = "How am I feeling today?",
                onClick = onCheckIn,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextCta(label = "Not now", onClick = onDismiss, color = InkMuted)
            }
            Spacer(modifier = Modifier.height(4.dp))
            SecondaryButton(
                label = "Pause reminders for a few days",
                onClick = onPauseReminders,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * `.nav .pill` — the floating three-tab capsule.
 *
 * Three flat, equal tabs and no raised centre. That is not a stylistic
 * preference: a raised centre action needs two flankers a side, so three slots
 * plus a raised button leaves the outer two pinned to the edges with a gulf
 * around the middle. The five-tab bar this replaces existed to give that raised
 * button its flankers; with the button gone, the reason goes with it.
 *
 * The three destinations that lost a tab did not lose their route — Journey is
 * the hero, Care is the care card's "View all", and Settings is the app bar.
 * A tab is not the only way to reach a screen, but an unreachable screen is a
 * deleted one, so each was given its door before this was narrowed.
 */
@Composable
fun BloomNavPill(
    tabs: List<BloomTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = NavPill,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, Lilac),
        ) {
            Row(
                modifier = Modifier.padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                tabs.forEach { tab ->
                    val selected = tab.key == selectedKey
                    Surface(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .widthIn(min = 86.dp)
                            .clickable(role = Role.Tab) { onSelect(tab.key) }
                            .semantics {
                                contentDescription =
                                    if (selected) "${tab.label}, selected" else tab.label
                            },
                        shape = CircleShape,
                        color = if (selected) NavActive else Color.Transparent,
                        contentColor = if (selected) NavInk else NavInkMuted,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One tab of [BloomNavPill]. */
data class BloomTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

/** `.emptystate` — an honest empty, with a way out of it. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = InkMuted.copy(alpha = 0.55f),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** `.btn-secondary` — the quieter partner to PrimaryButton. */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = IvoryDeep,
        contentColor = Plum,
        border = BorderStroke(1.5.dp, Lilac),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
