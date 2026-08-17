package com.aira.companion.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SecondaryButton
import com.aira.companion.ui.components.StatusNote
import com.aira.companion.ui.components.VoiceInputState
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Lilac
import com.aira.companion.ui.theme.LilacMist
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.Urgent

/**
 * Voice mode: the listening view, and the transcript review that follows it.
 *
 * These are one screen rather than two because they are one decision. A speech
 * recogniser mishears, and this app is used to describe symptoms — "I've had
 * some bleeding" and "I've had some swelling" are one consonant apart and would
 * be answered very differently. So nothing is sent from voice without the person
 * seeing the words first; the transcript is a draft, not a message.
 */
@Composable
fun VoiceModeScreen(
    voice: VoiceInputState,
    onSend: (String) -> Unit,
    onSwitchToTyping: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transcript = voice.finalText
    Surface(modifier = modifier.fillMaxSize(), color = Ivory) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            if (transcript == null) {
                Listening(voice = voice)
            } else {
                TranscriptReview(
                    transcript = transcript,
                    onSend = { onSend(transcript) },
                    onRetry = {
                        voice.clear()
                        voice.startListening()
                    },
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Spacer(modifier = Modifier.height(16.dp))
            StatusNote(
                text = "Your speech is turned into text on this device. Aira only ever " +
                    "receives the words you approve — no audio is recorded or sent.",
                icon = Icons.Outlined.Lock,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Aira offers general support, not medical advice.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryButton(
                    label = "Switch to typing",
                    onClick = onSwitchToTyping,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = CircleShape,
                    color = if (voice.listening) Urgent else Plum,
                    contentColor = Paper,
                    onClick = {
                        if (voice.listening) voice.stopListening() else voice.startListening()
                    },
                ) {
                    Icon(
                        imageVector = if (voice.listening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                        contentDescription = if (voice.listening) "Stop listening" else "Start listening",
                        modifier = Modifier
                            .padding(16.dp)
                            .size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Listening(voice: VoiceInputState) {
    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        // The halo tracks the real microphone level rather than animating on a
        // timer, so the screen is showing that it can hear you rather than only
        // that it is busy.
        val scale by animateFloatAsState(
            targetValue = 0.75f + voice.level * 0.45f,
            label = "mic-halo",
        )
        Box(
            modifier = Modifier
                .size((120 * scale).dp)
                .background(Lilac.copy(alpha = 0.35f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(LilacMist, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = null,
                tint = Plum,
                modifier = Modifier.size(30.dp),
            )
        }
    }
    Spacer(modifier = Modifier.height(26.dp))

    val heading = when {
        voice.error != null -> voice.error!!
        voice.partial.isNotBlank() -> "“${voice.partial}”"
        voice.listening -> "Listening…"
        else -> "Tap the microphone to start"
    }
    Text(
        text = heading,
        // A live region: the partial text changes as you speak, and a screen
        // reader user needs to hear that it is being heard.
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.headlineSmall,
        color = if (voice.error != null) Urgent else Ink,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Speak naturally. You'll see the words before anything is sent.",
        style = MaterialTheme.typography.bodySmall,
        color = InkMuted,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(22.dp))
    Waveform(level = voice.level, active = voice.listening)
}

/** Five bars driven by the live microphone level. */
@Composable
private fun Waveform(level: Float, active: Boolean) {
    val heights = listOf(0.45f, 0.85f, 0.6f, 1f, 0.5f)
    Row(
        modifier = Modifier.semantics { contentDescription = "Microphone level" },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEach { factor ->
            val target = if (active) (6f + level * 28f * factor) else 6f
            val height by animateFloatAsState(targetValue = target, label = "bar")
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height.dp)
                    .background(Plum, RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun TranscriptReview(
    transcript: String,
    onSend: () -> Unit,
    onRetry: () -> Unit,
) {
    Text(
        text = "Send this?",
        style = MaterialTheme.typography.headlineSmall,
        color = Ink,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Paper,
    ) {
        Text(
            text = transcript,
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = "Speech recognition makes mistakes, and a misheard symptom is " +
            "answered as though you said it. Check the words before sending.",
        style = MaterialTheme.typography.bodySmall,
        color = InkMuted,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(16.dp))
    PrimaryButton(
        label = "Send to Aira",
        onClick = onSend,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    SecondaryButton(
        label = "Say it again",
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
    )
}
