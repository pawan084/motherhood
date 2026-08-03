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
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aira.companion.model.AiraTool
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.theme.AmberMist
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.SageDeep
import com.aira.companion.ui.theme.SageMist

@Composable
fun JourneyScreen(
    onOpenTool: (AiraTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Ivory)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 28.dp),
    ) {
        Text(
            text = "Your journey",
            style = MaterialTheme.typography.headlineLarge,
            color = Ink,
        )
        Text(
            text = "Week 24 · A quieter view of what matters now.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )

        Spacer(modifier = Modifier.height(22.dp))

        AiraCard(containerColor = LilacMist) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    color = Plum,
                    contentColor = Paper,
                    shape = CircleShape,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("24", style = MaterialTheme.typography.titleLarge)
                            Text("weeks", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel("This week")
                    Text(
                        text = "The size of an ear of corn",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                    )
                    Text(
                        text = "Growth is steady. Rest and hydration remain useful priorities.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))
        SectionLabel("Explore gently")
        Spacer(modifier = Modifier.height(9.dp))

        JourneyCard(
            icon = Icons.Outlined.PersonOutline,
            title = "Your body this week",
            body = "Energy shifts, skin changes and common discomforts—without alarmist language.",
            color = SageMist,
            iconColor = SageDeep,
            onClick = { onOpenTool(AiraTool.CarePlan) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        JourneyCard(
            icon = Icons.Outlined.FavoriteBorder,
            title = "Your baby this week",
            body = "A gentle development story, with clear boundaries between education and medical advice.",
            color = LilacMist,
            iconColor = Plum,
            onClick = { onOpenTool(AiraTool.Companion) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        JourneyCard(
            icon = Icons.Outlined.Checklist,
            title = "Prepare for your visit",
            body = "Review notes and build three useful questions for your care professional.",
            color = AmberMist,
            iconColor = Plum,
            onClick = { onOpenTool(AiraTool.Appointment) },
        )

        Spacer(modifier = Modifier.height(22.dp))

        AiraCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = Plum,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Next milestone · Glucose screening",
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    "Aira can help you understand what to expect and prepare questions. " +
                        "Your care team decides what testing is right for you.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
            Spacer(modifier = Modifier.height(15.dp))
            PrimaryButton(
                label = "Ask Aira about this",
                onClick = { onOpenTool(AiraTool.Appointment) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = Icons.Outlined.ArrowForward,
            )
        }
    }
}

@Composable
private fun JourneyCard(
    icon: ImageVector,
    title: String,
    body: String,
    color: Color,
    iconColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Paper,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .background(color, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconColor)
            }
            Spacer(modifier = Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(body, style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
            Icon(
                imageVector = Icons.Outlined.ArrowForward,
                contentDescription = null,
                tint = InkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
