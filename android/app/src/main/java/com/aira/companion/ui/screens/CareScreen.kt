package com.aira.companion.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aira.companion.model.AiraTool
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.components.ToolListRow
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.SageDeep
import com.aira.companion.ui.theme.SageMist

@Composable
fun CareScreen(
    onOpenTool: (AiraTool) -> Unit,
    onUrgentHelp: () -> Unit,
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
            text = "Care",
            style = MaterialTheme.typography.headlineLarge,
            color = Ink,
        )
        Text(
            text = "Everything for your care, in one private place.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )

        Spacer(modifier = Modifier.height(22.dp))

        AiraCard(containerColor = LilacMist) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .background(Plum, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = Paper,
                    )
                }
                Spacer(modifier = Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel("Next appointment")
                    Text(
                        text = "Tomorrow · 10:30 AM",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                    )
                    Text(
                        text = "Dr. Meera Shah · City Care",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryButton(
                label = "Prepare questions",
                onClick = { onOpenTool(AiraTool.Appointment) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(22.dp))
        SectionLabel("Your care hub")

        ToolListRow(
            icon = Icons.Outlined.Medication,
            title = "Medicines",
            subtitle = "Prenatal vitamin · 8:00 PM",
            onClick = { onOpenTool(AiraTool.Medicines) },
            accent = SageDeep,
        )
        ToolListRow(
            icon = Icons.Outlined.FolderOpen,
            title = "Care Vault",
            subtitle = "Prescriptions, reports and scans",
            onClick = { onOpenTool(AiraTool.CareVault) },
        )
        ToolListRow(
            icon = Icons.Outlined.Description,
            title = "Care plan",
            subtitle = "Week 24 priorities",
            onClick = { onOpenTool(AiraTool.CarePlan) },
        )
        ToolListRow(
            icon = Icons.Outlined.LocalHospital,
            title = "Care team",
            subtitle = "Contacts and preferred hospital",
            onClick = { onOpenTool(AiraTool.Support) },
            accent = SageDeep,
        )

        Spacer(modifier = Modifier.height(18.dp))

        AiraCard(containerColor = SageMist) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = SageDeep,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Encrypted care context",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                    )
                    Text(
                        text = "Aira uses a document only after you approve its extracted details.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        androidx.compose.material3.OutlinedButton(
            onClick = onUrgentHelp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalHospital,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Urgent help")
        }
    }
}
