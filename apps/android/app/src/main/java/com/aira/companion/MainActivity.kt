package com.aira.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aira.companion.model.AppStage
import com.aira.companion.ui.AiraApp
import com.aira.companion.ui.AiraViewModel
import com.aira.companion.ui.theme.AiraTheme

class MainActivity : ComponentActivity() {
    // Owned by the activity rather than created inside AiraApp, so the splash can
    // read the stage before any Compose content exists. AiraApp already accepts it
    // as a parameter, so nothing downstream changes.
    private val viewModel: AiraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate, or the system installs the splash for the
        // next launch instead of this one.
        val splash = installSplashScreen()

        // Hold the splash for exactly as long as the session takes to resolve —
        // restoreSession deciding between Tutorial, Welcome and Today. That is real
        // work, so the splash covers a genuine wait. Deliberately not a timer: a
        // fixed delay would make every launch slower purely to look considered.
        splash.setKeepOnScreenCondition {
            viewModel.uiState.value.stage == AppStage.Starting
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiraTheme {
                AiraApp(viewModel)
            }
        }
    }
}
