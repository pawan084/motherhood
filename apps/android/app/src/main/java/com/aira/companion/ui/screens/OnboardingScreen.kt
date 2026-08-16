package com.aira.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PregnantWoman
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aira.companion.model.JourneyType
import com.aira.companion.ui.components.AiraCard
import com.aira.companion.ui.components.AnchorChip
import com.aira.companion.ui.components.ChoicePill
import com.aira.companion.ui.components.ObProgress
import com.aira.companion.ui.components.OnboardingStep
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SegmentedControl
import com.aira.companion.ui.components.StatusNote
import com.aira.companion.ui.components.TimeChip
import com.aira.companion.ui.components.ValueCard
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Lilac

/**
 * Onboarding as four dedicated screens: stage → timing → language → reminders.
 *
 * The reference is explicit that this is "one question per screen, not chat".
 * The chat transcript it replaces made every answer a scroll-back: to re-read
 * the language options you had to scroll past the turns above them, and the
 * question you were answering could leave the screen while you thought about it.
 *
 * Each step owns the whole viewport and pins its action to the bottom, so the
 * button is in the same place on every step and on every screen size.
 */
@Composable
fun OnboardingScreen(
    onComplete: (journey: JourneyType, weeks: Int?, language: String, remindersPerDay: Int) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableIntStateOf(0) }
    var journey by remember { mutableStateOf<JourneyType?>(null) }
    var weeks by remember { mutableStateOf<Int?>(null) }
    var timingAnswered by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf("English") }
    var cadence by remember { mutableIntStateOf(1) } // index into 1×/2×/3×

    // Timing is pregnancy-specific. For every other stage the question has no
    // honest form, so the flow is three steps rather than asking it anyway —
    // and the progress rule has to agree, or step 3 of 4 would be the last one.
    val asksTiming = journey == JourneyType.Pregnant
    // Four until a stage says otherwise. Deriving it from a null journey would
    // advertise "of 3" on the very first screen and then renumber to "of 4" the
    // moment someone picked Pregnant — a total that moves while you are reading
    // it is worse than one that is occasionally revised downward by your own
    // choice, which is at least an answer to something you just did.
    val total = if (journey == null || asksTiming) 4 else 3

    fun back() {
        if (step == 0) onExit() else step -= 1
    }

    Surface(modifier = modifier.fillMaxSize(), color = Ivory) {
        // The progress rule sits under the status bar and the back button under
        // that, so the insets belong here rather than on the step frame — without
        // them the back arrow is drawn behind the clock and loses half its target.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            ObProgress(step = step + 1, total = total)
            when (step) {
                0 -> StageStep(
                    total = total,
                    selected = journey,
                    onSelect = { journey = it },
                    onBack = ::back,
                    onContinue = { step = 1 },
                )

                1 -> if (asksTiming) {
                    TimingStep(
                        total = total,
                        weeks = weeks,
                        answered = timingAnswered,
                        onSelect = { picked ->
                            weeks = picked
                            timingAnswered = true
                        },
                        onBack = ::back,
                        onContinue = { step = 2 },
                    )
                } else {
                    LanguageStep(
                        step = 2,
                        total = total,
                        selected = language,
                        onSelect = { language = it },
                        onBack = ::back,
                        onSkip = { step = 2 },
                        onContinue = { step = 2 },
                    )
                }

                2 -> if (asksTiming) {
                    LanguageStep(
                        step = 3,
                        total = total,
                        selected = language,
                        onSelect = { language = it },
                        onBack = ::back,
                        onSkip = { step = 3 },
                        onContinue = { step = 3 },
                    )
                } else {
                    RemindersStep(
                        step = 3,
                        total = total,
                        cadenceIndex = cadence,
                        onCadence = { cadence = it },
                        onBack = ::back,
                        onSkip = { onComplete(journey ?: JourneyType.Exploring, weeks, language, cadence + 1) },
                        onDone = { onComplete(journey ?: JourneyType.Exploring, weeks, language, cadence + 1) },
                    )
                }

                else -> RemindersStep(
                    step = 4,
                    total = total,
                    cadenceIndex = cadence,
                    onCadence = { cadence = it },
                    onBack = ::back,
                    onSkip = { onComplete(journey ?: JourneyType.Exploring, weeks, language, cadence + 1) },
                    onDone = { onComplete(journey ?: JourneyType.Exploring, weeks, language, cadence + 1) },
                )
            }
        }
    }
}

