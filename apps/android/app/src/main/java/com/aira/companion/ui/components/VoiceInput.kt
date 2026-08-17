package com.aira.companion.ui.components

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Speech-to-text, wrapped so a screen can treat it as state.
 *
 * The app previously said, in its own copy, that voice "isn't wired up and the
 * mic is disabled". This wires it to Android's on-device recogniser rather than
 * to anything of ours: audio is turned into text by the platform, the text is
 * shown for review, and only text is ever sent. No audio is recorded, stored or
 * uploaded by this app.
 *
 * [available] is checked rather than assumed. Not every device or profile has a
 * recognition service, and offering a microphone that opens a screen which
 * cannot listen is worse than not offering one.
 */
class VoiceInputState internal constructor(
    val available: Boolean,
) {
    /** Live text as the recogniser refines it. Not yet the user's message. */
    var partial by mutableStateOf("")
        internal set

    /** The recogniser's settled answer, awaiting review. */
    var finalText by mutableStateOf<String?>(null)
        internal set

    var listening by mutableStateOf(false)
        internal set

    /**
     * Human-readable failure, or null.
     *
     * Kept as a sentence rather than an error code because it is shown to
     * someone who wanted to speak and could not, and "ERROR_NO_MATCH" tells
     * them nothing about what to do next.
     */
    var error by mutableStateOf<String?>(null)
        internal set

    /** Smoothed microphone level, 0..1, for the waveform. */
    var level by mutableFloatStateOf(0f)
        internal set

    internal var start: () -> Unit = {}
    internal var stop: () -> Unit = {}
    internal var reset: () -> Unit = {}

    fun startListening() = start()
    fun stopListening() = stop()

    /** Discard the transcript and the error, back to a clean listening screen. */
    fun clear() = reset()
}

private fun messageFor(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_AUDIO -> "Couldn't read the microphone."
    SpeechRecognizer.ERROR_CLIENT -> "Voice input stopped unexpectedly."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
        "Aira needs microphone access to listen."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
        "Voice input needs a connection right now."
    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again?"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Still finishing the last one."
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't hear anything."
    else -> "Voice input isn't working right now."
}

@Composable
fun rememberVoiceInput(): VoiceInputState {
    val context = LocalContext.current
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val state = remember { VoiceInputState(available) }

    DisposableEffect(available) {
        if (!available) {
            return@DisposableEffect onDispose { }
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            // Partials drive the live line on the listening screen. Without them
            // the screen sits silent until the recogniser finishes, which reads
            // as not hearing you.
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Prefer on-device recognition where the platform offers it: this is
            // someone describing a symptom out loud.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                state.listening = true
                state.error = null
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) {
                // rmsdB is roughly -2..10 in practice. Normalised and smoothed so
                // the bars move with the voice instead of flickering.
                val normalised = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                state.level = state.level * 0.6f + normalised * 0.4f
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                state.listening = false
                state.level = 0f
            }

            override fun onError(error: Int) {
                state.listening = false
                state.level = 0f
                // A no-match after the user has already said something useful
                // would otherwise throw away a good partial.
                if (error == SpeechRecognizer.ERROR_NO_MATCH && state.partial.isNotBlank()) {
                    state.finalText = state.partial
                } else {
                    state.error = messageFor(error)
                }
            }

            override fun onResults(results: android.os.Bundle?) {
                val best = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                state.listening = false
                state.level = 0f
                if (best != null) {
                    state.finalText = best
                } else {
                    state.error = "Didn't catch that — try again?"
                }
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {
                partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let { state.partial = it }
            }

            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })

        state.start = {
            state.partial = ""
            state.finalText = null
            state.error = null
            runCatching { recognizer.startListening(intent) }
                .onFailure { state.error = "Voice input isn't working right now." }
        }
        state.stop = { runCatching { recognizer.stopListening() } }
        state.reset = {
            state.partial = ""
            state.finalText = null
            state.error = null
            state.level = 0f
        }

        onDispose {
            // Destroyed with the screen. A recogniser left alive holds the
            // microphone, and holding a microphone after the user has navigated
            // away is exactly the thing this app must never do.
            runCatching { recognizer.stopListening() }
            runCatching { recognizer.destroy() }
            state.listening = false
        }
    }

    return state
}

/** Whether this device can offer voice input at all. */
fun voiceInputAvailable(context: Context): Boolean =
    SpeechRecognizer.isRecognitionAvailable(context)
