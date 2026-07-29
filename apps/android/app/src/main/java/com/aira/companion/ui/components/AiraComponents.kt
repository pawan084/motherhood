package com.aira.companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.aira.companion.model.MainDestination
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
                containerColor = Plum,
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
fun ChatBubble(
    text: String,
    fromAira: Boolean,
    modifier: Modifier = Modifier,
    /** Unix seconds, when known. Rendered under the bubble so a conversation
     *  can be placed against the day it happened — which only started to matter
     *  once history survived a restart and last night's worry sat above this
     *  morning's question. */
    at: Double? = null,
) {
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
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (at != null && at > 0) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = formatMessageTime(at),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (fromAira) InkMuted else Paper.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

/**
 * A message time a person would say out loud: "14:32" for today, "Yesterday
 * 22:10" before that, then the date. Absolute rather than "3 hours ago",
 * because the useful question about a 3am message is which night it was.
 */
private fun formatMessageTime(epochSeconds: Double): String {
    val at = java.time.Instant.ofEpochMilli((epochSeconds * 1000).toLong())
        .atZone(java.time.ZoneId.systemDefault())
    val time = at.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    val today = java.time.LocalDate.now()
    return when (at.toLocalDate()) {
        today -> time
        today.minusDays(1) -> "Yesterday $time"
        else -> at.format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm"))
    }
}

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
        MainDestination.Care to DestinationIcon(Icons.Filled.MedicalServices, Icons.Outlined.MedicalServices),
        MainDestination.You to DestinationIcon(Icons.Filled.Person, Icons.Outlined.Person),
    )

@Composable
fun AiraBottomNavigation(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = Paper,
        tonalElevation = 0.dp,
    ) {
        MainDestination.entries.forEach { destination ->
            val selectedItem = selected == destination
            val icon = destinationIcons.getValue(destination)
            NavigationBarItem(
                selected = selectedItem,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = if (selectedItem) icon.active else icon.inactive,
                        contentDescription = destination.label,
                        modifier = Modifier.size(21.dp),
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
                IconButton(
                    onClick = { editing = true },
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
