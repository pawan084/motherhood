package com.aira.companion.ui.components

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.aira.companion.model.MainDestination
import com.aira.companion.data.ActionCard
import com.aira.companion.model.bottomBarDestinations
import com.aira.companion.ui.theme.Amber
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Lilac
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.LocalAiraColors
import com.aira.companion.ui.theme.OutlineSoft
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.PlumDeep
import com.aira.companion.ui.theme.PlumSoft
import com.aira.companion.ui.theme.Sage
import com.aira.companion.ui.theme.SageDeep
import com.aira.companion.ui.theme.SageMist
import com.aira.companion.ui.theme.Urgent

@Composable
fun BrandOrb(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val size = if (compact) 32.dp else 82.dp
    // Resolved in composition: Canvas's draw block is not composable, so the
    // palette has to be read before entering it.
    val halo = Lilac
    val glow = Sage
    val core = Plum
    val highlight = Paper
    val orbOuter = if (LocalAiraColors.current.isDark) Color(0xFF2B2333) else Color(0xFFF9F2FA)
    val orbEdge = if (LocalAiraColors.current.isDark) Color(0xFF4B3B55) else Color(0xFFD6C1DE)
    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(orbOuter, halo, orbEdge),
                    center = center,
                    radius = radius,
                ),
            radius = radius,
        )
        drawCircle(
            color = glow.copy(alpha = 0.34f),
            radius = radius * 0.59f,
            center = center + Offset(radius * 0.08f, -radius * 0.04f),
        )
        drawCircle(
            color = core.copy(alpha = 0.88f),
            radius = radius * 0.29f,
            center = center,
        )
        drawCircle(
            color = highlight,
            radius = radius * 0.10f,
            center = center,
        )
    }
}

@Composable
fun SafetyBadge(
    text: String = "Safety checked",
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = SageMist,
        contentColor = SageDeep,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
            )
            Text(text = text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = Icons.Filled.ChevronRight,
    /** Overridden only where the action is not the ordinary primary one — the
     *  escalation card's call button is urgent, and a plum button there would
     *  read as the same weight as "Continue". */
    containerColor: Color = Plum,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        // heightIn, not height: at a raised system font scale a fixed 54dp
        // button clips its own label, and raising the font scale is what
        // someone does when they are struggling to read it in the first place.
        modifier = modifier.heightIn(min = 54.dp),
        shape = RoundedCornerShape(17.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = Paper,
                disabledContainerColor = Lilac,
                disabledContentColor = Plum.copy(alpha = 0.52f),
            ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelLarge,
        )
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun AiraCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Paper,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .border(1.dp, OutlineSoft.copy(alpha = 0.8f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content,
        )
    }
}

