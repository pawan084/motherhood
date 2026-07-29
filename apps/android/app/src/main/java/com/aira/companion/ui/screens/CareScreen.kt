package com.aira.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.aira.companion.data.CareData
import com.aira.companion.data.CareItem
import com.aira.companion.model.AiraTool
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.EditableRow
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.components.ToolListRow
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.SageDeep
import com.aira.companion.ui.theme.SageMist

/**
 * Everything the user has recorded, in one shape.
 *
 * This screen used to run four models at once. Appointments were a feature card
 * with a full-width primary button; reminders were an inline list with a small
 * "Add" text link; medicines, documents and the care plan were navigation rows
 * that opened sheets; and the documents themselves hung indented under the Care
 * Vault row, as though they belonged to the row rather than the screen. Four
 * ways of showing the same kind of thing meant an item's appearance told you
 * nothing about what it was or what you could do with it — and the single word
 * "Add" was a hero button in one section and a text link in the next.
 *
 * Every section is now identical: a heading, that section's Add, the items, and
 * an empty state naming what would appear there. Learn one and you have learned
 * all five.
 *
 * The "Care plan" row is gone. It was a derived count of the reminders now
 * listed directly above it — a summary of a list, one row below the list. The
 * count moved into the Reminders heading, where it describes what you're
 * looking at instead of sending you elsewhere to see it.
 */
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
    // Check-ins and symptom logs, and the vault's actual contents. Both were
    // written and never read back: the tools said "Added to your timeline" and
    // the vault reported a count with no way to see what was counted.
    timeline: List<CareItem> = emptyList(),
    documents: List<CareItem> = emptyList(),
    onReminderDone: (String, Boolean) -> Unit = { _, _ -> },
    // (id, field, value) — the field differs per kind, so the caller picks it.
    onRename: (String, String, String) -> Unit = { _, _, _ -> },
    onDelete: (String) -> Unit = {},
) {
    // Upcoming and past, split on a real date rather than guessed from free
    // text. Before appointments carried one, "Friday" was all the app had and
    // no amount of parsing turns that into a day without picking a Friday.
    // Undated appointments count as upcoming: someone who wrote "after the
    // scan" has not had it yet.
    val nowSeconds = System.currentTimeMillis() / 1000.0
    val allAppointments = care?.appointments.orEmpty()
    val appointments = allAppointments.filter { it.at == null || it.at >= nowSeconds }
    val pastAppointments = allAppointments
        .filter { it.at != null && it.at < nowSeconds }
        .sortedByDescending { it.at }
    // Every medicine, not only the outstanding ones. Showing `medicinesDue`
    // alone meant a medicine taken today vanished from the screen until
    // tomorrow — so the list answered "what's left?" but never "what do I
    // take?", and there was no way to see you'd already taken it.
    val medicines = care?.medicines.orEmpty().ifEmpty { care?.medicinesDue.orEmpty() }
    val reminders = care?.reminders.orEmpty()
    val stillLoading = loading && care == null

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
            text = "Everything you've recorded, and everything coming up.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )

        CareSection(
            title = "Appointments",
            addLabel = "Add",
            onAdd = { onOpenTool(AiraTool.Appointment) },
            empty = if (stillLoading) "Loading…" else "Nothing booked yet.",
            isEmpty = appointments.isEmpty(),
        ) {
            appointments.forEach { appt ->
                CareRow(
                    item = appt,
                    icon = Icons.Outlined.CalendarMonth,
                    tint = Plum,
                    renameField = "doctor",
                    onRename = onRename,
                    onDelete = onDelete,
                )
            }
        }

        if (pastAppointments.isNotEmpty()) {
            CareSection(
                title = "Past appointments",
                addLabel = "Add",
                onAdd = { onOpenTool(AiraTool.Appointment) },
                empty = "",
                isEmpty = false,
                count = "${pastAppointments.size}",
            ) {
                pastAppointments.forEach { appt ->
                    CareRow(
                        item = appt,
                        icon = Icons.Outlined.CalendarMonth,
                        tint = InkMuted,
                        renameField = "doctor",
                        onRename = onRename,
                        onDelete = onDelete,
                    )
                }
            }
        }

        CareSection(
            title = "Medicines",
            addLabel = "Add",
            onAdd = { onOpenTool(AiraTool.Medicines) },
            empty = if (stillLoading) "Loading…" else "Nothing added yet.",
            isEmpty = medicines.isEmpty(),
            count = if (medicines.isNotEmpty()) {
                "${medicines.count { it.takenToday }} of ${medicines.size} taken today"
            } else {
                null
            },
            // Aira organises what a care team prescribed; it never decides it.
            footnote = "Aira can organise reminders but never starts, stops or " +
                "changes any medication.",
        ) {
            medicines.forEach { med ->
                CareRow(
                    item = med,
                    icon = Icons.Outlined.Medication,
                    tint = SageDeep,
                    renameField = "name",
                    onRename = onRename,
                    onDelete = onDelete,
                    trailing = {
                        // Taken today reads as a state, not a spent button.
                        // "Take again" stays available because doses get
                        // missed, doubled and re-taken, and an app that refuses
                        // to record what actually happened is worse than one
                        // that trusts the person holding the tablets.
                        if (med.takenToday) {
                            TextButton(onClick = { onMarkTaken(med.id) }) {
                                Text("Taken today", color = SageDeep)
                            }
                        } else {
                            TextButton(onClick = { onMarkTaken(med.id) }) {
                                Text("Mark taken", color = Plum)
                            }
                        }
                    },
                )
            }
        }

        // The count in this heading is what the "Care plan" row used to say
        // from a screen away.
        CareSection(
            title = "Reminders",
            addLabel = "Add",
            onAdd = { onOpenTool(AiraTool.Reminder) },
            empty = if (stillLoading) "Loading…" else "Nothing to remember yet.",
            isEmpty = reminders.isEmpty(),
            count = if (reminders.isNotEmpty()) {
                "${reminders.count { it.done }} of ${reminders.size} done"
            } else {
                null
            },
        ) {
            reminders.forEach { rem ->
                EditableRow(
                    label = rem.title,
                    onRename = { onRename(rem.id, "title", it) },
                    onDelete = { onDelete(rem.id) },
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Checkbox(
                        checked = rem.done,
                        onCheckedChange = { onReminderDone(rem.id, it) },
                        // Without this the box announces as "checkbox, not
                        // ticked" and nothing else: it has its own click
                        // handler, so Compose doesn't fold the row's title into
                        // it, and the one control that marks a medicine done
                        // said nothing about which medicine.
                        modifier = Modifier.semantics {
                            contentDescription = if (rem.done) {
                                "${rem.title}, done"
                            } else {
                                "Mark ${rem.title} done"
                            }
                        },
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

        // Was "Care Vault" — a name only this app uses — with the files
        // indented beneath the row that opened it.
        CareSection(
            title = "Documents",
            addLabel = "Add",
            onAdd = { onOpenTool(AiraTool.CareVault) },
            empty = if (stillLoading) {
                "Loading…"
            } else {
                "Prescriptions, reports and scans go here."
            },
            isEmpty = documents.isEmpty(),
            footnote = "A document is only used in an answer after you approve it.",
        ) {
            documents.forEach { doc ->
                CareRow(
                    item = doc,
                    icon = Icons.Outlined.Description,
                    tint = Plum,
                    // The filename is fixed at upload; what a person can correct
                    // is what kind of document they said it was.
                    renameField = "type",
                    renameSeed = doc.subtitle.ifBlank { "Other" },
                    onRename = onRename,
                    onDelete = onDelete,
                )
            }
        }

        // The check-in and symptom tools both closed with "Added to your
        // timeline", and no timeline existed on any screen. Someone tracking a
        // symptom across a week — the reason to log one at all — could not see
        // what they had logged, in the app that asked them to log it.
        CareSection(
            title = "Your timeline",
            addLabel = "Log",
            onAdd = { onOpenTool(AiraTool.Symptom) },
            empty = "Check-ins and symptoms you log will appear here.",
            isEmpty = timeline.isEmpty(),
            footnote = "Logging is tracking, not diagnosis. Anything that worries " +
                "you is worth taking to your care team rather than waiting for a pattern.",
        ) {
            timeline.forEach { entry ->
                CareRow(
                    item = entry,
                    icon = if (entry.kind == "symptom") {
                        Icons.Outlined.MonitorHeart
                    } else {
                        Icons.Outlined.Mood
                    },
                    tint = SageDeep,
                    renameField = if (entry.kind == "symptom") "what" else "feeling",
                    onRename = onRename,
                    onDelete = onDelete,
                )
            }
        }

        // The one row here that isn't a list of things the user added: their
        // care team's details, which live in the emergency profile.
        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel("Who's looking after you")
        Spacer(modifier = Modifier.height(8.dp))
        ToolListRow(
            icon = Icons.Outlined.LocalHospital,
            title = "Your care team",
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
                        text = "Everything here is private to you",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink,
                    )
                    Text(
                        text = "Nothing on this screen is shared with anyone unless " +
                            "you turn on partner access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedButton(
            onClick = onUrgentHelp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
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

/**
 * One section of the Care screen.
 *
 * Every section takes the same shape, so the layout carries no meaning the
 * content doesn't: heading on the left, this section's Add on the right, then
 * either the items or a sentence saying what would be here.
 */
@Composable
private fun CareSection(
    title: String,
    addLabel: String,
    onAdd: () -> Unit,
    empty: String,
    isEmpty: Boolean,
    count: String? = null,
    footnote: String? = null,
    content: @Composable () -> Unit,
) {
    Spacer(modifier = Modifier.height(24.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(title)
        if (count != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        // Sized to the heading, not above it: at default button typography the
        // word "Add" was larger than the section it belonged to, so the eye
        // landed on the action before the thing being acted on.
        TextButton(onClick = onAdd) {
            Text(
                text = addLabel,
                color = Plum,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    if (isEmpty) {
        Text(
            text = empty,
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
        )
    } else {
        content()
    }
    if (footnote != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = footnote,
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
        )
    }
}

/** A single care item: icon, title, detail, and the edit/remove pair. */
@Composable
private fun CareRow(
    item: CareItem,
    icon: ImageVector,
    tint: Color,
    renameField: String,
    onRename: (String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    renameSeed: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    EditableRow(
        label = item.title,
        editValue = renameSeed ?: item.title,
        onRename = { onRename(item.id, renameField, it) },
        onDelete = { onDelete(item.id) },
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = Ink)
            if (item.subtitle.isNotBlank()) {
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
        }
        trailing?.invoke()
    }
}
