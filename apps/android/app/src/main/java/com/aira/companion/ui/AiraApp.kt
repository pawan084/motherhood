package com.aira.companion.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aira.companion.BuildConfig
import com.aira.companion.model.AiraTool
import com.aira.companion.model.AppStage
import com.aira.companion.model.AuthMode
import com.aira.companion.model.MainDestination
import com.aira.companion.model.journeyLabel
import com.aira.companion.model.updatesCount
import com.aira.companion.reminders.ReminderScheduler
import com.aira.companion.ui.components.AiraBottomNavigation
import com.aira.companion.ui.components.animationsEnabled
import com.aira.companion.ui.components.BrandOrb
import com.aira.companion.ui.components.rememberAiraHaptics
import com.aira.companion.ui.screens.AiraChatScreen
import com.aira.companion.ui.screens.AuthScreen
import com.aira.companion.ui.screens.CareScreen
import com.aira.companion.ui.screens.DocumentViewer
import com.aira.companion.ui.screens.DynamicToolSheet
import com.aira.companion.ui.screens.JourneyScreen
import com.aira.companion.ui.screens.JourneySectionSheet
import com.aira.companion.ui.screens.LearnScreen
import com.aira.companion.ui.screens.OnboardingScreen
import com.aira.companion.ui.screens.TodayScreen
import com.aira.companion.ui.screens.ToolActions
import com.aira.companion.ui.screens.ToolTraySheet
import com.aira.companion.ui.screens.TutorialScreen
import com.aira.companion.ui.screens.UrgentHelpDialog
import com.aira.companion.ui.screens.WelcomeScreen
import com.aira.companion.ui.screens.YouScreen
import com.aira.companion.ui.theme.Amber
import com.aira.companion.ui.theme.AmberMist
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.SageMist
import com.aira.companion.ui.theme.Urgent
import com.aira.companion.ui.theme.UrgentMist

@Composable
fun AiraApp(viewModel: AiraViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = state.snackbarAction,
                withDismissAction = state.snackbarAction == null,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete(context)
            } else {
                viewModel.clearSnackbar()
            }
        }
    }

    // Resolve the cached session once per process. A returning user goes straight
    // to Today instead of repeating onboarding, which is what happened before.
    LaunchedEffect(Unit) { viewModel.restoreSession(context) }

    when (state.stage) {
        // Nothing is drawn for Starting: the system splash is still on screen,
        // held by MainActivity until this stage ends. Rendering a spinner behind
        // it would only be visible as a flash on the handover.
        AppStage.Starting -> Unit
        AppStage.Tutorial -> TutorialScreen(onFinish = { viewModel.finishTutorial(context) })
        AppStage.Welcome ->
            WelcomeScreen(
                onStart = viewModel::startOnboarding,
                onCreateAccount = { viewModel.openAuth(context, AuthMode.SignUp) },
                onSignIn = { viewModel.openAuth(context, AuthMode.SignIn) },
            )
        AppStage.Auth ->
            AuthScreen(
                state = state,
                onModeChange = { viewModel.setAuthMode(context, it) },
                onSubmit = { email, password ->
                    if (state.authMode == AuthMode.SignUp) {
                        viewModel.signUp(context, email, password)
                    } else {
                        viewModel.signIn(context, email, password)
                    }
                },
                onGoogle = { viewModel.signInWithGoogle(context) },
                onClose = viewModel::closeAuth,
            )
        AppStage.Onboarding ->
            // Four dedicated screens rather than the chat transcript this
            // replaces. OnboardingChatScreen is still in the tree and still
            // tested; it is no longer the way in.
            OnboardingScreen(
                onComplete = { journey, weeks, language, remindersPerDay ->
                    viewModel.completeGuidedOnboarding(
                        context = context,
                        journey = journey,
                        weeks = weeks,
                        language = language,
                        remindersPerDay = remindersPerDay,
                    )
                },
                onExit = viewModel::replayTutorial,
            )
        AppStage.Main -> {
            MainExperience(
                state = state,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
            )
        }
    }
}

