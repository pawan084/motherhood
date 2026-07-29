package com.aira.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aira.companion.model.AiraUiState
import com.aira.companion.model.JourneyType
import com.aira.companion.model.OnboardingField
import com.aira.companion.model.onboardingPromptsFor
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
    // The question set depends on the journey — the pregnancy-week question is
    // only asked of someone who is pregnant — so it is resolved per render.
    val prompts = onboardingPromptsFor(state.journey)
    val finished = state.onboardingStep >= prompts.size
    val progress =
        (state.onboardingStep.coerceAtMost(prompts.size)).toFloat() / prompts.size.toFloat()

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
                    // Counted, not hardcoded — the set grows by one when someone
                    // says they're pregnant and we ask how many weeks.
                    text = "Hi, I’m Aira. I’ll keep this simple—${prompts.size} short " +
                        "questions, all inside our conversation.",
                    fromAira = true,
                )
            }

            items(state.onboardingAnswers) { answer ->
                ChatBubble(text = answer.question, fromAira = true)
                ChatBubble(text = answer.answer, fromAira = false)
            }

            if (!finished) {
                val prompt = prompts[state.onboardingStep]
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
                if (prompt.options.isEmpty()) {
                    // Free-text prompts (name, pregnancy week). `key` resets the
                    // field between questions so an answer can't leak forward.
                    item(key = "input-${prompt.field}") {
                        FreeTextAnswer(
                            hint = prompt.inputHint,
                            numeric = prompt.numeric,
                            skippable = prompt.skippable,
                            onSubmit = onAnswer,
                        )
                    }
                } else {
                    items(prompt.options) { option ->
                        val helper =
                            if (prompt.field == OnboardingField.Journey) {
                                JourneyType.entries.firstOrNull { it.label == option }?.supportingText
                            } else {
                                optionSupport(prompt.field, option)
                            }
                        ChoiceCard(
                            title = option,
                            subtitle = helper,
                            icon = optionIcon(prompt.field, option),
                            onClick = { onAnswer(option) },
                        )
                    }
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
                                    // The journey the person actually picked. This was hardcoded
                                    // to "Pregnancy guidance", so someone who chose "Trying to
                                    // conceive" was told their context was set up for pregnancy.
                                    text = "${state.journey?.label ?: "Wellness support"} · " +
                                        state.language,
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

/** A free-text onboarding answer (name, pregnancy week). */
@Composable
private fun FreeTextAnswer(
    hint: String,
    numeric: Boolean,
    skippable: Boolean,
    onSubmit: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint) },
            singleLine = true,
            shape = RoundedCornerShape(17.dp),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onSubmit(value) }),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(
                label = "Continue",
                onClick = { onSubmit(value) },
                modifier = Modifier.weight(1f),
                enabled = value.isNotBlank(),
            )
            if (skippable) {
                Spacer(modifier = Modifier.width(10.dp))
                // Submits blank; the ViewModel records it as "Skipped" and sends
                // nothing for that field.
                TextButton(onClick = { onSubmit("") }) {
                    Text("Skip", color = Plum)
                }
            }
        }
    }
}

private fun optionSupport(
    field: OnboardingField,
    option: String,
): String? =
    when (field) {
        OnboardingField.Language ->
            when (option) {
                "English" -> "Continue in English"
                "Hindi" -> "हिंदी में बातचीत"
                "Hinglish" -> "A natural mix of Hindi and English"
                else -> null
            }
        OnboardingField.Priority ->
            when (option) {
                // Was "Week-by-week body and baby context" — pregnancy copy shown
                // to postpartum and trying-to-conceive users too.
                "Understand changes" -> "What's typical for where you are now"
                "Prepare for a visit" -> "Questions, notes and appointment copilot"
                "Feel calmer" -> "Gentle check-ins and guided resets"
                "Plan my care" -> "Medicines, reminders and documents"
                else -> null
            }
        else -> null
    }

private fun optionIcon(
    field: OnboardingField,
    option: String,
): ImageVector =
    when (field) {
        OnboardingField.Journey -> Icons.Outlined.FavoriteBorder
        OnboardingField.Language -> Icons.Outlined.Language
        OnboardingField.Priority -> Icons.Outlined.AutoAwesome
        else -> Icons.Outlined.AutoAwesome
    }
