package com.aira.companion

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.aira.companion.data.AppPrefs
import com.aira.companion.model.AppStage
import com.aira.companion.security.AppLock
import com.aira.companion.ui.AiraApp
import com.aira.companion.ui.AiraViewModel
import com.aira.companion.ui.screens.LockedScreen
import com.aira.companion.ui.theme.AiraTheme

/**
 * FragmentActivity rather than ComponentActivity: BiometricPrompt hosts itself
 * in a fragment. Compose is unaffected — setContent works exactly the same.
 */
class MainActivity : FragmentActivity() {
    // Owned by the activity rather than created inside AiraApp, so the splash can
    // read the stage before any Compose content exists. AiraApp already accepts it
    // as a parameter, so nothing downstream changes.
    private val viewModel: AiraViewModel by viewModels()

    /** False until the OS has confirmed it is them, when the lock is on. */
    private var unlocked by mutableStateOf(true)

    /**
     * Re-lock when the app leaves the foreground.
     *
     * Locking only at cold start would protect almost nothing: Android keeps
     * this process alive for a long time, so a phone handed over five minutes
     * after you last looked would simply resume into your symptom log. The
     * moment that matters is the app going to the background — which is exactly
     * when a phone changes hands.
     *
     * Deliberately no grace period. A timeout is a nicer experience and a weaker
     * lock, and for what is behind this one that is the wrong trade.
     */
    override fun onStop() {
        super.onStop()
        if (AppPrefs.appLockEnabled(this)) unlocked = false
    }

    override fun onStart() {
        super.onStart()
        applySecureFlag()
        if (AppPrefs.appLockEnabled(this) && !unlocked) askToUnlock()
    }

    /**
     * With the lock on, keep the app out of screenshots and the recents preview.
     *
     * Tied to the lock rather than always on, because blocking screenshots has a
     * real cost — people photograph a question to show a midwife, or a symptom
     * list to send to a partner — and that is theirs to decide. Turning the lock
     * on is a clear statement about who else touches this phone, so it is the
     * right switch to hang this on. Without it the recents carousel keeps a live
     * thumbnail of whatever was on screen, which defeats the lock entirely.
     */
    private fun applySecureFlag() {
        if (AppPrefs.appLockEnabled(this)) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun askToUnlock() {
        AppLock.prompt(this, onSuccess = { unlocked = true })
    }

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
        unlocked = !AppPrefs.appLockEnabled(this)
        enableEdgeToEdge()
        setContent {
            AiraTheme {
                // Locked means the app is not composed at all, rather than
                // composed and covered. A blur or an overlay still has the real
                // content underneath, and one mis-drawn frame shows it.
                if (unlocked) {
                    AiraApp(viewModel)
                } else {
                    LockedScreen(onUnlock = { askToUnlock() })
                }
            }
        }
    }
}