// StartingScreen used to live here — a spinner shown while the session resolved.
// The system splash now covers that window instead, so drawing anything behind it
// would only ever be seen as a flash at the handover.

@Composable
private fun MainExperience(
    state: com.aira.companion.model.AiraUiState,
    viewModel: AiraViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val haptics = rememberAiraHaptics()

    // Back returns to Today rather than leaving the app.
    //
    // There was no BackHandler anywhere in the app, so back from Aira, Care,
    // Journey or You closed it outright. On a phone that reads as a crash, and
    // it is the kind of thing nobody reports — they just stop opening the app.
    //
    // Only registered off Today, so back from Today still exits, which is what
    // the system gesture should do at the top of a stack. The tool sheet and
    // the urgent dialog register their own handlers while they are shown and,
    // being registered later, take precedence — so back closes the sheet first
    // and only then walks the tabs.
    BackHandler(enabled = state.destination != MainDestination.Today) {
        viewModel.selectDestination(MainDestination.Today)
    }

    // Keeps each tab's scroll position while you are away from it.
    //
    // The destinations are a `when`, so leaving one removes it from composition
    // and every `rememberScrollState` inside it was discarded — coming back to
    // Care after a glance at Today put you at the top of the list again. This
    // holds each tab's saveable state by destination, and because
    // `rememberScrollState` is already saveable, the positions survive both the
    // switch and process death without any screen having to know about it.
    val tabState = rememberSaveableStateHolder()
    val pullState = rememberPullToRefreshState()

    // Refresh the journey-aware content whenever the user lands on Today/Journey.
    LaunchedEffect(state.destination) {
        when (state.destination) {
            // Today shows medicines/appointments too, so it needs Care as well.
            // Journey content loads with Today now that it lives there. Without
            // this the "Read about" sections would only ever appear if someone
            // had happened to open the old tab before it was removed.
            MainDestination.Today -> {
                viewModel.loadToday(context)
                viewModel.loadCare(context)
                viewModel.loadJourney(context)
                // The mood card shows this week's check-ins, so Today needs the
                // timeline that Care was previously alone in loading. Without it
                // the week strip renders empty on a user who has logged all week.
                viewModel.loadTimeline(context)
            }
            MainDestination.Aira -> viewModel.loadChatHistory(context)
            MainDestination.Journey -> viewModel.loadJourney(context)
            MainDestination.Care -> {
                viewModel.loadCare(context)
                viewModel.loadTimeline(context)
                viewModel.loadDocuments(context)
            }
            MainDestination.You -> { viewModel.loadConsent(context); viewModel.loadPrefs(context) }
            else -> {}
        }
    }
    // The export writes to a location the user picks, so no storage permission
    // and no FileProvider are involved.
    val exportLauncher = rememberLauncherForActivityResult(
        // A zip now, not JSON: the export carries the Care Vault's actual files
        // alongside the records.
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) viewModel.exportAccountTo(context, uri) }

    // Notification permission, asked at the moment it means something.
    //
    // Not at launch: a permission dialog before the user knows what the app is
    // gets declined, and on Android 13+ a decline is close to final. It is
    // requested when the reminder sheet opens — the one point where the answer
    // has an obvious consequence the person is already thinking about.
    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onNotificationPermissionResult(context, granted) }
    LaunchedEffect(state.activeTool) {
        if (state.activeTool == AiraTool.Reminder &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ReminderScheduler.canNotify(context)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Ivory,
            topBar = {
                AiraAppHeader(
                    destination = state.destination,
                    weeks = state.todayData?.weeks,
                    journey = state.todayData?.journey,
                    onUrgentHelp = { haptics.weighty(); viewModel.openUrgentHelp(context) },
                )
            },
            bottomBar = {
                AiraBottomNavigation(
                    selected = state.destination,
                    onSelect = viewModel::selectDestination,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            // Offline.
            //
            // Every screen loader used to swallow its exception, so a user with
            // no signal watched a screen that never filled in — indistinguishable
            // from a broken app, and with nothing to press.
            //
            // This REPLACES the screen rather than sitting above it. `loadFailed`
            // is only set when there is no data at all, and a Today with nothing
            // in it doesn't degrade quietly: it falls back to "Exploring" and an
            // empty ring, so an offline pregnant user was shown someone else's
            // stage. An honest empty screen beats a confident wrong one.
            if (state.loadFailed) {
                OfflineNotice(
                    // Keeps its own inset: this branch returns early, outside
                    // the Column that carries the padding for the tabs.
                    modifier = Modifier.padding(padding),
                    onRetry = { viewModel.retryLoad(context) },
                )
                return@Scaffold
            }
            Column(modifier = Modifier.padding(padding)) {
            // Cached content keeps the screen; it just says how old it is. The
            // notice sits above the tab rather than replacing it, because what
            // is underneath is real and useful — it is the one thing an offline
            // user came here for.
            if (state.showingCached) {
                CachedNotice(
                    savedAt = state.cachedAt,
                    onRetry = { viewModel.retryLoad(context) },
                )
            }
            // Pull to refresh, on the tabs where "refresh" means something.
            //
            // Not on Aira: that screen is a conversation in a LazyColumn, where
            // a downward drag at the top means "read what I said earlier", and
            // hijacking it to reload would fight the gesture people already
            // have. Not on You either — nothing there is a feed.
            val pullable = state.destination in setOf(
                MainDestination.Today,
                MainDestination.Journey,
                MainDestination.Care,
                MainDestination.Learn,
            )
            PullToRefreshBox(
                isRefreshing = pullable && state.refreshing,
                onRefresh = { if (pullable) viewModel.refreshCurrent(context) },
                modifier = Modifier.fillMaxSize(),
                state = pullState,
                indicator = {
                    if (pullable) {
                        Indicator(
                            modifier = Modifier.align(Alignment.TopCenter),
                            isRefreshing = state.refreshing,
                            state = pullState,
                            containerColor = Paper,
                            color = Plum,
                        )
                    }
                },
            ) {
            // A short crossfade between tabs.
            //
            // Switching was an instant cut, which on a screen that changes
            // entirely reads as a flicker — you cannot tell whether the app
            // moved or redrew. 180ms is enough to say "this is a different
            // place" and short enough not to be a thing you wait through.
            //
            // Zero when the system's animator scale is off. Someone who turned
            // animation off has already said so once, and the reasons for
            // turning it off apply more in an app used during pregnancy and
            // after birth, not less.
            val motion = animationsEnabled()
            Crossfade(
                targetState = state.destination,
                animationSpec = tween(durationMillis = if (motion) 180 else 0),
                label = "tab",
            ) { destination ->
            tabState.SaveableStateProvider(destination) {
            when (destination) {
                MainDestination.Today ->
                    TodayScreen(
                        onDestination = viewModel::selectDestination,
                        onOpenTool = viewModel::openTool,
                        modifier = Modifier,
                        today = state.todayData,
                        waiting = updatesCount(state.careData),
                        journey = state.journeyData,
                        onOpenSection = viewModel::openJourneySection,
                        onOpenLearn = {
                            viewModel.selectDestination(MainDestination.Learn)
                        },
                        onOpenJourney = {
                            viewModel.selectDestination(MainDestination.Journey)
                        },
                        timeline = state.timeline,
                        onLogMood = { mood ->
                            haptics.confirm()
                            // Sleep and note belong to the full check-in tool,
                            // not to this card. A one-tap mood must not invent a
                            // sleep figure just to fill the column.
                            viewModel.saveCheckIn(context, mood, 0.0, "")
                        },
                    )
                MainDestination.Aira ->
                    AiraChatScreen(
                        state = state,
                        onDraftChange = viewModel::updateDraft,
                        onSend = { haptics.confirm(); viewModel.sendMessage(context) },
                        onRetry = { viewModel.resendMessage(it, context) },
                        onRate = { message, helpful ->
                            viewModel.rateReply(context, message, helpful)
                        },
                        onQuickMessage = { haptics.confirm(); viewModel.quickMessage(it, context) },
                        onOpenTools = viewModel::openTools,
                        onOpenTool = viewModel::openTool,
                        modifier = Modifier,
                    )
                MainDestination.Journey ->
                    JourneyScreen(
                        onOpenTool = viewModel::openTool,
                        modifier = Modifier,
                        journey = state.journeyData,
                        onOpenSection = viewModel::openJourneySection,
                        onOpenLearn = {
                            viewModel.selectDestination(MainDestination.Learn)
                        },
                    )
                MainDestination.Learn ->
                    LearnScreen(
                        modifier = Modifier,
                        videos = state.videos,
                        weekVideo = state.weekVideo,
                        // Only for a pregnant caller with a known week: the
                        // server refuses to guess a stage, and neither should
                        // this. Anyone else simply has no weekly card, which is
                        // correct rather than missing.
                        weekWithoutVideo = state.todayData
                            ?.takeIf { state.weekVideo == null }
                            ?.takeIf { it.journey.equals("pregnant", ignoreCase = true) }
                            ?.weeks,
                        categories = state.videoCategories,
                        savedIds = state.savedVideoIds,
                        loading = state.videosLoading,
                        onLoad = { viewModel.loadVideos(context) },
                        // Opens the produced video in whatever the phone uses
                        // for it. Nothing in the catalogue is playable yet, so
                        // this is unreachable today — but it is wired, so the
                        // Watch button can never be the inert control that
                        // appears the moment a topic is marked ready.
                        onWatch = { video ->
                            video.mediaUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            android.net.Uri.parse(
                                                com.aira.companion.data.AiraApi.absoluteUrl(url),
                                            ),
                                        )
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }
                        },
                        onToggleSave = { haptics.confirm(); viewModel.toggleSaveVideo(context, it) },
                        onUrgentHelp = { haptics.weighty(); viewModel.openUrgentHelp(context) },
                    )
                MainDestination.Care ->
                    CareScreen(
                        onOpenTool = viewModel::openTool,
                        onUrgentHelp = { haptics.weighty(); viewModel.openUrgentHelp(context) },
                        modifier = Modifier,
                        care = state.careData,
                        loading = state.careLoading,
                        highlightedItem = state.highlightedCareItem,
                        onHighlightShown = viewModel::clearCareHighlight,
                        onMarkTaken = { haptics.confirm(); viewModel.markMedicineTaken(context, it) },
                        timeline = state.timeline,
                        documents = state.documents,
                        onReminderDone = { id, done ->
                            haptics.confirm()
                            viewModel.setReminderDone(context, id, done)
                        },
                        onRename = { id, field, value ->
                            viewModel.renameCareItem(context, id, field, value)
                        },
                        onDelete = { haptics.weighty(); viewModel.deleteCareItem(context, it) },
                        onEditReminder = viewModel::editReminder,
                        onOpenDocument = { viewModel.openDocument(context, it) },
                        timelineFailed = state.timelineFailed,
                        documentsFailed = state.documentsFailed,
                        onRetry = { viewModel.retryLoad(context) },
                        remindersMayBeDelayed =
                            ReminderScheduler.remindersMayBeDelayed(context),
                        onOpenBatterySettings = {
                            // Best-effort: a few ROMs do not expose this screen,
                            // and crashing on a settings shortcut would be a
                            // worse outcome than the notice standing on its own.
                            runCatching {
                                context.startActivity(ReminderScheduler.batterySettingsIntent())
                            }
                        },
                    )
                MainDestination.You ->
                    YouScreen(
                        onOpenTool = viewModel::openTool,
                        modifier = Modifier,
                        name = state.todayData?.name.orEmpty(),
                        weeks = state.todayData?.weeks,
                        journey = state.todayData?.journey,
                        language = state.language,
                        consent = state.consent,
                        voice = state.voicePrefs.voice,
                        exporting = state.exporting,
                        deleting = state.deleting,
                        onLoadConsent = { viewModel.loadConsent(context) },
                        onSetConsent = { f, g -> viewModel.setConsent(context, f, g) },
                        onExport = { exportLauncher.launch("aira-export.zip") },
                        onDelete = { viewModel.deleteAccount(context) },
                        signedIn = state.signedIn,
                        accountEmail = state.accountEmail,
                        onSignOut = { viewModel.signOut(context) },
                        onCreateAccount = { viewModel.openAuth(context, AuthMode.SignUp) },
                        onSignIn = { viewModel.openAuth(context, AuthMode.SignIn) },
                        journeyType = state.journey,
                        onSaveProfile = { n, j, l, w, p, d ->
                            viewModel.saveProfile(context, n, j, l, w, p, d)
                        },
                        weeksReported = state.todayData?.weeksReported,
                        dueDate = state.todayData?.dueDate,
                        priorities = state.todayData?.priorities.orEmpty(),
                        onReplayTutorial = viewModel::replayTutorial,
                    )
            }
            }
            }
            }
            }
        }

        if (state.toolsOpen) {
            ToolTraySheet(
                onDismiss = viewModel::closeTools,
                onOpenTool = viewModel::openTool,
                journey = state.todayData?.journey,
                weeks = state.todayData?.weeks,
            )
        }

        val toolActions = remember(context) {
            ToolActions(
                saveReminder = { t, time, repeat -> viewModel.saveReminder(context, t, time, repeat) },
                updateReminder = { id, t, time, repeat ->
                    viewModel.updateReminder(context, id, t, time, repeat)
                },
                saveMedicine = { n, d, t -> viewModel.saveMedicine(context, n, d, t) },
                saveAppointment = { doc, p, w, at -> viewModel.saveAppointment(context, doc, p, w, at) },
                saveCheckIn = { f, s, n -> viewModel.saveCheckIn(context, f, s, n) },
                saveSymptom = { w, sev, st -> viewModel.saveSymptom(context, w, sev, st) },
                markMedicineTaken = { viewModel.markMedicineTaken(context, it) },
                setReminderDone = { id, done -> viewModel.setReminderDone(context, id, done) },
                saveEmergencyProfile = { viewModel.saveEmergencyProfile(context, it) },
                loadEmergencyProfile = { viewModel.loadEmergencyProfile(context) },
                saveMovement = { c, m -> viewModel.saveMovement(context, c, m) },
                saveContraction = { sec, gap -> viewModel.saveContraction(context, sec, gap) },
                loadMovements = { viewModel.loadMovements(context) },
                loadContractions = { viewModel.loadContractions(context) },
                sendReport = { k, m -> viewModel.sendReport(context, k, m) },
                setConsent = { f, g -> viewModel.setConsent(context, f, g) },
                setMemoryApproved = { id, a -> viewModel.setMemoryApproved(context, id, a) },
                forgetMemory = { viewModel.forgetMemory(context, it) },
                loadMemory = { viewModel.loadMemory(context) },
                loadConsent = { viewModel.loadConsent(context) },
                uploadDocument = { uri, kind -> viewModel.uploadDocument(context, uri, kind) },
                setVoice = { viewModel.setVoice(context, it) },
                loadPrefs = { viewModel.loadPrefs(context) },
                createPartnerInvite = { appts, rem, health ->
                    viewModel.createPartnerInvite(context, appts, rem, health)
                },
                clearPartnerInvite = viewModel::clearPartnerInvite,
                loadPartner = { viewModel.loadPartner(context) },
                revokePartnerInvite = { viewModel.revokePartnerInvite(context, it) },
                acceptPartnerInvite = { viewModel.acceptPartnerInvite(context, it) },
                // The user shares the code themselves — Aira never sends an
                // email or SMS, so no contact detail for a partner is collected.
                sharePartnerInvite = { text ->
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            },
                            "Share your Aira invite",
                        ),
                    )
                },
            )
        }

        state.activeJourneySection?.let { section ->
            JourneySectionSheet(
                section = section,
                stageLabel = state.journeyData?.weeks?.let { "Week $it" }
                    ?: journeyLabel(state.journeyData?.journey),
                onDismiss = viewModel::closeJourneySection,
            )
        }

        state.activeTool?.let { tool ->
            DynamicToolSheet(
                tool = tool,
                onDismiss = {
                    viewModel.clearPartnerInvite()   // the code is single-use
                    viewModel.closeReminderEditor()
                    viewModel.closeTool()
                },
                onNotify = viewModel::notify,
                onUrgentHelp = { haptics.weighty(); viewModel.openUrgentHelp(context) },
                actions = toolActions,
                care = state.careData,
                memory = state.memory,
                consent = state.consent,
                voicePrefs = state.voicePrefs,
                partnerInvite = state.partnerInvite,
                partnerInvites = state.partnerInvites,
                partnerShared = state.partnerShared,
                uploading = state.uploadingDocument,
                editingReminder = state.editingReminder,
                emergencyProfile = state.emergencyProfile,
                movements = state.movements,
                contractions = state.contractions,
            )
        }

        // Reading a document takes the whole screen, above the tabs. It is a
        // scan of somebody's own record; sharing the screen with a nav bar
        // invites a stray tap out of it.
        state.openDocument?.let { doc ->
            BackHandler(enabled = true) { viewModel.closeDocument() }
            DocumentViewer(
                file = java.io.File(doc.path),
                title = doc.title,
                contentType = doc.contentType,
                onClose = viewModel::closeDocument,
                onOpenExternally = { viewModel.openDocumentExternally(context) },
            )
        }

        if (state.urgentHelpOpen) {
            UrgentHelpDialog(
                onDismiss = viewModel::closeUrgentHelp,
                onOpenEmergencyProfile = {
                    viewModel.closeUrgentHelp()
                    viewModel.openTool(AiraTool.Emergency)
                },
                careTeamPhone = state.careTeamPhone,
                message = state.urgentMessage,
            )
        }
    }
}

