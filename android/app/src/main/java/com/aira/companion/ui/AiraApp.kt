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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aira.companion.model.AiraTool
import com.aira.companion.model.AppStage
import com.aira.companion.model.CareSummary
import com.aira.companion.model.DetailPage
import com.aira.companion.model.MainDestination
import com.aira.companion.ui.components.AiraBottomNavigation
import com.aira.companion.ui.components.BrandOrb
import com.aira.companion.ui.screens.AiraChatScreen
import com.aira.companion.ui.screens.CareDetailScreen
import com.aira.companion.ui.screens.JourneyDetailScreen
import com.aira.companion.ui.screens.MoodDetailScreen
import com.aira.companion.ui.screens.DynamicToolSheet
import com.aira.companion.ui.screens.MeScreen
import com.aira.companion.ui.screens.OnboardingChatScreen
import com.aira.companion.ui.screens.SettingsScreen
import com.aira.companion.ui.screens.ToolTraySheet
import com.aira.companion.ui.screens.UrgentHelpDialog
import com.aira.companion.ui.screens.VideoPlayerScreen
import com.aira.companion.ui.screens.VideosScreen
import com.aira.companion.ui.screens.WelcomeScreen
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Paper
import com.aira.companion.ui.theme.Urgent
import com.aira.companion.ui.theme.UrgentMist

