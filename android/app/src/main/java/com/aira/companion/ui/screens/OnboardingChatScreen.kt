package com.aira.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aira.companion.model.AiraUiState
import com.aira.companion.model.JourneyType
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.BrandOrb
import com.aira.companion.ui.components.ChatBubble
import com.aira.companion.ui.components.ChoiceCard
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SafetyBadge
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.SageMist

@Composable
fun OnboardingChatScreen(
    state: AiraUiState,
    onAnswer: (String) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finished = state.onboardingStep >= state.prompts.size
    val progress =
        (state.onboardingStep.coerceAtMost(state.prompts.size)).toFloat() /
            state.prompts.size.toFloat()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Ivory)
                .statusBarsPadding(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Paper)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandOrb(compact = true)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Aira", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    if (finished) "Your care context is ready" else "Getting to know you",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            SafetyBadge()
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = Plum,
            trackColor = LilacMist,
        )

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .weight(1f),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 20.dp,
                    bottom = 28.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ChatBubble(
                    text = "Hi, I’m Aira. I’ll keep this simple—four short questions, all inside our conversation.",
                    fromAira = true,
                )
            }

            items(state.onboardingAnswers) { answer ->
                ChatBubble(text = answer.question, fromAira = true)
                ChatBubble(text = answer.answer, fromAira = false)
            }

            if (!finished) {
                val prompt = state.prompts[state.onboardingStep]
                item {
                    ChatBubble(text = prompt.question, fromAira = true)
                }
                item {
                    Text(
                        text = prompt.helper,
                        modifier = Modifier.padding(start = 42.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
                items(prompt.options) { option ->
                    val helper =
                        if (state.onboardingStep == 0) {
                            JourneyType.entries.firstOrNull { it.label == option }?.supportingText
                        } else {
                            optionSupport(state.onboardingStep, option)
                        }
                    ChoiceCard(
                        title = option,
                        subtitle = helper,
                        icon = optionIcon(state.onboardingStep, option),
                        onClick = { onAnswer(option) },
                    )
                }
            } else {
                item {
                    ChatBubble(
                        text = "Thank you. I’ll begin quietly and only surface what matters now.",
                        fromAira = true,
                    )
                }
                item {
                    AiraCard(containerColor = Paper) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(44.dp)
                                        .background(SageMist, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Plum,
                                )
                            }
                            Spacer(modifier = Modifier.width(13.dp))
                            Column {
                                Text(
                                    text = "Your private care context is ready",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Ink,
                                )
                                Text(
                                    text = "Pregnancy guidance · ${state.language} · ${state.companionPreference}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkMuted,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        PrimaryButton(
                            label = "Open my Today",
                            onClick = onFinish,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private fun optionSupport(
    step: Int,
    option: String,
): String? =
    when (step) {
        1 ->
            when (option) {
                "English" -> "Continue in English"
                "Hindi" -> "हिंदी में बातचीत"
                "Hinglish" -> "A natural mix of Hindi and English"
                else -> null
            }
        2 ->
            when (option) {
                "Understand changes" -> "Week-by-week body and baby context"
                "Prepare for a visit" -> "Questions, notes and appointment copilot"
                "Feel calmer" -> "Gentle check-ins and guided resets"
                "Plan my care" -> "Medicines, reminders and documents"
                else -> null
            }
        3 ->
            when (option) {
                "Text & voice" -> "Switch naturally between typing and speaking"
                "Talking avatar" -> "A warm, lip-synced companion mode"
                "Chat only" -> "A quiet text-first experience"
                else -> null
            }
        else -> null
    }

private fun optionIcon(
    step: Int,
    option: String,
): ImageVector =
    when (step) {
        0 -> Icons.Outlined.FavoriteBorder
        1 -> Icons.Outlined.Language
        2 -> Icons.Outlined.AutoAwesome
        3 -> if (option == "Chat only") Icons.Outlined.ChatBubbleOutline else Icons.Outlined.MicNone
        else -> Icons.Outlined.AutoAwesome
    }
