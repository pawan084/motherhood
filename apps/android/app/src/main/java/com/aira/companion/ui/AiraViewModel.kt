package com.aira.companion.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aira.companion.data.AiraApi
import com.aira.companion.model.AiraTool
import com.aira.companion.model.AiraUiState
import com.aira.companion.model.AppStage
import com.aira.companion.model.ChatMessage
import com.aira.companion.model.JourneyType
import com.aira.companion.model.MainDestination
import com.aira.companion.model.OnboardingAnswer
import com.aira.companion.model.onboardingPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single source of UI state. Network-touching actions take an optional [Context]
 * (supplied by the composables via LocalContext) — when it is null (tests, or an
 * offline path) the ViewModel skips the backend and uses a safe local fallback,
 * so it stays a plain, dependency-free [ViewModel] that unit tests construct with
 * no arguments.
 */
class AiraViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AiraUiState())
    val uiState: StateFlow<AiraUiState> = _uiState.asStateFlow()

    fun startOnboarding() {
        _uiState.update { it.copy(stage = AppStage.Onboarding) }
    }

    fun answerOnboarding(answer: String) {
        _uiState.update { state ->
            // Guard the index: answerOnboarding is public and tap-driven, so a
            // queued/double tap past the last prompt must be a no-op, not a crash.
            if (state.onboardingStep >= onboardingPrompts.size) return@update state
            val prompt = onboardingPrompts[state.onboardingStep]
            var next =
                state.copy(
                    onboardingAnswers = state.onboardingAnswers + OnboardingAnswer(prompt.question, answer),
                    onboardingStep = state.onboardingStep + 1,
                )

            when (state.onboardingStep) {
                0 ->
                    next =
                        next.copy(
                            journey = JourneyType.entries.firstOrNull { it.label == answer },
                        )
                1 -> next = next.copy(language = answer)
                2 -> next = next.copy(priority = answer)
                3 -> next = next.copy(companionPreference = answer)
            }

            next
        }
    }

    fun finishOnboarding(context: Context? = null) {
        val snapshot = _uiState.value
        _uiState.update {
            it.copy(
                stage = AppStage.Main,
                destination = MainDestination.Today,
                messages =
                    listOf(
                        ChatMessage(
                            id = 1,
                            fromAira = true,
                            text = "You're all set. I'll keep Today focused on one meaningful step — " +
                                "ask me anything by text or voice whenever you like.",
                        ),
                    ),
            )
        }
        if (context == null) return
        // Persist the collected onboarding (best-effort). The journey is what makes
        // Today/Journey content correct for this user, per-journey on the backend.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AiraApi.onboarding(
                    ctx = context,
                    journey = apiJourney(snapshot.journey),
                    name = null,
                    language = snapshot.language,
                    priorities = listOfNotNull(snapshot.priority.takeIf { it.isNotBlank() }),
                    weeks = null,
                )
            } catch (_: Exception) {
            }
        }
    }

    fun selectDestination(destination: MainDestination) {
        _uiState.update { it.copy(destination = destination, toolsOpen = false) }
    }

    /** Load journey-aware Today content (best-effort; keeps last data on failure). */
    fun loadToday(context: Context?) {
        if (context == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = AiraApi.today(context)
                _uiState.update { it.copy(todayData = data) }
            } catch (_: Exception) {
            }
        }
    }

    /** Load journey-aware Journey content (best-effort). */
    fun loadJourney(context: Context?) {
        if (context == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = AiraApi.journey(context)
                _uiState.update { it.copy(journeyData = data) }
            } catch (_: Exception) {
            }
        }
    }

    fun openTools() {
        _uiState.update { it.copy(toolsOpen = true, activeTool = null) }
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

    fun openUrgentHelp(context: Context? = null) {
        _uiState.update { it.copy(urgentHelpOpen = true, activeTool = null, toolsOpen = false) }
        if (context == null) return
        // Populate the dialer with the user's REAL care-team number (best-effort).
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ep = AiraApi.emergencyProfile(context)
                val phone = ep.optString("care_team_phone").ifBlank { null }
                if (phone != null) _uiState.update { it.copy(careTeamPhone = phone) }
            } catch (_: Exception) {
            }
        }
    }

    fun closeUrgentHelp() {
        _uiState.update { it.copy(urgentHelpOpen = false, urgentMessage = null) }
    }

    fun updateDraft(value: String) {
        _uiState.update { it.copy(chatDraft = value) }
    }

    fun sendMessage(context: Context? = null) {
        val clean = _uiState.value.chatDraft.trim()
        if (clean.isEmpty()) return
        _uiState.update { it.copy(chatDraft = "") }
        dispatch(clean, context)
    }

    fun quickMessage(text: String, context: Context? = null) {
        dispatch(text, context)
    }

    /**
     * Send one message through the SERVER safety gate. A red result opens the
     * urgent-help handoff (with a real care-team number) instead of an AI reply;
     * green/amber append a trust-labelled reply. Falls back to the local keyword
     * gate + a calm reply when the backend is unreachable or unset.
     */
    private fun dispatch(text: String, context: Context?) {
        val history = _uiState.value.messages.map {
            (if (it.fromAira) "assistant" else "user") to it.text
        }
        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage(System.nanoTime(), fromAira = false, text = text),
                sending = true,
            )
        }
        if (context == null) {
            applyOfflineReply(text)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = AiraApi.chatTurn(context, text, history)
                if (res.urgent) {
                    _uiState.update {
                        it.copy(
                            sending = false,
                            urgentHelpOpen = true,
                            activeTool = null,
                            toolsOpen = false,
                            careTeamPhone = res.urgentHelp?.careTeamPhone ?: it.careTeamPhone,
                            urgentMessage = res.urgentHelp?.message,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            sending = false,
                            messages = it.messages + ChatMessage(
                                id = System.nanoTime(),
                                fromAira = true,
                                text = res.reply ?: "I'm here with you.",
                                trustLabel = res.trustLabel,
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
                applyOfflineReply(text)
            }
        }
    }

    private fun applyOfflineReply(text: String) {
        if (RED_WORDS.any { text.lowercase().contains(it) }) {
            _uiState.update {
                it.copy(sending = false, urgentHelpOpen = true, activeTool = null, toolsOpen = false)
            }
        } else {
            _uiState.update {
                it.copy(
                    sending = false,
                    messages = it.messages + ChatMessage(
                        id = System.nanoTime(),
                        fromAira = true,
                        text = "I've understood that. I can help organise the next step, or show " +
                            "you when contacting your care team would be safer.",
                        trustLabel = "wellness",
                    ),
                )
            }
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

    private companion object {
        // Local FALLBACK only — the authoritative gate runs server-side in AiraApi.
        val RED_WORDS = listOf(
            "bleeding", "chest pain", "cannot breathe", "can't breathe", "severe pain",
            "fainted", "not moving", "kill myself", "harm my baby", "emergency",
        )

        fun apiJourney(journey: JourneyType?): String = when (journey) {
            JourneyType.Trying -> "trying"
            JourneyType.Pregnant -> "pregnant"
            JourneyType.Postpartum -> "postpartum"
            else -> "exploring"
        }
    }
}
