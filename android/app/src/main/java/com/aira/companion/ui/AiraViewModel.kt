package com.aira.companion.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aira.companion.model.AiraTool
import com.aira.companion.model.AiraUiState
import com.aira.companion.model.AppStage
import com.aira.companion.model.ChatMessage
import com.aira.companion.model.DetailPage
import com.aira.companion.model.JourneyType
import com.aira.companion.model.MainDestination
import com.aira.companion.model.OnboardingAnswer
import com.aira.companion.model.Reminder
import com.aira.companion.model.VideoItem
import com.aira.companion.model.applyTick
import com.aira.companion.model.applyUntick
import com.aira.companion.model.postpartumAnchorPrompt
import com.aira.companion.model.pregnancyAnchorPrompt
import com.aira.companion.model.upsertMood
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

    init {
        // Returning user: skip straight to Me. runCatching because JVM unit
        // tests construct this with a stub Application (no SharedPreferences).
        val nameDismissed = runCatching { AiraApi.namePromptDismissed(appContext) }
            .getOrDefault(false)
        if (nameDismissed) {
            _uiState.update { it.copy(namePromptDismissed = true) }
        }
        val onboarded = runCatching { AiraApi.isOnboarded(appContext) }.getOrDefault(false)
        if (onboarded) {
            _uiState.update {
                it.copy(
                    stage = AppStage.Main,
                    destination = MainDestination.Me,
                    messages = listOf(
                        ChatMessage(
                            id = 1,
                            fromAira = true,
                            text = "Welcome back. How are you feeling today?",
                        ),
                    ),
                )
            }
        }
    }

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
                destination = MainDestination.Me,
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
        runCatching { AiraApi.setOnboarded(appContext) }
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

    // ── Me / Videos data (loaders fired from LaunchedEffect in MainExperience,
    // never from selectDestination — state transitions stay pure for tests) ──

    fun refreshMe() {
        if (_uiState.value.meLoading) return
        _uiState.update { it.copy(meLoading = true) }
        viewModelScope.launch {
            // Individually runCatching: one failed fetch must not blank the rest.
            val care = runCatching { AiraApi.getCareContext(appContext) }.getOrNull()
            val moods = runCatching { AiraApi.getMoods(appContext) }.getOrNull()
            val reminders = runCatching { AiraApi.getReminders(appContext) }.getOrNull()
            val suggested = runCatching { AiraApi.getSuggestedVideo(appContext) }.getOrNull()
            val journey = runCatching { AiraApi.getJourney(appContext) }.getOrNull()
            val feed = runCatching {
                AiraApi.getToday(appContext, java.time.LocalTime.now().hour,
                                 LocalDate.now().toString())
            }.getOrNull()
            // Week-flip celebration (review #8): the first refresh that sees
            // a HIGHER computed week than the last acknowledged one raises the
            // banner; first-ever sight is stored silently (nothing to
            // celebrate about opening the app).
            val flipped = care?.week?.let { week ->
                val prev = runCatching { AiraApi.lastSeenWeek(appContext) }.getOrNull()
                when {
                    prev == null -> {
                        runCatching { AiraApi.setLastSeenWeek(appContext, week) }
                        null
                    }
                    week > prev -> week
                    else -> null
                }
            }
            _uiState.update { state ->
                state.copy(
                    careSummary = care ?: state.careSummary,
                    moods = moods ?: state.moods,
                    reminders = reminders ?: state.reminders,
                    suggestedVideo = suggested ?: state.suggestedVideo,
                    journeyContent = journey ?: state.journeyContent,
                    todayFeed = feed ?: state.todayFeed,
                    weekJustFlipped = flipped ?: state.weekJustFlipped,
                    // Stale flag (review #39): every fetch failed but old
                    // data is still on screen — say so instead of lying.
                    meStale = care == null && feed == null &&
                        state.careSummary != null,
                    meLoading = false,
                )
            }
            if (care == null && moods == null && reminders == null) {
                notify("Couldn't reach Aira's care service — showing what's cached.")
            }
            // Widget cache (review #55): the widget renders from prefs and
            // never talks to the network itself.
            runCatching {
                val water = (reminders ?: _uiState.value.reminders)
                    .firstOrNull { it.kind == "water" }
                appContext.getSharedPreferences(
                    "aira_net", android.content.Context.MODE_PRIVATE,
                )
                    .edit()
                    .putInt("widget_week", care?.week ?: -1)
                    .putInt("widget_day", feed?.dayInWeek ?: -1)
                    .putInt("widget_water", water?.ticksToday ?: -1)
                    .putInt("widget_target", water?.targetPerDay ?: -1)
                    .apply()
                com.aira.companion.widget.AiraWidgetProvider.pushUpdate(appContext)
            }
        }
    }

    fun ensureVideos() {
        if (_uiState.value.videosLoaded || _uiState.value.videosLoading) return
        _uiState.update { it.copy(videosLoading = true) }
        viewModelScope.launch {
            runCatching { AiraApi.getVideos(appContext) }
                .onSuccess { videos ->
                    _uiState.update {
                        it.copy(videos = videos, videosLoading = false, videosLoaded = true)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(videosLoading = false) }
                    notify("Couldn't load videos right now — try again in a moment.")
                }
        }
    }

    fun setMood(mood: String, note: String? = null) {
        val day = LocalDate.now().toString()
        // Postpartum logs twice daily (am/pm slot by clock); others once.
        val slot = if (_uiState.value.careSummary?.stage == "postpartum") {
            if (java.time.LocalTime.now().hour < 15) "am" else "pm"
        } else null
        _uiState.update { it.copy(moods = upsertMood(it.moods, day, mood)) } // optimistic
        viewModelScope.launch {
            runCatching { AiraApi.postMood(appContext, mood, note, slot) }
                .onSuccess { if (note != null) notify("Noted — thanks for sharing.") }
                .onFailure {
                    runCatching { AiraApi.getMoods(appContext) } // re-sync to server truth
                        .onSuccess { fresh -> _uiState.update { it.copy(moods = fresh) } }
                    notify("Couldn't save your check-in — try again in a moment.")
                }
        }
    }

    fun nudgeActedOn(kind: String) {
        viewModelScope.launch {
            runCatching { AiraApi.postFocusTap(appContext, kind) }
            runCatching { AiraApi.postMetric(appContext, "nudge_tap") }
        }
    }

    fun trackOpen(destination: MainDestination) {
        val event = when (destination) {
            MainDestination.Me -> "me_open"
            MainDestination.Chat -> "chat_open"
            MainDestination.Videos -> "videos_open"
        }
        viewModelScope.launch { runCatching { AiraApi.postMetric(appContext, event) } }
    }

    fun sendTipFeedback(tipId: String, helpful: Boolean) {
        viewModelScope.launch {
            runCatching { AiraApi.sendTipFeedback(appContext, tipId, helpful) }
        }
    }

    fun dismissNamePrompt() {
        runCatching { AiraApi.setNamePromptDismissed(appContext) }
        _uiState.update { it.copy(namePromptDismissed = true) }
    }

    fun acknowledgeWeekFlip() {
        _uiState.value.weekJustFlipped?.let { week ->
            runCatching { AiraApi.setLastSeenWeek(appContext, week) }
        }
        _uiState.update { it.copy(weekJustFlipped = null) }
    }

    fun untickReminder(reminder: Reminder) {
        if (reminder.ticksToday == 0) return
        _uiState.update { it.copy(reminders = applyUntick(it.reminders, reminder.id)) }
        viewModelScope.launch {
            runCatching {
                if (reminder.kind == "medicine") AiraApi.medicineUntaken(appContext, reminder.id)
                else AiraApi.untickReminder(appContext, reminder.id)
            }.onFailure {
                runCatching { AiraApi.getReminders(appContext) }
                    .onSuccess { fresh -> _uiState.update { it.copy(reminders = fresh) } }
            }
        }
    }

    fun setReminderTarget(reminder: Reminder, target: Int) {
        val clamped = target.coerceIn(1, 24)
        _uiState.update { state ->
            state.copy(reminders = state.reminders.map {
                if (it.id == reminder.id) {
                    it.copy(targetPerDay = clamped, doneToday = it.ticksToday >= clamped)
                } else it
            })
        }
        viewModelScope.launch {
            runCatching { AiraApi.setReminderTarget(appContext, reminder.id, clamped) }
                .onFailure {
                    runCatching { AiraApi.getReminders(appContext) }
                        .onSuccess { fresh -> _uiState.update { it.copy(reminders = fresh) } }
                    notify("Couldn't change the goal — try again in a moment.")
                }
        }
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            runCatching { AiraApi.setDisplayName(appContext, name) }
                .onSuccess {
                    runCatching { AiraApi.getCareContext(appContext) }
                        .onSuccess { fresh -> _uiState.update { it.copy(careSummary = fresh) } }
                    notify("Nice to meet you${if (name.isNotBlank()) ", $name" else ""}.")
                }
                .onFailure { notify("Couldn't save your name — try again in a moment.") }
        }
    }

    fun tickReminder(reminder: Reminder) {
        if (reminder.doneToday) return
        _uiState.update { it.copy(reminders = applyTick(it.reminders, reminder.id)) } // optimistic
        viewModelScope.launch {
            runCatching {
                if (reminder.kind == "medicine") AiraApi.markMedicineTaken(appContext, reminder.id)
                else AiraApi.tickReminder(appContext, reminder.id)
            }.onFailure {
                runCatching { AiraApi.getReminders(appContext) } // re-sync
                    .onSuccess { fresh -> _uiState.update { it.copy(reminders = fresh) } }
                notify("Couldn't record that — it'll stay unticked until it syncs.")
            }
        }
    }

    fun openSettings() {
        _uiState.update { it.copy(settingsOpen = true, toolsOpen = false) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(settingsOpen = false) }
    }

    // ── Detail pages (Journey / Moods / Care) — pure transitions; their data
    // loads fire from LaunchedEffect(state.detail) in MainExperience ─────────

    fun openDetail(page: DetailPage) {
        _uiState.update { it.copy(detail = page, toolsOpen = false) }
    }

    fun closeDetail() {
        _uiState.update { it.copy(detail = null) }
    }

    fun loadDetailData(page: DetailPage) {
        viewModelScope.launch {
            when (page) {
                DetailPage.Journey ->
                    runCatching { AiraApi.getJourney(appContext) }
                        .onSuccess { c -> _uiState.update { it.copy(journeyContent = c) } }
                        .onFailure { notify("Couldn't load this week's guide right now.") }
                DetailPage.Moods ->
                    runCatching { AiraApi.getReport(appContext, days = 30) }
                        .onSuccess { rep ->
                            _uiState.update {
                                it.copy(report = rep, moodHistory = rep.moods)
                            }
                        }
                        .onFailure { notify("Couldn't load your mood history right now.") }
                DetailPage.Care -> {
                    runCatching { AiraApi.getReminders(appContext) }
                        .onSuccess { r -> _uiState.update { it.copy(reminders = r) } }
                        .onFailure { notify("Couldn't sync reminders right now.") }
                    runCatching { AiraApi.getReport(appContext, days = 30) }
                        .onSuccess { rep -> _uiState.update { it.copy(report = rep) } }
                        .onFailure { /* report is enrichment for the page */ }
                }
            }
        }
    }

    /** Browse another week inside the journey detail (never mutates the
     * stored context — same contract as the web client). */
    fun browseJourneyWeek(week: Int) {
        viewModelScope.launch {
            runCatching { AiraApi.getJourney(appContext, week) }
                .onSuccess { c -> _uiState.update { it.copy(journeyContent = c) } }
                .onFailure { notify("Couldn't load week $week right now.") }
        }
    }

    fun addReminder(title: String, kind: String, targetPerDay: Int) {
        viewModelScope.launch {
            runCatching { AiraApi.addReminder(appContext, title, kind, targetPerDay) }
                .onSuccess {
                    runCatching { AiraApi.getReminders(appContext) }
                        .onSuccess { r -> _uiState.update { it.copy(reminders = r) } }
                }
                .onFailure { notify("Couldn't add that reminder — try again in a moment.") }
        }
    }

    fun deleteReminder(reminder: Reminder) {
        if (reminder.kind == "medicine") return // medicines are managed in their own sheet
        _uiState.update { state ->
            state.copy(reminders = state.reminders.filterNot { it.id == reminder.id })
        }
        viewModelScope.launch {
            runCatching { AiraApi.deleteReminder(appContext, reminder.id) }
                .onFailure {
                    runCatching { AiraApi.getReminders(appContext) } // re-sync
                        .onSuccess { r -> _uiState.update { it.copy(reminders = r) } }
                    notify("Couldn't remove that — it's back until it syncs.")
                }
        }
    }

    // ── Tool-sheet data + actions (P11 part 3): sheets render server truth;
    // loads fire from LaunchedEffect(state.activeTool) in MainExperience ─────

    fun loadToolData(tool: AiraTool) {
        viewModelScope.launch {
            when (tool) {
                AiraTool.Medicines -> runCatching { AiraApi.getMedicines(appContext) }
                    .onSuccess { fresh -> _uiState.update { it.copy(medicines = fresh) } }
                AiraTool.Appointment -> runCatching { AiraApi.getAppointments(appContext) }
                    .onSuccess { fresh -> _uiState.update { it.copy(appointments = fresh) } }
                AiraTool.CarePlan -> runCatching { AiraApi.getCarePlan(appContext) }
                    .onSuccess { fresh -> _uiState.update { it.copy(planItems = fresh) } }
                AiraTool.Memory -> runCatching { AiraApi.getMemory(appContext) }
                    .onSuccess { fresh -> _uiState.update { it.copy(memoryItems = fresh) } }
                else -> Unit
            }
        }
    }

    fun addMedicine(name: String, dose: String, timeOfDay: String) {
        viewModelScope.launch {
            runCatching { AiraApi.addMedicine(appContext, name, dose, timeOfDay) }
                .onSuccess {
                    loadToolData(AiraTool.Medicines)
                    notify("$name added to your medicines.")
                }
                .onFailure { notify("Couldn't add $name — try again in a moment.") }
        }
    }

    fun markMedicineTakenFromSheet(id: String) {
        viewModelScope.launch {
            runCatching { AiraApi.markMedicineTaken(appContext, id) }
                .onSuccess { loadToolData(AiraTool.Medicines) }
                .onFailure { notify("Couldn't record that — try again in a moment.") }
        }
    }

    fun addAppointment(title: String, whenTs: Double, location: String) {
        viewModelScope.launch {
            runCatching { AiraApi.addAppointment(appContext, title, whenTs, location) }
                .onSuccess {
                    loadToolData(AiraTool.Appointment)
                    notify("Appointment saved.")
                }
                .onFailure { notify("Couldn't save the appointment — try again in a moment.") }
        }
    }

    fun addPlanItem(title: String) {
        viewModelScope.launch {
            runCatching { AiraApi.addPlanItem(appContext, title) }
                .onSuccess { loadToolData(AiraTool.CarePlan) }
                .onFailure { notify("Couldn't add that to your plan — try again in a moment.") }
        }
    }

    fun togglePlanItem(id: String) {
        // Optimistic: flip locally, re-sync on failure.
        _uiState.update { state ->
            state.copy(planItems = state.planItems?.map {
                if (it.id == id) it.copy(done = !it.done) else it
            })
        }
        viewModelScope.launch {
            runCatching { AiraApi.togglePlanItem(appContext, id) }
                .onFailure { loadToolData(AiraTool.CarePlan) }
        }
    }

    fun forgetMemory(id: String) {
        _uiState.update { state ->
            state.copy(memoryItems = state.memoryItems?.filterNot { it.id == id })
        }
        viewModelScope.launch {
            runCatching { AiraApi.forgetMemory(appContext, id) }
                .onSuccess { notify("Memory removed.") }
                .onFailure {
                    loadToolData(AiraTool.Memory)
                    notify("Couldn't remove that memory — try again in a moment.")
                }
        }
    }

    /** Create a real reminder from the tool sheet (server assigns id/seed). */
    fun createReminder(title: String, targetPerDay: Int) {
        viewModelScope.launch {
            runCatching { AiraApi.addReminder(appContext, title, "custom", targetPerDay) }
                .onSuccess {
                    runCatching { AiraApi.getReminders(appContext) }
                        .onSuccess { fresh -> _uiState.update { it.copy(reminders = fresh) } }
                    notify("Reminder created — it lives in Today's care on Me.")
                }
                .onFailure { notify("Couldn't create the reminder — try again in a moment.") }
        }
    }

    // ── In-app video player + the social layer (likes / stars) ──────────────

    fun openPlayer(video: VideoItem) {
        _uiState.update { it.copy(playerVideo = video) }
        viewModelScope.launch { runCatching { AiraApi.postMetric(appContext, "video_play") } }
    }

    fun closePlayer() {
        _uiState.update { it.copy(playerVideo = null) }
    }

    /** Completion from the in-app player -> unwatched-first rotation. */
    fun playerCompleted(video: VideoItem) {
        viewModelScope.launch {
            runCatching { AiraApi.markVideoWatched(appContext, video.id) }
        }
    }

    private fun updateVideoEverywhere(id: String, transform: (VideoItem) -> VideoItem) {
        _uiState.update { state ->
            state.copy(
                videos = state.videos.map { if (it.id == id) transform(it) else it },
                suggestedVideo = state.suggestedVideo
                    ?.let { if (it.id == id) transform(it) else it },
                playerVideo = state.playerVideo
                    ?.let { if (it.id == id) transform(it) else it },
            )
        }
    }

    fun toggleVideoLike(video: VideoItem) {
        // Optimistic flip; the server response then carries the true count.
        updateVideoEverywhere(video.id) {
            it.copy(myLike = !it.myLike,
                    likeCount = it.likeCount + if (it.myLike) -1 else 1)
        }
        viewModelScope.launch {
            runCatching { AiraApi.toggleVideoLike(appContext, video.id) }
                .onSuccess { (liked, count) ->
                    updateVideoEverywhere(video.id) {
                        it.copy(myLike = liked, likeCount = count)
                    }
                }
                .onFailure {
                    updateVideoEverywhere(video.id) {
                        it.copy(myLike = video.myLike, likeCount = video.likeCount)
                    }
                }
        }
    }

    fun rateVideo(video: VideoItem, stars: Int) {
        updateVideoEverywhere(video.id) { it.copy(myStars = stars) }
        viewModelScope.launch {
            runCatching { AiraApi.rateVideo(appContext, video.id, stars) }
                .onSuccess { (my, avg, count) ->
                    updateVideoEverywhere(video.id) {
                        it.copy(myStars = my, avgStars = avg, ratingCount = count)
                    }
                }
                .onFailure { notify("Couldn't save your rating — try again in a moment.") }
        }
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
                    appendAira(turn.reply ?: "", turn.cards)
                    _uiState.update { it.copy(sending = false) }
                }
            }
        }
    }

    private fun appendAira(text: String, cards: List<com.aira.companion.model.ActionCard> = emptyList()) {
        if (text.isBlank()) return
        _uiState.update { state ->
            state.copy(
                messages = state.messages +
                    ChatMessage(id = System.nanoTime(), fromAira = true, text = text, cards = cards),
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
