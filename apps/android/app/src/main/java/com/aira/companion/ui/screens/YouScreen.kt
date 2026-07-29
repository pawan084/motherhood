package com.aira.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aira.companion.data.ConsentFeature
import com.aira.companion.model.AiraTool
import com.aira.companion.model.JourneyType
import com.aira.companion.model.journeyLabel
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.ChoiceCard
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.components.ToolListRow
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Lilac
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.SageDeep
import com.aira.companion.ui.theme.SageMist
import com.aira.companion.ui.theme.Urgent

/** The phrase privacy.DELETE_CONFIRMATION expects, shown to the user rather
 *  than filled in for them. */
private const val DELETE_PHRASE = "DELETE MY DATA"

@Composable
fun YouScreen(
    onOpenTool: (AiraTool) -> Unit,
    modifier: Modifier = Modifier,
    // Real profile, not the "Maya · Week 24" placeholder this screen shipped with.
    name: String = "",
    weeks: Int? = null,
    journey: String? = null,
    language: String = "",
    // The consent ledger, the stored voice, and the two data rights — all of
    // which this screen either faked or didn't offer at all.
    consent: List<ConsentFeature> = emptyList(),
    voice: String = "",
    exporting: Boolean = false,
    deleting: Boolean = false,
    onLoadConsent: () -> Unit = {},
    onSetConsent: (feature: String, granted: Boolean) -> Unit = { _, _ -> },
    onExport: () -> Unit = {},
    onDelete: () -> Unit = {},
    signedIn: Boolean = false,
    onSignOut: () -> Unit = {},
    onCreateAccount: () -> Unit = {},
    onSignIn: () -> Unit = {},
    /** Name, journey and language, as the app currently holds them. */
    journeyType: JourneyType? = null,
    onSaveProfile: (
        name: String,
        journey: JourneyType?,
        language: String,
        weeks: Int?,
        priorities: List<String>?,
    ) -> Unit = { _, _, _, _, _ -> },
    /** What the user last told us, for the editor to prefill. */
    weeksReported: Int? = null,
    priorities: List<String> = emptyList(),
    /** Replay the intro. It was shown once on first run — when someone is
     *  least able to absorb it — and then unreachable forever. */
    onReplayTutorial: () -> Unit = {},
) {
    LaunchedEffect(Unit) { onLoadConsent() }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteConfirmation by remember { mutableStateOf("") }
    var editingProfile by remember { mutableStateOf(false) }
    val personalisation = consent.firstOrNull { it.key == "personalization" }
    val partnerAccess = consent.firstOrNull { it.key == "partner_access" }

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
            Box(
                modifier =
                    Modifier
                        .size(64.dp)
                        .background(Lilac, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // The user's own initial, not a hardcoded "M" for "Maya".
                    text = name.trim().firstOrNull()?.uppercase() ?: "·",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Plum,
                )
            }
            Spacer(modifier = Modifier.width(15.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name.ifBlank { "You" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink,
                )
                Text(
                    listOfNotNull(
                        weeks?.let { "Week $it" } ?: journeyLabel(journey),
                        language.ifBlank { null },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
            }
            TextButton(onClick = { editingProfile = !editingProfile }) {
                Text(if (editingProfile) "Close" else "Edit", color = Plum)
            }
        }

        // Editing the profile.
        //
        // /account/profile has always existed and Android never called it, so
        // the onboarding answers were permanent. In an app built around a
        // journey that changes — trying to conceive, then pregnant, then
        // postpartum — someone whose situation had moved on kept being shown
        // content for where they used to be, with deleting their account as the
        // only way to correct it. A pregnancy that ends is the case that makes
        // this urgent rather than tidy.
        if (editingProfile) {
            Spacer(modifier = Modifier.height(16.dp))
            ProfileEditor(
                initialName = name,
                initialJourney = journeyType,
                initialLanguage = language,
                initialWeeks = weeksReported,
                initialPriorities = priorities,
                onSave = { n, j, l, w, p ->
                    onSaveProfile(n, j, l, w, p)
                    editingProfile = false
                },
            )
        }

        // Sign out sits ABOVE the data rights, and deliberately apart from them:
        // it ends a session, it does not remove anything. Putting it next to
        // "Delete all my data" would invite the reading that leaving takes your
        // care with it.
        Spacer(modifier = Modifier.height(22.dp))
        SectionLabel("Account")
        Spacer(modifier = Modifier.height(8.dp))
        if (signedIn) {
            AiraCard {
                Text(
                    text = "Signing out ends this session on every device. Your care " +
                        "data stays in your account — sign back in any time to reach it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign out")
                }
            }
        } else {
            // The offer has to live here as well as on Welcome. Someone who has
            // been using Aira for weeks is exactly who wants their care to
            // survive a new phone, and they can never reach Welcome again.
            AiraCard {
                Text(
                    text = "You're using Aira without an account, which is fine — " +
                        "everything works. An account only means what you've saved " +
                        "follows you if you change phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
                Spacer(modifier = Modifier.height(14.dp))
                PrimaryButton(
                    label = "Create an account",
                    onClick = onCreateAccount,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("I already have an account")
                }
            }
        }

        // The two data rights. legal.py and the privacy page both promise you can

        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel("Privacy and settings")

        ToolListRow(
            icon = Icons.Outlined.Info,
            title = "How Aira works",
            subtitle = "The three-card introduction, again",
            onClick = onReplayTutorial,
        )
        ToolListRow(
            icon = Icons.Outlined.Lock,
            title = "Privacy & consent",
            subtitle = "Data, permissions and export",
            onClick = { onOpenTool(AiraTool.Privacy) },
        )
        ToolListRow(
            icon = Icons.Outlined.Memory,
            title = "What Aira remembers",
            subtitle = "Review or forget what it knows",
            onClick = { onOpenTool(AiraTool.Memory) },
        )
        ToolListRow(
            icon = Icons.Outlined.Language,
            // "Voice & language" named a thing this build does not do. The row
            // showed "English · Aira warm" — a voice that never plays — giving
            // a stored preference the same billing as a working setting.
            title = "Language",
            subtitle = language.ifBlank { "How Aira speaks with you" },
            onClick = { onOpenTool(AiraTool.Voice) },
        )
        // The "Companion mode" row is gone. It opened a sheet whose entire
        // content was an explanation that voice and avatar aren't built —
        // a settings row whose only function was to apologise for itself.
        // Restore it when there is a mode to choose.
        ToolListRow(
            icon = Icons.Outlined.Group,
            title = "Partner access",
            // Reflects the consent state rather than describing the feature in
            // the abstract — it defaults to off, so "Practical tasks only" read
            // as though something were already being shared.
            subtitle = if (partnerAccess?.granted == true) {
                "On — share or revoke access"
            } else {
                "Off — nothing is shared"
            },
            onClick = { onOpenTool(AiraTool.Partner) },
            accent = SageDeep,
        )
        ToolListRow(
            icon = Icons.Outlined.SupportAgent,
            title = "Help & human support",
            subtitle = "Care navigator, FAQs and feedback",
            onClick = { onOpenTool(AiraTool.Support) },
            accent = SageDeep,
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Backed by the consent ledger. This was `remember { mutableStateOf(true) }`
        // — flipping it changed a local boolean and nothing else, so the backend
        // went on using remembered context in every reply.
        if (personalisation != null) {
            AiraCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .background(SageMist, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = SageDeep,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI personalisation",
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                        )
                        Text(
                            // Was "When off, nothing Aira remembers shapes its
                            // replies" — accurate, and abstract enough that
                            // nobody could tell what turning it off would cost
                            // them. An example does that in one line.
                            text = if (personalisation.granted) {
                                "On — Aira uses what it knows, so \"is this normal?\" " +
                                    "is answered for where you are"
                            } else {
                                "Off — Aira answers every question as if it were " +
                                    "the first thing you'd asked"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                    Switch(
                        checked = personalisation.granted,
                        onCheckedChange = { onSetConsent("personalization", it) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AiraCard(containerColor = SageMist) {
            Text(
                text = "Never used for advertising",
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
            )
            Text(
                text = "Your sensitive health data is not an ad product.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        }

        // export or delete "at any time"; until now only the web client could,
        // so on Android the promise was unkeepable.
        Spacer(modifier = Modifier.height(22.dp))
        SectionLabel("Your data")
        Spacer(modifier = Modifier.height(8.dp))
        AiraCard {
            Text(
                text = "Export everything Aira holds for you as a JSON file, or erase it. " +
                    "Deletion is immediate and cannot be undone — it removes your profile, " +
                    "conversations, care items, memory, consent history and safety records.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = onExport,
                enabled = !exporting && !deleting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (exporting) "Preparing…" else "Download my data")
            }
            Spacer(modifier = Modifier.height(10.dp))
            // Typed confirmation, not a second tap.
            //
            // The backend requires the exact phrase "DELETE MY DATA" before it
            // will erase an account — a deliberate speed bump — and the client
            // was filling that in on the user's behalf, so the only thing
            // standing between a stray press and every record being destroyed
            // was one more stray press in the same place. Typing the words is
            // the guard the server was asking for.
            if (!confirmDelete) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = !deleting && !exporting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Urgent),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (deleting) "Deleting…" else "Delete all my data")
                }
            } else {
                Text(
                    text = "Type $DELETE_PHRASE to confirm. This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Urgent,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = deleteConfirmation,
                    onValueChange = { deleteConfirmation = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { confirmDelete = false; deleteConfirmation = "" },
                        modifier = Modifier.weight(1f),
                    ) { Text("Keep my data", color = Plum) }
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = deleteConfirmation.trim() == DELETE_PHRASE && !deleting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Urgent),
                    ) {
                        Text(if (deleting) "Deleting…" else "Delete everything")
                    }
                }
            }
        }
    }
}

/**
 * Change name, journey and language after onboarding.
 *
 * Deliberately not a full re-run of onboarding: the pregnancy-week question is
 * left out because weeks move on their own, and re-asking it here would invite
 * a stale number to be re-committed as if it were current.
 */
@Composable
private fun ProfileEditor(
    initialName: String,
    initialJourney: JourneyType?,
    initialLanguage: String,
    initialWeeks: Int?,
    initialPriorities: List<String>,
    onSave: (String, JourneyType?, String, Int?, List<String>?) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var journey by remember(initialJourney) { mutableStateOf(initialJourney) }
    var language by remember(initialLanguage) {
        mutableStateOf(initialLanguage.ifBlank { "English" })
    }
    var weeks by remember(initialWeeks) { mutableStateOf(initialWeeks?.toString().orEmpty()) }
    val chosen = remember(initialPriorities) { initialPriorities.toMutableStateList() }

    AiraCard {
        SectionLabel("What Aira calls you")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Your name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))
        SectionLabel("Where you are now")
        JourneyType.entries.forEach { option ->
            Spacer(modifier = Modifier.height(8.dp))
            ChoiceCard(
                title = option.label,
                subtitle = option.supportingText,
                selected = journey == option,
                onClick = { journey = option },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionLabel("Language")
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("English", "Hindi", "Hinglish").forEach { option ->
                FilterChip(
                    selected = language == option,
                    onClick = { language = option },
                    label = { Text(option) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        // The week, which the server advances on its own from whatever was
        // last reported — so this is a correction, not a weekly chore.
        if (journey == JourneyType.Pregnant) {
            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("How many weeks")
            OutlinedTextField(
                value = weeks,
                onValueChange = { entry -> weeks = entry.filter { it.isDigit() }.take(2) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. 24") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
            )
            Text(
                text = "Aira counts the weeks forward from here, so you only need " +
                    "to change this if it drifts.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // Priorities decide what Today suggests, and were answered once before
        // the user had used the app at all.
        Spacer(modifier = Modifier.height(16.dp))
        SectionLabel("What should Aira focus on")
        Spacer(modifier = Modifier.height(8.dp))
        PRIORITY_OPTIONS.forEach { option ->
            val selected = option in chosen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (selected) chosen.remove(option) else chosen.add(option)
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = selected, onCheckedChange = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(option, style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        PrimaryButton(
            label = "Save profile",
            onClick = {
                onSave(
                    name,
                    journey,
                    language,
                    weeks.toIntOrNull()?.takeIf { journey == JourneyType.Pregnant },
                    chosen.toList(),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Changing your journey changes what Today and Journey show you.",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}


/** The same options onboarding offers, so a change here is a change to the same
 *  answer rather than a second, differently-worded question. */
private val PRIORITY_OPTIONS = listOf(
    "Understand changes",
    "Prepare for a visit",
    "Feel calmer",
    "Plan my care",
)
