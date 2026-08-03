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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aira.companion.model.AiraTool
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.BrandOrb
import com.aira.companion.ui.components.ChoiceCard
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

private data class ChatToolItem(
    val tool: AiraTool,
    val label: String,
    val detail: String,
    val icon: ImageVector,
)

private val chatToolItems =
    listOf(
        ChatToolItem(AiraTool.CheckIn, "Check in", "Mood, energy & sleep", Icons.Outlined.FavoriteBorder),
        ChatToolItem(AiraTool.Reminder, "Reminder", "Medicine or care task", Icons.Outlined.AccessTime),
        ChatToolItem(AiraTool.CareVault, "Care Vault", "Prescription or report", Icons.Outlined.FolderOpen),
        ChatToolItem(AiraTool.Reset, "Reset", "Two calm minutes", Icons.Outlined.Waves),
        ChatToolItem(AiraTool.Symptom, "Track", "Log a change", Icons.Outlined.TrackChanges),
        ChatToolItem(AiraTool.Companion, "Companion", "Voice or avatar mode", Icons.Outlined.RecordVoiceOver),
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
                    SectionLabel("Tools in this conversation")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicToolSheet(
    tool: AiraTool,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit,
    onUrgentHelp: () -> Unit,
) {
    val documentLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> if (uri != null) onNotify("Document selected securely.") }

    var selfPhoto by remember(tool) { mutableStateOf<Uri?>(null) }
    var partnerPhoto by remember(tool) { mutableStateOf<Uri?>(null) }
    val selfPhotoLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { selfPhoto = it }
    val partnerPhotoLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { partnerPhoto = it }

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
            ToolHeader(tool = tool, onDismiss = onDismiss)
            Spacer(modifier = Modifier.height(20.dp))

            when (tool) {
                AiraTool.Notifications -> NotificationsTool(onNotify)
                AiraTool.CheckIn -> CheckInTool(onNotify)
                AiraTool.Reminder -> ReminderTool(onNotify)
                AiraTool.Medicines -> MedicinesTool(onNotify)
                AiraTool.Appointment -> AppointmentTool(onNotify)
                AiraTool.CareVault ->
                    CareVaultTool(
                        onPickDocument = {
                            documentLauncher.launch(
                                arrayOf("application/pdf", "image/jpeg", "image/png"),
                            )
                        },
                        onNotify = onNotify,
                    )
                AiraTool.Reset -> ResetTool(onNotify)
                AiraTool.Symptom -> SymptomTool(onNotify, onUrgentHelp)
                AiraTool.Companion ->
                    CompanionTool(
                        selfPhoto = selfPhoto,
                        partnerPhoto = partnerPhoto,
                        onPickSelfPhoto = { selfPhotoLauncher.launch("image/*") },
                        onPickPartnerPhoto = { partnerPhotoLauncher.launch("image/*") },
                        onNotify = onNotify,
                    )
                AiraTool.CarePlan -> CarePlanTool(onNotify)
                AiraTool.Privacy -> PrivacyTool(onNotify)
                AiraTool.Memory -> MemoryTool(onNotify)
                AiraTool.Voice -> VoiceTool(onNotify)
                AiraTool.Partner -> PartnerTool(onNotify)
                AiraTool.Support -> SupportTool(onNotify, onUrgentHelp)
                AiraTool.Emergency -> EmergencyProfileTool(onNotify)
            }
        }
    }
}

