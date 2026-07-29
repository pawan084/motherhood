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
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.aira.companion.data.CareData
import com.aira.companion.data.CareItem
import com.aira.companion.model.AiraTool
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.EditableRow
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
    // Check-ins and symptom logs, and the Care Vault's actual contents. Both
    // were written and never read back: the tools said "Added to your timeline"
    // and the Vault reported a count with no way to see what was counted.
    timeline: List<CareItem> = emptyList(),
    documents: List<CareItem> = emptyList(),
    onReminderDone: (String, Boolean) -> Unit = { _, _ -> },
    // (id, field, value) — the field differs per kind, so the caller picks it.
    onRename: (String, String, String) -> Unit = { _, _, _ -> },
    onDelete: (String) -> Unit = {},
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
            if (nextAppointment != null) {
                EditableRow(
                    label = nextAppointment.title,
                    onRename = { onRename(nextAppointment.id, "doctor", it) },
                    onDelete = { onDelete(nextAppointment.id) },
                ) {
                    Spacer(modifier = Modifier.weight(1f))
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
                EditableRow(
                    label = med.title,
                    onRename = { onRename(med.id, "name", it) },
                    onDelete = { onDelete(med.id) },
                    modifier = Modifier.padding(vertical = 9.dp),
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

        // Reminders, with the way to create one.
        //
        // Reminders could only be added from the chat tool tray, so the Care
        // screen listed a "care plan" total for something the screen itself
        // gave no way to add to. The list was invisible here too — only its
        // count reached this screen.
        Spacer(modifier = Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Reminders", modifier = Modifier.weight(1f))
            TextButton(onClick = { onOpenTool(AiraTool.Reminder) }) { Text("Add", color = Plum) }
        }
        if (reminders.isEmpty()) {
            Text(
                text = if (loading && care == null) "Loading…" else "Nothing to remember yet.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        } else {
            reminders.forEach { rem ->
                EditableRow(
                    label = rem.title,
                    onRename = { onRename(rem.id, "title", it) },
                    onDelete = { onDelete(rem.id) },
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = rem.done,
                        onCheckedChange = { onReminderDone(rem.id, it) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rem.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (rem.done) InkMuted else Ink,
                            textDecoration = if (rem.done) TextDecoration.LineThrough else null,
                        )
                        if (rem.subtitle.isNotBlank()) {
                            Text(
                                rem.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))
        SectionLabel("Everything else")

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
        // The files themselves. The Vault reported "3 documents stored
        // privately" and then offered no way to see which three — for a folder
        // holding someone's scans and prescriptions, a count is not a listing.
        documents.forEach { doc ->
            EditableRow(
                label = doc.title,
                // A document's filename is fixed at upload; what a person can
                // correct is what kind of document they said it was.
                editValue = doc.subtitle.ifBlank { "Other" },
                onRename = { onRename(doc.id, "type", it) },
                onDelete = { onDelete(doc.id) },
                modifier = Modifier.padding(start = 46.dp, top = 2.dp, bottom = 2.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(doc.title, style = MaterialTheme.typography.bodyMedium, color = Ink)
                    if (doc.subtitle.isNotBlank()) {
                        Text(
                            doc.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                }
            }
        }
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

        // The timeline.
        //
        // The check-in and symptom tools both closed with "Added to your
        // timeline", and no timeline existed on any screen. Someone tracking a
        // symptom across a week — the reason to log one at all — could not see
        // what they had logged, in the app that asked them to log it.
        Spacer(modifier = Modifier.height(22.dp))
        SectionLabel("Your timeline")
        if (timeline.isEmpty()) {
            Text(
                text = "Check-ins and symptoms you log will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        } else {
            timeline.forEach { entry ->
                EditableRow(
                    label = entry.title,
                    onRename = {
                        onRename(entry.id, if (entry.kind == "symptom") "what" else "feeling", it)
                    },
                    onDelete = { onDelete(entry.id) },
                    modifier = Modifier.padding(vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = if (entry.kind == "symptom") {
                            Icons.Outlined.MonitorHeart
                        } else {
                            Icons.Outlined.Mood
                        },
                        contentDescription = null,
                        tint = SageDeep,
                    )
                    Spacer(modifier = Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                        )
                        if (entry.subtitle.isNotBlank()) {
                            Text(
                                entry.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                            )
                        }
                    }
                }
            }
        }

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
                        text = "Your documents stay private",
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
