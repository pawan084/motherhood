package com.aira.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aira.companion.data.CareItem
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.BackButton
import com.aira.companion.ui.components.EmptyState
import com.aira.companion.ui.components.Eyebrow
import com.aira.companion.ui.components.MoodWeek
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.StatusNote
import com.aira.companion.ui.components.moodStyle
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.OutlineSoft
import com.aira.companion.ui.theme.Plum
import java.time.LocalDate

/**
 * Your moods — the week, the month's shape, and what that does and doesn't mean.
 *
 * Everything here is counted from the care timeline. Nothing is smoothed,
 * averaged or scored: this screen shows someone their own feelings back, and a
 * derived "wellness number" would be the app forming an opinion about a person
 * from six buttons.
 */
@Composable
fun MoodsDetailScreen(
    timeline: List<CareItem>,
    onBack: () -> Unit,
    onLogToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val week = remember(timeline) { MoodWeek.lastDays(timeline, today) }
    val labels = remember(timeline) { MoodWeek.dayInitials(today) }
    val breakdown = remember(timeline) { MoodWeek.breakdown(timeline, today) }
    val loggedDays = remember(timeline) { MoodWeek.loggedDayCount(timeline, today) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 108.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onClick = onBack)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Your moods",
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        Eyebrow(text = "This week")
        Spacer(modifier = Modifier.height(8.dp))
        AiraCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                week.forEachIndexed { index, key ->
                    val isToday = index == week.lastIndex
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = labels.getOrElse(index) { "" },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) Plum else InkMuted,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = Ivory,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isToday) 2.dp else 1.dp,
                                color = if (isToday) Plum else OutlineSoft,
                            ),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (key != null) {
                                    val style = moodStyle(key)
                                    Icon(
                                        imageVector = style.icon,
                                        contentDescription = style.label,
                                        tint = style.color,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Eyebrow(text = "Last 30 days")
        Spacer(modifier = Modifier.height(8.dp))

        if (breakdown.isEmpty()) {
            AiraCard {
                EmptyState(
                    icon = Icons.Outlined.Info,
                    title = "Nothing logged yet",
                    body = "Check in once and this fills in. One tap is enough — " +
                        "there's no streak to keep and nothing to catch up on.",
                )
            }
        } else {
            val most = breakdown.first().count
            AiraCard {
                breakdown.forEachIndexed { index, entry ->
                    if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                    val style = moodStyle(entry.key)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription =
                                    "${entry.label}: ${entry.count} " +
                                        if (entry.count == 1) "day" else "days"
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = style.icon,
                            contentDescription = null,
                            tint = style.color,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = entry.label,
                            modifier = Modifier.width(64.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Ink,
                        )
                        // The bar is relative to the commonest mood, not to the
                        // 30-day window: with four check-ins in a month every
                        // bar against 30 would be a sliver, and the shape of the
                        // month is the thing this row is for.
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(14.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(entry.count.toFloat() / most.toFloat())
                                    .height(14.dp)
                                    .background(
                                        style.color.copy(alpha = 0.75f),
                                        RoundedCornerShape(6.dp),
                                    ),
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = entry.count.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "$loggedDays ${if (loggedDays == 1) "day" else "days"} " +
                        "logged in the last 30",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        // The reference's own line, and the most important sentence here. A
        // sparse month is a record of logging, not of feeling — and someone
        // scanning their own chart at 3am should not read a gap as a bad day.
        StatusNote(
            text = "Empty days mean “not logged”, not a low mood. This is a record of " +
                "what you told Aira, nothing more.",
            icon = Icons.Outlined.Info,
        )

        Spacer(modifier = Modifier.height(16.dp))
        PrimaryButton(
            label = "Log today's mood",
            onClick = onLogToday,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
