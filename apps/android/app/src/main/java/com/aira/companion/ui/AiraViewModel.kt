package com.aira.companion.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aira.companion.data.AiraApi
import com.aira.companion.data.optStringOrNull
import com.aira.companion.model.AiraTool
import com.aira.companion.model.AiraUiState
import com.aira.companion.model.AppStage
import com.aira.companion.model.ChatMessage
import com.aira.companion.model.JourneyType
import com.aira.companion.model.MainDestination
import com.aira.companion.model.journeyLabel
import com.aira.companion.model.OnboardingAnswer
import com.aira.companion.model.OnboardingField
import com.aira.companion.model.onboardingPromptsFor
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

    /**
     * Decide at launch whether onboarding still needs to run.
     *
     * The session token is cached in SharedPreferences and the backend already
     * knows whether this user finished setup, but nothing ever asked — so the
     * app restarted the four-question onboarding on EVERY launch. This resolves
     * that against `/account/me` and drops straight into Today when the answer
     * is yes. Failure is silent and lands on Welcome, which is the safe default.
     */
    fun restoreSession(context: Context?) {
        if (context == null || _uiState.value.stage != AppStage.Starting) return
        viewModelScope.launch(Dispatchers.IO) {
            val user = try {
                AiraApi.me(context)
            } catch (_: Exception) {
                null
            }
            if (user != null && user.onboarded) {
                _uiState.update {
                    it.copy(
                        stage = AppStage.Main,
                        destination = MainDestination.Today,
                        language = user.language.ifBlank { it.language },
                        journey = JourneyType.entries.firstOrNull { j ->
                            j.name.equals(user.journey, ignoreCase = true) ||
                                journeyLabel(user.journey) == j.label
                        } ?: it.journey,
                        messages = it.messages.ifEmpty {
                            listOf(
                                ChatMessage(
                                    id = 1,
                                    fromAira = true,
                                    text = "Welcome back. Ask me anything, or open Today " +
                                        "for the one step that matters now.",
                                ),
                            )
                        },
                    )
                }
                loadToday(context)
                loadJourney(context)
                loadCare(context)
            } else {
                _uiState.update { it.copy(stage = AppStage.Welcome) }
            }
        }
    }

    fun startOnboarding() {
        _uiState.update { it.copy(stage = AppStage.Onboarding) }
    }

    fun answerOnboarding(answer: String) {
        _uiState.update { state ->
            // The prompt list depends on the journey (weeks is pregnancy-only), so
            // resolve it from the CURRENT state — the step being answered was
            // rendered against exactly this list.
            val prompts = onboardingPromptsFor(state.journey)
            // Guard the index: answerOnboarding is public and tap-driven, so a
            // queued/double tap past the last prompt must be a no-op, not a crash.
            if (state.onboardingStep >= prompts.size) return@update state
            val prompt = prompts[state.onboardingStep]
            val trimmed = answer.trim()
            // A skipped free-text answer is recorded honestly in the transcript
            // rather than as an empty bubble.
            var next =
                state.copy(
                    onboardingAnswers = state.onboardingAnswers +
                        OnboardingAnswer(
                            prompt.question,
                            trimmed.ifBlank { "Skipped" },
                        ),
                    onboardingStep = state.onboardingStep + 1,
                )

            next = when (prompt.field) {
                OnboardingField.Journey ->
                    next.copy(journey = JourneyType.entries.firstOrNull { it.label == trimmed })
                OnboardingField.Name -> next.copy(name = trimmed.take(120))
                // Only a plausible pregnancy week; anything else is treated as skipped.
                OnboardingField.Weeks ->
                    next.copy(weeks = trimmed.toIntOrNull()?.takeIf { it in 1..45 })
                OnboardingField.Language -> next.copy(language = trimmed)
                OnboardingField.Priority -> next.copy(priority = trimmed)
                OnboardingField.Companion -> next.copy(companionPreference = trimmed)
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
                            // Not "by text or voice": spoken conversation isn't
                            // wired up in this build and the mic is disabled.
                            text = "You're all set. I'll keep Today focused on one meaningful step — " +
                                "ask me anything whenever you like.",
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
                    name = snapshot.name.ifBlank { null },
                    language = snapshot.language,
                    priorities = listOfNotNull(snapshot.priority.takeIf { it.isNotBlank() }),
                    // Only sent for a pregnancy — the backend week-bands on this,
                    // and a stale week from a changed journey would be worse than none.
                    weeks = snapshot.weeks.takeIf { snapshot.journey == JourneyType.Pregnant },
                )
                // Pull the assembled Today/Journey straight away so the first
                // screen reflects the name and week just submitted.
                loadToday(context)
                loadJourney(context)
                loadCare(context)
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

    /**
     * Load the Care hub from `/v1/care`. Until this existed, Care rendered a
     * fixed "Dr. Meera Shah · Tomorrow 10:30 AM · Prenatal vitamin" for every
     * user, including people who had entered nothing at all.
     */
    fun loadCare(context: Context?) {
        if (context == null) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(careLoading = it.careData == null) }
            try {
                val data = AiraApi.care(context)
                _uiState.update { it.copy(careData = data, careLoading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(careLoading = false) }
            }
        }
    }

    // ── tool actions: these WRITE to the backend ─────────────────────────────
    //
    // Every tool sheet used to end in `onNotify("Saved")` and nothing else, so
    // reminders, medicines, appointments, check-ins and symptom logs were all
    // discarded the moment the sheet closed. Each action below persists, then
    // refreshes Care so the new row is visible immediately.

    private fun write(
        context: Context?,
        success: String,
        refreshCare: Boolean = true,
        block: suspend (Context) -> Unit,
    ) {
        if (context == null) {
            notify("Not connected — nothing was saved.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block(context)
                notify(success)
                if (refreshCare) loadCare(context)
            } catch (e: Exception) {
                notify("Couldn't save that. ${e.message.orEmpty()}".trim())
            }
        }
    }

    fun saveReminder(context: Context?, title: String, time: String, repeat: String) =
        write(context, "Reminder saved.") { AiraApi.addReminder(it, title, time, repeat) }

    fun saveMedicine(context: Context?, name: String, dose: String, time: String) =
        write(context, "Medicine added.") { AiraApi.addMedicine(it, name, dose, time) }

    fun markMedicineTaken(context: Context?, id: String) =
        write(context, "Marked as taken.") { AiraApi.markMedicineTaken(it, id) }

    fun setReminderDone(context: Context?, id: String, done: Boolean) =
        write(context, if (done) "Reminder done." else "Reminder reopened.") {
            AiraApi.setReminderDone(it, id, done)
        }

    /**
     * Stream a picked document into the Care Vault.
     *
     * Not routed through [write]: the upload takes long enough to need its own
     * in-progress flag, and the sheet stays open until it finishes so a failure
     * is visible instead of being dismissed along with the sheet.
     */
    fun uploadDocument(context: Context?, uri: Uri, kind: String) {
        if (context == null) {
            notify("Not connected — nothing was uploaded.")
            return
        }
        _uiState.update { it.copy(uploadingDocument = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AiraApi.uploadDocument(context, uri, kind)
                _uiState.update { it.copy(uploadingDocument = false, activeTool = null) }
                notify("Saved to your private Care Vault.")
                loadCare(context)
            } catch (e: Exception) {
                _uiState.update { it.copy(uploadingDocument = false) }
                notify("Couldn't upload that. ${e.message.orEmpty()}".trim())
            }
        }
    }

    fun loadPrefs(context: Context?) {
        if (context == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(voicePrefs = AiraApi.prefs(context)) }
            } catch (_: Exception) {
            }
        }
    }

    fun setVoice(context: Context?, voice: String) =
        write(context, "Voice preference saved.", refreshCare = false) {
            AiraApi.setVoice(it, voice)
            _uiState.update { s -> s.copy(voicePrefs = s.voicePrefs.copy(voice = voice)) }
        }

    /**
     * Create a real partner invite. The resulting code goes into UI state so the
     * sheet can show it and offer the system share sheet — previously this was a
     * toast reading "Private partner invitation prepared", and no invitation of
     * any kind existed.
     */
    fun createPartnerInvite(
        context: Context?,
        appointments: Boolean,
        reminders: Boolean,
        healthDetails: Boolean,
    ) = write(context, "Invite ready to share.", refreshCare = false) {
        val invite = AiraApi.createPartnerInvite(it, appointments, reminders, healthDetails)
        _uiState.update { s -> s.copy(partnerInvite = invite) }
    }

    /** Drop the code from memory when the sheet closes; it is single-use anyway. */
    fun clearPartnerInvite() {
        _uiState.update { it.copy(partnerInvite = null) }
    }

    fun loadPartner(context: Context?) {
        if (context == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update {
                    it.copy(
                        partnerInvites = AiraApi.partnerInvites(context),
                        partnerShared = AiraApi.partnerShared(context),
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    fun revokePartnerInvite(context: Context?, inviteId: String) =
        write(context, "Access revoked.", refreshCare = false) {
            AiraApi.revokePartnerInvite(it, inviteId)
            _uiState.update { s -> s.copy(partnerInvites = AiraApi.partnerInvites(it)) }
        }

    /**
     * Redeem a code someone shared. Without this the invite flow was one-ended:
     * a code could be created and sent, and nobody could do anything with it.
     */
    fun acceptPartnerInvite(context: Context?, code: String) {
        if (context == null) {
            notify("Not connected — the code wasn't checked.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val who = AiraApi.acceptPartnerInvite(context, code)
                notify("You can now see what $who shared.")
                _uiState.update { it.copy(partnerShared = AiraApi.partnerShared(context)) }
            } catch (e: Exception) {
                // A wrong or spent code is the common case, not an error state —
                // say so in the words the user needs rather than an HTTP code.
                notify(
                    if (e is com.aira.companion.data.AiraApiException && e.code == 404) {
                        "That code isn't valid. It may have been used already, or expired."
                    } else {
                        "Couldn't check that code. ${e.message.orEmpty()}".trim()
                    },
                )
            }
        }
    }

    // ── data rights ─────────────────────────────────────────────────────────

    /**
     * Write the account export to a location the user picked. The Uri comes from
     * ACTION_CREATE_DOCUMENT, so the file lands wherever they chose and no
     * storage permission or FileProvider is involved.
     */
    fun exportAccountTo(context: Context?, uri: Uri) {
        if (context == null) {
            notify("Not connected — nothing was exported.")
            return
        }
        _uiState.update { it.copy(exporting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = AiraApi.exportAccount(context)
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray())
                } ?: throw IllegalStateException("couldn't open that location")
                _uiState.update { it.copy(exporting = false) }
                notify("Your data was saved to this device.")
            } catch (e: Exception) {
                _uiState.update { it.copy(exporting = false) }
                notify("Export failed. ${e.message.orEmpty()}".trim())
            }
        }
    }

    /**
     * Irreversible. On success the token is already dead, so the app returns to
     * Welcome with cleared state rather than sitting on a session that can only
     * 401 — and a fresh anonymous user is registered on the next call.
     */
    fun deleteAccount(context: Context?) {
        if (context == null) {
            notify("Not connected — nothing was deleted.")
            return
        }
        _uiState.update { it.copy(deleting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AiraApi.deleteAccount(context)
                _uiState.value = AiraUiState(stage = AppStage.Welcome)
                notify("Your data has been deleted.")
            } catch (e: Exception) {
                _uiState.update { it.copy(deleting = false) }
                notify("Deletion failed. ${e.message.orEmpty()}".trim())
            }
        }
    }

    fun saveAppointment(context: Context?, doctor: String, place: String, whenText: String) =
        write(context, "Appointment saved.") { AiraApi.addAppointment(it, doctor, place, whenText) }

    fun saveCheckIn(context: Context?, feeling: String, sleepHours: Double, note: String) =
        write(context, "Check-in saved.") { AiraApi.addCheckIn(it, feeling, sleepHours, note) }

    fun saveSymptom(context: Context?, what: String, severity: String, started: String) =
        write(context, "Added to your timeline.") { AiraApi.addSymptom(it, what, severity, started) }

    fun saveEmergencyProfile(context: Context?, fields: Map<String, String>) =
        write(context, "Emergency profile saved.", refreshCare = false) {
            AiraApi.putEmergencyProfile(it, fields)
            val phone = fields["care_team_phone"]?.ifBlank { null }
            _uiState.update { s -> s.copy(careTeamPhone = phone) }
        }

    fun sendReport(context: Context?, kind: String, message: String) =
        write(context, "Thank you — a human will review this.", refreshCare = false) {
            AiraApi.reportAnswer(it, kind, message)
        }

    fun setConsent(context: Context?, feature: String, granted: Boolean) =
        write(context, if (granted) "Turned on." else "Turned off.", refreshCare = false) {
            AiraApi.setConsent(it, feature, granted)
            _uiState.update { s -> s.copy(consent = AiraApi.consent(it)) }
        }

    fun loadConsent(context: Context?) {
        if (context == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(consent = AiraApi.consent(context)) }
            } catch (_: Exception) {
            }
        }
    }

    fun loadMemory(context: Context?) {
        if (context == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(memory = AiraApi.memory(context)) }
            } catch (_: Exception) {
            }
        }
    }

    fun setMemoryApproved(context: Context?, id: String, approved: Boolean) =
        write(context, if (approved) "Aira can use this." else "Aira won't use this.", refreshCare = false) {
            AiraApi.setMemoryApproved(it, id, approved)
            _uiState.update { s -> s.copy(memory = AiraApi.memory(it)) }
        }

    fun forgetMemory(context: Context?, id: String) =
        write(context, "Forgotten.", refreshCare = false) {
            AiraApi.forgetMemory(it, id)
            _uiState.update { s -> s.copy(memory = AiraApi.memory(it)) }
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
                // Null-aware: see optStringOrNull — a JSON null would otherwise
                // become the string "null" and look like a real phone number.
                val phone = ep.optStringOrNull("care_team_phone")
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