@Composable
private fun ToolHeader(
    tool: AiraTool,
    onDismiss: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            SectionLabel(tool.eyebrow)
            Text(
                text = tool.title,
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
private fun NotificationsTool(onNotify: (String) -> Unit) {
    listOf(
        Triple("Tomorrow · 10:30 AM", "Appointment with Dr. Meera Shah", Icons.Outlined.CalendarMonth),
        Triple("Today · 8:00 PM", "Prenatal vitamin reminder", Icons.Outlined.Medication),
        Triple("New", "Week 24 guide is ready", Icons.Outlined.AutoAwesome),
    ).forEachIndexed { index, (time, title, icon) ->
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
    OutlinedButton(onClick = { onNotify("All updates marked as read.") }, modifier = Modifier.fillMaxWidth()) {
        Text("Mark all as read")
    }
}

@Composable
private fun CheckInTool(onNotify: (String) -> Unit) {
    var mood by remember { mutableStateOf("Steady") }
    var energy by remember { mutableStateOf("Medium") }
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
        value = "",
        onValueChange = {},
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Anything you want Aira to know?") },
        minLines = 3,
        shape = RoundedCornerShape(17.dp),
    )
    Spacer(Modifier.height(18.dp))
    PrimaryButton(
        label = "Save check-in",
        onClick = { onNotify("Check-in saved: $mood mood, $energy energy.") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReminderTool(onNotify: (String) -> Unit) {
    var title by remember { mutableStateOf("Prenatal vitamin") }
    var time by remember { mutableStateOf("8:00 PM") }
    var repeat by remember { mutableStateOf(true) }

    OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Reminder") },
        leadingIcon = { Icon(Icons.Outlined.Medication, null) },
        shape = RoundedCornerShape(17.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text("Time", style = MaterialTheme.typography.titleSmall, color = Ink)
    Spacer(Modifier.height(8.dp))
    ChoiceChips(
        options = listOf("8:00 AM", "2:00 PM", "8:00 PM"),
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
        label = "Create reminder",
        onClick = { onNotify("Reminder created for $time.") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MedicinesTool(onNotify: (String) -> Unit) {
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
                Text("Prenatal vitamin", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text("1 tablet · 8:00 PM", style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            label = "Mark as taken",
            onClick = { onNotify("Prenatal vitamin marked as taken.") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = Icons.Filled.Check,
        )
    }
    Spacer(Modifier.height(12.dp))
    InfoBanner(
        icon = Icons.Outlined.HealthAndSafety,
        text = "Aira can remind and organise, but does not change medication advice.",
        color = SageMist,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = { onNotify("Add-medicine flow ready for API integration.") }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, null)
        Spacer(Modifier.width(8.dp))
        Text("Add medicine")
    }
}

@Composable
private fun AppointmentTool(onNotify: (String) -> Unit) {
    val checked = remember { mutableStateListOf(false, false, false) }
    val questions =
        listOf(
            "Do I need any tests this week?",
            "What changes should I expect next?",
            "How can I manage fatigue better?",
        )
    AiraCard(containerColor = LilacMist) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CalendarMonth, null, tint = Plum)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Tomorrow · 10:30 AM", style = MaterialTheme.typography.titleSmall, color = Ink)
                Text("Dr. Meera Shah · City Care", style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("Suggested questions")
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
    PrimaryButton(
        label = "Save visit brief",
        onClick = { onNotify("Visit brief saved with ${checked.count { it }} selected questions.") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CareVaultTool(
    onPickDocument: () -> Unit,
    onNotify: (String) -> Unit,
) {
    var category by remember { mutableStateOf("Prescription") }
    var useInAnswers by remember { mutableStateOf(false) }

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
                .clickable(role = Role.Button, onClick = onPickDocument)
                .border(1.dp, OutlineSoft, RoundedCornerShape(20.dp)),
        color = Paper,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = Plum, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text("Choose or scan a document", style = MaterialTheme.typography.titleSmall, color = Ink)
            Text("PDF, JPG or PNG · Max 20 MB", style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
    }
    Spacer(Modifier.height(14.dp))
    SettingLine(
        title = "Use in future answers",
        subtitle = "Only after you approve extracted details",
        checked = useInAnswers,
        onCheckedChange = { useInAnswers = it },
    )
    Spacer(Modifier.height(16.dp))
    PrimaryButton(
        label = "Save to Care Vault",
        onClick = { onNotify("$category saved to your private Care Vault.") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ResetTool(onNotify: (String) -> Unit) {
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
                    .scale(scale)
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
        text = "Breathe in slowly",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        color = Ink,
    )
    Text(
        text = "A gentle two-minute reset. Stop whenever you need.",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = InkMuted,
    )
    Spacer(Modifier.height(18.dp))
    PrimaryButton(
        label = "Begin guided session",
        onClick = { onNotify("Two-minute reset started.") },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = Icons.Outlined.PlayArrow,
    )
}

@Composable
private fun SymptomTool(
    onNotify: (String) -> Unit,
    onUrgentHelp: () -> Unit,
) {
    var symptom by remember { mutableStateOf("") }
    var severity by remember { mutableFloatStateOf(2f) }

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
    Text("How noticeable is it?  ${severity.toInt()}/5", style = MaterialTheme.typography.titleSmall, color = Ink)
    Slider(value = severity, onValueChange = { severity = it }, valueRange = 1f..5f, steps = 3)
    InfoBanner(
        icon = Icons.Outlined.Security,
        text = "Aira tracks patterns; it does not diagnose symptoms.",
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
        onClick = { onNotify("Symptom logged safely for your timeline.") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CompanionTool(
    selfPhoto: Uri?,
    partnerPhoto: Uri?,
    onPickSelfPhoto: () -> Unit,
    onPickPartnerPhoto: () -> Unit,
    onNotify: (String) -> Unit,
) {
    var mode by remember { mutableStateOf("Aira avatar") }
    var consent by remember { mutableStateOf(false) }
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
            PrimaryButton(
                label = "Use talking avatar",
                onClick = { onNotify("Talking avatar mode selected.") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = Icons.Outlined.RecordVoiceOver,
            )
        }
    } else {
        AiraCard {
            Text("A gentle imagined character", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(
                "A private connection experience—not a prediction.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PhotoPickerCard(
                    label = if (selfPhoto == null) "Add your photo" else "Your photo added",
                    selected = selfPhoto != null,
                    onClick = onPickSelfPhoto,
                    modifier = Modifier.weight(1f),
                )
                PhotoPickerCard(
                    label = if (partnerPhoto == null) "Add partner photo" else "Partner photo added",
                    selected = partnerPhoto != null,
                    onClick = onPickPartnerPhoto,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            InfoBanner(
                icon = Icons.Outlined.Security,
                text = "Illustrative only—not a prediction of appearance, health, personality or genetics.",
                color = AmberMist,
                contentColor = Amber,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = consent, onCheckedChange = { consent = it })
                Text(
                    "Both people consent to this private creative use.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink,
                )
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                label = "Create private preview",
                enabled = selfPhoto != null && partnerPhoto != null && consent,
                onClick = { onNotify("Private illustrative story preview created.") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CarePlanTool(onNotify: (String) -> Unit) {
    val complete = remember { mutableStateListOf(true, false, false) }
    listOf(
        "Daily prenatal vitamin",
        "Prepare appointment questions",
        "Complete two-minute reset",
    ).forEachIndexed { index, item ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { complete[index] = !complete[index] }
                    .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = complete[index], onCheckedChange = { complete[index] = it })
            Text(item, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Ink)
        }
    }
    Spacer(Modifier.height(14.dp))
    PrimaryButton(
        label = "Save care plan",
        onClick = { onNotify("Care plan updated.") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PrivacyTool(onNotify: (String) -> Unit) {
    var personalisation by remember { mutableStateOf(true) }
    var partner by remember { mutableStateOf(false) }
    var rawAudio by remember { mutableStateOf(false) }
    SettingLine("AI personalisation", "Approved context shapes answers", personalisation) { personalisation = it }
    HorizontalDivider(color = OutlineSoft)
    SettingLine("Partner access", "Tasks only", partner) { partner = it }
    HorizontalDivider(color = OutlineSoft)
    SettingLine("Store raw voice audio", "Off · transcript only", rawAudio) { rawAudio = it }
    HorizontalDivider(color = OutlineSoft)
    SettingLine("Health data for ads", "Never", false, onCheckedChange = {}, enabled = false)
    Spacer(Modifier.height(16.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onNotify("Data export requested.") }) {
            Icon(Icons.Outlined.Download, null)
            Spacer(Modifier.width(6.dp))
            Text("Download data")
        }
        OutlinedButton(onClick = { onNotify("Consent history opened.") }) {
            Icon(Icons.Outlined.Description, null)
            Spacer(Modifier.width(6.dp))
            Text("Consent history")
        }
    }
    Spacer(Modifier.height(10.dp))
    OutlinedButton(onClick = { onNotify("Selective deletion flow opened.") }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.DeleteOutline, null, tint = Urgent)
        Spacer(Modifier.width(7.dp))
        Text("Delete selected data", color = Urgent)
    }
}

@Composable
private fun MemoryTool(onNotify: (String) -> Unit) {
    val memories =
        remember {
            mutableStateListOf(
                "You are 24 weeks pregnant",
                "You prefer English with occasional Hindi",
                "You noted fatigue on Monday",
                "Your next appointment is tomorrow",
            )
        }
    if (memories.isEmpty()) {
        InfoBanner(Icons.Outlined.Memory, "Aira is not currently remembering any care context.", SageMist)
    } else {
        memories.toList().forEach { memory ->
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
                    Text(memory, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Ink)
                    IconButton(onClick = {
                        memories.remove(memory)
                        onNotify("Memory removed.")
                    }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Forget $memory", tint = InkMuted)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VoiceTool(onNotify: (String) -> Unit) {
    var language by remember { mutableStateOf("English") }
    var voice by remember { mutableStateOf("Aira warm") }
    Text("Conversation language", style = MaterialTheme.typography.titleSmall, color = Ink)
    Spacer(Modifier.height(8.dp))
    ChoiceChips(listOf("English", "Hindi", "Hinglish"), language) { language = it }
    Spacer(Modifier.height(16.dp))
    Text("Voice", style = MaterialTheme.typography.titleSmall, color = Ink)
    Spacer(Modifier.height(8.dp))
    listOf("Aira warm", "Aira gentle", "Text only").forEach { option ->
        ChoiceCard(
            title = option,
            subtitle = if (option == "Text only") "No spoken responses" else "Tap to preview this voice",
            icon = if (option == "Text only") Icons.Outlined.EditNote else Icons.Outlined.Mic,
            selected = voice == option,
            onClick = { voice = option },
        )
        Spacer(Modifier.height(8.dp))
    }
    PrimaryButton(
        label = "Save voice settings",
        onClick = { onNotify("$language · $voice saved.") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PartnerTool(onNotify: (String) -> Unit) {
    var appointment by remember { mutableStateOf(true) }
    var reminders by remember { mutableStateOf(true) }
    var healthDetails by remember { mutableStateOf(false) }
    InfoBanner(
        icon = Icons.Outlined.Group,
        text = "Partner access is practical by default. Health details stay private unless you share them.",
        color = SageMist,
    )
    Spacer(Modifier.height(14.dp))
    SettingLine("Appointment tasks", "Time, location and preparation list", appointment) { appointment = it }
    SettingLine("Care reminders", "Medicines and practical support", reminders) { reminders = it }
    SettingLine("Health details", "Off by default", healthDetails) { healthDetails = it }
    Spacer(Modifier.height(16.dp))
    PrimaryButton(
        label = "Invite partner",
        onClick = { onNotify("Private partner invitation prepared.") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SupportTool(
    onNotify: (String) -> Unit,
    onUrgentHelp: () -> Unit,
) {
    listOf(
        Triple(Icons.Outlined.SupportAgent, "Chat with a care navigator", "Available 8 AM–8 PM"),
        Triple(Icons.Outlined.Phone, "Call your care team", "+91 11 4000 1234"),
        Triple(Icons.Outlined.MedicalInformation, "Find care guidance", "Reviewed wellness articles"),
    ).forEach { (icon, title, subtitle) ->
        ChoiceCard(
            title = title,
            subtitle = subtitle,
            icon = icon,
            onClick = { onNotify("$title opened.") },
        )
        Spacer(Modifier.height(9.dp))
    }
    TextButton(onClick = onUrgentHelp, modifier = Modifier.fillMaxWidth()) {
        Text("I need urgent help", color = Urgent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmergencyProfileTool(onNotify: (String) -> Unit) {
    AiraCard(containerColor = UrgentMist) {
        Text("Maya Sharma · Week 24", style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text("Blood group: B+", style = MaterialTheme.typography.bodyMedium, color = Ink)
        Text("Allergies: None recorded", style = MaterialTheme.typography.bodyMedium, color = Ink)
        Text("Primary care team: City Care", style = MaterialTheme.typography.bodyMedium, color = Ink)
        Text("Emergency contact: Arjun · +91 98••• ••210", style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
    Spacer(Modifier.height(14.dp))
    InfoBanner(
        icon = Icons.Outlined.Lock,
        text = "This profile can be made available offline from device settings.",
        color = SageMist,
    )
    Spacer(Modifier.height(14.dp))
    PrimaryButton(
        label = "Update emergency profile",
        onClick = { onNotify("Emergency profile editor opened.") },
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun InfoBanner(
    icon: ImageVector,
    text: String,
    color: Color,
    contentColor: Color = SageDeep,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PhotoPickerCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .height(104.dp)
                .clickable(role = Role.Button, onClick = onClick),
        color = if (selected) SageMist else Ivory,
        shape = RoundedCornerShape(17.dp),
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                if (selected) SageDeep.copy(alpha = 0.45f) else OutlineSoft,
            ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.CameraAlt,
                contentDescription = null,
                tint = if (selected) SageDeep else Plum,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = Ink,
                textAlign = TextAlign.Center,
            )
        }
    }
}
