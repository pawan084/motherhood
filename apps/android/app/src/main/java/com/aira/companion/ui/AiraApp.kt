package com.aira.companion.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aira.companion.model.AiraTool
import com.aira.companion.model.AppStage
import com.aira.companion.model.AuthMode
import com.aira.companion.model.MainDestination
import com.aira.companion.model.journeyLabel
import com.aira.companion.model.updatesCount
import com.aira.companion.reminders.ReminderScheduler
import com.aira.companion.ui.components.AiraBottomNavigation
import com.aira.companion.ui.components.BrandOrb
import com.aira.companion.ui.screens.AiraChatScreen
import com.aira.companion.ui.screens.AuthScreen
import com.aira.companion.ui.screens.CareScreen
import com.aira.companion.ui.screens.DynamicToolSheet
import com.aira.companion.ui.screens.JourneyScreen
import com.aira.companion.ui.screens.JourneySectionSheet
import com.aira.companion.ui.screens.OnboardingChatScreen
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
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
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
                onClose = viewModel::closeAuth,
            )
        AppStage.Onboarding ->
            OnboardingChatScreen(
                state = state,
                onAnswer = viewModel::answerOnboarding,
                onFinish = { viewModel.finishOnboarding(context) },
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
    // Refresh the journey-aware content whenever the user lands on Today/Journey.
    LaunchedEffect(state.destination) {
        when (state.destination) {
            // Today shows medicines/appointments too, so it needs Care as well.
            MainDestination.Today -> { viewModel.loadToday(context); viewModel.loadCare(context) }
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
        contract = ActivityResultContracts.CreateDocument("application/json"),
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
                    notificationCount = updatesCount(state.careData),
                    weeks = state.todayData?.weeks,
                    journey = state.todayData?.journey,
                    onNotifications = { viewModel.openTool(AiraTool.Notifications) },
                    onUrgentHelp = { viewModel.openUrgentHelp(context) },
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
                    modifier = Modifier.padding(padding),
                    onRetry = { viewModel.retryLoad(context) },
                )
                return@Scaffold
            }
            when (state.destination) {
                MainDestination.Today ->
                    TodayScreen(
                        onDestination = viewModel::selectDestination,
                        onOpenTool = viewModel::openTool,
                        modifier = Modifier.padding(padding),
                        today = state.todayData,
                        waiting = updatesCount(state.careData),
                    )
                MainDestination.Aira ->
                    AiraChatScreen(
                        state = state,
                        onDraftChange = viewModel::updateDraft,
                        onSend = { viewModel.sendMessage(context) },
                        onQuickMessage = { viewModel.quickMessage(it, context) },
                        onOpenTools = viewModel::openTools,
                        onOpenTool = viewModel::openTool,
                        modifier = Modifier.padding(padding),
                    )
                MainDestination.Journey ->
                    JourneyScreen(
                        onOpenTool = viewModel::openTool,
                        modifier = Modifier.padding(padding),
                        journey = state.journeyData,
                        onOpenSection = viewModel::openJourneySection,
                    )
                MainDestination.Care ->
                    CareScreen(
                        onOpenTool = viewModel::openTool,
                        onUrgentHelp = { viewModel.openUrgentHelp(context) },
                        modifier = Modifier.padding(padding),
                        care = state.careData,
                        loading = state.careLoading,
                        onMarkTaken = { viewModel.markMedicineTaken(context, it) },
                        timeline = state.timeline,
                        documents = state.documents,
                        onReminderDone = { id, done -> viewModel.setReminderDone(context, id, done) },
                        onRename = { id, field, value ->
                            viewModel.renameCareItem(context, id, field, value)
                        },
                        onDelete = { viewModel.deleteCareItem(context, it) },
                        onEditReminder = viewModel::editReminder,
                        onOpenDocument = { viewModel.openDocument(context, it) },
                    )
                MainDestination.You ->
                    YouScreen(
                        onOpenTool = viewModel::openTool,
                        modifier = Modifier.padding(padding),
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
                        onExport = { exportLauncher.launch("aira-data-export.json") },
                        onDelete = { viewModel.deleteAccount(context) },
                        signedIn = state.signedIn,
                        onSignOut = { viewModel.signOut(context) },
                        onCreateAccount = { viewModel.openAuth(context, AuthMode.SignUp) },
                        onSignIn = { viewModel.openAuth(context, AuthMode.SignIn) },
                        journeyType = state.journey,
                        onSaveProfile = { n, j, l -> viewModel.saveProfile(context, n, j, l) },
                        onReplayTutorial = viewModel::replayTutorial,
                    )
            }
        }

        if (state.toolsOpen) {
            ToolTraySheet(
                onDismiss = viewModel::closeTools,
                onOpenTool = viewModel::openTool,
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
                onUrgentHelp = { viewModel.openUrgentHelp(context) },
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
    notificationCount: Int,
    weeks: Int?,
    journey: String?,
    onNotifications: () -> Unit,
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
            IconButton(onClick = onNotifications) {
                BadgedBox(
                    badge = {
                        if (notificationCount > 0) {
                            Badge(containerColor = Plum) {
                                Text(notificationCount.toString())
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Open notifications",
                        tint = Ink,
                    )
                }
            }
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
        MainDestination.Aira -> "Chat"
        MainDestination.Care -> "Your care"
        MainDestination.You -> "Settings"
    }


/**
 * Shown when a screen has nothing to show because the load failed.
 *
 * Deliberately not an error dialog: losing signal is ordinary, especially in a
 * hospital, and it is not the user's mistake. It names the likely cause,
 * confirms nothing was lost, and offers one button.
 */
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
                Spacer(modifier = Modifier.size(12.dp))
                TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}
