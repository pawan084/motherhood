@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.aira.companion.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MedicalInformation
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aira.companion.data.CareData
import com.aira.companion.data.CareItem
import com.aira.companion.data.ConsentFeature
import com.aira.companion.data.MemoryItem
import com.aira.companion.data.PartnerInvite
import com.aira.companion.data.PartnerInviteRow
import com.aira.companion.data.PartnerShare
import com.aira.companion.data.VoicePrefs
import com.aira.companion.model.AiraTool
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.BrandOrb
import com.aira.companion.ui.components.ChoiceCard
import com.aira.companion.ui.components.InfoBanner
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.theme.Amber
import com.aira.companion.ui.theme.AmberMist
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Lilac
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.OutlineSoft
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.PlumSoft
import com.aira.companion.ui.theme.SageDeep
import com.aira.companion.ui.theme.SageMist
import com.aira.companion.ui.theme.Urgent
import com.aira.companion.ui.theme.UrgentMist
import kotlinx.coroutines.delay

private data class ChatToolItem(
    val tool: AiraTool,
    val label: String,
    val detail: String,
    val icon: ImageVector,
)

// The tray used to show six of the app's tools with no way to reach the rest —
// no "more", no scroll cue, nothing to say the list was partial. Everything a
// user can actually do from here is now here, in the order you'd reach for it:
// the things you add most often first, the reference and admin ones after.
//
// "Companion — Voice or avatar mode" is not in the list: it offered a choice
// between two things that don't exist, from the tray of tools that do.
private val chatToolItems =
    listOf(
        ChatToolItem(AiraTool.CheckIn, "Check in", "Mood, energy & sleep", Icons.Outlined.FavoriteBorder),
        ChatToolItem(AiraTool.Reminder, "Reminder", "Medicine or care task", Icons.Outlined.AccessTime),
        ChatToolItem(AiraTool.Symptom, "Track", "Log a change", Icons.Outlined.TrackChanges),
        ChatToolItem(AiraTool.Medicines, "Medicines", "Your routine", Icons.Outlined.Medication),
        ChatToolItem(AiraTool.Appointment, "Appointment", "Prepare for a visit", Icons.Outlined.CalendarMonth),
        ChatToolItem(AiraTool.CareVault, "Documents", "Prescription or report", Icons.Outlined.FolderOpen),
        ChatToolItem(AiraTool.Reset, "Reset", "Two calm minutes", Icons.Outlined.Waves),
        ChatToolItem(AiraTool.CarePlan, "Your plan", "Built from your reminders", Icons.Outlined.Description),
        ChatToolItem(AiraTool.Memory, "What Aira knows", "Review or forget it", Icons.Outlined.Memory),
        ChatToolItem(AiraTool.Partner, "Partner access", "Sharing with someone", Icons.Outlined.Group),
        ChatToolItem(AiraTool.Emergency, "Emergency profile", "Care team and contact", Icons.Outlined.Security),
        ChatToolItem(AiraTool.Support, "Help", "Questions and feedback", Icons.Outlined.SupportAgent),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolTraySheet(
    onDismiss: () -> Unit,
    onOpenTool: (AiraTool) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ivory,
        dragHandle = null,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel("Add to your care")
                    Text(
                        text = "What would help now?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ink,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close tools")
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                chatToolItems.forEach { item ->
                    Surface(
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = 112.dp)
                                .clickable(role = Role.Button) { onOpenTool(item.tool) },
                        color = Paper,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineSoft),
                    ) {
                        Column(modifier = Modifier.padding(15.dp)) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(38.dp)
                                        .background(LilacMist, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = null,
                                    tint = Plum,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(11.dp))
                            Text(item.label, style = MaterialTheme.typography.titleSmall, color = Ink)
                            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The backend writes a tool sheet can perform.
 *
 * Every sheet used to end at `onNotify("Saved")` and persist nothing — a
 * reminder, medicine, appointment, check-in or symptom log vanished the moment
 * the sheet closed. The ViewModel supplies these, so the sheets stay free of
 * Context and networking.
 */
data class ToolActions(
    val saveReminder: (title: String, time: String, repeat: String) -> Unit = { _, _, _ -> },
    val saveMedicine: (name: String, dose: String, time: String) -> Unit = { _, _, _ -> },
    val saveAppointment: (doctor: String, place: String, whenText: String, at: Long?) -> Unit =
        { _, _, _, _ -> },
    val updateReminder: (id: String, title: String, time: String, repeat: String) -> Unit =
        { _, _, _, _ -> },
    val saveCheckIn: (feeling: String, sleepHours: Double, note: String) -> Unit = { _, _, _ -> },
    val saveSymptom: (what: String, severity: String, started: String) -> Unit = { _, _, _ -> },
    val markMedicineTaken: (id: String) -> Unit = {},
    val setReminderDone: (id: String, done: Boolean) -> Unit = { _, _ -> },
    val saveEmergencyProfile: (Map<String, String>) -> Unit = {},
    val loadEmergencyProfile: () -> Unit = {},
    val sendReport: (kind: String, message: String) -> Unit = { _, _ -> },
    val setConsent: (feature: String, granted: Boolean) -> Unit = { _, _ -> },
    val setMemoryApproved: (id: String, approved: Boolean) -> Unit = { _, _ -> },
    val forgetMemory: (id: String) -> Unit = {},
    val loadMemory: () -> Unit = {},
    val loadConsent: () -> Unit = {},
    val uploadDocument: (uri: Uri, kind: String) -> Unit = { _, _ -> },
    val setVoice: (voice: String) -> Unit = {},
    val loadPrefs: () -> Unit = {},
    val createPartnerInvite:
        (appointments: Boolean, reminders: Boolean, healthDetails: Boolean) -> Unit =
        { _, _, _ -> },
    val clearPartnerInvite: () -> Unit = {},
    val sharePartnerInvite: (text: String) -> Unit = {},
    val loadPartner: () -> Unit = {},
    val revokePartnerInvite: (id: String) -> Unit = {},
    val acceptPartnerInvite: (code: String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicToolSheet(
    tool: AiraTool,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit,
    onUrgentHelp: () -> Unit,
    actions: ToolActions = ToolActions(),
    care: CareData? = null,
    memory: List<MemoryItem> = emptyList(),
    consent: List<ConsentFeature> = emptyList(),
    voicePrefs: VoicePrefs = VoicePrefs(),
    partnerInvite: PartnerInvite? = null,
    partnerInvites: List<PartnerInviteRow> = emptyList(),
    partnerShared: List<PartnerShare> = emptyList(),
    uploading: Boolean = false,
    editingReminder: CareItem? = null,
    emergencyProfile: Map<String, String>? = null,
) {
    // The picked document's Uri is KEPT now. It used to be dropped on the floor
    // here ("Document selected securely."), which is why "Save to Care Vault"
    // could only ever be a toast.
    var pickedDocument by remember(tool) { mutableStateOf<Uri?>(null) }
    val documentLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> if (uri != null) pickedDocument = uri }

    // Photographing a report, which is how most of them arrive.
    //
    // The card has said "Choose or scan a document" all along and there was no
    // scan — only a file picker, which cannot photograph the piece of paper a
    // clinic just handed you. Getting it in meant leaving Aira, opening the
    // camera, coming back and hunting for the file.
    //
    // The capture is written into the same cache/documents directory the
    // FileProvider already scopes, so nothing widens what this app can hand
    // out. No CAMERA permission is declared, and none is needed: the system
    // camera takes the picture and returns it: Aira never opens the lens.
    val context = LocalContext.current
    var cameraTarget by remember(tool) { mutableStateOf<Uri?>(null) }
    val cameraLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
        ) { saved -> if (saved) pickedDocument = cameraTarget else cameraTarget = null }

    // The self/partner photo pickers that used to live here are gone with the
    // future-baby story's picker UI — see CompanionTool.

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ivory,
        dragHandle = null,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 740.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp, bottom = 34.dp),
        ) {
            ToolHeader(
                tool = tool,
                onDismiss = onDismiss,
                // The same sheet does both jobs, so it has to say which one it
                // is doing. "Create a reminder" over a prefilled form invites
                // you to think you're making a second one.
                titleOverride = if (tool == AiraTool.Reminder && editingReminder != null) {
                    "Edit reminder"
                } else {
                    null
                },
            )
            Spacer(modifier = Modifier.height(20.dp))

            when (tool) {
                AiraTool.Notifications -> NotificationsTool(care)
                AiraTool.CheckIn -> CheckInTool(actions, onDismiss)
                AiraTool.Reminder -> ReminderTool(actions, onDismiss, editingReminder)
                AiraTool.Medicines -> MedicinesTool(actions, care, onDismiss)
                AiraTool.Appointment -> AppointmentTool(actions, care, onDismiss)
                AiraTool.CareVault ->
                    CareVaultTool(
                        picked = pickedDocument,
                        uploading = uploading,
                        onPickDocument = {
                            documentLauncher.launch(
                                arrayOf("application/pdf", "image/jpeg", "image/png"),
                            )
                        },
                        onPhotograph = {
                            val dir = java.io.File(context.cacheDir, "documents").apply { mkdirs() }
                            val file = java.io.File(dir, "photo-${System.currentTimeMillis()}.jpg")
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.files", file,
                            )
                            cameraTarget = uri
                            cameraLauncher.launch(uri)
                        },
                        onUpload = actions.uploadDocument,
                    )
                AiraTool.Reset -> ResetTool()
                AiraTool.Symptom -> SymptomTool(actions, onUrgentHelp, onDismiss)
                AiraTool.Companion -> CompanionTool()
                AiraTool.CarePlan -> CarePlanTool(care, actions)
                AiraTool.Privacy -> PrivacyTool(actions, consent)
                AiraTool.Memory -> MemoryTool(actions, memory)
                AiraTool.Voice -> VoiceTool(voicePrefs, actions)
                AiraTool.Partner ->
                    PartnerTool(partnerInvite, partnerInvites, partnerShared, consent, actions)
                AiraTool.Support -> SupportTool(actions, onUrgentHelp, onDismiss)
                AiraTool.Emergency ->
                    EmergencyProfileTool(actions, emergencyProfile, onDismiss)
            }
        }
    }
}

