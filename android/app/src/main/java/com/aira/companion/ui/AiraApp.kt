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
import com.aira.companion.model.MainDestination
import com.aira.companion.ui.components.AiraBottomNavigation
import com.aira.companion.ui.components.BrandOrb
import com.aira.companion.ui.screens.AiraChatScreen
import com.aira.companion.ui.screens.CareScreen
import com.aira.companion.ui.screens.DynamicToolSheet
import com.aira.companion.ui.screens.JourneyScreen
import com.aira.companion.ui.screens.OnboardingChatScreen
import com.aira.companion.ui.screens.TodayScreen
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
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Ivory,
            topBar = {
                AiraAppHeader(
                    destination = state.destination,
                    notificationCount = state.notificationCount,
                    onNotifications = { viewModel.openTool(AiraTool.Notifications) },
                    onUrgentHelp = viewModel::openUrgentHelp,
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
                    )
                MainDestination.Aira ->
                    AiraChatScreen(
                        state = state,
                        onDraftChange = viewModel::updateDraft,
                        onSend = viewModel::sendMessage,
                        onQuickMessage = viewModel::quickMessage,
                        onOpenTools = viewModel::openTools,
                        onOpenTool = viewModel::openTool,
                        modifier = Modifier.padding(padding),
                    )
                MainDestination.Journey ->
                    JourneyScreen(
                        onOpenTool = viewModel::openTool,
                        modifier = Modifier.padding(padding),
                    )
                MainDestination.Care ->
                    CareScreen(
                        onOpenTool = viewModel::openTool,
                        onUrgentHelp = viewModel::openUrgentHelp,
                        modifier = Modifier.padding(padding),
                    )
                MainDestination.You ->
                    YouScreen(
                        onOpenTool = viewModel::openTool,
                        modifier = Modifier.padding(padding),
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
                onDismiss = viewModel::closeTool,
                onNotify = viewModel::notify,
                onUrgentHelp = viewModel::openUrgentHelp,
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
    notificationCount: Int,
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
                    text = destinationSubtitle(destination),
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

private fun destinationSubtitle(destination: MainDestination): String =
    when (destination) {
        MainDestination.Today -> "Tuesday · Week 24"
        MainDestination.Aira -> "Your care companion"
        MainDestination.Journey -> "Week 24"
        MainDestination.Care -> "Private care hub"
        MainDestination.You -> "Your care, your control"
    }
