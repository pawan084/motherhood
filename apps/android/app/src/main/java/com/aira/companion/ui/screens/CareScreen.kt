package com.aira.companion.ui.screens

import com.aira.companion.ui.theme.LilacMist
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.aira.companion.data.CareData
import com.aira.companion.data.CareItem
import com.aira.companion.model.AiraTool
import androidx.compose.material.icons.outlined.NotificationsOff
import com.aira.companion.ui.components.SecondaryButton
import com.aira.companion.ui.components.TextCta
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.EditableRow
import com.aira.companion.ui.components.dayLabel
import com.aira.companion.ui.components.dayOf
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.components.SkeletonRows
import com.aira.companion.ui.components.ToolListRow
import com.aira.companion.ui.theme.Amber
import com.aira.companion.ui.theme.AmberMist
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.SageDeep
import com.aira.companion.ui.theme.SageMist
import com.aira.companion.ui.theme.Urgent

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
    /** Opens the full reminder sheet rather than an inline rename. */
    onEditReminder: (CareItem) -> Unit = {},
    /** The item a notification pointed at, brought into view and marked once. */
    highlightedItem: String? = null,
    onHighlightShown: () -> Unit = {},
    onOpenDocument: (CareItem) -> Unit = {},
    /** Per-section "we asked and could not get an answer", so an empty list is
     *  never presented as a fact about the user. */
    timelineFailed: Boolean = false,
    documentsFailed: Boolean = false,
    onRetry: (() -> Unit)? = null,
    /** The OS may defer background work on this phone, so a reminder can
     *  arrive after its time. See ReminderScheduler.remindersMayBeDelayed. */
    remindersMayBeDelayed: Boolean = false,
    /** True while today's routine reminders are muted — see AppPrefs. */
    snoozedToday: Boolean = false,
    onSnoozeAll: () -> Unit = {},
    onUnsnooze: () -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
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
    // Ordered by the time of day they happen, not by when they were typed.
    //
    // The server returns newest-created first, which is the order they were
    // added and no order at all to read a day in: an 8am tablet sat below a
    // 10pm one because it was entered second. Reading "what is left today" then
    // means scanning every row instead of running down the list. Anything
    // without a time sorts last — a reminder with no time is a note, and notes
    // do not belong in the middle of a schedule.
    val medicines = care?.medicines.orEmpty().ifEmpty { care?.medicinesDue.orEmpty() }
        .sortedWith(compareBy(nullsLast()) { minutesOfDay(it.subtitle) })
    val reminders = care?.reminders.orEmpty()
        // Done ones drop to the bottom rather than out of sight: seeing what you
        // have already dealt with is part of knowing the day is under control.
        .sortedWith(
            compareBy<CareItem> { it.done }
                .thenBy(nullsLast()) { minutesOfDay(it.subtitle) },
        )
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
            empty = "Nothing booked yet.",
            isEmpty = appointments.isEmpty(),
            loading = stillLoading,
        ) {
            appointments.forEach { appt ->
                CareRow(
                    item = appt,
                    highlighted = highlightedItem == appt.id,
                    onHighlightShown = onHighlightShown,
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
                        highlighted = highlightedItem == appt.id,
                        onHighlightShown = onHighlightShown,
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
            empty = "Nothing added yet.",
            isEmpty = medicines.isEmpty(),
            loading = stillLoading,
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
                    highlighted = highlightedItem == med.id,
                    onHighlightShown = onHighlightShown,
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

        // Said here, not only in the snackbar when one is saved.
        //
        // A snackbar is gone in three seconds and only appears at the moment of
        // saving; somebody who set a reminder last week and has been quietly
        // missing it needs to find the reason where the reminders are. Only
        // shown when there is something to be delayed.
        // Snooze, offered where the reminders are rather than buried in
        // Settings: the moment someone wants this is the moment they are looking
        // at the list that is nagging them.
        if (reminders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            if (snoozedToday) {
                AiraCard(containerColor = LilacMist) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsOff,
                            contentDescription = null,
                            tint = Plum,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reminders snoozed until tomorrow",
                                style = MaterialTheme.typography.titleSmall,
                                color = Ink,
                            )
                            Text(
                                text = "Your tasks are still here and still tickable. " +
                                    "Appointment reminders are unaffected.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    // Reversible in one tap. A mute you cannot lift without
                    // remembering where you set it is an off switch wearing a
                    // different word.
                    SecondaryButton(
                        label = "Turn reminders back on",
                        onClick = onUnsnooze,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    TextCta(label = "Snooze all today", onClick = onSnoozeAll)
                }
            }
        }

        if (reminders.isNotEmpty() && remindersMayBeDelayed) {
            Spacer(modifier = Modifier.height(14.dp))
            AiraCard(containerColor = AmberMist) {
                Text(
                    text = "Reminders may arrive late",
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This phone's battery saving can hold back background " +
                        "work, so a reminder may come through later than its time. " +
                        "Allowing Aira to run in the background fixes it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
                TextButton(onClick = onOpenBatterySettings) {
                    Text("Open battery settings", color = Plum)
                }
            }
        }

        // The count in this heading is what the "Care plan" row used to say
        // from a screen away.
        CareSection(
            title = "Reminders",
            addLabel = "Add",
            onAdd = { onOpenTool(AiraTool.Reminder) },
            empty = "Nothing to remember yet.",
            isEmpty = reminders.isEmpty(),
            loading = stillLoading,
            count = if (reminders.isNotEmpty()) {
                "${reminders.count { it.done }} of ${reminders.size} done"
            } else {
                null
            },
        ) {
            reminders.forEach { rem ->
                // Reminders are the ONLY thing a notification is ever about, and
                // they are the one list here that does not go through CareRow —
                // so the deep link has to be wired at this row too. Missing it
                // meant tapping a reminder opened Care and marked nothing, which
                // is the behaviour the deep link existed to replace.
                val isTarget = highlightedItem == rem.id
                val bring = remember(rem.id) { BringIntoViewRequester() }
                LaunchedEffect(isTarget) {
                    if (isTarget) {
                        bring.bringIntoView()
                        kotlinx.coroutines.delay(2500)
                        onHighlightShown()
                    }
                }
                val mark by animateColorAsState(
                    targetValue = if (isTarget) LilacMist else Color.Transparent,
                    animationSpec = tween(durationMillis = 400),
                    label = "reminder-highlight",
                )
                EditableRow(
                    label = rem.title,
                    onRename = { onRename(rem.id, "title", it) },
                    onDelete = { onDelete(rem.id) },
                    modifier = Modifier
                        .bringIntoViewRequester(bring)
                        .background(mark, RoundedCornerShape(12.dp))
                        .padding(vertical = 2.dp),
                    // The full sheet, because a reminder's time is the field
                    // most worth changing and now the one with consequences —
                    // it decides when the notification arrives.
                    onEditInstead = { onEditReminder(rem) },
                    actionsEnabled = !rem.pending,
                ) {
                    Checkbox(
                        checked = rem.done,
                        onCheckedChange = { onReminderDone(rem.id, it) },
                        // An unsent reminder has no server id, so "done" has
                        // nothing to mark. Disabled rather than failing on tap.
                        enabled = !rem.pending,
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
                        // Reminders draw their own row rather than CareRow, so
                        // the unsent marker has to be repeated here. Found by
                        // adding a reminder with the server down and seeing the
                        // row appear with no marker at all — the build was
                        // clean, and the label existed, just not on this list.
                        if (rem.pending) {
                            Text(
                                "Waiting to send",
                                style = MaterialTheme.typography.labelSmall,
                                color = Amber,
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
            empty = "Prescriptions, reports and scans go here.",
            isEmpty = documents.isEmpty(),
            loading = stillLoading,
            failed = documentsFailed,
            onRetry = onRetry,
            footnote = "A document is only used in an answer after you approve it.",
        ) {
            documents.forEach { doc ->
                CareRow(
                    item = doc,
                    highlighted = highlightedItem == doc.id,
                    onHighlightShown = onHighlightShown,
                    icon = Icons.Outlined.Description,
                    tint = Plum,
                    // The row opens the file. Until the bytes were stored there
                    // was nothing to open, so this list was a set of filenames
                    // next to a promise that the documents were kept.
                    onOpen = { onOpenDocument(doc) },
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
            loading = stillLoading,
            failed = timelineFailed,
            onRetry = onRetry,
            footnote = "Logging is tracking, not diagnosis. Anything that worries " +
                "you is worth taking to your care team rather than waiting for a pattern.",
        ) {
            // Grouped by the day it happened, newest day first.
            //
            // A flat run of "Headache / Tired / Headache" is a list of words. A
            // record is read for its shape — three bad nights in a row, nothing
            // for a fortnight — and that shape is invisible without the days in
            // it. This is also the view someone scrolls through in front of a
            // midwife, where "when" is the first question asked.
            var lastDay: java.time.LocalDate? = null
            timeline.forEach { entry ->
                val day = entry.created?.let { dayOf(it) }
                if (day != null && day != lastDay) {
                    lastDay = day
                    Spacer(modifier = Modifier.height(if (entry === timeline.first()) 2.dp else 12.dp))
                    Text(
                        text = dayLabel(entry.created),
                        style = MaterialTheme.typography.labelSmall,
                        color = Plum,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                CareRow(
                    item = entry,
                    highlighted = highlightedItem == entry.id,
                    onHighlightShown = onHighlightShown,
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

        // The same red as the header control, because it is the same control.
        //
        // This took Material's default outlined colours, so the app's most
        // safety-critical action rendered neutral grey at the foot of Care while
        // the header showed it in red — the bigger target being the quieter one.
        // Someone scrolling to the end of their record because they are worried
        // is exactly who this is for.
        OutlinedButton(
            onClick = onUrgentHelp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Urgent),
            border = androidx.compose.foundation.BorderStroke(1.dp, Urgent),
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
    /** Still arriving. Draws placeholder rows instead of the empty copy, which
     *  would otherwise claim the section is empty before anyone has asked. */
    loading: Boolean = false,
    /** Asked and could not get an answer. Distinct from empty on purpose — see
     *  AiraUiState.timelineFailed. */
    failed: Boolean = false,
    onRetry: (() -> Unit)? = null,
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
    when {
        loading && isEmpty -> SkeletonRows(count = 2, label = title)
        failed && isEmpty -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // Not "nothing here yet". We do not know that.
                text = "Couldn't load this.",
                style = MaterialTheme.typography.bodySmall,
                color = Amber,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("Retry", color = Plum) }
            }
        }
        isEmpty -> Text(
            text = empty,
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
        )
        else -> content()
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
@OptIn(ExperimentalFoundationApi::class)
private fun CareRow(
    item: CareItem,
    icon: ImageVector,
    tint: Color,
    renameField: String,
    onRename: (String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    renameSeed: String? = null,
    onOpen: (() -> Unit)? = null,
    /** True when a notification about this item is what opened the app. */
    highlighted: Boolean = false,
    onHighlightShown: () -> Unit = {},
    trailing: @Composable (() -> Unit)? = null,
) {
    // Scrolled to rather than merely tinted. BringIntoViewRequester works with
    // the plain verticalScroll this screen already uses, so a deep link lands
    // on its own row without restructuring the page into a lazy list.
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(highlighted) {
        if (highlighted) {
            bring.bringIntoView()
            // Marked briefly, then released: it says "this is the one you
            // tapped", which stops being true a moment later.
            kotlinx.coroutines.delay(2500)
            onHighlightShown()
        }
    }
    val markColour by animateColorAsState(
        targetValue = if (highlighted) LilacMist else Color.Transparent,
        animationSpec = tween(durationMillis = 400),
        label = "care-row-highlight",
    )
    EditableRow(
        label = item.title,
        editValue = renameSeed ?: item.title,
        onRename = { onRename(item.id, renameField, it) },
        onDelete = { onDelete(item.id) },
        modifier = Modifier
            .bringIntoViewRequester(bring)
            .background(markColour, RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp),
        // Same reason as the reminder rows: an item that has not reached the
        // server has no id to rename or delete.
        actionsEnabled = !item.pending,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(11.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onOpen != null) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = "Open ${item.title}",
                            onClick = onOpen,
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = Ink)
            if (item.subtitle.isNotBlank()) {
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            // Written with no signal and not yet sent. Said plainly, because a
            // list that mixes sent and unsent items without distinguishing them
            // lets someone believe their care team can already see this.
            if (item.pending) {
                Text(
                    "Waiting to send",
                    style = MaterialTheme.typography.labelSmall,
                    color = Amber,
                    modifier = Modifier.semantics {
                        contentDescription = "${item.title} is saved on this phone " +
                            "and waiting to send"
                    },
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * The time of day in a care item's detail line, as minutes past midnight.
 *
 * The line is what the app already shows — "8:00 PM · Daily" — so this reads
 * the same string the user does rather than a second field that could disagree
 * with it. Null when there is no time in it, which sorts last.
 */
internal fun minutesOfDay(subtitle: String?): Int? {
    val m = Regex("""(\d{1,2}):(\d{2})\s*(AM|PM)?""", RegexOption.IGNORE_CASE)
        .find(subtitle.orEmpty()) ?: return null
    var hour = m.groupValues[1].toIntOrNull() ?: return null
    val minute = m.groupValues[2].toIntOrNull() ?: return null
    val meridiem = m.groupValues[3].uppercase()
    if (hour !in 0..23 || minute !in 0..59) return null
    // 12 AM is midnight and 12 PM is noon — the pair a naive "+12 for PM" gets
    // backwards, which would file a bedtime medicine first thing in the morning.
    if (meridiem == "AM" && hour == 12) hour = 0
    if (meridiem == "PM" && hour != 12) hour += 12
    return hour * 60 + minute
}
