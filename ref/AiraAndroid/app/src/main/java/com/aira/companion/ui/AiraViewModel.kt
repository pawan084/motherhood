package com.aira.companion.ui

import androidx.lifecycle.ViewModel
import com.aira.companion.model.AiraTool
import com.aira.companion.model.AiraUiState
import com.aira.companion.model.AppStage
import com.aira.companion.model.ChatMessage
import com.aira.companion.model.JourneyType
import com.aira.companion.model.MainDestination
import com.aira.companion.model.OnboardingAnswer
import com.aira.companion.model.onboardingPrompts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AiraViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AiraUiState())
    val uiState: StateFlow<AiraUiState> = _uiState.asStateFlow()

    fun startOnboarding() {
        _uiState.update { it.copy(stage = AppStage.Onboarding) }
    }

    fun answerOnboarding(answer: String) {
        _uiState.update { state ->
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

    fun finishOnboarding() {
        _uiState.update {
            it.copy(
                stage = AppStage.Main,
                destination = MainDestination.Today,
                messages =
                    listOf(
                        ChatMessage(
                            id = 1,
                            fromAira = true,
                            text = "Good morning, Maya. Your appointment is tomorrow. Shall we prepare together?",
                        ),
                    ),
            )
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
        _uiState.update { state ->
            val clean = state.chatDraft.trim()
            if (clean.isEmpty()) return@update state

            state.copy(
                messages =
                    state.messages +
                        ChatMessage(
                            id = System.nanoTime(),
                            fromAira = false,
                            text = clean,
                        ),
                chatDraft = "",
                snackbarMessage = "Prototype: connect this action to the Aira orchestration API.",
            )
        }
    }

    fun quickMessage(text: String) {
        _uiState.update { state ->
            state.copy(
                messages =
                    state.messages +
                        ChatMessage(
                            id = System.nanoTime(),
                            fromAira = false,
                            text = text,
                        ),
                snackbarMessage = "Aira added this to your private care context.",
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