@Composable
fun AiraApp(viewModel: AiraViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    when (state.stage) {
        AppStage.Welcome -> WelcomeScreen(onStart = viewModel::startOnboarding)
        AppStage.Onboarding ->
            OnboardingChatScreen(
                state = state,
                onAnswer = viewModel::answerOnboarding,
                onFinish = viewModel::finishOnboarding,
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

@Composable
private fun MainExperience(
    state: com.aira.companion.model.AiraUiState,
    viewModel: AiraViewModel,
    snackbarHostState: SnackbarHostState,
) {
    // Data loads fire here, not in selectDestination — ViewModel transitions
    // stay pure/synchronous for the JVM unit tests.
    LaunchedEffect(state.destination) {
        viewModel.trackOpen(state.destination)
        when (state.destination) {
            MainDestination.Me -> viewModel.refreshMe()
            MainDestination.Videos -> viewModel.ensureVideos()
            else -> Unit
        }
    }
    // Coming back from the background must refetch Me: the /today slot,
    // focus cards, and day counters go stale while the app sleeps — a
    // proactive home is only proactive if resume means "now", not "then".
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME &&
                viewModel.uiState.value.destination == MainDestination.Me
            ) {
                viewModel.refreshMe()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.detail) {
        state.detail?.let(viewModel::loadDetailData)
    }
    // Tool sheets render server truth — fetch when one opens.
    LaunchedEffect(state.activeTool) {
        state.activeTool?.let(viewModel::loadToolData)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Ivory,
            topBar = {
                AiraAppHeader(
                    destination = state.destination,
                    careSummary = state.careSummary,
                    onSettings = viewModel::openSettings,
                    onUrgentHelp = viewModel::openUrgentHelp,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            when (state.destination) {
                MainDestination.Me ->
                    MeScreen(
                        state = state,
                        onNudgeTapped = viewModel::nudgeActedOn,
                        onOpenPlayer = viewModel::openPlayer,
                        onSetMood = viewModel::setMood,
                        onSaveMoodNote = { mood, note -> viewModel.setMood(mood, note) },
                        onTick = viewModel::tickReminder,
                        onUntick = viewModel::untickReminder,
                        onOpenDetail = viewModel::openDetail,
                        onOpenTool = viewModel::openTool,
                        onTalkToAira = {
                            viewModel.selectDestination(MainDestination.Chat)
                        },
                        onAckWeekFlip = viewModel::acknowledgeWeekFlip,
                        onTipFeedback = viewModel::sendTipFeedback,
                        onSaveName = viewModel::updateDisplayName,
                        onDismissName = viewModel::dismissNamePrompt,
                        onRefresh = viewModel::refreshMe,
                        onSetStage = viewModel::setStage,
                        modifier = Modifier.padding(padding),
                    )
                MainDestination.Chat ->
                    AiraChatScreen(
                        state = state,
                        onDraftChange = viewModel::updateDraft,
                        onSend = viewModel::sendMessage,
                        onQuickMessage = viewModel::quickMessage,
                        onOpenTools = viewModel::openTools,
                        onOpenTool = viewModel::openTool,
                        onOpenDetail = viewModel::openDetail,
                        modifier = Modifier.padding(padding),
                    )
                MainDestination.Videos ->
                    VideosScreen(
                        videos = state.videos,
                        loading = state.videosLoading,
                        onOpenPlayer = viewModel::openPlayer,
                        onLike = viewModel::toggleVideoLike,
                        modifier = Modifier.padding(padding),
                    )
            }
        }

        // Floating nav overlays the content (transparent around it, so the page
        // shows through behind the pill) — only on the main destinations;
        // detail and player screens draw over it.
        if (state.detail == null && state.playerVideo == null) {
            AiraBottomNavigation(
                selected = state.destination,
                onSelect = viewModel::selectDestination,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        state.detail?.let { page ->
            BackHandler { viewModel.closeDetail() }
            Surface(modifier = Modifier.fillMaxSize(), color = Ivory) {
                when (page) {
                    DetailPage.Journey ->
                        JourneyDetailScreen(
                            content = state.journeyContent,
                            onBrowseWeek = viewModel::browseJourneyWeek,
                            onClose = viewModel::closeDetail,
                            modifier = Modifier.statusBarsPadding(),
                        )
                    DetailPage.Moods ->
                        MoodDetailScreen(
                            history = state.moodHistory,
                            onClose = viewModel::closeDetail,
                            modifier = Modifier.statusBarsPadding(),
                        )
                    DetailPage.Care ->
                        CareDetailScreen(
                            reminders = state.reminders,
                            report = state.report,
                            onTick = viewModel::tickReminder,
                            onUntick = viewModel::untickReminder,
                            onSetTarget = viewModel::setReminderTarget,
                            onAdd = viewModel::addReminder,
                            onDelete = viewModel::deleteReminder,
                            onClose = viewModel::closeDetail,
                            modifier = Modifier.statusBarsPadding(),
                        )
                }
            }
        }

        state.playerVideo?.let { playing ->
            BackHandler { viewModel.closePlayer() }
            VideoPlayerScreen(
                video = playing,
                onClose = viewModel::closePlayer,
                onCompleted = viewModel::playerCompleted,
                onLike = viewModel::toggleVideoLike,
                onRate = viewModel::rateVideo,
            )
        }

        if (state.settingsOpen) {
            BackHandler { viewModel.closeSettings() }
            Surface(modifier = Modifier.fillMaxSize(), color = Ivory) {
                SettingsScreen(
                    careSummary = state.careSummary,
                    onOpenTool = viewModel::openTool,
                    onClose = viewModel::closeSettings,
                    modifier = Modifier.statusBarsPadding(),
                )
            }
        }

        if (state.toolsOpen) {
            ToolTraySheet(
                onDismiss = viewModel::closeTools,
                onOpenTool = viewModel::openTool,
            )
        }

        state.activeTool?.let { tool ->
            DynamicToolSheet(
                tool = tool,
                state = state,
                onDismiss = viewModel::closeTool,
                onNotify = viewModel::notify,
                onUrgentHelp = viewModel::openUrgentHelp,
                onSaveMood = viewModel::setMood,
                onCreateReminder = viewModel::createReminder,
                onAddMedicine = viewModel::addMedicine,
                onMedicineTaken = viewModel::markMedicineTakenFromSheet,
                onAddAppointment = viewModel::addAppointment,
                onAddPlanItem = viewModel::addPlanItem,
                onTogglePlanItem = viewModel::togglePlanItem,
                onForgetMemory = viewModel::forgetMemory,
                onAddSymptom = viewModel::addSymptom,
                onUploadDocument = viewModel::uploadDocument,
                onShareExport = viewModel::shareExport,
                onEraseEverything = viewModel::eraseEverything,
                onCalmDone = viewModel::logCalmDone,
            )
        }

        if (state.urgentHelpOpen) {
            UrgentHelpDialog(
                onDismiss = viewModel::closeUrgentHelp,
                onOpenEmergencyProfile = {
                    viewModel.closeUrgentHelp()
                    viewModel.openTool(AiraTool.Emergency)
                },
            )
        }
    }
}

@Composable
private fun AiraAppHeader(
    destination: MainDestination,
    careSummary: CareSummary?,
    onSettings: () -> Unit,
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
                    text = destinationSubtitle(destination, careSummary),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Open settings",
                    tint = Ink,
                )
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

private fun destinationSubtitle(
    destination: MainDestination,
    careSummary: CareSummary?,
): String =
    when (destination) {
        MainDestination.Me -> careSummary?.week?.let { "Week $it" } ?: "Your day"
        MainDestination.Chat -> "Your care companion"
        MainDestination.Videos -> "Short, stage-aware guides"
    }
