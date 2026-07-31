package com.aira.companion.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aira.companion.data.AiraApi
import com.aira.companion.data.CareItem
import com.aira.companion.data.CareData
import com.aira.companion.data.PendingWrites
import com.aira.companion.data.dropPending
import com.aira.companion.data.toCareItem
import com.aira.companion.data.SafetyKeywords
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
import com.aira.companion.model.OpenDocument
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
class AiraViewModel(
    /**
     * Survives process death, which is not the same as surviving rotation.
     *
     * Android kills backgrounded apps under memory pressure and then restores
     * the task as though it had been there all along. Nothing here was saved, so
     * a half-typed message — the one somebody was working up to asking — came
     * back as an empty box, and they landed on Today instead of where they were.
     * A ViewModel alone does not cover this; it dies with the process.
     *
     * Defaulted so the 23 tests that construct this with no arguments still can.
     * A fix for losing work should not start by breaking the suite that guards
     * the rest of it.
     */
    private val saved: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle(),
) : ViewModel() {

    /** How long a removed row can be brought back. Long enough to notice the
     *  snackbar and react, short enough that the delete is not left hanging. */
    private val UNDO_WINDOW_MS = 5_000L

    private val _uiState = MutableStateFlow(AiraUiState())

    init {
        // Restored through pendingDestination rather than written straight into
        // the state: restoreSession runs a moment later and sets Today for every
        // onboarded user, so anything set here directly would be overwritten —
        // the same trap the notification deep link fell into.
        saved.get<String>(KEY_DESTINATION)?.let { name ->
            runCatching { MainDestination.valueOf(name) }.getOrNull()?.let {
                pendingDestination = it
            }
        }
        saved.get<String>(KEY_DRAFT)?.takeIf { it.isNotBlank() }?.let { draft ->
            _uiState.update { it.copy(chatDraft = draft) }
        }
    }

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
                // Read and cleared before the update, so a notification tap that
                // arrived while this was in flight still decides where we land.
                val requested = pendingDestination
                pendingDestination = null
                _uiState.update {
                    it.copy(
                        stage = AppStage.Main,
                        destination = requested ?: MainDestination.Today,
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

    /**
     * Where a notification tap wants to land.
     *
     * Held rather than applied when the session is still resolving. Setting the
     * destination directly at that point looks correct and is not: restoreSession
     * finishes a moment later and sets Today for every onboarded user, so the tap
     * silently lost its destination. Caught by tapping one — the code read as
     * though it worked.
     */
    private var pendingDestination: MainDestination? = null

    fun requestDestination(destination: MainDestination) {
        if (_uiState.value.stage == AppStage.Main) {
            selectDestination(destination)
        } else {
            pendingDestination = destination
        }
    }

    fun selectDestination(destination: MainDestination) {
        _uiState.update { it.copy(destination = destination, toolsOpen = false) }
        saved[KEY_DESTINATION] = destination.name
    }

    /** Load the educational video library (best-effort; keeps last data on failure). */
    fun loadVideos(context: Context?) {
        if (context == null) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(videosLoading = it.videos.isEmpty()) }
            try {
                val res = AiraApi.videos(context)
                _uiState.update {
                    it.copy(
                        videos = res.items,
                        weekVideo = res.weekVideo,
                        videoCategories = res.categories,
                        savedVideoIds = res.savedIds,
                        videosLoading = false,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(videosLoading = false) }
            }
        }
    }

    /** Save/unsave a video — optimistic, rolled back if the server rejects it. */
    fun toggleSaveVideo(context: Context?, id: String) {
        if (context == null) return
        val wasSaved = _uiState.value.savedVideoIds.contains(id)
        _uiState.update {
            val next = it.savedVideoIds.toMutableSet()
            if (wasSaved) next.remove(id) else next.add(id)
            it.copy(savedVideoIds = next)
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (wasSaved) AiraApi.unsaveVideo(context, id) else AiraApi.saveVideo(context, id)
            } catch (_: Exception) {
                _uiState.update {
                    val next = it.savedVideoIds.toMutableSet()
                    if (wasSaved) next.add(id) else next.remove(id)
                    it.copy(savedVideoIds = next)
                }
            }
        }
    }

    /** Load journey-aware Today content (best-effort; keeps last data on failure). */
    fun loadToday(context: Context?) {
        if (context == null) return
        viewModelScope.launch(Dispatchers.IO) {
            // Paint the last known answer before asking for a new one. On a slow
            // connection this is the difference between a blank screen and your
            // medicines; on no connection it is the difference between the app
            // working and the app being a retry button.
            showCachedIfEmpty(context)
            try {
                val data = AiraApi.today(context)
                _uiState.update {
                    it.copy(todayData = data, loadFailed = false,
                            showingCached = false, cachedAt = null)
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        loadFailed = it.todayData == null,
                        // Live data already on screen is not stale just because
                        // a later refresh failed — only say "cached" when what
                        // is showing actually came from the cache.
                        showingCached = it.showingCached && it.todayData != null,
                    )
                }
            }
        }
    }

    /**
     * Fill the screen from disk when there is nothing on it yet.
     *
     * Only when empty: a successful load must never be overwritten by an older
     * cached copy, and this runs on every visit to a tab. `cachedAt` keeps the
     * OLDEST of whatever it put up, because a notice saying "as of 09:04" has
     * to be true of everything under it, not just the newest piece.
     */
    private fun showCachedIfEmpty(context: Context) {
        var oldest: Long? = null
        fun note(at: Long) { oldest = minOf(oldest ?: at, at) }

        val s = _uiState.value
        val today = if (s.todayData == null) AiraApi.cachedToday(context) else null
        val care = if (s.careData == null) AiraApi.cachedCare(context) else null
        val journey = if (s.journeyData == null) AiraApi.cachedJourney(context) else null
        val timeline = if (s.timeline.isEmpty()) AiraApi.cachedTimeline(context) else null
        val documents = if (s.documents.isEmpty()) AiraApi.cachedDocuments(context) else null
        today?.let { note(it.savedAt) }
        care?.let { note(it.savedAt) }
        journey?.let { note(it.savedAt) }
        timeline?.let { note(it.savedAt) }
        documents?.let { note(it.savedAt) }
        val at = oldest ?: return

        _uiState.update {
            it.copy(
                todayData = today?.value ?: it.todayData,
                careData = care?.value ?: it.careData,
                journeyData = journey?.value ?: it.journeyData,
                timeline = timeline?.value ?: it.timeline,
                documents = documents?.value ?: it.documents,
                showingCached = true,
                cachedAt = at,
                // There is something real on screen now, so the whole-screen
                // offline notice is no longer the right answer.
                loadFailed = false,
                careLoading = false,
            )
        }
        mergePending(context)
    }

    /**
     * A refresh the user asked for.
     *
     * Reuses retryLoad rather than inventing a second path, so pulling does
     * exactly what the offline Retry button does — one definition of "load this
     * screen again", not two that drift.
     *
     * The spinner is held briefly on purpose. The loaders are fire-and-forget
     * coroutines with no single completion to await, and a spinner that vanishes
     * the instant you release reads as though nothing happened. This is the one
     * place a short fixed delay is the honest choice rather than a lazy one.
     */
    fun refreshCurrent(context: Context?) {
        if (context == null) return
        _uiState.update { it.copy(refreshing = true) }
        retryLoad(context)
        viewModelScope.launch {
            kotlinx.coroutines.delay(700)
            _uiState.update { it.copy(refreshing = false) }
        }
    }

    /** Retry whatever the current screen needs. */
    fun retryLoad(context: Context?) {
        if (context == null) return
        // Clear the per-section failures too, so the sections show placeholders
        // while the retry runs rather than sitting on "Couldn't load this"
        // until it finishes — pressing Retry should visibly do something.
        _uiState.update {
            it.copy(loadFailed = false, timelineFailed = false, documentsFailed = false)
        }
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
            showCachedIfEmpty(context)
            try {
                val data = AiraApi.journey(context)
                _uiState.update {
                    it.copy(journeyData = data, loadFailed = false,
                            showingCached = false, cachedAt = null)
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        loadFailed = it.journeyData == null,
                        showingCached = it.showingCached && it.journeyData != null,
                    )
                }
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
            showCachedIfEmpty(context)
            _uiState.update { it.copy(careLoading = it.careData == null) }
            try {
                // A successful read proves there is a connection, which is
                // the only signal this app has that queued writes can go. No
                // connectivity listener, no polling — the moment we know, we
                // send, and if anything went the list is re-read so the server's
                // version replaces the local one.
                if (AiraApi.flushPending(context) > 0) {
                    _uiState.update { it.copy(careData = AiraApi.care(context)) }
                }
                val data = AiraApi.care(context)
                _uiState.update {
                    it.copy(careData = data, careLoading = false, loadFailed = false,
                            showingCached = false, cachedAt = null)
                }
                mergePending(context)
                // The server's list is the source of truth for what should
                // fire, so scheduling follows every load rather than only
                // creation — a reminder added on the web arrives here too, and
                // one deleted there stops arriving.
                ReminderScheduler.syncAll(context, data.reminders, data.appointments)
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        careLoading = false,
                        loadFailed = it.careData == null,
                        showingCached = it.showingCached && it.careData != null,
                    )
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

    /**
     * A create, which may be sent now or held until there is a connection.
     *
     * Separate from [write] because the message has to differ. Telling someone
     * "Reminder saved. Aira will notify you." when the request never left the
     * phone is the class of lie this project keeps finding and removing — the
     * screen would be claiming a notification that nothing has been scheduled
     * to send.
     */
    private fun writeCreate(
        context: Context?,
        sent: String,
        queued: String,
        block: suspend (Context) -> AiraApi.CreateOutcome,
    ) {
        if (context == null) {
            notify("Not saved — Aira isn't connected right now.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val outcome = block(context)
                notify(if (outcome == AiraApi.CreateOutcome.SENT) sent else queued)
                // Queued items are shown from the queue itself, so the list is
                // right either way.
                if (outcome == AiraApi.CreateOutcome.SENT) loadCare(context) else mergePending(context)
            } catch (e: Exception) {
                notify(saveFailureMessage(e))
            }
        }
    }

    /**
     * Put anything still waiting to be sent into the lists on screen.
     *
     * Without this a reminder added with no signal simply vanishes: it is safely
     * on disk and will reach the server later, but the person who typed it sees
     * an unchanged screen and reasonably concludes it did not save. Marked
     * [pending] so the row can say so rather than pretending it is synced.
     */
    private fun mergePending(context: Context) {
        val queued = PendingWrites.all(context, AiraApi.pendingUserKey(context))
        val care = _uiState.value.careData ?: CareData()
        fun items(kind: String) = queued.filter { it.kind == kind }.map { it.toCareItem() }
        _uiState.update {
            it.copy(
                careData = care.copy(
                    reminders = care.reminders.dropPending() + items("reminder"),
                    medicines = care.medicines.dropPending() + items("medicine"),
                    appointments = care.appointments.dropPending() + items("appointment"),
                ),
                timeline = it.timeline.dropPending() +
                    items("checkin") + items("symptom"),
            )
        }
    }

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
    internal fun saveFailureMessage(e: Exception, lead: String = "Not saved"): String {
        val raw = e.message.orEmpty()
        return when {
            e is java.net.UnknownHostException ||
                e is java.net.ConnectException ||
                e is java.net.SocketTimeoutException ->
                "$lead — Aira can't reach the server. Check your connection and try again."
            raw.contains("401") || raw.contains("403") ->
                "$lead — your session ended. Open Aira again to sign back in."
            // A 4xx with a message is the server explaining a rule the input
            // broke, which is the one case worth quoting verbatim.
            raw.isNotBlank() && raw.length < 120 -> "$lead — $raw"
            else -> "$lead. Try again in a moment."
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
        writeCreate(
            context,
            // Says whether it will actually arrive. Notifications can be off at
            // the OS level, and a "Reminder saved." that quietly never fires is
            // the failure this whole feature exists to prevent.
            when {
                context == null || !ReminderScheduler.canNotify(context) ->
                    "Reminder saved. Turn on notifications to be reminded."
                // Permission granted is not the same as being allowed to run
                // when the time comes. Saying "Aira will notify you" on a phone
                // whose battery manager defers background work is a promise the
                // app cannot keep, and it is discovered by missing a dose.
                ReminderScheduler.remindersMayBeDelayed(context) ->
                    "Reminder saved. It may arrive late while battery saving is on."
                else -> "Reminder saved. Aira will notify you."
            },
            queued = "Saved on this phone. Aira will send it — and start " +
                "reminding you — once you're back online.",
        ) { AiraApi.addReminder(it, title, time, repeat) }

    fun saveMedicine(context: Context?, name: String, dose: String, time: String) =
        writeCreate(
            context, "Medicine added.",
            "Saved on this phone. Aira will add it once you're back online.",
        ) { AiraApi.addMedicine(it, name, dose, time) }

    // ── the two toggles ─────────────────────────────────────────────────────
    //
    // These moved the screen only after the server agreed, so on a slow
    // connection a tap did nothing visible for a second or more. The natural
    // response to a control that does not respond is to press it again, and on
    // "Taken" that is the one place in this app where pressing twice is a
    // question about medication rather than a UI annoyance.
    //
    // They now move immediately and go back if the server refuses. Going back
    // has to be VISIBLE — a tick that quietly un-ticks itself while someone is
    // looking away is worse than one that never moved, because they will
    // remember ticking it. So the row reverts and a message says what happened.

    fun markMedicineTaken(context: Context?, id: String) {
        if (context == null) {
            notify("Not saved — Aira isn't connected right now.")
            return
        }
        val before = _uiState.value.careData
        setTakenLocally(id, true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AiraApi.markMedicineTaken(context, id)
                notify("Marked as taken.")
                loadCare(context)
            } catch (e: Exception) {
                // Put the screen back exactly as it was, not to a guess at what
                // it was: restoring the whole snapshot cannot leave a second
                // row wrong if two taps overlapped.
                _uiState.update { it.copy(careData = before) }
                notify(saveFailureMessage(e, "Couldn't record that dose"))
            }
        }
    }

    fun setReminderDone(context: Context?, id: String, done: Boolean) {
        if (context == null) {
            notify("Not saved — Aira isn't connected right now.")
            return
        }
        val before = _uiState.value.careData
        setDoneLocally(id, done)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AiraApi.setReminderDone(context, id, done)
                notify(if (done) "Reminder done." else "Reminder reopened.")
                loadCare(context)
            } catch (e: Exception) {
                _uiState.update { it.copy(careData = before) }
                notify(saveFailureMessage(e, "Couldn't update that reminder"))
            }
        }
    }

    private fun setTakenLocally(id: String, taken: Boolean) {
        _uiState.update { s ->
            val care = s.careData ?: return@update s
            fun mark(list: List<CareItem>) =
                list.map { if (it.id == id) it.copy(takenToday = taken, done = taken) else it }
            s.copy(
                careData = care.copy(
                    medicines = mark(care.medicines),
                    // Due is what Today counts, so it has to move with the row
                    // or the badge keeps claiming a dose is outstanding.
                    medicinesDue = care.medicinesDue.filterNot { taken && it.id == id },
                ),
            )
        }
    }

    private fun setDoneLocally(id: String, done: Boolean) {
        _uiState.update { s ->
            val care = s.careData ?: return@update s
            s.copy(
                careData = care.copy(
                    reminders = care.reminders.map {
                        if (it.id == id) it.copy(done = done) else it
                    },
                ),
            )
        }
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

    /**
     * Remove a care item, with a few seconds to change your mind.
     *
     * Delete was immediate and final. In a list holding medicines, a mistaken
     * tap on the wrong row is a medicine gone with nothing to bring it back —
     * and the row next to the one people mean to press is the pattern this
     * screen already had to widen its touch targets for.
     *
     * The row disappears at once and the SERVER call is what waits. Reversing a
     * completed delete would mean recreating the item, which gives it a new id
     * and a new created time — a different row wearing the same name. Deferring
     * instead means undo is genuinely "never mind" rather than "make me another
     * one".
     *
     * If the app dies inside the window the item survives on the server and
     * comes back on the next load. That is the safe direction to fail in: a
     * deletion that did not happen is recoverable, and one that happened
     * without the user seeing it through is not.
     */
    private var pendingDelete: kotlinx.coroutines.Job? = null

    fun deleteCareItem(context: Context?, id: String) {
        if (context == null) {
            notify("Not removed — Aira isn't connected right now.")
            return
        }
        // Any earlier pending delete commits now rather than being lost: a
        // second delete must not silently cancel the first.
        pendingDelete = null
        removeItemLocally(id)
        notify("Removed.", action = "Undo")
        pendingDelete = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(UNDO_WINDOW_MS)
            try {
                AiraApi.deleteCareItem(context, id)
                refreshTimelineAndDocs(context)
                loadCare(context)
            } catch (e: Exception) {
                notify(saveFailureMessage(e, "Couldn't remove that"))
                loadCare(context)
            }
        }
    }

    /** Cancel a delete that has not reached the server yet, and put the row
     *  back from the source of truth rather than from memory. */
    fun undoDelete(context: Context?) {
        pendingDelete?.cancel()
        pendingDelete = null
        clearSnackbar()
        loadCare(context)
        context?.let { refreshTimelineAndDocsAsync(it) }
    }

    private fun refreshTimelineAndDocsAsync(context: Context) {
        loadTimeline(context)
        loadDocuments(context)
    }

    private fun removeItemLocally(id: String) {
        _uiState.update { s ->
            val care = s.careData
            s.copy(
                careData = care?.copy(
                    reminders = care.reminders.filterNot { it.id == id },
                    medicines = care.medicines.filterNot { it.id == id },
                    medicinesDue = care.medicinesDue.filterNot { it.id == id },
                    appointments = care.appointments.filterNot { it.id == id },
                ),
                timeline = s.timeline.filterNot { it.id == id },
                documents = s.documents.filterNot { it.id == id },
            )
        }
    }

    fun closeDocument() {
        _uiState.update { it.copy(openDocument = null) }
    }

    /**
     * Hand the open document to another app, because they asked.
     *
     * Still a one-shot read grant on a FileProvider uri — the same as before —
     * but now it is a decision somebody makes with the file already in front of
     * them, rather than the only way to see their own scan.
     */
    fun openDocumentExternally(context: Context?) {
        val doc = _uiState.value.openDocument ?: return
        if (context == null) return
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.files", java.io.File(doc.path),
            )
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, doc.contentType ?: "*/*")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (_: android.content.ActivityNotFoundException) {
            notify("No app on this phone can open that file.")
        }
    }

    /** Check-ins and symptom logs — read back for the first time. */
    fun loadTimeline(context: Context?) {
        if (context == null) return
        viewModelScope.launch(Dispatchers.IO) {
            showCachedIfEmpty(context)
            try {
                _uiState.update {
                    it.copy(timeline = AiraApi.timeline(context), timelineFailed = false)
                }
            } catch (_: Exception) {
                // No banner — the screen already has one for the same lost
                // connection. But the section must not go on claiming the user
                // has logged nothing when we simply could not ask.
                // Only when there is nothing to show at all. If a cached copy
                // is up, the banner at the top already says how old it is, and
                // a second "couldn't load" inside the section would both repeat
                // it and overstate it — we DID have an answer, just an older
                // one, and it said the timeline was empty.
                _uiState.update {
                    it.copy(timelineFailed = it.timeline.isEmpty() && !it.showingCached)
                }
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
                // Held for the in-app viewer rather than thrown at whatever app
                // claims the type. Nothing outside this process has been given
                // access to it at this point.
                _uiState.update {
                    it.copy(
                        openDocument = OpenDocument(
                            path = file.absolutePath,
                            title = item.title,
                            contentType = item.contentType,
                        ),
                    )
                }
            } catch (e: Exception) {
                notify(e.message ?: "Couldn't open that document.")
            }
        }
    }

    fun loadDocuments(context: Context?) {
        if (context == null) return
        viewModelScope.launch(Dispatchers.IO) {
            showCachedIfEmpty(context)
            try {
                _uiState.update {
                    it.copy(documents = AiraApi.documents(context), documentsFailed = false)
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(documentsFailed = it.documents.isEmpty() && !it.showingCached)
                }
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
    fun saveProfile(
        context: Context?,
        name: String,
        journey: JourneyType?,
        language: String,
        weeks: Int? = null,
        priorities: List<String>? = null,
    ) =
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
            // Week and priorities live in the care context rather than the
            // profile, so they are a second call — but one save to the user.
            if (weeks != null || priorities != null) {
                AiraApi.updateCareContext(ctx, weeks, priorities)
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
                context.contentResolver.openOutputStream(uri)?.use { sink ->
                    AiraApi.exportAccountTo(context, sink)
                } ?: throw IllegalStateException("couldn't open that location")
                _uiState.update { it.copy(exporting = false) }
                notify("Your data and documents were saved to this device.")
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
        writeCreate(
            context, "Check-in saved.",
            "Saved on this phone. Aira will send it once you're back online.",
        ) { AiraApi.addCheckIn(it, feeling, sleepHours, note) }

    fun saveSymptom(context: Context?, what: String, severity: String, started: String) =
        writeCreate(
            context, "Added to your timeline.",
            "Saved on this phone. Aira will send it once you're back online.",
        ) { AiraApi.addSymptom(it, what, severity, started) }

    /**
     * The keys the emergency-profile editor reads and writes.
     *
     * Listed once so the read and the write cannot drift: a field the editor
     * saves but does not load back is one that disappears the next time
     * somebody edits anything else on the screen.
     */
    private val EMERGENCY_FIELDS = listOf(
        "care_team_name", "care_team_phone",
        "emergency_contact_name", "emergency_contact_phone",
        "blood_group", "allergies",
    )

    /**
     * Read the emergency profile back so its editor can show what is saved.
     *
     * Without this the form opened blank every time, and because saving sends
     * all six fields, anything not retyped was erased — an emergency contact
     * disappearing because someone came back to correct a phone number.
     */
    fun loadMovements(context: Context?) {
        val ctx = context ?: return
        viewModelScope.launch {
            runCatching { AiraApi.movements(ctx) }.getOrNull()?.let { history ->
                _uiState.update { it.copy(movements = history) }
            }
        }
    }

    /** Record a counting session. Queued like every other create, so one
     *  counted with no signal is not lost. */
    fun saveMovement(context: Context?, count: Int, minutes: Int) =
        write(context, "Movements recorded.", refreshCare = false) { ctx ->
            AiraApi.addMovement(ctx, count, minutes, java.util.UUID.randomUUID().toString())
            runCatching { AiraApi.movements(ctx) }.getOrNull()?.let { history ->
                _uiState.update { it.copy(movements = history) }
            }
        }

    fun saveContraction(context: Context?, seconds: Int, sincePrevious: Int?) =
        write(context, "Contraction recorded.", refreshCare = false) { ctx ->
            AiraApi.addContraction(ctx, seconds, sincePrevious,
                                   java.util.UUID.randomUUID().toString())
        }

    fun loadEmergencyProfile(context: Context?) {
        val ctx = context ?: return
        viewModelScope.launch {
            fun fieldsOf(o: org.json.JSONObject) = buildMap {
                for (key in EMERGENCY_FIELDS) {
                    o.optStringOrNull(key)?.let { put(key, it) }
                }
            }
            // Fall back to the copy on the device. Without this the editor is
            // blank with no signal, on the screen badged "Available offline".
            val fields = runCatching { fieldsOf(AiraApi.emergencyProfile(ctx)) }
                .recoverCatching {
                    AiraApi.cachedEmergencyProfile(ctx)?.let { fieldsOf(it.value) }
                        ?: throw it
                }
                .getOrNull()
            // On failure leave it null rather than empty: the editor can then
            // say it could not load, instead of showing blanks that look like
            // "nothing saved" over the top of details that exist.
            _uiState.update { s -> s.copy(emergencyProfile = fields) }
        }
    }

    fun saveEmergencyProfile(context: Context?, fields: Map<String, String>) =
        write(context, "Emergency profile saved.", refreshCare = false) {
            AiraApi.putEmergencyProfile(it, fields)
            val phone = fields["care_team_phone"]?.ifBlank { null }
            _uiState.update { s ->
                s.copy(careTeamPhone = phone, emergencyProfile = fields)
            }
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

    /**
     * Whether a reply helped, sent against the turn it was about.
     *
     * "Not helpful" goes to /feedback/report rather than general feedback,
     * because that endpoint forces a review-worthy kind. In an app that answers
     * questions about a pregnancy, a reply somebody marks wrong is worth a
     * person looking at it; filing it as generic feedback would put it in the
     * same pile as "the button is too small".
     *
     * The row acknowledges either way. A control that does nothing visible is
     * one somebody presses twice and then stops trusting.
     */
    fun rateReply(context: Context?, message: ChatMessage, helpful: Boolean) {
        _uiState.update { s ->
            s.copy(messages = s.messages.map { if (it.id == message.id) it.copy(rated = true) else it })
        }
        notify(if (helpful) "Thank you — noted." else "Thank you. This one will be looked at.")
        if (context == null) return
        val ref = message.at?.let { "chat@${it.toLong()}" }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (helpful) {
                    AiraApi.rateAnswer(context, true, ref)
                } else {
                    AiraApi.reportAnswer(
                        context, "clinical", "Marked unhelpful in chat", ref,
                    )
                }
            } catch (_: Exception) {
                // Deliberately silent. The person has already been thanked, and
                // a failure notice here would make them think their objection
                // was lost — when the useful thing is that they told us at all.
            }
        }
    }

    fun updateDraft(value: String) {
        _uiState.update { it.copy(chatDraft = value) }
        saved[KEY_DRAFT] = value
    }

    fun sendMessage(context: Context? = null) {
        val clean = _uiState.value.chatDraft.trim()
        if (clean.isEmpty()) return
        _uiState.update { it.copy(chatDraft = "") }
        // Cleared here too, or a sent message reappears in the box after a
        // process death — which reads as "it didn't send".
        saved[KEY_DRAFT] = ""
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
    private fun dispatch(text: String, context: Context?, resendId: Long? = null) {
        val history = _uiState.value.messages
            .filterNot { it.failed }
            .map { (if (it.fromAira) "assistant" else "user") to it.text }
        // Kept so the exact bubble can be marked failed, or cleared on a
        // successful resend. Matching on text would pick the wrong one when
        // somebody sends the same short message twice, which people do.
        val messageId = resendId ?: System.nanoTime()
        _uiState.update {
            it.copy(
                messages = if (resendId != null) {
                    it.messages.map { m -> if (m.id == resendId) m.copy(failed = false) else m }
                } else {
                    it.messages + ChatMessage(
                        messageId, fromAira = false, text = text, at = nowSeconds(),
                    )
                },
                sending = true,
            )
        }
        if (context == null) {
            applyOfflineReply(text, messageId)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            // Try the streaming turn first, and fall back to the one-shot one if
            // it does not get going. Anything between here and the server — a
            // proxy that buffers, an older build — can stop a stream, and the
            // reply matters more than the way it arrives.
            if (streamTurn(context, text, history, messageId)) return@launch
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
                                disclaimer = res.disclaimerNeeded,
                                card = res.actionCard,
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
                applyOfflineReply(text, messageId)
            }
        }
    }

    /** Send a message that failed, again. */
    fun resendMessage(id: Long, context: Context?) {
        val message = _uiState.value.messages.firstOrNull { it.id == id } ?: return
        dispatch(message.text, context, resendId = id)
    }

    /**
     * The message did not reach the server.
     *
     * This used to answer as though it had. It appended "I've understood that.
     * I can help organise the next step..." and labelled it `wellness` — a reply
     * to something Aira never saw, carrying a trust label asserting a screening
     * that never ran, while the message itself was dropped with no way to send
     * it. Someone typing "I've been bleeding since this morning" on a bad
     * connection got a calm acknowledgement and no delivery.
     *
     * Now the bubble says it did not send and offers to try again. The keyword
     * floor still runs first and still opens the urgent handoff, because that
     * is the whole reason the floor exists — it is the one thing that must work
     * with no server.
     */
    /**
     * A turn read as it arrives. Returns false if nothing usable came back, so
     * the caller can fall back rather than leaving somebody with a half-turn.
     *
     * The reply bubble is created on the FIRST chunk, not before. An empty
     * bubble waiting to be filled is a promise the stream might not keep — and
     * on a red turn there is no reply at all, so a bubble would have to be
     * taken away again in front of the person it was shown to.
     */
    private suspend fun streamTurn(
        context: Context,
        text: String,
        history: List<Pair<String, String>>,
        messageId: Long,
    ): Boolean {
        var replyId: Long? = null
        var got = false
        val ok = AiraApi.chatTurnStream(context, text, history) { event ->
            when (event.optString("type")) {
                "safety" ->
                    _uiState.update { it.copy(screeningDegraded = event.optBoolean("degraded")) }

                "urgent" -> {
                    got = true
                    _uiState.update {
                        it.copy(
                            sending = false,
                            urgentHelpOpen = true,
                            activeTool = null,
                            toolsOpen = false,
                        )
                    }
                }

                "chunk" -> {
                    val piece = event.optString("text")
                    if (piece.isNotEmpty()) {
                        got = true
                        val id = replyId ?: System.nanoTime().also { replyId = it }
                        _uiState.update { s ->
                            val existing = s.messages.firstOrNull { it.id == id }
                            s.copy(
                                sending = true,
                                messages = if (existing == null) {
                                    s.messages + ChatMessage(
                                        id = id, fromAira = true, text = piece,
                                        at = nowSeconds(),
                                    )
                                } else {
                                    s.messages.map {
                                        if (it.id == id) it.copy(text = it.text + piece) else it
                                    }
                                },
                            )
                        }
                    }
                }

                "done" -> {
                    got = true
                    val label = event.optStringOrNull("trust_label")
                    // The streamed turn carries the same flag as the plain one;
                    // it arrives with "done" because the model decides it after
                    // the prose it applies to.
                    val needsDisclaimer = event.optBoolean("disclaimer_needed", false)
                    val card = event.optJSONObject("action_card")?.let {
                        val tool = it.optString("tool")
                        if (tool.isBlank()) {
                            null
                        } else {
                            com.aira.companion.data.ActionCard(
                                tool = tool,
                                title = it.optString("title"),
                                detail = it.optString("detail"),
                            )
                        }
                    }
                    val id = replyId
                    _uiState.update { s ->
                        s.copy(
                            sending = false,
                            messages = s.messages.map {
                                if (it.id == id) {
                                    it.copy(
                                        trustLabel = label,
                                        disclaimer = needsDisclaimer,
                                        card = card,
                                    )
                                } else {
                                    it
                                }
                            },
                        )
                    }
                }
            }
        }
        if (!ok || !got) {
            // Nothing usable arrived. Anything half-drawn is removed so the
            // fallback does not append a second reply beneath a stub.
            replyId?.let { id ->
                _uiState.update { s -> s.copy(messages = s.messages.filterNot { it.id == id }) }
            }
            return false
        }
        return true
    }

    private fun applyOfflineReply(text: String, messageId: Long) {
        val urgent = SafetyKeywords.looksUrgent(text)
        _uiState.update { state ->
            state.copy(
                sending = false,
                urgentHelpOpen = urgent || state.urgentHelpOpen,
                activeTool = if (urgent) null else state.activeTool,
                toolsOpen = if (urgent) false else state.toolsOpen,
                messages = state.messages.map {
                    if (it.id == messageId) it.copy(failed = true) else it
                },
                // The header already carries the degraded-screening state; this
                // says the narrower, more urgent thing: nothing was sent.
                screeningDegraded = true,
            )
        }
    }

    fun notify(message: String, action: String? = null) {
        _uiState.update { it.copy(snackbarMessage = message, snackbarAction = action) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null, snackbarAction = null) }
    }

    fun resetDemo() {
        _uiState.value = AiraUiState()
    }

    private companion object {
        /** Keys for the values that must outlive the process — see the
         *  constructor. */
        const val KEY_DRAFT = "chat_draft"
        const val KEY_DESTINATION = "destination"

        // Local FALLBACK only — the authoritative gate runs server-side in AiraApi.

        fun apiJourney(journey: JourneyType?): String = when (journey) {
            JourneyType.Trying -> "trying"
            JourneyType.Pregnant -> "pregnant"
            JourneyType.Postpartum -> "postpartum"
            JourneyType.Loss -> "loss"
            else -> "exploring"
        }
    }
}
