package com.aira.companion.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aira.companion.data.AiraApi
import com.aira.companion.data.CareItem
import com.aira.companion.data.AppPrefs
import com.aira.companion.data.optStringOrNull
import com.aira.companion.model.AiraTool
import com.aira.companion.reminders.ReminderScheduler
import com.aira.companion.model.AiraUiState
import com.aira.companion.model.AppStage
import com.aira.companion.model.AuthMode
import com.aira.companion.model.ChatMessage
import com.aira.companion.model.JourneySection
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
        // Seed the trust badge before any turn — with no classifier configured
        // screening is keyword-only from the very first message.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val d = AiraApi.screeningDegraded(context)
                _uiState.update { it.copy(screeningDegraded = d) }
            } catch (_: Exception) {
                // Unreachable backend means no full screening either; say so.
                _uiState.update { it.copy(screeningDegraded = true) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            // `reached` separates "the server said you have no account" from
            // "the server said nothing at all". Only the first is a reason to
            // send someone back to the Welcome screen.
            var reached = true
            val user = try {
                AiraApi.me(context)
            } catch (_: Exception) {
                reached = false
                null
            }
            if (!reached && AiraApi.hasSession(context) && AppPrefs.wasOnboarded(context)) {
                // Offline, but this install has been through onboarding and is
                // still holding a session. Open the app they know, and let the
                // screens say they can't load rather than pretending this is a
                // first run.
                _uiState.update {
                    it.copy(
                        stage = AppStage.Main,
                        destination = MainDestination.Today,
                        loadFailed = true,
                    )
                }
                return@launch
            }
            if (user != null && user.onboarded) {
                AppPrefs.markOnboarded(context)
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
                // Not an onboarded session. Someone new gets the introduction
                // first; someone who has already seen it (or skipped it) goes
                // straight to the choice, even if they later signed out.
                _uiState.update {
                    it.copy(
                        stage = if (AppPrefs.tutorialSeen(context)) {
                            AppStage.Welcome
                        } else {
                            AppStage.Tutorial
                        },
                    )
                }
            }
        }
    }

    /**
     * Completed or skipped — both count as seen. Re-showing it to someone who
     * chose to skip is not a second chance to explain, it is ignoring them.
     */
    fun finishTutorial(context: Context?) {
        context?.let { AppPrefs.markTutorialSeen(it) }
        _uiState.update {
            if (it.replayingTutorial) {
                it.copy(stage = AppStage.Main, replayingTutorial = false)
            } else {
                it.copy(stage = AppStage.Welcome)
            }
        }
    }

    fun startOnboarding() {
        _uiState.update { it.copy(stage = AppStage.Onboarding) }
    }

    /**
     * Show the three intro cards again, from Settings.
     *
     * They explained what Aira is, what it does with your data and what it
     * won't do — and were then unreachable forever, on first run, when someone
     * is least able to take any of it in. `replayingTutorial` keeps this
     * distinct from the first-run path so finishing it returns to the app
     * rather than dropping the user back at the Welcome screen.
     */
    fun replayTutorial() {
        _uiState.update { it.copy(stage = AppStage.Tutorial, replayingTutorial = true) }
    }

    // ── accounts (optional) ─────────────────────────────────────────────────

    /**
     * Open the auth screen. For sign-in we first count what's on this device, so
     * the screen can warn that signing in switches to the account's data and
     * leaves local notes behind — rather than discovering it afterwards.
     */
    fun openAuth(context: Context?, mode: AuthMode) {
        _uiState.update {
            it.copy(
                stage = AppStage.Auth,
                // Remember where we came from, so closing returns there rather
                // than dumping someone mid-use back onto Welcome. Clamped to the
                // two stages auth can legitimately be opened from: returning to
                // Starting would land on a screen that draws nothing, since the
                // splash it belongs to is long gone.
                authReturnStage = when {
                    it.stage == AppStage.Auth -> it.authReturnStage
                    it.stage == AppStage.Main -> AppStage.Main
                    else -> AppStage.Welcome
                },
                authMode = mode,
                authError = null,
                localCareItems = 0,
            )
        }
        if (context == null || mode != AuthMode.SignIn) return
        viewModelScope.launch(Dispatchers.IO) {
            val n = AiraApi.localCareItemCount(context)
            _uiState.update { it.copy(localCareItems = n) }
        }
    }

    fun setAuthMode(context: Context?, mode: AuthMode) = openAuth(context, mode)

    fun closeAuth() {
        _uiState.update { it.copy(stage = it.authReturnStage, authError = null) }
    }

    fun signUp(context: Context?, email: String, password: String) =
        authenticate(context) { AiraApi.signUp(it, email, password) }

    fun signIn(context: Context?, email: String, password: String) =
        authenticate(context) { AiraApi.signIn(it, email, password) }

    /**
     * Shared tail for both: on success land on Today if the account has already
     * been onboarded, otherwise run onboarding. On failure STAY on the auth
     * screen with the reason — advancing anyway would be the "said saved,
     * saved nothing" failure in a new place.
     */
    private fun authenticate(
        context: Context?,
        call: suspend (Context) -> com.aira.companion.data.UserProfile,
    ) {
        if (context == null) {
            _uiState.update { it.copy(authError = "Not connected — try again in a moment.") }
            return
        }
        _uiState.update { it.copy(authBusy = true, authError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val user = call(context)
                _uiState.update {
                    it.copy(
                        authBusy = false,
                        authError = null,
                        signedIn = true,
                        language = user.language.ifBlank { it.language },
                        journey = JourneyType.entries.firstOrNull { j ->
                            j.name.equals(user.journey, ignoreCase = true) ||
                                journeyLabel(user.journey) == j.label
                        } ?: it.journey,
                        stage = if (user.onboarded) AppStage.Main else AppStage.Onboarding,
                        destination = MainDestination.Today,
                    )
                }
                if (user.onboarded) {
                    loadToday(context)
                    loadJourney(context)
                    loadCare(context)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(authBusy = false, authError = readableAuthError(e)) }
            }
        }
    }

    /** Server details are written for developers. These are the same facts in
     *  the words someone staring at the form needs. */
    private fun readableAuthError(e: Exception): String {
        val api = e as? com.aira.companion.data.AiraApiException
        return when (api?.code) {
            409 -> "An account with that email already exists. Try signing in instead."
            401 -> "That email and password don't match. Check both and try again."
            400 -> when {
                api.message?.contains("password must be", true) == true ->
                    "Please choose a password of at least 12 characters."
                else -> "Please check the email address and try again."
            }
            else -> "Couldn't reach Aira. Check your connection and try again."
        }
    }

    /**
     * Sign out everywhere and return to Welcome with a clean slate. The whole
     * UI state is reset — leaving another account's name or care rows on screen
     * after signing out would be its own kind of lie.
     */
    fun signOut(context: Context?) {
        if (context == null) {
            notify("Not connected — you're still signed in.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AiraApi.logout(context)
            } catch (_: Exception) {
                // logout() clears the local token regardless, so the device is
                // signed out even when the server can't be told.
            }
            AppPrefs.clearOnboarded(context)
            _uiState.value = AiraUiState(stage = AppStage.Welcome)
            notify("Signed out.")
        }
    }

    /**
     * Tidy a typed name before it becomes how the app greets someone.
     *
     * A phone keyboard's autocorrect turned "Riya" into "Rita I" during device
     * testing and the app stored it verbatim, so every screen said "Good
     * morning, Rita I" — the app using someone's name wrongly is worse than not
     * using it. Collapses runs of whitespace, drops trailing single letters
     * left behind by a committed suggestion, and caps the length.
     */
    internal fun cleanName(raw: String): String {
        val collapsed = raw.trim().replace(Regex("""\s+"""), " ")
        val words = collapsed.split(" ").filter { it.isNotBlank() }
        val kept = if (words.size > 1 && words.last().length == 1 &&
            words.last().all { it.isLetter() }
        ) {
            words.dropLast(1)
        } else {
            words
        }
        return kept.joinToString(" ").take(60)
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
                OnboardingField.Name -> next.copy(name = cleanName(trimmed))
                // Only a plausible pregnancy week; anything else is treated as skipped.
                OnboardingField.Weeks ->
                    next.copy(weeks = trimmed.toIntOrNull()?.takeIf { it in 1..45 })
                OnboardingField.Language -> next.copy(language = trimmed)
                OnboardingField.Priority -> next.copy(priority = trimmed)
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
                // Remember locally that this install has an onboarded account,
                // so a later start with no connection opens the app rather than
                // the Welcome screen.
                AppPrefs.markOnboarded(context)
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
                _uiState.update { it.copy(todayData = data, loadFailed = false) }
            } catch (_: Exception) {
                // Only claim failure when there is nothing to show. A refresh
                // that fails over content already on screen is not worth a
                // banner — the content is still true, just not newer.
                _uiState.update { it.copy(loadFailed = it.todayData == null) }
            }
        }
    }

    /** Retry whatever the current screen needs. */
    fun retryLoad(context: Context?) {
        if (context == null) return
        _uiState.update { it.copy(loadFailed = false) }
        when (_uiState.value.destination) {
            MainDestination.Today -> { loadToday(context); loadCare(context) }
            MainDestination.Journey -> loadJourney(context)
            MainDestination.Care -> {
                loadCare(context); loadTimeline(context); loadDocuments(context)
            }
            MainDestination.You -> { loadConsent(context); loadPrefs(context) }
            else -> loadToday(context)
        }
    }

    private fun nowSeconds(): Double = System.currentTimeMillis() / 1000.0

    /**
     * Pull this conversation back from the server.
     *
     * The turns have been stored since chat existed and nothing ever read them,
     * so the app threw the conversation away every time it closed while the
     * backend still had it. Replaces the in-memory log rather than appending,
     * so returning to the tab twice can't double it.
     */
    fun loadChatHistory(context: Context?) {
        if (context == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val turns = AiraApi.chatHistory(context)
                if (turns.isEmpty()) return@launch
                _uiState.update { state ->
                    state.copy(
                        messages = turns.mapIndexed { index, t ->
                            ChatMessage(
                                id = -(index.toLong() + 1),
                                fromAira = t.fromAira,
                                text = t.text,
                                trustLabel = t.safetyLevel,
                                at = t.at,
                            )
                        },
                    )
                }
            } catch (_: Exception) {
                // The greeting already on screen is a fine place to start from.
            }
        }
    }

    /** Load journey-aware Journey content (best-effort). */
    fun loadJourney(context: Context?) {
        if (context == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = AiraApi.journey(context)
                _uiState.update { it.copy(journeyData = data, loadFailed = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(loadFailed = it.journeyData == null) }
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
                _uiState.update { it.copy(careData = data, careLoading = false, loadFailed = false) }
                // The server's list is the source of truth for what should
                // fire, so scheduling follows every load rather than only
                // creation — a reminder added on the web arrives here too, and
                // one deleted there stops arriving.
                ReminderScheduler.syncAll(context, data.reminders, data.appointments)
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(careLoading = false, loadFailed = it.careData == null)
                }
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
            notify("Not saved — Aira isn't connected right now.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block(context)
                notify(success)
                if (refreshCare) loadCare(context)
            } catch (e: Exception) {
                notify(saveFailureMessage(e))
            }
        }
    }

    /**
     * What went wrong, and what to do about it.
     *
     * Every failed write said "Couldn't save that." followed by whatever the
     * exception's message happened to be — often a raw socket error, sometimes
     * nothing at all. It named no cause and offered no next step, so the only
     * information the user got was that something had gone wrong somewhere.
     * The three cases below are the ones that actually happen, and each has a
     * different thing for the person to do.
     */
    internal fun saveFailureMessage(e: Exception): String {
        val raw = e.message.orEmpty()
        return when {
            e is java.net.UnknownHostException ||
                e is java.net.ConnectException ||
                e is java.net.SocketTimeoutException ->
                "Not saved — Aira can't reach the server. Check your connection and try again."
            raw.contains("401") || raw.contains("403") ->
                "Not saved — your session ended. Open Aira again to sign back in."
            // A 4xx with a message is the server explaining a rule the input
            // broke, which is the one case worth quoting verbatim.
            raw.isNotBlank() && raw.length < 120 -> "Not saved — $raw"
            else -> "Not saved. Try again in a moment."
        }
    }

    /** After the OS permission dialog: schedule what's already saved, or say
     *  plainly that reminders won't arrive. */
    fun onNotificationPermissionResult(context: Context?, granted: Boolean) {
        if (context == null) return
        if (granted) {
            _uiState.value.careData?.reminders?.let { ReminderScheduler.syncAll(context, it) }
        } else {
            notify("Reminders are saved, but Aira can't notify you without permission.")
        }
    }

    fun saveReminder(context: Context?, title: String, time: String, repeat: String) =
        write(
            context,
            // Says whether it will actually arrive. Notifications can be off at
            // the OS level, and a "Reminder saved." that quietly never fires is
            // the failure this whole feature exists to prevent.
            if (context != null && ReminderScheduler.canNotify(context)) {
                "Reminder saved. Aira will notify you."
            } else {
                "Reminder saved. Turn on notifications to be reminded."
            },
        ) { AiraApi.addReminder(it, title, time, repeat) }

    fun saveMedicine(context: Context?, name: String, dose: String, time: String) =
        write(context, "Medicine added.") { AiraApi.addMedicine(it, name, dose, time) }

    fun markMedicineTaken(context: Context?, id: String) =
        write(context, "Marked as taken.") { AiraApi.markMedicineTaken(it, id) }

    fun setReminderDone(context: Context?, id: String, done: Boolean) =
        write(context, if (done) "Reminder done." else "Reminder reopened.") {
            AiraApi.setReminderDone(it, id, done)
        }

    // ── correcting and removing ─────────────────────────────────────────────
    //
    // Every care kind used to be create-only. A typo was permanent, a cancelled
    // appointment stayed on Today forever, and a medicine the care team had
    // stopped went on reading as due — the last is a safety problem, not an
    // annoyance.

    fun renameCareItem(context: Context?, id: String, field: String, value: String) =
        write(context, "Updated.") {
            AiraApi.updateCareItem(it, id, field, value)
            refreshTimelineAndDocs(it)
        }

    fun deleteCareItem(context: Context?, id: String) =
        write(context, "Removed.") {
            AiraApi.deleteCareItem(it, id)
            refreshTimelineAndDocs(it)
        }

    /** Check-ins and symptom logs — read back for the first time. */
    fun loadTimeline(context: Context?) {
        if (context == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(timeline = AiraApi.timeline(context)) }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Open a stored document in whatever the phone uses for it.
     *
     * Downloads to the app's cache and hands over a content:// URI with a
     * one-shot read grant, rather than writing anywhere shared: a scan is the
     * most sensitive thing in this app, and "open" should not mean "publish".
     */
    fun openDocument(context: Context?, item: CareItem) {
        if (context == null) {
            notify("Not connected — Aira can't fetch that file right now.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = AiraApi.downloadDocument(context, item.id, item.title)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.files", file,
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, item.contentType ?: "*/*")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                notify("No app on this phone can open that file.")
            } catch (e: Exception) {
                notify(e.message ?: "Couldn't open that document.")
            }
        }
    }

    fun loadDocuments(context: Context?) {
        if (context == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(documents = AiraApi.documents(context)) }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun refreshTimelineAndDocs(ctx: Context) {
        // An edit or delete can land on any kind, and the caller doesn't know
        // which — cheaper to refresh both lists than to make every call site
        // reason about it.
        runCatching { _uiState.update { it.copy(timeline = AiraApi.timeline(ctx)) } }
        runCatching { _uiState.update { it.copy(documents = AiraApi.documents(ctx)) } }
    }

    /**
     * Change name, journey or language after onboarding.
     *
     * Android never called /account/profile, so onboarding answers were
     * permanent. In an app whose premise is a journey that changes, someone who
     * moved from trying to conceive to pregnant kept getting content for where
     * they used to be, with deleting their account as the only way out.
     */
    fun saveProfile(context: Context?, name: String, journey: JourneyType?, language: String) =
        write(context, "Profile saved.", refreshCare = false) { ctx ->
            val user = AiraApi.updateProfile(
                ctx,
                name = name.trim().ifBlank { null },
                journey = apiJourney(journey),
                language = language,
            )
            _uiState.update {
                it.copy(
                    name = user.name,
                    language = user.language.ifBlank { it.language },
                    journey = JourneyType.entries.firstOrNull { j ->
                        j.name.equals(user.journey, ignoreCase = true)
                    } ?: it.journey,
                )
            }
            // Journey drives what Today and Journey render, so both have to be
            // refetched or the screens keep describing the old stage.
            loadToday(ctx)
            loadJourney(ctx)
            loadCare(ctx)
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
                AppPrefs.clearOnboarded(context)
            _uiState.value = AiraUiState(stage = AppStage.Welcome)
                notify("Your data has been deleted.")
            } catch (e: Exception) {
                _uiState.update { it.copy(deleting = false) }
                notify("Deletion failed. ${e.message.orEmpty()}".trim())
            }
        }
    }

    fun saveAppointment(
        context: Context?,
        doctor: String,
        place: String,
        whenText: String,
        at: Long? = null,
    ) = write(context, "Appointment saved.") {
        AiraApi.addAppointment(it, doctor, place.ifBlank { null }, whenText.ifBlank { null }, at)
    }

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

    /** Read one Journey section. Its own destination, rather than borrowing an
     *  unrelated tool sheet — see JourneySectionSheet. */
    fun openJourneySection(section: JourneySection) {
        _uiState.update { it.copy(activeJourneySection = section, activeTool = null) }
    }

    /** Open the reminder sheet on an existing reminder. */
    fun editReminder(item: CareItem) {
        _uiState.update { it.copy(editingReminder = item, activeTool = AiraTool.Reminder) }
    }

    fun closeReminderEditor() {
        _uiState.update { it.copy(editingReminder = null) }
    }

    /** Save an edited reminder, then reschedule — the notification has to move
     *  with the time, or the edit is cosmetic. */
    fun updateReminder(context: Context?, id: String, title: String, time: String, repeat: String) =
        write(context, "Reminder updated.") {
            AiraApi.updateCareItem(
                it, id,
                mapOf("title" to title, "time" to time, "repeat" to repeat),
            )
        }

    fun closeJourneySection() {
        _uiState.update { it.copy(activeJourneySection = null) }
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
                messages = it.messages + ChatMessage(
                    System.nanoTime(), fromAira = false, text = text, at = nowSeconds(),
                ),
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
                _uiState.update { it.copy(screeningDegraded = res.degraded) }
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
                                at = nowSeconds(),
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
                        at = nowSeconds(),
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
