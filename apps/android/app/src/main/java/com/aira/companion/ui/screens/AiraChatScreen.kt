package com.aira.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aira.companion.model.AiraTool
import com.aira.companion.model.ChatMessage
import com.aira.companion.model.chatTipFor
import com.aira.companion.model.quickPromptsFor
import com.aira.companion.model.toolForActionCard
import com.aira.companion.model.AiraUiState
import com.aira.companion.ui.components.ChatBubble
import com.aira.companion.ui.components.dayLabel
import com.aira.companion.ui.components.dayOf
import com.aira.companion.ui.components.EscalationNudge
import com.aira.companion.ui.components.MemoryStrip
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SafetyBadge
import com.aira.companion.ui.components.TypingIndicator
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Lilac
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.OutlineSoft
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.SageMist

@Composable
fun AiraChatScreen(
    state: AiraUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    /** Send a message that never reached the server. */
    onRetry: (Long) -> Unit = {},
    onRate: (ChatMessage, Boolean) -> Unit = { _, _ -> },
    onQuickMessage: (String) -> Unit,
    onOpenTools: () -> Unit,
    onOpenTool: (AiraTool) -> Unit,
    modifier: Modifier = Modifier,
    /** Dials the saved care-team number. */
    onCallCareTeam: () -> Unit = {},
    /** Opens the emergency profile, where the number is entered. */
    onAddCareTeam: () -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Ivory)
                .imePadding(),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 18.dp,
                    bottom = 22.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Was unconditional "Safety checked" — a promise about the
                    // safety system that held even when only the deterministic
                    // keyword floor was running. Then it read "Keyword-only
                    // screening", which is accurate and means nothing to the
                    // person reading it: "keyword" and "screening" describe our
                    // implementation, not their safety. Say what it means for
                    // them instead, and keep the detail for the Updates entry.
                    SafetyBadge(
                        text = if (state.screeningDegraded) {
                            "Basic safety checks only"
                        } else {
                            "Every reply is safety checked"
                        },
                    )
                }
            }

            // What Aira is currently using to personalise, and the way to change
            // it. Only approved memories: an item the user has not approved is
            // not being used, and listing it would misreport what is happening.
            val remembered = state.memory.filter { it.approved }.map { it.value }
            if (remembered.isNotEmpty()) {
                item {
                    MemoryStrip(
                        items = remembered.take(3),
                        onManage = { onOpenTool(AiraTool.Memory) },
                    )
                }
            }

            // A day heading whenever the conversation crosses midnight.
            //
            // History survives a restart now, so last night's worry sits
            // directly above this morning's question with nothing between them.
            // In an app people re-read to work out whether something has been
            // going on for one day or four, that gap is the information.
            itemsIndexed(state.messages, key = { _, m -> m.id }) { index, message ->
                val day = message.at?.let { dayOf(it) }
                val previousDay = state.messages.getOrNull(index - 1)?.at?.let { dayOf(it) }
                if (day != null && day != previousDay) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = dayLabel(message.at),
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted,
                        )
                    }
                }
                ChatBubble(
                    text = message.text,
                    fromAira = message.fromAira,
                    at = message.at,
                    failed = message.failed,
                    onRetry = { onRetry(message.id) },
                    // Not offered on the canned welcome line, which has no
                    // server turn behind it to attach the answer to.
                    onRate = if (message.fromAira && message.at != null) {
                        { helpful -> onRate(message, helpful) }
                    } else {
                        null
                    },
                    rated = message.rated,
                    disclaimer = message.disclaimer,
                    trustLabel = message.trustLabel,
                    card = message.card,
                    // Null when the tool name is not one we can open, which
                    // keeps the card from being drawn at all.
                    onCardClick = message.card
                        ?.let { toolForActionCard(it.tool) }
                        ?.let { tool -> { onOpenTool(tool) } },
                )

                // The middle step between an ordinary reply and the Urgent Help
                // takeover. "watchful" is the backend's own screening verdict
                // for concerning-but-not-red-flag language, and until now it
                // only tinted a chip — the turn that most needed a route to care
                // was the one that offered none.
                if (message.fromAira && message.trustLabel == "watchful") {
                    EscalationNudge(
                        title = "Please contact your care team today",
                        body = "This is worth checking with a professional the same day, " +
                            "not tomorrow. Aira can't assess it for you.",
                        phone = state.careTeamPhone,
                        onCall = onCallCareTeam,
                        onAddCareTeam = onAddCareTeam,
                    )
                }
            }

            // Waiting for a reply showed nothing at all — no spinner, no
            // placeholder — so a slow network was indistinguishable from a
            // message that never sent.
            if (state.sending) {
                item { TypingIndicator() }
            }

            // The chat used to carry its own "Suggested for you" card, naming
            // the same action Today proposes — "Prepare for your visit" here,
            // "Prepare for your next appointment" there, and a third wording on
            // the server. Two surfaces proposing, disagreeing about the name, and
            // neither one being the place Aira tells you what matters.
            //
            // Today proposes; this screen is where you act. The quick prompts
            // below stay, because they are inputs to a conversation rather than
            // a second dashboard — a blank composer is the worst discoverability
            // in the app.

            // Shown only while the conversation is still just the greeting.
            // Once someone is actually talking it is in the way, and a tip that
            // outstays its usefulness is the thing people learn to scroll past.
            if (state.messages.size <= 1) {
                item {
                    val tip = chatTipFor(state.todayData?.journey)
                    Surface(
                        color = LilacMist,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(15.dp)) {
                            Text(
                                text = tip.eyebrow.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Plum,
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = tip.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ink,
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    quickPromptsFor(state.todayData?.journey).forEach { prompt ->
                        Surface(
                            color = Paper,
                            shape = CircleShape,
                            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineSoft),
                            onClick = {
                                val tool = prompt.tool
                                if (tool != null) onOpenTool(tool) else onQuickMessage(prompt.label)
                            },
                        ) {
                            Text(
                                text = prompt.label,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = Ink,
                            )
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Paper,
            shadowElevation = 12.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                IconButton(onClick = onOpenTools) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Open Aira tools",
                        tint = Plum,
                    )
                }
                OutlinedTextField(
                    value = state.chatDraft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Message Aira…", color = InkMuted)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                )
                // The mic is gone, not disabled. It once sent the literal text
                // "Start a voice conversation" into the chat as if the user had
                // typed it; then it became a greyed-out button that explained
                // itself when tapped. Both versions occupy the composer with a
                // promise of speech input that does not exist — and a control
                // you cannot use is worse than one you never see, because it
                // reads as something broken about your phone. Bring it back
                // with the feature.
                FilledIconButton(
                    onClick = onSend,
                    enabled = state.chatDraft.isNotBlank(),
                    colors =
                        androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = Plum,
                            contentColor = Paper,
                            disabledContainerColor = SageMist,
                            disabledContentColor = InkMuted,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send message",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
