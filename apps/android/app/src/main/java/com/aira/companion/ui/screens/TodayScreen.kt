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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.aira.companion.model.AiraTool
import com.aira.companion.model.MainDestination
import com.aira.companion.model.TodayData
import com.aira.companion.model.journeyLabel
import com.aira.companion.model.toolKeyToTool
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.GradientHeroSurface
import com.aira.companion.ui.components.MetricPill
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Lilac
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.SageDeep
import com.aira.companion.ui.theme.SageMist

@Composable
fun TodayScreen(
    onDestination: (MainDestination) -> Unit,
    onOpenTool: (AiraTool) -> Unit,
    modifier: Modifier = Modifier,
    today: TodayData? = null,
) {
    // Journey-aware fields, falling back to the static demo copy when offline.
    val weeks = today?.weeks
    // No invented name. Onboarding never asks for one, so the fallback greeted
    // every user as "Maya" — a stranger's name on a private health app.
    val name = today?.name?.ifBlank { null }
    val priorities = today?.priorities.orEmpty()
    val headerText = if (weeks != null) "Week $weeks" else journeyLabel(today?.journey)
    val contextLine = today?.contextLine?.ifBlank { null } ?: "Second trimester"
    val action = today?.nextAction
    val actionTitle = action?.title?.ifBlank { null } ?: "Prepare for tomorrow’s appointment"
    // Generic fallback only — the real copy comes from /v1/today. It used to
    // reference "Week 24" and a fatigue note that belonged to nobody.
    val actionDetail = action?.detail?.ifBlank { null }
        ?: "A small step Aira can help you take today."
    val actionMinutes = action?.minutes ?: 3
    val actionTool = toolKeyToTool(action?.tool) ?: AiraTool.Appointment
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Ivory)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(34.dp),
                color = SageMist,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.WbSunny,
                        contentDescription = null,
                        tint = SageDeep,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = headerText,
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (name != null) "Good morning, $name" else "Good morning",
            style = MaterialTheme.typography.bodyLarge,
            color = InkMuted,
        )
        Text(
            text = "You’re on track.",
            style = MaterialTheme.typography.headlineLarge,
            color = Ink,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Nothing urgent needs your attention right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )

        Spacer(modifier = Modifier.height(24.dp))

        GradientHeroSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionLabel("Your current context")
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(72.dp)
                                .background(
                                    brush = Brush.radialGradient(listOf(Lilac, Paper)),
                                    shape = CircleShape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = weeks?.toString() ?: journeyLabel(today?.journey).take(1),
                                style = MaterialTheme.typography.headlineSmall,
                                color = Plum,
                            )
                            Text(
                                text = if (weeks != null) "weeks" else "stage",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contextLine,
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink,
                        )
                        Text(
                            text = "Only what's most useful to know right now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Open Journey",
                        tint = Plum,
                    )
                }
                // The "6h sleep / Steady mood / None new concern" pills that used
                // to sit here were invented readings — nothing in the app had
                // measured any of them. They come back when check-ins are
                // aggregated server-side; until then the user's own priorities
                // are real and worth showing.
                if (priorities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        priorities.take(3).forEach { priority ->
                            MetricPill(
                                value = priority,
                                label = "focus",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(26.dp))
        SectionLabel("One meaningful next action")
        Spacer(modifier = Modifier.height(10.dp))

        AiraCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(46.dp)
                            .background(Lilac, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = Plum,
                    )
                }
                Spacer(modifier = Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Next best action",
                        style = MaterialTheme.typography.labelMedium,
                        color = Plum,
                    )
                    Text(
                        text = "About $actionMinutes min",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
            Spacer(modifier = Modifier.height(15.dp))
            Text(
                text = actionTitle,
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = actionDetail,
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
            Spacer(modifier = Modifier.height(17.dp))
            PrimaryButton(
                label = "Start with Aira",
                onClick = { onOpenTool(actionTool) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = Icons.Outlined.ArrowForward,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SageMist,
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = SageDeep,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "You’re caught up for today.",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                    )
                    Text(
                        text = "Aira will surface something only when it matters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Plum,
            contentColor = Paper,
            shape = RoundedCornerShape(22.dp),
            onClick = { onDestination(MainDestination.Aira) },
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Continue with Aira",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Ask anything, by text or voice",
                        style = MaterialTheme.typography.bodySmall,
                        color = Paper.copy(alpha = 0.72f),
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ArrowForward,
                    contentDescription = null,
                )
            }
        }
    }
}
