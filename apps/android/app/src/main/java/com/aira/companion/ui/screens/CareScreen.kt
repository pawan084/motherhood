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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aira.companion.data.CareData
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
    // Real data from GET /v1/care. This screen previously rendered a fixed
    // "Dr. Meera Shah · Tomorrow 10:30 AM · Prenatal vitamin" for everyone,
    // inventing an appointment with a named doctor for users who had entered
    // nothing — worse than showing an empty state in a health app.
    care: CareData? = null,
    loading: Boolean = false,
    onMarkTaken: (String) -> Unit = {},
) {
    val nextAppointment = care?.appointments?.firstOrNull()
    val medicinesDue = care?.medicinesDue.orEmpty()
    val reminders = care?.reminders.orEmpty()
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
                    SectionLabel(if (nextAppointment != null) "Next appointment" else "Appointments")
                    Text(
                        text = when {
                            loading && care == null -> "Loading…"
                            nextAppointment != null -> nextAppointment.title
                            else -> "Nothing booked yet"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                    )
                    Text(
                        text = nextAppointment?.subtitle?.ifBlank { "Details not set" }
                            ?: "Add a visit and Aira will help you prepare questions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryButton(
                label = if (nextAppointment != null) "Prepare questions" else "Add an appointment",
                onClick = { onOpenTool(AiraTool.Appointment) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (medicinesDue.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("Due now")
            medicinesDue.forEach { med ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Medication,
                        contentDescription = null,
                        tint = SageDeep,
                    )
                    Spacer(modifier = Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(med.title, style = MaterialTheme.typography.titleSmall, color = Ink)
                        if (med.subtitle.isNotBlank()) {
                            Text(
                                med.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                            )
                        }
                    }
                    TextButton(onClick = { onMarkTaken(med.id) }) { Text("Taken", color = Plum) }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))
        SectionLabel("Your care hub")

        ToolListRow(
            icon = Icons.Outlined.Medication,
            title = "Medicines",
            subtitle = medicinesDue.firstOrNull()
                ?.let { m -> listOf(m.title, m.subtitle).filter { it.isNotBlank() }.joinToString(" · ") }
                ?: "None due — add a routine you were given",
            onClick = { onOpenTool(AiraTool.Medicines) },
            accent = SageDeep,
        )
        ToolListRow(
            icon = Icons.Outlined.FolderOpen,
            title = "Care Vault",
            subtitle = (care?.documentsCount ?: 0).let { n ->
                if (n > 0) "$n document${if (n == 1) "" else "s"} stored privately"
                else "Prescriptions, reports and scans"
            },
            onClick = { onOpenTool(AiraTool.CareVault) },
        )
        ToolListRow(
            icon = Icons.Outlined.Description,
            title = "Care plan",
            subtitle = if ((care?.planTotal ?: 0) > 0) {
                "${care?.planOnTrack ?: 0} of ${care?.planTotal ?: 0} reminders on track"
            } else if (reminders.isEmpty()) {
                "No reminders yet"
            } else {
                "Your priorities this week"
            },
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