@Composable
private fun StageStep(
    total: Int,
    selected: JourneyType?,
    onSelect: (JourneyType) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    OnboardingStep(
        eyebrow = "Step 1 of $total",
        title = "Where are you in your journey?",
        lede = "This shapes your week, your videos, and how Aira talks to you. " +
            "You can change it anytime.",
        onBack = onBack,
        // No Skip here, deliberately: the week hero, the video shelves and the
        // guidance all branch on this answer, so skipping it would leave every
        // later screen guessing.
        onSkip = null,
        fill = { Deco(Icons.Outlined.PregnantWoman) },
        cta = {
            PrimaryButton(
                label = "Continue",
                onClick = onContinue,
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Spacer(modifier = Modifier.height(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // All five stages, not the reference's three. "After a loss" and
            // "Just exploring" already exist here, and the reference's own stage
            // picker adds the loss path explicitly — dropping them to match a
            // shorter list would close the one door that must never be closed.
            JourneyType.entries.forEach { type ->
                ChoicePill(
                    label = type.label,
                    selected = selected == type,
                    onClick = { onSelect(type) },
                )
            }
        }
    }
}

@Composable
private fun TimingStep(
    total: Int,
    weeks: Int?,
    answered: Boolean,
    onSelect: (Int?) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    OnboardingStep(
        eyebrow = "Step 2 of $total",
        title = "About how far along?",
        lede = "Pregnant · helps Aira show the right week and guidance.",
        onBack = onBack,
        fill = { Deco(Icons.Outlined.CalendarMonth) },
        cta = {
            PrimaryButton(
                label = "Continue",
                onClick = onContinue,
                enabled = answered,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Spacer(modifier = Modifier.height(22.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(8, 16, 24, 32).forEach { anchor ->
                AnchorChip(
                    label = "~$anchor weeks",
                    selected = answered && weeks == anchor,
                    onClick = { onSelect(anchor) },
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        // "Not sure yet" rather than a bare Skip: it records a real answer, so
        // the app knows the week is unknown instead of merely unasked.
        AnchorChip(
            label = "Not sure yet",
            selected = answered && weeks == null,
            onClick = { onSelect(null) },
        )
    }
}

@Composable
private fun LanguageStep(
    step: Int,
    total: Int,
    selected: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
) {
    OnboardingStep(
        eyebrow = "Step $step of $total",
        title = "Which language feels most natural?",
        lede = "You can switch anytime in Settings.",
        onBack = onBack,
        onSkip = onSkip,
        fill = { Deco(Icons.Outlined.Language) },
        cta = {
            PrimaryButton(
                label = "Continue",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Spacer(modifier = Modifier.height(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ValueCard(
                title = "English",
                subtitle = "Continue in English",
                icon = Icons.Outlined.Language,
                selected = selected == "English",
                onClick = { onSelect("English") },
            )
            ValueCard(
                title = "Hindi",
                subtitle = "हिंदी में बातचीत",
                icon = Icons.Outlined.Language,
                selected = selected == "Hindi",
                onClick = { onSelect("Hindi") },
            )
            ValueCard(
                title = "Hinglish",
                subtitle = "A natural mix of Hindi and English",
                icon = Icons.Outlined.Language,
                selected = selected == "Hinglish",
                onClick = { onSelect("Hinglish") },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        StatusNote(
            text = "Core safety guidance is available in all three choices. Some videos may " +
                "use English audio with translated subtitles; Aira will label those clearly.",
            icon = Icons.Outlined.Language,
        )
    }
}

@Composable
private fun RemindersStep(
    step: Int,
    total: Int,
    cadenceIndex: Int,
    onCadence: (Int) -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit,
) {
    // The times shown follow the cadence rather than being fixed decoration: a
    // card promising 09:00 and 20:00 while "3× a day" is selected would be the
    // screen disagreeing with itself.
    val times = when (cadenceIndex) {
        0 -> listOf("09:00")
        1 -> listOf("09:00", "20:00")
        else -> listOf("09:00", "14:00", "20:00")
    }
    OnboardingStep(
        eyebrow = "Step $step of $total",
        title = "How often should we remind you?",
        lede = "Gentle nudges for water and vitamins — change this anytime in Settings.",
        onBack = onBack,
        onSkip = onSkip,
        fill = { Deco(Icons.Outlined.Notifications) },
        cta = {
            PrimaryButton(
                label = "You're all set",
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Spacer(modifier = Modifier.height(22.dp))
        SegmentedControl(
            options = listOf("1× a day", "2× a day", "3× a day"),
            selectedIndex = cadenceIndex,
            onSelect = onCadence,
        )
        Spacer(modifier = Modifier.height(18.dp))
        AiraCard {
            times.forEachIndexed { index, time ->
                if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Reminder ${index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink,
                    )
                    TimeChip(label = time, tinted = false)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quiet hours",
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                )
                Text(
                    text = "No routine reminders overnight",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            TimeChip(label = "22:00–07:00")
        }
    }
}

/** `.fill .deco` — the faint glyph occupying the step's leftover height. */
@Composable
private fun Deco(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(76.dp),
        tint = Lilac,
    )
}
