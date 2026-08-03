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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aira.companion.model.AiraTool
import com.aira.companion.model.AiraUiState
import com.aira.companion.ui.components.ChatBubble
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SafetyBadge
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
    onQuickMessage: (String) -> Unit,
    onOpenTools: () -> Unit,
    onOpenTool: (AiraTool) -> Unit,
    modifier: Modifier = Modifier,
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
                    SafetyBadge()
                }
            }

            items(state.messages, key = { it.id }) { message ->
                ChatBubble(text = message.text, fromAira = message.fromAira)
            }

            item {
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
                                    imageVector = Icons.Outlined.CalendarMonth,
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
                                    text = "Based on your context",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkMuted,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Appointment copilot",
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink,
                        )
                        Text(
                            text = "Three questions based on Week 24 and your fatigue note.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted,
                        )
                        Spacer(modifier = Modifier.height(15.dp))
                        PrimaryButton(
                            label = "Prepare questions",
                            onClick = { onOpenTool(AiraTool.Appointment) },
                            modifier = Modifier.fillMaxWidth(),
                        )
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
                    listOf("I feel tired", "Set a reminder", "Ask anything").forEach { prompt ->
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
                IconButton(onClick = { onQuickMessage("Start a voice conversation") }) {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = "Start voice conversation",
                        tint = Plum,
                    )
                }
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
