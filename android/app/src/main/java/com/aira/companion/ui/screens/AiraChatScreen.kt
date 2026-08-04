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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Mic
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aira.companion.model.AiraTool
import com.aira.companion.model.AiraUiState
import com.aira.companion.model.DetailPage
import com.aira.companion.model.toolForCard
import com.aira.companion.ui.components.ChatBubble
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SafetyBadge
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
import com.aira.companion.ui.theme.SageDeep
import com.aira.companion.ui.theme.SageMist

@Composable
fun AiraChatScreen(
    state: AiraUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onQuickMessage: (String) -> Unit,
    onOpenTools: () -> Unit,
    onOpenTool: (AiraTool) -> Unit,
    onOpenDetail: (DetailPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Keep the thread following the newest turn (message + typing bubble).
    LaunchedEffect(state.messages.size, state.sending) {
        runCatching { listState.animateScrollToItem(state.messages.size) }
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Ivory)
                .imePadding()
                // Lift the input clear of the floating nav pill + system nav bar
                // (keyboard closed). Matches the Videos list bottom clearance.
                .padding(bottom = 104.dp),
    ) {
        LazyColumn(
            state = listState,
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
            items(state.messages, key = { it.id }) { message ->
                Column(
                    modifier = Modifier.animateItem(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChatBubble(text = message.text, fromAira = message.fromAira)
                    // Per-turn safety state (#8): trust shown on the reply itself,
                    // not once at the top of the screen. Urgent turns hand off to
                    // the takeover, so they don't get a chip here.
                    if (message.fromAira && message.decision != null &&
                        message.decision != "urgent"
                    ) {
                        SafetyChip(message.decision)
                    }
                    // The turn's typed suggestions (0-3) as tappable cards.
                    message.cards.forEach { card ->
                        Surface(
                            color = SageMist,
                            shape = RoundedCornerShape(16.dp),
                            onClick = { toolForCard(card.type)?.let(onOpenTool) },
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(
                                    text = card.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Ink,
                                )
                                if (card.subtitle.isNotBlank()) {
                                    Text(
                                        text = card.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Typing indicator while Aira composes a reply (#4).
            if (state.sending) {
                item { TypingBubble() }
            }

            state.todayFeed?.focus?.firstOrNull()?.let { focus ->
                item {
                    val (buttonLabel, action) = when (focus.kind) {
                        "appointment_soon" ->
                            "Prepare questions" to { onOpenTool(AiraTool.Appointment) }
                        "mood_checkin" ->
                            "Log how you feel" to { onOpenTool(AiraTool.CheckIn) }
                        "milestone_week" ->
                            "See your path" to { onOpenDetail(DetailPage.Journey) }
                        else ->
                            "Open today's care" to { onOpenDetail(DetailPage.Care) }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = LilacMist),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Lilac),
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(40.dp)
                                            .background(Plum, RoundedCornerShape(13.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = if (focus.kind == "appointment_soon") {
                                            Icons.Outlined.CalendarMonth
                                        } else {
                                            Icons.Outlined.AutoAwesome
                                        },
                                        contentDescription = null,
                                        tint = Paper,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(11.dp))
                                Column {
                                    Text(
                                        text = "NEXT BEST ACTION",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Plum,
                                    )
                                    Text(
                                        text = "Based on your day right now",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkMuted,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = focus.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = Ink,
                            )
                            if (focus.body.isNotBlank()) {
                                Text(
                                    text = focus.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InkMuted,
                                )
                            }
                            Spacer(modifier = Modifier.height(15.dp))
                            PrimaryButton(
                                label = buttonLabel,
                                onClick = action,
                                modifier = Modifier.fillMaxWidth(),
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
                    val care = state.careSummary
                    val stageChip = when (care?.stage) {
                        "pregnant" -> care.week?.let { "What's happening in week $it?" }
                            ?: "What's happening this week?"
                        "postpartum" -> "Is this normal after birth?"
                        "trying_to_conceive" -> "When is my fertile window?"
                        else -> "I feel tired"
                    }
                    listOf(stageChip, "Set a reminder", "Ask anything").forEach { prompt ->
                        Surface(
                            color = Paper,
                            shape = CircleShape,
                            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineSoft),
                            onClick = {
                                if (prompt == "Set a reminder") {
                                    onOpenTool(AiraTool.Reminder)
                                } else {
                                    onQuickMessage(prompt)
                                }
                            },
                        ) {
                            Text(
                                text = prompt,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = Ink,
                            )
                        }
                    }
                }
            }
        }

        // Gentle, ever-present disclaimer (#19): support, not medical advice.
        Text(
            text = "Aira offers general support, not medical advice.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Paper,
            shadowElevation = 12.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
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
                    // Multi-line (#11): grows up to a few lines for longer questions.
                    maxLines = 5,
                    shape = RoundedCornerShape(20.dp),
                )
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

/** Per-reply trust chip (#8): honest about each turn's gate result. */
@Composable
private fun SafetyChip(decision: String) {
    val error = decision == "error"
    Surface(
        color = if (error) AmberMist else SageMist,
        contentColor = if (error) Amber else SageDeep,
        shape = CircleShape,
    ) {
        Text(
            text = if (error) "Safety check unavailable" else "Safety checked",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** "Aira is thinking" bubble (#4) shown while a reply is on its way. */
@Composable
private fun TypingBubble() {
    Row(verticalAlignment = Alignment.Bottom) {
        Surface(
            modifier = Modifier.size(30.dp),
            color = Plum,
            contentColor = Paper,
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("A", style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.width(9.dp))
        Surface(
            color = Paper,
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp,
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineSoft),
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(InkMuted.copy(alpha = 0.55f)),
                    )
                }
            }
        }
    }
}
