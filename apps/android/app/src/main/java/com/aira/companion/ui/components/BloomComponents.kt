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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Lilac
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
        color = if (selected) Color(0xFFF0E3FF) else Paper,
        contentColor = if (selected) Color(0xFF4E167D) else Ink,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) Color(0xFF8738FF) else OutlineSoft,
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
            .background(Color(0xFFF4EDFA), RoundedCornerShape(16.dp))
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
        color = if (selected) Color(0xFFF8F1FF) else Paper,
        contentColor = Ink,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) Color(0xFF7D2CF6) else OutlineSoft,
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
                    .background(Color(0xFFF1E5FF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF7423F2),
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
                tint = Color(0xFF5B3975),
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(9.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
            color = Color(0xFF5B3975),
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
        color = Color(0xFFFBF8FF),
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