@Composable
private fun AiraAppHeader(
    destination: MainDestination,
    weeks: Int?,
    journey: String?,
    onUrgentHelp: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Paper,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandOrb(compact = true)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aira",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                Text(
                    text = destinationSubtitle(destination, weeks, journey),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            // Nothing sits here now.
            //
            // A bell lived here first, opening a sheet built from updatesCount —
            // the same count, from the same care data, that Today already lists
            // under what needs attention. Two doors onto one thing, one of them
            // a glyph with a number on it. It became the way into You while You
            // was off the tab bar; You is a tab again, so keeping the avatar
            // would repeat the bell's mistake with a different glyph.
            //
            // The header is left with the one control that has to be reachable
            // from every screen and cannot wait for a tab press.

            // The urgent control carries its own name.
            //
            // It was a red circle with a shield glyph and no text — on the one
            // control in the app that someone might need while frightened, at
            // speed, having never pressed it before. An icon is a thing you
            // learn; a word is a thing you read.
            Surface(
                color = UrgentMist,
                contentColor = Urgent,
                shape = CircleShape,
                onClick = onUrgentHelp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HealthAndSafety,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Urgent",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/**
 * The app-bar subtitle. Today and Journey describe where the person actually is,
 * from the backend's `/v1/today`. Both were hardcoded to "Week 24", so a
 * trying-to-conceive or postpartum user was shown a pregnancy week that had
 * nothing to do with them — the same persona bug the backend exists to fix.
 */
private fun destinationSubtitle(
    destination: MainDestination,
    weeks: Int?,
    journey: String?,
): String =
    when (destination) {
        MainDestination.Today, MainDestination.Journey ->
            // journeyLabel(null) is "Exploring", so an unloaded header announced
            // a stage the user might not be in — the same fabrication the Today
            // fallbacks were removed for. Say nothing until something is known.
            when {
                weeks != null -> "Week $weeks"
                !journey.isNullOrBlank() -> journeyLabel(journey)
                else -> ""
            }
        // Three different descriptions of the same app used to sit here —
        // "Your care companion", "Private care hub", "Your care, your control" —
        // changing under the logo as you moved between tabs, which reads as
        // three products rather than one. The subtitle now says which screen
        // you are on, which is the only thing that actually differs.
        //
        // Each of these describes the screen without renaming it: the tab says
        // Care and the header says "Your care". You was the exception — the tab
        // said You and the header said "Settings", so one screen answered to two
        // names depending on where you looked, and neither was what the screen
        // called itself. It leads with the person's name and holds an Account
        // section, a "Privacy and settings" section and their data; settings are
        // a part of it rather than the whole.
        MainDestination.Aira -> "Chat"
        MainDestination.Learn -> "Short videos"
        MainDestination.Care -> "Your care"
        MainDestination.You -> "Profile and privacy"
    }


/**
 * Shown when a screen has nothing to show because the load failed.
 *
 * Deliberately not an error dialog: losing signal is ordinary, especially in a
 * hospital, and it is not the user's mistake. It names the likely cause,
 * confirms nothing was lost, and offers one button.
 */
/**
 * A slim line above cached content saying how old it is.
 *
 * Separate from [OfflineNotice], which takes the whole screen because there is
 * nothing to show. Here there IS something to show, and it is real — just not
 * necessarily current. The date and time are stated rather than "recently",
 * because the question this app gets opened for is often "have I taken it
 * today?", and only a timestamp lets someone answer that for themselves.
 */
@Composable
private fun CachedNotice(savedAt: Long?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val label = remember(savedAt) {
        savedAt?.let {
            val now = java.util.Calendar.getInstance()
            val then = java.util.Calendar.getInstance().apply { timeInMillis = it }
            val sameDay =
                now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
                    now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
            val pattern = if (sameDay) "HH:mm" else "d MMM, HH:mm"
            java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                .format(java.util.Date(it))
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        color = AmberMist,
        contentColor = Amber,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = if (label == null) {
                    "Offline — showing your last saved copy"
                } else {
                    "Offline — showing what Aira had at $label"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun OfflineNotice(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Surface(
            color = AmberMist,
            contentColor = Amber,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Aira can't reach your care data",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "You're probably offline. Nothing you've saved is lost — " +
                        "it's on the server waiting for you.",
                    style = MaterialTheme.typography.bodySmall,
                )
                // Debug builds only. "You're probably offline" is the right thing
                // to tell someone using Aira, but during development it is
                // frequently a lie: the commonest cause by far is an APK built
                // without -PairaApiBase, which points at the emulator's 10.0.2.2
                // and cannot resolve from a phone. That looked exactly like a
                // dead tunnel and cost an hour of debugging the wrong layer.
                //
                // Naming the host turns "probably offline" into a fact you can
                // check. Never shipped — a release user cannot act on a URL, and
                // it would only expose infrastructure.
                if (BuildConfig.DEBUG) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Debug build — tried ${BuildConfig.AIRA_API_BASE}. " +
                            "If that host isn't reachable from this device, rebuild " +
                            "with -PairaApiBase=… rather than looking for a network " +
                            "fault.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}