@Composable
private fun ToolHeader(
    tool: AiraTool,
    onDismiss: () -> Unit,
    titleOverride: String? = null,
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            SectionLabel(tool.eyebrow)
            Text(
                text = titleOverride ?: tool.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Close ${tool.title}")
        }
    }
}

@Composable
private fun NotificationsTool(care: CareData?) {
    // Derived from real state, not a fixed list. This used to announce an
    // appointment with Dr. Meera Shah, a prenatal vitamin and a "Week 24 guide"
    // to every user regardless of what they had entered.
    val items = buildList {
        care?.appointments?.forEach {
            add(Triple(it.subtitle.ifBlank { "Appointment" }, it.title, Icons.Outlined.CalendarMonth))
        }
        care?.medicinesDue?.forEach {
            add(Triple(it.subtitle.ifBlank { "Due" }, "${it.title} is due", Icons.Outlined.Medication))
        }
        care?.reminders?.filterNot { it.done }?.forEach {
            add(Triple(it.subtitle.ifBlank { "Reminder" }, it.title, Icons.Outlined.AccessTime))
        }
    }

    if (items.isEmpty()) {
        InfoBanner(
            Icons.Outlined.CheckCircle,
            "You're caught up. Aira surfaces something here only when it genuinely matters.",
            SageMist,
        )
        return
    }

    items.forEachIndexed { index, (time, title, icon) ->
        AiraCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(42.dp)
                            .background(if (index == 0) LilacMist else SageMist, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = if (index == 0) Plum else SageDeep)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(time, style = MaterialTheme.typography.labelSmall, color = PlumSoft)
                    Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
                }
            }
        }
        Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun CheckInTool(actions: ToolActions, onDismiss: () -> Unit) {
    var mood by remember { mutableStateOf("Steady") }
    var energy by remember { mutableStateOf("Medium") }
    var note by remember { mutableStateOf("") }
    // Rough hours, so the check-in carries something quantitative the backend
    // can keep alongside the mood word.
    val sleepForEnergy = mapOf("Low" to 4.0, "Medium" to 7.0, "High" to 9.0)
    Text("How are you feeling right now?", style = MaterialTheme.typography.titleMedium, color = Ink)
    Spacer(Modifier.height(12.dp))
    ChoiceChips(
        options = listOf("Good", "Steady", "Low", "Anxious"),
        selected = mood,
        onSelect = { mood = it },
    )
    Spacer(Modifier.height(18.dp))
    Text("Energy", style = MaterialTheme.typography.titleSmall, color = Ink)
    Spacer(Modifier.height(8.dp))
    ChoiceChips(
        options = listOf("Low", "Medium", "High"),
        selected = energy,
        onSelect = { energy = it },
    )
    Spacer(Modifier.height(18.dp))
    OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Anything you want Aira to know?") },
        minLines = 3,
        shape = RoundedCornerShape(17.dp),
    )
    Spacer(Modifier.height(18.dp))
    PrimaryButton(
        label = "Save check-in",
        onClick = {
            actions.saveCheckIn(mood, sleepForEnergy[energy] ?: 7.0, note)
            onDismiss()
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReminderTool(
    actions: ToolActions,
    onDismiss: () -> Unit,
    /** The reminder being changed, or null when creating one. Same sheet for
     *  both: editing a reminder asks exactly the questions creating one does,
     *  and a second screen that asked them differently would be a second place
     *  to get them wrong. */
    editing: CareItem? = null,
) {
    // Empty, not "Prenatal vitamin" — that prefilled a pregnancy supplement for
    // every user, including postpartum and trying-to-conceive.
    var title by remember(editing?.id) { mutableStateOf(editing?.title.orEmpty()) }
    var time by remember(editing?.id) { mutableStateOf(editing?.time ?: "8:00 PM") }
    var repeat by remember(editing?.id) {
        mutableStateOf(editing?.repeat?.equals("Daily", ignoreCase = true) ?: true)
    }

    OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Remind me to…") },
        leadingIcon = { Icon(Icons.Outlined.Medication, null) },
        shape = RoundedCornerShape(17.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text("Time", style = MaterialTheme.typography.titleSmall, color = Ink)
    Spacer(Modifier.height(8.dp))
    ChoiceChips(
        // The stored time is included even when it isn't one of the presets, so
        // opening an edit sheet can't silently move a reminder to 8pm just
        // because the original time wasn't on the list.
        options = (listOf("8:00 AM", "2:00 PM", "8:00 PM") + time).distinct(),
        selected = time,
        onSelect = { time = it },
    )
    Spacer(Modifier.height(14.dp))
    SettingLine(
        title = "Repeat daily",
        subtitle = "Aira will ask before changing this routine",
        checked = repeat,
        onCheckedChange = { repeat = it },
    )
    Spacer(Modifier.height(18.dp))
    PrimaryButton(
        label = if (editing != null) "Save changes" else "Create reminder",
        onClick = {
            val repeatText = if (repeat) "Daily" else "Once"
            if (editing != null) {
                actions.updateReminder(editing.id, title.trim(), time, repeatText)
            } else {
                actions.saveReminder(title.trim(), time, repeatText)
            }
            onDismiss()
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = title.isNotBlank(),
    )
}

@Composable
private fun MedicinesTool(actions: ToolActions, care: CareData?, onDismiss: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("8:00 PM") }
    val due = care?.medicinesDue.orEmpty()

    if (due.isEmpty()) {
        Text(
            "Nothing due right now.",
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
        )
        Text(
            "Add a routine your care team has already given you, and Aira will remind you.",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
        )
    } else {
        due.forEach { med ->
            AiraCard(containerColor = LilacMist) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(46.dp)
                                .background(Plum, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Medication, null, tint = Paper)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(med.title, style = MaterialTheme.typography.titleMedium, color = Ink)
                        if (med.subtitle.isNotBlank()) {
                            Text(med.subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    label = "Mark as taken",
                    onClick = { actions.markMedicineTaken(med.id); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = Icons.Filled.Check,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    Spacer(Modifier.height(12.dp))
    InfoBanner(
        icon = Icons.Outlined.HealthAndSafety,
        text = "Aira can remind and organise, but never starts, stops or changes medication.",
        color = SageMist,
    )
    Spacer(Modifier.height(12.dp))

    if (!adding) {
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add medicine")
        }
    } else {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Medicine") },
            shape = RoundedCornerShape(17.dp),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = dose,
            onValueChange = { dose = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Dose (optional)") },
            shape = RoundedCornerShape(17.dp),
        )
        Spacer(Modifier.height(12.dp))
        ChoiceChips(
            options = listOf("8:00 AM", "2:00 PM", "8:00 PM"),
            selected = time,
            onSelect = { time = it },
        )
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            label = "Save medicine",
            onClick = { actions.saveMedicine(name.trim(), dose.trim(), time); onDismiss() },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank(),
        )
    }
}

@Composable
private fun AppointmentTool(actions: ToolActions, care: CareData?, onDismiss: () -> Unit) {
    val next = care?.appointments?.firstOrNull()
    var doctor by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var whenText by remember { mutableStateOf("") }
    // The date as a real moment, when the person is willing to pick one.
    // Nullable throughout: "sometime next week" is a real answer, and refusing
    // to record the appointment until they commit to a day is how a care app
    // ends up empty.
    var at by remember { mutableStateOf<Long?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    val checked = remember { mutableStateListOf(false, false, false) }
    val questions =
        listOf(
            "Do I need any tests before the next visit?",
            "What changes should I expect next?",
            "What's worth calling you about rather than waiting?",
        )

    if (next != null) {
        AiraCard(containerColor = LilacMist) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = Plum)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(next.title, style = MaterialTheme.typography.titleSmall, color = Ink)
                    if (next.subtitle.isNotBlank()) {
                        Text(next.subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("Questions worth asking")
        Spacer(Modifier.height(8.dp))
        questions.forEachIndexed { index, question ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { checked[index] = !checked[index] }
                        .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked[index], onCheckedChange = { checked[index] = it })
                Text(question, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("Or add another visit")
        Spacer(Modifier.height(8.dp))
    } else {
        Text(
            "No appointments yet.",
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
        )
        Text(
            "Add one and Aira will help you prepare questions for it.",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
        )
        Spacer(Modifier.height(14.dp))
    }

    OutlinedTextField(
        value = doctor,
        onValueChange = { doctor = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Who are you seeing?") },
        shape = RoundedCornerShape(17.dp),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = place,
        onValueChange = { place = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Where (optional)") },
        shape = RoundedCornerShape(17.dp),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedButton(
        onClick = { pickingDate = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
    ) {
        Icon(Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(at?.let { "On ${formatAppointmentDate(it)}" } ?: "Pick a date (optional)")
    }
    if (at != null) {
        TextButton(onClick = { at = null }) { Text("Clear date", color = Plum) }
    }
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = whenText,
        onValueChange = { whenText = it },
        modifier = Modifier.fillMaxWidth(),
        // Sits alongside the date rather than replacing it. "Friday, early" is
        // how someone remembers the visit; the date is how the app sorts it.
        label = { Text("Anything else about when (optional)") },
        shape = RoundedCornerShape(17.dp),
    )
    Spacer(Modifier.height(14.dp))
    PrimaryButton(
        label = "Save appointment",
        onClick = {
            actions.saveAppointment(doctor.trim(), place.trim(), whenText.trim(), at)
            onDismiss()
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = doctor.isNotBlank(),
    )

    if (pickingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = at?.let { it * 1000 })
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    at = picker.selectedDateMillis?.let { it / 1000 }
                    pickingDate = false
                }) { Text("Choose", color = Plum) }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text("Cancel", color = InkMuted) }
            },
        ) {
            DatePicker(state = picker)
        }
    }
}

@Composable
private fun CareVaultTool(
    picked: Uri?,
    uploading: Boolean,
    onPickDocument: () -> Unit,
    onPhotograph: () -> Unit,
    onUpload: (Uri, String) -> Unit,
) {
    // The "Use in future answers" switch that used to sit here was removed
    // rather than kept: nothing extracts text from a document in this build, so
    // there is no "extracted detail" to approve and the toggle could not have
    // changed anything. The note below says what actually happens instead.
    var category by remember { mutableStateOf("Prescription") }

    ChoiceChips(
        options = listOf("Prescription", "Lab report", "Scan", "Other"),
        selected = category,
        onSelect = { category = it },
    )
    Spacer(Modifier.height(16.dp))
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(138.dp)
                .clickable(role = Role.Button, enabled = !uploading, onClick = onPickDocument)
                .border(
                    1.dp,
                    if (picked != null) Plum else OutlineSoft,
                    RoundedCornerShape(20.dp),
                ),
        color = Paper,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = if (picked != null) Icons.Filled.Check else Icons.Outlined.CloudUpload,
                contentDescription = null,
                tint = Plum,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (picked != null) "Ready to upload" else "Choose a file",
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
            )
            Text(
                text = picked?.lastPathSegment?.substringAfterLast('/')
                    ?: "PDF, JPG or PNG · Max 20 MB",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        }
    }

    // A second, equal way in — not a link buried under the drop zone. A paper
    // report handed over at a clinic is the common case, and it has no file to
    // choose.
    Spacer(Modifier.height(10.dp))
    OutlinedButton(
        onClick = onPhotograph,
        enabled = !uploading,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Photograph a document")
    }
    Spacer(Modifier.height(14.dp))
    InfoBanner(
        icon = Icons.Outlined.Security,
        text = "This build stores the file's details — name, type and size. Nothing is " +
            "read out of a document, and a document is never used in an answer.",
        color = SageMist,
    )
    Spacer(Modifier.height(16.dp))
    PrimaryButton(
        label = if (uploading) "Uploading…" else "Save to Care Vault",
        onClick = { picked?.let { onUpload(it, category) } },
        modifier = Modifier.fillMaxWidth(),
        enabled = picked != null && !uploading,
    )
}

/**
 * The two-minute reset, with a timer that actually runs.
 *
 * "Begin guided session" previously showed a toast and nothing happened — the
 * breathing circle animated whether or not you pressed it, so the button was
 * pure decoration on a screen that claimed to guide a session. The web build has
 * had a real 2:00 countdown; this brings Android to parity.
 */
@Composable
private fun ResetTool() {
    var remaining by remember { mutableIntStateOf(RESET_SECONDS) }
    var running by remember { mutableStateOf(false) }
    val finished = remaining == 0
    val ticking = running && !finished

    LaunchedEffect(ticking) {
        while (ticking && remaining > 0) {
            delay(1000)
            remaining -= 1
        }
    }

    val infinite = rememberInfiniteTransition(label = "breathing")
    val scale by infinite.animateFloat(
        initialValue = 0.84f,
        targetValue = 1.08f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(4000),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "breathing scale",
    )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(210.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    // The circle only breathes while the session is running.
                    // It used to animate regardless, which is what made the
                    // inert button so easy to miss.
                    .scale(if (ticking) scale else 1f)
                    .size(142.dp)
                    .background(Lilac.copy(alpha = 0.48f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(92.dp)
                        .background(Plum.copy(alpha = 0.13f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BrandOrb(compact = true)
            }
        }
    }
    Text(
        text = "%d:%02d".format(remaining / 60, remaining % 60),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.displaySmall,
        textAlign = TextAlign.Center,
        color = Plum,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = when {
            ticking -> "Breathe in… and soften."
            finished -> "That's two minutes. Well done."
            else -> "A gentle two-minute reset. Stop whenever you need."
        },
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = InkMuted,
    )
    Spacer(Modifier.height(18.dp))
    PrimaryButton(
        label = when {
            ticking -> "Pause"
            finished -> "Again"
            else -> "Begin guided session"
        },
        onClick = {
            if (finished) {
                remaining = RESET_SECONDS
                running = true
            } else {
                running = !running
            }
        },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = if (ticking) null else Icons.Outlined.PlayArrow,
    )
}

private const val RESET_SECONDS = 120

@Composable
private fun SymptomTool(
    actions: ToolActions,
    onUrgentHelp: () -> Unit,
    onDismiss: () -> Unit,
) {
    var symptom by remember { mutableStateOf("") }
    var severity by remember { mutableFloatStateOf(2f) }
    // The backend stores a word, not a 1-5 number.
    val severityWord = when {
        severity <= 2f -> "Mild"
        severity <= 4f -> "Moderate"
        else -> "Severe"
    }

    OutlinedTextField(
        value = symptom,
        onValueChange = { symptom = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("What changed?") },
        placeholder = { Text("Describe it in your own words") },
        minLines = 3,
        shape = RoundedCornerShape(17.dp),
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "How noticeable is it?  $severityWord",
        style = MaterialTheme.typography.titleSmall,
        color = Ink,
    )
    Slider(value = severity, onValueChange = { severity = it }, valueRange = 1f..5f, steps = 3)
    InfoBanner(
        icon = Icons.Outlined.Security,
        text = if (severityWord == "Severe") {
            "Severe or sudden symptoms need your care team now — don't wait for Aira."
        } else {
            "Aira tracks patterns; it does not diagnose symptoms."
        },
        color = AmberMist,
        contentColor = Amber,
    )
    Spacer(Modifier.height(10.dp))
    TextButton(onClick = onUrgentHelp, modifier = Modifier.fillMaxWidth()) {
        Text("I’m worried this may be urgent", color = Urgent)
    }
    Spacer(Modifier.height(6.dp))
    PrimaryButton(
        label = "Save symptom log",
        enabled = symptom.isNotBlank(),
        onClick = { actions.saveSymptom(symptom.trim(), severityWord, "Today"); onDismiss() },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Companion mode — the one tool in this sheet that is still unbuilt.
 *
 * Both halves need a service this project doesn't have: the talking avatar needs
 * speech synthesis and lip-sync, the future-baby story needs image generation.
 * They previously showed "Talking avatar mode selected." and "Private
 * illustrative story preview created." and produced nothing at all.
 *
 * They are labelled and disabled rather than silently faked — and the photo
 * pickers and consent checkbox that fed the story preview are gone, because
 * asking someone to hand over their and their partner's photos for a feature
 * that cannot run is a worse version of the same lie.
 */
@Composable
private fun CompanionTool() {
    var mode by remember { mutableStateOf("Aira avatar") }
    ChoiceChips(
        options = listOf("Aira avatar", "Future-baby story"),
        selected = mode,
        onSelect = { mode = it },
    )
    Spacer(Modifier.height(16.dp))

    if (mode == "Aira avatar") {
        AiraCard(containerColor = LilacMist) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandOrb()
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Aira · warm avatar", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Text("Lip-synced voice conversation", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                }
            }
            Spacer(Modifier.height(16.dp))
            InfoBanner(
                icon = Icons.Outlined.Mic,
                text = "The talking avatar isn't wired up in this build — it needs spoken " +
                    "replies, which Aira doesn't have yet.",
                color = AmberMist,
                contentColor = Amber,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                label = "Use talking avatar",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                trailingIcon = Icons.Outlined.RecordVoiceOver,
            )
        }
    } else {
        AiraCard {
            Text("A gentle imagined character", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(
                "A private connection experience — not a prediction.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
            Spacer(Modifier.height(14.dp))
            InfoBanner(
                icon = Icons.Outlined.Security,
                text = "Illustrative only — not a prediction of appearance, health, personality or genetics.",
                color = AmberMist,
                contentColor = Amber,
            )
            Spacer(Modifier.height(12.dp))
            InfoBanner(
                icon = Icons.Outlined.AutoAwesome,
                text = "Story previews aren't wired up in this build, so Aira doesn't ask for " +
                    "your photos yet. Nothing is generated and nothing is uploaded.",
                color = AmberMist,
                contentColor = Amber,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                label = "Create private preview",
                enabled = false,
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CarePlanTool(care: CareData?, actions: ToolActions) {
    // The plan IS the user's reminders. This used to list three invented tasks
    // ("Daily prenatal vitamin") with checkboxes that saved nothing.
    //
    // The rows are tappable now. Until POST /v1/care/reminders/{id}/done
    // existed there was no way to complete a reminder from any client, so
    // "on track" was permanently 0 no matter what the user did.
    val reminders = care?.reminders.orEmpty()
    if (reminders.isEmpty()) {
        InfoBanner(
            Icons.Outlined.Memory,
            "Your care plan builds itself from the reminders you add. Nothing here yet.",
            SageMist,
        )
        return
    }
    Text(
        "${care?.planOnTrack ?: 0} of ${care?.planTotal ?: reminders.size} on track",
        style = MaterialTheme.typography.titleMedium,
        color = Ink,
    )
    Spacer(Modifier.height(10.dp))
    reminders.forEach { item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Toggle, not one-way: a reminder ticked by accident has to be
                // reversible.
                .clickable(role = Role.Checkbox) {
                    actions.setReminderDone(item.id, !item.done)
                }
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (item.done) Icons.Filled.Check else Icons.Outlined.AccessTime,
                contentDescription = if (item.done) "Done — tap to reopen" else "Tap to mark done",
                tint = if (item.done) SageDeep else InkMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, color = Ink)
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = "Tap a reminder to mark it done, or tap it again to reopen it.",
        style = MaterialTheme.typography.bodySmall,
        color = InkMuted,
    )
}

@Composable
private fun PrivacyTool(actions: ToolActions, consent: List<ConsentFeature>) {
    // Real toggles backed by the append-only consent ledger. These used to be
    // four `remember { mutableStateOf(...) }` switches — flipping "AI
    // personalisation" off changed nothing about what Aira actually used.
    LaunchedEffect(Unit) { actions.loadConsent() }

    if (consent.isEmpty()) {
        InfoBanner(Icons.Outlined.Lock, "Loading your consent settings…", SageMist)
        return
    }
    // Three states, not two. A switch that governs nothing is the defect this
    // screen exists to prevent, so an unbuilt feature is disabled with the
    // reason rather than shown as a control the user can operate.
    consent.forEachIndexed { index, feature ->
        SettingLine(
            title = feature.label,
            subtitle = when {
                feature.locked -> "Permanently off — health data is never an ad product"
                !feature.available -> "Not available in this build, so there's nothing to permit yet"
                feature.granted -> "On"
                else -> "Off"
            },
            checked = feature.granted,
            enabled = !feature.locked && feature.available,
            onCheckedChange = { actions.setConsent(feature.key, it) },
        )
        if (index < consent.lastIndex) HorizontalDivider(color = OutlineSoft)
    }
    Spacer(Modifier.height(16.dp))
    InfoBanner(
        icon = Icons.Outlined.Lock,
        text = "Every change is recorded in an append-only ledger, so you can see " +
            "exactly what you agreed to and when.",
        color = SageMist,
    )
}

@Composable
private fun MemoryTool(actions: ToolActions, memory: List<MemoryItem>) {
    // Real memory from GET /v1/memory. The list used to be four invented strings
    // ("You are 24 weeks pregnant") that deleting removed only from local state,
    // so "forget" was purely cosmetic.
    LaunchedEffect(Unit) { actions.loadMemory() }

    if (memory.isEmpty()) {
        InfoBanner(Icons.Outlined.Memory, "Aira isn't remembering anything about you yet.", SageMist)
        return
    }
    Text(
        "Only approved items shape future answers. Turning one off keeps it here but stops it being used.",
        style = MaterialTheme.typography.bodySmall,
        color = InkMuted,
    )
    Spacer(Modifier.height(12.dp))
    memory.forEach { item ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Paper,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineSoft),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Memory, null, tint = Plum, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.label, style = MaterialTheme.typography.titleSmall, color = Ink)
                    Text(item.value, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                }
                Switch(
                    checked = item.approved,
                    onCheckedChange = { actions.setMemoryApproved(item.id, it) },
                )
                IconButton(onClick = { actions.forgetMemory(item.id) }) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Forget ${item.label}",
                        tint = InkMuted,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Voice preference. The choice is stored for real (PUT /v1/prefs) — it used to
 * be a toast reading "English · Aira warm saved." that persisted nothing.
 *
 * Two things the old sheet claimed and this one does not: there is no language
 * picker here, because conversation language lives on the profile and having a
 * second copy would give two sources of truth that drift; and no option offers
 * to "preview this voice", because there is no speech synthesis to preview.
 */
@Composable
private fun VoiceTool(
    prefs: VoicePrefs,
    actions: ToolActions,
) {
    LaunchedEffect(Unit) { actions.loadPrefs() }
    InfoBanner(
        icon = Icons.Outlined.Mic,
        text = "Spoken replies aren't available in this build. Your choice is saved and " +
            "applies as soon as they are — until then Aira replies in text.",
        color = AmberMist,
        contentColor = Amber,
    )
    Spacer(Modifier.height(16.dp))
    Text("Voice", style = MaterialTheme.typography.titleSmall, color = Ink)
    Spacer(Modifier.height(8.dp))
    listOf("Aira warm", "Aira gentle", "Text only").forEach { option ->
        ChoiceCard(
            title = option,
            subtitle = if (option == "Text only") {
                "Never speak, even once spoken replies ship"
            } else {
                "Used when spoken replies are available"
            },
            icon = if (option == "Text only") Icons.Outlined.EditNote else Icons.Outlined.Mic,
            selected = prefs.voice == option,
            onClick = { actions.setVoice(option) },
        )
        Spacer(Modifier.height(8.dp))
    }
    Text(
        text = "Saved automatically.",
        style = MaterialTheme.typography.bodySmall,
        color = InkMuted,
    )
}

/**
 * Partner access. Creates a real, revocable, single-use invite via
 * POST /v1/partner/invite — this button used to show "Private partner
 * invitation prepared" and no invitation of any kind was created.
 *
 * The code is shared by the user through the system share sheet rather than
 * being emailed or texted by the backend, so Aira never collects a contact
 * detail for a third party.
 */
@Composable
private fun PartnerTool(
    invite: PartnerInvite?,
    invites: List<PartnerInviteRow>,
    shared: List<PartnerShare>,
    consent: List<ConsentFeature>,
    actions: ToolActions,
) {
    LaunchedEffect(Unit) { actions.loadPartner(); actions.loadConsent() }
    var tab by remember { mutableStateOf("Share") }
    ChoiceChips(listOf("Share", "Redeem"), tab) { tab = it }
    Spacer(Modifier.height(16.dp))
    if (tab == "Redeem") {
        RedeemPartnerCode(shared, actions)
        return
    }
    PartnerShareTab(invite, invites, consent, actions)
}

/**
 * The receiving end of an invite. This did not exist: the backend has had
 * POST /v1/partner/accept all along, but no client called it, so a code could be
 * created and shared and then had nowhere to go.
 */
@Composable
private fun RedeemPartnerCode(
    shared: List<PartnerShare>,
    actions: ToolActions,
) {
    var code by remember { mutableStateOf("") }
    OutlinedTextField(
        value = code,
        onValueChange = { code = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Invite code") },
        singleLine = true,
        shape = RoundedCornerShape(17.dp),
    )
    Spacer(Modifier.height(12.dp))
    PrimaryButton(
        label = "Redeem code",
        onClick = { actions.acceptPartnerInvite(code.trim()); code = "" },
        modifier = Modifier.fillMaxWidth(),
        enabled = code.isNotBlank(),
    )
    Spacer(Modifier.height(18.dp))

    if (shared.isEmpty()) {
        InfoBanner(
            Icons.Outlined.Group,
            "Nobody has shared their care with you yet. A code works once and " +
                "expires after 7 days.",
            SageMist,
        )
        return
    }
    shared.forEach { share ->
        SectionLabel("Shared by ${share.sharedBy}")
        Spacer(Modifier.height(8.dp))
        val rows = share.appointments + share.medicines + share.reminders
        if (rows.isEmpty()) {
            Text(
                "Nothing to show yet — they've shared access, but haven't added " +
                    "anything in the scopes they granted.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        }
        rows.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when (item.kind) {
                        "appointment" -> Icons.Outlined.CalendarMonth
                        "medicine" -> Icons.Outlined.Medication
                        else -> Icons.Outlined.AccessTime
                    },
                    contentDescription = null,
                    tint = SageDeep,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, color = Ink)
                    if (item.subtitle.isNotBlank()) {
                        Text(
                            item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                }
            }
        }
        // The `health_details` scope. These arrive as counts and never as text —
        // the server does not send a partner the body of a symptom log or a
        // private check-in note whatever scope is set. They were being fetched
        // and dropped, so granting the scope changed nothing on screen.
        val counts = listOfNotNull(
            share.symptomCount?.let { "$it symptom${if (it == 1) "" else "s"} logged" },
            share.checkinCount?.let { "$it check-in${if (it == 1) "" else "s"}" },
            share.documentsCount?.let { "$it document${if (it == 1) "" else "s"}" },
        )
        if (counts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            AiraCard(containerColor = LilacMist) {
                Text("Health details", style = MaterialTheme.typography.titleSmall, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    counts.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Counts only — Aira never shares what was written in them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        InfoBanner(
            Icons.Outlined.Lock,
            "This is read-only, and only what ${share.sharedBy} chose to share. " +
                "They can revoke it at any time.",
            SageMist,
        )
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun PartnerShareTab(
    invite: PartnerInvite?,
    invites: List<PartnerInviteRow>,
    consent: List<ConsentFeature>,
    actions: ToolActions,
) {
    var appointment by remember { mutableStateOf(true) }
    var reminders by remember { mutableStateOf(true) }
    var healthDetails by remember { mutableStateOf(false) }

    // `partner_access` defaults to off and the backend now enforces it on every
    // read, so this is the actual switch rather than a label: with it off,
    // invites can't be created and anyone who already accepted sees nothing.
    val access = consent.firstOrNull { it.key == "partner_access" }
    if (access != null && !access.granted) {
        InfoBanner(
            icon = Icons.Outlined.Lock,
            text = "Partner access is off. Sharing any part of your care with " +
                "another person is something you turn on deliberately — and " +
                "turning it back off cuts off everyone you've shared with, " +
                "straight away.",
            color = SageMist,
        )
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            label = "Turn on partner access",
            onClick = { actions.setConsent("partner_access", true) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Existing invites stay visible and revocable even with access off, so
        // withdrawing consent never locks someone out of their own audit trail.
        if (invites.isNotEmpty()) {
            IssuedInvites(invites, actions)
        }
        return
    }

    if (invite == null) {
        InfoBanner(
            icon = Icons.Outlined.Group,
            text = "Partner access is practical by default. Health details stay private " +
                "unless you share them — and even then a partner sees counts, never the " +
                "text of a symptom log or check-in note.",
            color = SageMist,
        )
        Spacer(Modifier.height(14.dp))
        SettingLine("Appointment tasks", "Time, location and preparation list", appointment) { appointment = it }
        SettingLine("Care reminders", "Medicines and practical support", reminders) { reminders = it }
        SettingLine("Health details", "Off by default", healthDetails) { healthDetails = it }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            label = "Create invite",
            onClick = { actions.createPartnerInvite(appointment, reminders, healthDetails) },
            modifier = Modifier.fillMaxWidth(),
            enabled = appointment || reminders || healthDetails,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "The invite works once and expires in 7 days. You can revoke it at any " +
                "time, including after it's been accepted.",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
        )
    } else {
        Text("Share this code", style = MaterialTheme.typography.titleSmall, color = Ink)
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = LilacMist,
            shape = RoundedCornerShape(17.dp),
        ) {
            Text(
                text = invite.code,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = Plum,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            label = "Share invite",
            onClick = { actions.sharePartnerInvite(invite.shareText) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = actions.clearPartnerInvite,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create a different invite", color = Plum) }
    }

    if (invites.isNotEmpty()) {
        IssuedInvites(invites, actions)
    }
}

/**
 * Issued invites, with the revoke the backend has always supported and no client
 * ever called. Without this an accepted invite could not be taken back from the
 * app that created it.
 *
 * Shown whether or not partner access is currently on, because withdrawing
 * consent must not hide someone's own audit trail from them.
 */
@Composable
private fun IssuedInvites(
    invites: List<PartnerInviteRow>,
    actions: ToolActions,
) {
    Spacer(Modifier.height(22.dp))
    SectionLabel("Invites you've issued")
    Spacer(Modifier.height(6.dp))
    invites.forEach { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.code ?: "Code hidden",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${row.state.replaceFirstChar { it.uppercase() }} · ${row.scopeSummary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            // Revoking an expired or already-revoked invite would be a no-op
            // dressed as an action, so the control is only offered when it can
            // actually change something.
            if (row.state == "pending" || row.state == "accepted") {
                TextButton(onClick = { actions.revokePartnerInvite(row.id) }) {
                    Text("Revoke", color = Urgent)
                }
            }
        }
    }
}

@Composable
private fun SupportTool(
    actions: ToolActions,
    onUrgentHelp: () -> Unit,
    onDismiss: () -> Unit,
) {
    // "Report an AI answer", wired to POST /v1/feedback/report so it lands in
    // the admin review queue beside the safety flags. The previous version
    // listed a fabricated care-team number (+91 11 4000 1234) that belonged to
    // nobody — the most dangerous string in the app after the urgent dialer.
    var kind by remember { mutableStateOf("clinical") }
    var message by remember { mutableStateOf("") }

    Text(
        "If something Aira said worried you, tell us. Reports go straight to the " +
            "review queue beside the safety flags.",
        style = MaterialTheme.typography.bodyMedium,
        color = InkMuted,
    )
    Spacer(Modifier.height(14.dp))
    SectionLabel("What kind of concern?")
    Spacer(Modifier.height(8.dp))
    ChoiceChips(
        options = listOf("clinical", "safety", "technical"),
        selected = kind,
        onSelect = { kind = it },
    )
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
        value = message,
        onValueChange = { message = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("What happened?") },
        minLines = 3,
        shape = RoundedCornerShape(17.dp),
    )
    Spacer(Modifier.height(14.dp))
    PrimaryButton(
        label = "Send report",
        onClick = { actions.sendReport(kind, message.trim()); onDismiss() },
        modifier = Modifier.fillMaxWidth(),
        enabled = message.isNotBlank(),
    )
    Spacer(Modifier.height(12.dp))
    InfoBanner(
        icon = Icons.Outlined.Security,
        text = "For anything urgent, use Urgent help or your local emergency services — not this form.",
        color = AmberMist,
        contentColor = Amber,
    )
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onUrgentHelp, modifier = Modifier.fillMaxWidth()) {
        Text("I need urgent help", color = Urgent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmergencyProfileTool(
    actions: ToolActions,
    saved: Map<String, String>?,
    onDismiss: () -> Unit,
) {
    // A real editor. This screen used to display a fictional patient — "Maya
    // Sharma · Week 24 · Blood group B+ · Emergency contact Arjun" — which is
    // the single most dangerous kind of placeholder in an app whose urgent
    // handoff depends on these details being the user's own.
    //
    // It then went to the opposite failure: the fields were hardcoded empty and
    // nothing ever read the profile back, so the form opened blank however much
    // was stored. Saving sends all six fields, so anything not retyped was
    // erased — verified on a device, where correcting a care-team number
    // deleted the emergency contact next to it. On the one screen the app dials
    // in a crisis.
    LaunchedEffect(Unit) { actions.loadEmergencyProfile() }

    var careTeamName by remember { mutableStateOf("") }
    var careTeamPhone by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var bloodGroup by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }

    // Fill in once, when the saved profile arrives. Keyed on `saved` rather
    // than run on every recomposition so it cannot overwrite something being
    // typed; `prefilled` stops a later refresh doing the same.
    var prefilled by remember { mutableStateOf(false) }
    LaunchedEffect(saved) {
        if (saved != null && !prefilled) {
            prefilled = true
            careTeamName = saved["care_team_name"].orEmpty()
            careTeamPhone = saved["care_team_phone"].orEmpty()
            contactName = saved["emergency_contact_name"].orEmpty()
            contactPhone = saved["emergency_contact_phone"].orEmpty()
            bloodGroup = saved["blood_group"].orEmpty()
            allergies = saved["allergies"].orEmpty()
        }
    }

    InfoBanner(
        icon = Icons.Outlined.Lock,
        text = "These details are what Urgent help dials. Without a number, Aira " +
            "can only point you to local emergency services.",
        color = SageMist,
    )
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
        value = careTeamName,
        onValueChange = { careTeamName = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Care team") },
        shape = RoundedCornerShape(17.dp),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = careTeamPhone,
        onValueChange = { careTeamPhone = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Care team phone") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        shape = RoundedCornerShape(17.dp),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = contactName,
        onValueChange = { contactName = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Emergency contact") },
        shape = RoundedCornerShape(17.dp),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = contactPhone,
        onValueChange = { contactPhone = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Contact phone") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        shape = RoundedCornerShape(17.dp),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = bloodGroup,
        onValueChange = { bloodGroup = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Blood group (optional)") },
        shape = RoundedCornerShape(17.dp),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = allergies,
        onValueChange = { allergies = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Allergies and notes (optional)") },
        minLines = 2,
        shape = RoundedCornerShape(17.dp),
    )
    Spacer(Modifier.height(16.dp))
    PrimaryButton(
        label = "Save emergency profile",
        onClick = {
            actions.saveEmergencyProfile(
                mapOf(
                    "care_team_name" to careTeamName.trim(),
                    "care_team_phone" to careTeamPhone.trim(),
                    "emergency_contact_name" to contactName.trim(),
                    "emergency_contact_phone" to contactPhone.trim(),
                    "blood_group" to bloodGroup.trim(),
                    "allergies" to allergies.trim(),
                ),
            )
            onDismiss()
        },
        modifier = Modifier.fillMaxWidth(),
        // Any detail is worth keeping, not just a phone number. The gate used
        // to require one, so typing an allergy and pressing Save did nothing at
        // all — and with the form opening blank there was no way to tell that
        // from a save that worked. The banner already says what a number buys.
        enabled = listOf(
            careTeamName, careTeamPhone, contactName, contactPhone, bloodGroup, allergies,
        ).any { it.isNotBlank() },
    )
}

@Composable
private fun ChoiceChips(
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

@Composable
private fun SettingLine(
    title: String,
    subtitle: String,
    checked: Boolean,
    // `enabled` sits BEFORE the callback on purpose: Kotlin binds a trailing
    // lambda to the last parameter only, so with `enabled` last every
    // `SettingLine(...) { x = it }` call bound its lambda to `enabled: Boolean`
    // and failed to compile.
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

// InfoBanner moved to ui/components/AiraComponents.kt when the auth
// screens needed it too — it had outgrown being private to this file.



/** A date a person would say out loud: "Fri 7 Aug". Unix seconds in. */
internal fun formatAppointmentDate(epochSeconds: Long): String =
    java.time.Instant.ofEpochSecond(epochSeconds)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))