@Composable
fun ChoiceCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(role = Role.RadioButton, onClick = onClick),
        color = if (selected) LilacMist else Paper,
        contentColor = Ink,
        shape = RoundedCornerShape(18.dp),
        border =
            androidx.compose.foundation.BorderStroke(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) Plum.copy(alpha = 0.54f) else OutlineSoft,
            ),
        shadowElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .background(if (selected) Lilac else Ivory, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Plum,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(13.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = if (selected) Plum else InkMuted.copy(alpha = 0.65f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ChatBubble(
    text: String,
    fromAira: Boolean,
    modifier: Modifier = Modifier,
    /** Unix seconds, when known. Rendered under the bubble so a conversation
     *  can be placed against the day it happened — which only started to matter
     *  once history survived a restart and last night's worry sat above this
     *  morning's question. */
    at: Double? = null,
    /** Never reached the server. The bubble says so and offers to try again. */
    failed: Boolean = false,
    onRetry: (() -> Unit)? = null,
    /** Aira replies only, and only until it has been answered. */
    onRate: ((helpful: Boolean) -> Unit)? = null,
    rated: Boolean = false,
    /** The backend screened this turn as raised concern and asked for the
     *  medical disclaimer. Rendered inside the bubble, attached to the answer
     *  it qualifies, rather than as a banner somewhere else on the screen. */
    disclaimer: Boolean = false,
    /** How the gate saw this turn: "wellness" or "watchful". Shown as a small
     *  chip above the answer, because "consider your care team" changes how the
     *  sentence under it should be read. */
    trustLabel: String? = null,
    /** The one next step this reply offers, and what to do when it is pressed.
     *  Both must be non-null for the card to appear — a card that goes nowhere
     *  is worse than no card. */
    card: ActionCard? = null,
    onCardClick: (() -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    val haptics = rememberAiraHaptics()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (fromAira) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (fromAira) {
            Surface(
                modifier = Modifier.size(30.dp),
                color = Plum,
                contentColor = Paper,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("A", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.width(9.dp))
        }
        Surface(
            modifier = Modifier.fillMaxWidth(0.82f),
            color = if (fromAira) Paper else Plum,
            contentColor = if (fromAira) Ink else Paper,
            shape =
                RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (fromAira) 6.dp else 20.dp,
                    bottomEnd = if (fromAira) 20.dp else 6.dp,
                ),
            border =
                if (fromAira) {
                    androidx.compose.foundation.BorderStroke(1.dp, OutlineSoft)
                } else {
                    null
                },
            shadowElevation = if (fromAira) 1.dp else 0.dp,
        ) {
            Column(
                modifier = Modifier
                    // Long-press to copy. There was no way to keep anything
                    // Aira said — not a phone number, not a question worth
                    // taking to an appointment. Long-press is where people
                    // already reach for this in every other messaging app.
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboard.setText(AnnotatedString(text))
                            haptics.confirm()
                        },
                        onLongClickLabel = "Copy message",
                    )
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                // How the gate read this turn, above the words it applies to.
                if (fromAira && !trustLabel.isNullOrBlank()) {
                    val watchful = trustLabel.equals("watchful", ignoreCase = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.HealthAndSafety,
                            contentDescription = null,
                            tint = if (watchful) Amber else Sage,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (watchful) {
                                "Watchful · consider your care team"
                            } else {
                                "Wellness guidance"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (watchful) Amber else Sage,
                        )
                    }
                    Spacer(modifier = Modifier.height(7.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (disclaimer) {
                    Spacer(modifier = Modifier.height(7.dp))
                    Text(
                        text = "This isn’t medical advice — please check with your care team.",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (fromAira) InkMuted else Paper.copy(alpha = 0.85f),
                    )
                }
                if (at != null && at > 0) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = formatMessageTime(at),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (fromAira) InkMuted else Paper.copy(alpha = 0.75f),
                    )
                }
                // The one next step, when the reply suggests one.
                //
                // Drawn only when it has somewhere to go: `onCardClick` is null
                // for a tool name we do not recognise, and an inert card that
                // looks pressable is the defect this codebase keeps removing.
                if (card != null && onCardClick != null) {
                    Spacer(modifier = Modifier.height(11.dp))
                    Surface(
                        onClick = onCardClick,
                        color = LilacMist,
                        contentColor = Ink,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Plum,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = card.title.ifBlank { "Open" },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Ink,
                                )
                                if (card.detail.isNotBlank()) {
                                    Text(
                                        text = card.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkMuted,
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = Plum,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                // Was this any use?
                //
                // Quiet, but on every reply rather than behind a long-press: the
                // point is to hear about an answer that was wrong, and a control
                // people have to discover is one that only the confident use.
                // The global feedback form could not say WHICH reply, which made
                // the most useful reports the hardest ones to file.
                if (fromAira && onRate != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    if (rated) {
                        Text(
                            text = "Thanks",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onRate(true) }) {
                                Text(
                                    "Helpful",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted,
                                )
                            }
                            TextButton(onClick = { onRate(false) }) {
                                Text(
                                    "Not helpful",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted,
                                )
                            }
                        }
                    }
                }
                if (failed) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Not sent",
                            style = MaterialTheme.typography.labelSmall,
                            color = Paper,
                        )
                        if (onRetry != null) {
                            TextButton(onClick = onRetry) {
                                Text("Try again", color = Paper)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Just the clock time. The day is carried by the separator above the run of
 * messages now, so repeating it on every bubble said the same thing five times
 * down a screen — and the two could drift, which is worse than either.
 *
 * Still absolute rather than "3 hours ago": the useful question about a 3am
 * message is which night it was.
 */
private fun formatMessageTime(epochSeconds: Double): String =
    java.time.Instant.ofEpochMilli((epochSeconds * 1000).toLong())
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        // Sentence case, not uppercase.
        //
        // Screen readers pronounce short all-caps strings as initialisms
        // ("D-U-E N-O-W"), and capitals remove the word shapes that make
        // reading fast — which matters most to the people using this app at
        // 3am on no sleep. The letter-spacing keeps the label distinct from
        // body copy without shouting it.
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.08.em),
        color = PlumSoft,
    )
}

@Composable
fun MetricPill(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Ivory,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
            )
            // A blank label draws nothing rather than an empty line — callers
            // that have a group heading don't need a caption per chip.
            if (label.isNotBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class DestinationIcon(
    val active: ImageVector,
    val inactive: ImageVector,
)

private val destinationIcons =
    mapOf(
        MainDestination.Today to DestinationIcon(Icons.Filled.Home, Icons.Outlined.Home),
        MainDestination.Aira to DestinationIcon(Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
        MainDestination.Journey to DestinationIcon(Icons.Filled.Book, Icons.Outlined.Book),
        MainDestination.Learn to DestinationIcon(Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
        MainDestination.Care to DestinationIcon(Icons.Filled.MedicalServices, Icons.Outlined.MedicalServices),
        MainDestination.You to DestinationIcon(Icons.Filled.Person, Icons.Outlined.Person),
    )

/**
 * Aira is composing a reply.
 *
 * This was a ChatBubble containing the literal text "Aira is typing…", styled
 * exactly like a message — so the transcript appeared to contain a message in
 * which Aira announced its own typing, and for a moment you could not tell the
 * placeholder from an answer.
 *
 * Three dots in the bubble's shape instead: recognisable everywhere, and it
 * cannot be mistaken for something said. The description carries the meaning
 * for screen readers, which is the one place words are still the right answer.
 *
 * Holds still when the system's animator scale is zero, like every other motion
 * in the app.
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val animate = animationsEnabled()
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier.semantics { contentDescription = "Aira is typing" },
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            color = Plum,
            contentColor = Paper,
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("A", style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(modifier = Modifier.width(9.dp))
        Surface(
            color = Paper,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp,
                                       bottomStart = 6.dp, bottomEnd = 20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineSoft),
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(3) { index ->
                    val alpha by if (animate) {
                        transition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 600, delayMillis = index * 160),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "dot$index",
                        )
                    } else {
                        remember { mutableStateOf(0.55f) }
                    }
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .alpha(alpha)
                            .background(color = Plum, shape = CircleShape),
                    )
                }
            }
        }
    }
}

/**
 * Five slots, with the conversation raised out of the middle one.
 *
 * Aira is not a peer of the other four. Today, Journey, Care and You are where
 * the results of a conversation are kept; the conversation is the product. As a
 * same-sized tab between them it read as one of several filing cabinets, so it
 * is now a filled circle lifted above the bar — the one thing on the screen that
 * looks pressable from across a room.
 *
 * Two flankers a side is what makes that raise legible. See
 * [bottomBarDestinations] for why three slots and a raised centre was the wrong
 * shape.
 *
 * Raised with an offset inside a Box that reserves the extra height, rather than
 * by letting it overflow. An overflowing child is clipped by the Scaffold slot on
 * some devices and not others, which is a bug that only appears on hardware
 * nobody tested.
 */
@Composable
fun AiraBottomNavigation(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAiraHaptics()
    val lift = 26.dp
    // Read once for the whole bar: someone who has turned animation off at the
    // system level has said so, and should not have to say it per control.
    val motion = animationsEnabled()
    Box(modifier = modifier.fillMaxWidth()) {
        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            containerColor = Paper,
            tonalElevation = 0.dp,
        ) {
            bottomBarDestinations.forEach { destination ->
                if (destination == MainDestination.Aira) {
                    // Its slot stays in the row so the other four keep their
                    // fifths; the button itself is drawn above, in the Box.
                    Spacer(modifier = Modifier.weight(1f))
                    return@forEach
                }
                val selectedItem = selected == destination
                val icon = destinationIcons.getValue(destination)
                NavigationBarItem(
                    selected = selectedItem,
                    onClick = {
                        // Only on an actual change. Re-tapping the tab you are
                        // already on is not a navigation, and buzzing for it
                        // teaches people the feedback means nothing.
                        if (!selectedItem) haptics.select()
                        onSelect(destination)
                    },
                    icon = {
                        // A small spring on the tab you just moved to.
                        //
                        // The bar was a filled icon swapping for an outlined one
                        // with nothing in between, which reads as a screenshot
                        // changing rather than as a thing responding to a touch.
                        // Deliberately slight: this is a place people press
                        // dozens of times a day, and anything with a bounce in it
                        // stops being pleasant by the third press.
                        val scale by animateFloatAsState(
                            targetValue = if (selectedItem && motion) 1.12f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            label = "tab-icon-${destination.name}",
                        )
                        Icon(
                            imageVector = if (selectedItem) icon.active else icon.inactive,
                            contentDescription = null,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale },
                        )
                    },
                    label = {
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = Plum,
                            selectedTextColor = Plum,
                            indicatorColor = LilacMist,
                            unselectedIconColor = InkMuted,
                            unselectedTextColor = InkMuted,
                        ),
                )
            }
        }

        val airaSelected = selected == MainDestination.Aira
        // The centre button is the one control the whole bar is arranged around,
        // and it did not move when pressed — no ripple reads through a filled
        // circle at this size, so a press looked like nothing had happened on the
        // most-pressed control in the app. It now dips under the finger and
        // settles back, and sits marginally larger while it is the current tab.
        val pressSource = remember { MutableInteractionSource() }
        val pressed by pressSource.collectIsPressedAsState()
        val airaScale by animateFloatAsState(
            targetValue = when {
                !motion -> 1f
                pressed -> 0.93f
                airaSelected -> 1.04f
                else -> 1f
            },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            label = "aira-button",
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp),
        ) {
            Surface(
                onClick = {
                    if (!airaSelected) haptics.select()
                    onSelect(MainDestination.Aira)
                },
                shape = CircleShape,
                color = Plum,
                contentColor = Paper,
                // Lifted and shadowed so it reads as sitting on top of the bar
                // rather than punched into it.
                shadowElevation = 8.dp,
                interactionSource = pressSource,
                modifier = Modifier
                    .size(62.dp)
                    .graphicsLayer { scaleX = airaScale; scaleY = airaScale }
                    .semantics {
                        contentDescription = "Aira, the conversation"
                        role = Role.Tab
                        // Qualified: the composable's own `selected` parameter
                        // shadows the semantics property of the same name.
                        this.selected = airaSelected
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (airaSelected) {
                            Icons.Filled.AutoAwesome
                        } else {
                            Icons.Outlined.AutoAwesome
                        },
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Text(
                text = MainDestination.Aira.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (airaSelected) Plum else InkMuted,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // Reserves the height the lifted button needs, so nothing is clipped.
        Spacer(modifier = Modifier.height(lift))
    }
}

@Composable
fun GradientHeroSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .background(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    LilacMist,
                                    Paper,
                                    SageMist.copy(alpha = 0.72f),
                                ),
                        ),
                    shape = RoundedCornerShape(30.dp),
                ).border(
                    width = 1.dp,
                    // Was Color.White at 75%: a highlight that reads as a soft
                    // sheen on the light gradient and as a hard white outline on
                    // the dark one. The palette's own outline works in both,
                    // because it moves with the theme.
                    color = OutlineSoft.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(30.dp),
                ).padding(22.dp),
        content = content,
    )
}

@Composable
fun ToolListRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Plum,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(modifier = Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = InkMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * A short, tinted explanation attached to whatever sits above it — a scope note,
 * a caveat, a reason something is unavailable.
 *
 * Lived privately in ToolSheets until the auth screens needed the same thing.
 * Kept as one component rather than copied, so a wording or spacing change can't
 * apply to only half the places it appears.
 */
@Composable
fun InfoBanner(
    icon: ImageVector,
    text: String,
    color: Color,
    contentColor: Color = SageDeep,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * A care row that can be corrected or removed.
 *
 * Every care kind used to be create-only, so a typo in a doctor's name was
 * permanent and a cancelled appointment sat on Today forever. Editing is limited
 * to the row's main label — the field people actually mistype — with the full
 * form still reachable through the tool sheet.
 *
 * The edit and confirm states *replace* the row rather than sitting beside it.
 * Sharing the line put the field, Save and Cancel in the space left over after
 * the label, which on a 360dp screen pushed Save off the right edge — an editor
 * you can open and cannot commit.
 *
 * Removing asks first. These rows sit close together, the action can't be undone,
 * and one of them is somebody's medication.
 */
@Composable
fun EditableRow(
    label: String,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** What the edit field starts with, when that isn't the row's own label —
     *  a document row is titled by its filename but what you can correct is the
     *  document type, and a box pre-filled with the wrong value invites you to
     *  overwrite the wrong thing. */
    editValue: String = label,
    /** When set, the pencil hands off instead of editing in place. A reminder
     *  has a time and a repeat as well as a name, and renaming it inline would
     *  quietly offer only a third of what needs changing. */
    onEditInstead: (() -> Unit)? = null,
    /** False for a row that exists only on this phone and has not been sent.
     *
     *  Editing or removing it would act on an id the server has never seen, so
     *  the controls are not rendered at all rather than shown and failing. A
     *  disabled-looking button still invites the press; an absent one says the
     *  row is not ready yet, which is the truth. */
    actionsEnabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    var editing by remember(label) { mutableStateOf(false) }
    var confirming by remember(label) { mutableStateOf(false) }
    var value by remember(label) { mutableStateOf(editValue) }

    Row(
        // 48dp floor. Measured on the device, the shorter rows came out at
        // 35dp, which drags their edit and remove buttons under the minimum
        // with them however large the buttons themselves are.
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            editing -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                TextButton(
                    onClick = { onRename(value.trim()); editing = false },
                    enabled = value.isNotBlank(),
                ) { Text("Save", color = Plum) }
                IconButton(onClick = { value = editValue; editing = false }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel editing", tint = InkMuted)
                }
            }

            confirming -> {
                Text(
                    text = "Remove $label?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDelete) { Text("Remove", color = Urgent) }
                TextButton(onClick = { confirming = false }) { Text("Keep", color = Plum) }
            }

            else -> {
                content()
                // 44dp targets around an 18dp glyph. Measured on the device
                // these were 18dp square — the size of the icon itself — well
                // under the 48dp Android asks for, on the pair of controls that
                // sit closest together and where the wrong one deletes.
                // 44dp targets around an 18dp glyph, with the label on the
                // BUTTON rather than the icon.
                //
                // Measured on the device these reported 18dp square — the size
                // of the glyph — because the accessibility node carrying the
                // description was the Icon inside the button, so both the
                // finger target and the node a screen reader aims at were the
                // drawing rather than the control. On the one pair of buttons
                // that sit side by side and where the wrong one deletes.
                if (actionsEnabled) {
                IconButton(
                    onClick = { onEditInstead?.invoke() ?: run { editing = true } },
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { contentDescription = "Edit $label" },
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = InkMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { confirming = true },
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { contentDescription = "Remove $label" },
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = InkMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                }
            }
        }
    }
}
