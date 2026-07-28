package com.aira.companion.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.aira.companion.model.MainDestination
import com.aira.companion.model.journeyLabel
import com.aira.companion.ui.components.AiraBottomNavigation
import com.aira.companion.ui.components.BrandOrb
import com.aira.companion.ui.screens.AiraChatScreen
import com.aira.companion.ui.screens.CareScreen
import com.aira.companion.ui.screens.DynamicToolSheet
import com.aira.companion.ui.screens.JourneyScreen
import com.aira.companion.ui.screens.OnboardingChatScreen
import com.aira.companion.ui.screens.TodayScreen
import com.aira.companion.ui.screens.ToolActions
import com.aira.companion.ui.screens.ToolTraySheet
import com.aira.companion.ui.screens.UrgentHelpDialog
import com.aira.companion.ui.screens.WelcomeScreen
import com.aira.companion.ui.screens.YouScreen
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
        AppStage.Starting -> StartingScreen()
        AppStage.Welcome -> WelcomeScreen(onStart = viewModel::startOnboarding)
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

/** Shown for the moment it takes to resolve the cached session. */
@Composable
private fun StartingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ivory),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Plum, strokeWidth = 2.dp)
    }
}

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
            MainDestination.Journey -> viewModel.loadJourney(context)
            MainDestination.Care -> viewModel.loadCare(context)
            MainDestination.You -> viewModel.loadConsent(context)
            else -> {}
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
                    notificationCount = state.notificationCount,
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
            when (state.destination) {
                MainDestination.Today ->
                    TodayScreen(
                        onDestination = viewModel::selectDestination,
                        onOpenTool = viewModel::openTool,
                        modifier = Modifier.padding(padding),
                        today = state.todayData,
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
                    )
                MainDestination.Care ->
                    CareScreen(
                        onOpenTool = viewModel::openTool,
                        onUrgentHelp = { viewModel.openUrgentHelp(context) },
                        modifier = Modifier.padding(padding),
                        care = state.careData,
                        loading = state.careLoading,
                        onMarkTaken = { viewModel.markMedicineTaken(context, it) },
                    )
                MainDestination.You ->
                    YouScreen(
                        onOpenTool = viewModel::openTool,
                        modifier = Modifier.padding(padding),
                        name = state.todayData?.name.orEmpty(),
                        weeks = state.todayData?.weeks,
                        journey = state.todayData?.journey,
                        language = state.language,
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
                saveMedicine = { n, d, t -> viewModel.saveMedicine(context, n, d, t) },
                saveAppointment = { doc, p, w -> viewModel.saveAppointment(context, doc, p, w) },
                saveCheckIn = { f, s, n -> viewModel.saveCheckIn(context, f, s, n) },
                saveSymptom = { w, sev, st -> viewModel.saveSymptom(context, w, sev, st) },
                markMedicineTaken = { viewModel.markMedicineTaken(context, it) },
                saveEmergencyProfile = { viewModel.saveEmergencyProfile(context, it) },
                sendReport = { k, m -> viewModel.sendReport(context, k, m) },
                setConsent = { f, g -> viewModel.setConsent(context, f, g) },
                setMemoryApproved = { id, a -> viewModel.setMemoryApproved(context, id, a) },
                forgetMemory = { viewModel.forgetMemory(context, it) },
                loadMemory = { viewModel.loadMemory(context) },
                loadConsent = { viewModel.loadConsent(context) },
            )
        }

        state.activeTool?.let { tool ->
            DynamicToolSheet(
                tool = tool,
                onDismiss = viewModel::closeTool,
                onNotify = viewModel::notify,
                onUrgentHelp = { viewModel.openUrgentHelp(context) },
                actions = toolActions,
                care = state.careData,
                memory = state.memory,
                consent = state.consent,
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
            Surface(
                modifier = Modifier.size(42.dp),
                color = UrgentMist,
                contentColor = Urgent,
                shape = CircleShape,
                onClick = onUrgentHelp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.HealthAndSafety,
                        contentDescription = "Open urgent help",
                        modifier = Modifier.size(21.dp),
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
            if (weeks != null) "Week $weeks" else journeyLabel(journey)
        MainDestination.Aira -> "Your care companion"
        MainDestination.Care -> "Private care hub"
        MainDestination.You -> "Your care, your control"
    }
