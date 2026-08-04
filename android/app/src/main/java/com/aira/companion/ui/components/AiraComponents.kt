package com.aira.companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aira.companion.model.MainDestination
import com.aira.companion.ui.theme.AiraPalette
import com.aira.companion.ui.theme.HeroBottom
import com.aira.companion.ui.theme.HeroTop
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.NavActive
import com.aira.companion.ui.theme.NavInk
import com.aira.companion.ui.theme.NavInkMuted
import com.aira.companion.ui.theme.NavPill
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Lilac
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.OutlineSoft
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.PlumSoft
import com.aira.companion.ui.theme.Rose
import com.aira.companion.ui.theme.PlumDeep
import com.aira.companion.ui.theme.Sage
import com.aira.companion.ui.theme.SageDeep
import com.aira.companion.ui.theme.SageMist

/**
 * Dawn move 2 (docs/design/redesign.html): in the light theme surfaces float
 * on a soft plum-tinted shadow instead of a hairline border. The dark theme
 * keeps the border — shadows disappear against near-black.
 */
@Composable
fun Modifier.softSurface(
    shape: Shape,
    elevation: Dp = 8.dp,
): Modifier =
    if (AiraPalette.dark) {
        border(1.dp, OutlineSoft.copy(alpha = 0.8f), shape)
    } else {
        shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = ShadowTint,
            spotColor = ShadowTint,
        )
    }

private val ShadowTint = Color(0xFF5A2B5C)

@Composable
fun BrandOrb(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val size = if (compact) 32.dp else 82.dp
    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            Color(0xFFF9F2FA),
                            Lilac,
                            Color(0xFFD6C1DE),
                        ),
                    center = center,
                    radius = radius,
                ),
            radius = radius,
        )
        drawCircle(
            color = Sage.copy(alpha = 0.34f),
            radius = radius * 0.59f,
            center = center + Offset(radius * 0.08f, -radius * 0.04f),
        )
        drawCircle(
            color = Plum.copy(alpha = 0.88f),
            radius = radius * 0.29f,
            center = center,
        )
        drawCircle(
            color = Paper,
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
        modifier = modifier.height(54.dp),
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
    val shape = RoundedCornerShape(28.dp)
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .softSurface(shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (selected) Modifier else Modifier.softSurface(shape, 4.dp))
                .clickable(role = Role.RadioButton, onClick = onClick),
        color = if (selected) LilacMist else Paper,
        contentColor = Ink,
        shape = shape,
        border =
            if (selected) {
                androidx.compose.foundation.BorderStroke(1.5.dp, Plum.copy(alpha = 0.54f))
            } else {
                null
            },
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
        val bubbleShape =
            RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (fromAira) 6.dp else 20.dp,
                bottomEnd = if (fromAira) 20.dp else 6.dp,
            )
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(0.82f)
                    .then(if (fromAira) Modifier.softSurface(bubbleShape, 4.dp) else Modifier),
            color = if (fromAira) Paper else Plum,
            contentColor = if (fromAira) Ink else Paper,
            shape = bubbleShape,
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = Rose,
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

private data class DestinationIcon(
    val active: ImageVector,
    val inactive: ImageVector,
)

private val destinationIcons =
    mapOf(
        MainDestination.Me to DestinationIcon(Icons.Filled.Person, Icons.Outlined.Person),
        MainDestination.Chat to DestinationIcon(Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
        MainDestination.Videos to DestinationIcon(Icons.Filled.SmartDisplay, Icons.Outlined.SmartDisplay),
    )

@Composable
fun AiraBottomNavigation(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Floating capsule: frosted white in the light theme (Dawn move 4), dark
    // in the dark theme; the active tab is a solid plum icon+label pill.
    // Transparent around it so the page shows through — it reads as one
    // confident control, not a heavy bar.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = NavPill,
            shape = CircleShape,
            shadowElevation = 12.dp,
            border =
                if (AiraPalette.dark) {
                    null
                } else {
                    androidx.compose.foundation.BorderStroke(1.dp, Color.White)
                },
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MainDestination.entries.forEach { destination ->
                    val active = selected == destination
                    Surface(
                        onClick = { onSelect(destination) },
                        color = if (active) NavActive else Color.Transparent,
                        contentColor = if (active) NavInk else NavInkMuted,
                        shape = CircleShape,
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = if (active) 17.dp else 15.dp,
                                vertical = 12.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            if (active) {
                                Icon(
                                    imageVector = destinationIcons.getValue(destination).active,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
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
                            colors = listOf(HeroTop, HeroBottom),
                        ),
                    shape = RoundedCornerShape(28.dp),
                ).padding(horizontal = 22.dp, vertical = 24.dp),
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

@Composable
fun ChoiceChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Surface(
                color = if (active) Plum else Paper,
                contentColor = if (active) Paper else Ink,
                shape = CircleShape,
                border =
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (active) Plum else OutlineSoft,
                    ),
                onClick = { onSelect(option) },
            ) {
                Text(
                    text = option,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

