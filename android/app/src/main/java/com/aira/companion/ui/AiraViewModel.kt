package com.aira.companion.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aira.companion.model.AiraTool
import com.aira.companion.model.AiraUiState
import com.aira.companion.model.AppStage
import com.aira.companion.model.ChatMessage
import com.aira.companion.model.JourneyType
import com.aira.companion.model.MainDestination
import com.aira.companion.model.OnboardingAnswer
import com.aira.companion.model.postpartumAnchorPrompt
import com.aira.companion.model.pregnancyAnchorPrompt
import com.aira.companion.net.AiraApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Wired to the real Aira backend (P11): onboarding writes the care context,
 * every chat turn runs through the server-side safety gate, and an urgent
 * decision opens the Urgent Help screen — the same contract as the web
 * client. The gate lives SERVER-side; this app never decides safety locally.
 */
class AiraViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(AiraUiState())
    val uiState: StateFlow<AiraUiState> = _uiState.asStateFlow()

    private val appContext get() = getApplication<Application>()

    fun startOnboarding() {
        _uiState.update { it.copy(stage = AppStage.Onboarding) }
    }

    fun answerOnboarding(answer: String) {
        _uiState.update { state ->
            val prompt = state.prompts[state.onboardingStep]
            var next =
                state.copy(
                    onboardingAnswers = state.onboardingAnswers + OnboardingAnswer(prompt.question, answer),
                    onboardingStep = state.onboardingStep + 1,
                )

            when (state.onboardingStep) {
                0 -> {
                    val journey = JourneyType.entries.firstOrNull { it.label == answer }
                    next = next.copy(journey = journey)
                    // Pregnant/postpartum contexts need an anchor date the
                    // backend can compute a week from — append the question.
                    val anchor = when (journey) {
                        JourneyType.Pregnant -> pregnancyAnchorPrompt
                        JourneyType.Postpartum -> postpartumAnchorPrompt
                        else -> null
                    }
                    if (anchor != null) next = next.copy(prompts = next.prompts + anchor)
                }
                1 -> next = next.copy(language = answer)
                2 -> next = next.copy(priority = answer)
                3 -> next = next.copy(companionPreference = answer)
                else -> next = next.copy(anchorChoice = answer)
            }

            next
        }
    }

    fun finishOnboarding() {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                stage = AppStage.Main,
                destination = MainDestination.Today,
                messages =
                    listOf(
                        ChatMessage(
                            id = 1,
                            fromAira = true,
                            text = "Thanks — your private care context is ready. How are you feeling today?",
                        ),
                    ),
            )
        }
        viewModelScope.launch {
            runCatching { pushCareContext(state) }
                .onFailure {
                    notify("Couldn't sync your context yet — Aira will still chat, and you can retry from You.")
                }
        }
    }

    private suspend fun pushCareContext(state: AiraUiState) {
        val language = when (state.language) {
            "Hindi" -> "hi"
            "Hinglish" -> "hi-Latn"
            else -> "en"
        }
        val today = LocalDate.now()
        when (state.journey) {
            JourneyType.Trying ->
                AiraApi.putCareContext(appContext, "trying_to_conceive", language = language)
            JourneyType.Pregnant -> {
                val week = when (state.anchorChoice) {
                    "~8 weeks" -> 8
                    "~16 weeks" -> 16
                    "~32 weeks" -> 32
                    else -> 24
                }
                AiraApi.putCareContext(
                    appContext, "pregnant",
                    dueDate = today.plusDays(((40 - week) * 7).toLong()).toString(),
                    language = language,
                )
            }
            JourneyType.Postpartum -> {
                val daysAgo = when (state.anchorChoice) {
                    "Under 2 weeks" -> 7L
                    "About a month" -> 30L
                    "2–3 months" -> 75L
                    else -> 135L
                }
                AiraApi.putCareContext(
                    appContext, "postpartum",
                    birthDate = today.minusDays(daysAgo).toString(),
                    language = language,
                )
            }
            // Just exploring: no care context yet — the chat gently asks, and
            // the gate treats an unknown stage by applying every rule.
            else -> Unit
        }
    }

    fun selectDestination(destination: MainDestination) {
        _uiState.update { it.copy(destination = destination, toolsOpen = false) }
    }

    fun openTools() {
        _uiState.update { it.copy(toolsOpen = true) }
    }

    fun closeTools() {
        _uiState.update { it.copy(toolsOpen = false) }
    }

    fun openTool(tool: AiraTool) {
        _uiState.update { it.copy(activeTool = tool, toolsOpen = false) }
    }

    fun closeTool() {
        _uiState.update { it.copy(activeTool = null) }
    }

    fun openUrgentHelp() {
        _uiState.update { it.copy(urgentHelpOpen = true, activeTool = null, toolsOpen = false) }
    }

    fun closeUrgentHelp() {
        _uiState.update { it.copy(urgentHelpOpen = false) }
    }

    fun updateDraft(value: String) {
        _uiState.update { it.copy(chatDraft = value) }
    }

    fun sendMessage() {
        val clean = _uiState.value.chatDraft.trim()
        if (clean.isEmpty() || _uiState.value.sending) return
        _uiState.update { it.copy(chatDraft = "") }
        deliver(clean)
    }

    fun quickMessage(text: String) = deliver(text)

    /** One real, gated turn against the backend. */
    private fun deliver(text: String) {
        if (_uiState.value.sending) return
        val history = _uiState.value.messages.map { it.fromAira to it.text }
        _uiState.update { state ->
            state.copy(
                messages = state.messages +
                    ChatMessage(id = System.nanoTime(), fromAira = false, text = text),
                sending = true,
            )
        }
        viewModelScope.launch {
            val turn = runCatching { AiraApi.respond(appContext, text, history) }
                .getOrElse {
                    appendAira(
                        "Aira can't reach its care service right now. If anything feels urgent, " +
                            "contact your care team — don't wait for the app.",
                    )
                    _uiState.update { it.copy(sending = false) }
                    return@launch
                }
            when (turn.decision) {
                "urgent" -> {
                    appendAira(turn.urgentHeadline ?: "Please contact your care team now.")
                    _uiState.update { it.copy(urgentHelpOpen = true, sending = false) }
                }
                "error" -> {
                    appendAira(turn.message ?: "Aira can't safely respond right now.")
                    _uiState.update { it.copy(sending = false) }
                }
                else -> {
                    appendAira(turn.reply ?: "")
                    if (turn.cardTitles.isNotEmpty()) {
                        appendAira("You could try: " + turn.cardTitles.joinToString(" · "))
                    }
                    _uiState.update { it.copy(sending = false) }
                }
            }
        }
    }

    private fun appendAira(text: String) {
        if (text.isBlank()) return
        _uiState.update { state ->
            state.copy(
                messages = state.messages +
                    ChatMessage(id = System.nanoTime(), fromAira = true, text = text),
            )
        }
    }

    fun notify(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun resetDemo() {
        _uiState.value = AiraUiState()
    }
}
