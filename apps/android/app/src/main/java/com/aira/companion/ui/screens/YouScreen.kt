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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aira.companion.data.ConsentFeature
import com.aira.companion.model.AiraTool
import com.aira.companion.model.journeyLabel
import com.aira.companion.ui.components.AiraCard
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
) {
    LaunchedEffect(Unit) { onLoadConsent() }
    var confirmDelete by remember { mutableStateOf(false) }
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
            Column {
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
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel("Your care, your control")

        ToolListRow(
            icon = Icons.Outlined.Lock,
            title = "Privacy & consent",
            subtitle = "Data, permissions and export",
            onClick = { onOpenTool(AiraTool.Privacy) },
        )
        ToolListRow(
            icon = Icons.Outlined.Memory,
            title = "What Aira remembers",
            subtitle = "Review or forget care context",
            onClick = { onOpenTool(AiraTool.Memory) },
        )
        ToolListRow(
            icon = Icons.Outlined.Language,
            title = "Voice & language",
            // Was hardcoded "English · Aira warm voice" regardless of what the
            // user had chosen or what the server had stored.
            subtitle = listOfNotNull(
                language.ifBlank { null },
                voice.ifBlank { null },
            ).joinToString(" · ").ifBlank { "Conversation settings" },
            onClick = { onOpenTool(AiraTool.Voice) },
        )
        ToolListRow(
            icon = Icons.Outlined.RecordVoiceOver,
            title = "Companion mode",
            // Was "Text, voice or talking avatar" — it advertised two modes the
            // build doesn't have, on the row that opens the sheet where both are
            // disabled and labelled as such.
            subtitle = "Chat today; voice and avatar aren't wired up yet",
            onClick = { onOpenTool(AiraTool.Companion) },
        )
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
                            text = "When off, nothing Aira remembers shapes its replies",
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
                        "everything works. An account only means your care context " +
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
            // Two-tap rather than one, matching the web client: an irreversible
            // erase should not be a single stray press.
            OutlinedButton(
                onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
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
                Text(
                    when {
                        deleting -> "Deleting…"
                        confirmDelete -> "Tap again to permanently delete"
                        else -> "Delete all my data"
                    },
                )
            }
        }
    }
}
