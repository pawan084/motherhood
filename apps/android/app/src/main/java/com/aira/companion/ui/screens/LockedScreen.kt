package com.aira.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aira.companion.ui.components.BrandOrb
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory

/**
 * What is on screen while the app is locked.
 *
 * Says nothing about the person using it. No name, no week, no "welcome back,
 * Priya" — the whole point is that whoever is holding the phone has not been
 * confirmed yet, and a greeting would leak the one thing the lock is for.
 *
 * There is a button rather than only the system dialog because the prompt can
 * be dismissed, and a dismissed prompt with no way back would strand someone in
 * an app they cannot open. There is no "skip": a lock you can walk past is
 * decoration.
 */
@Composable
fun LockedScreen(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory)
            .systemBarsPadding()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandOrb()
        Text(
            text = "Aira is locked",
            style = MaterialTheme.typography.headlineSmall,
            color = Ink,
            modifier = Modifier.padding(top = 22.dp),
        )
        Text(
            text = "Unlock with your fingerprint, face or screen lock to open your care.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        PrimaryButton(
            label = "Unlock",
            onClick = onUnlock,
            modifier = Modifier.padding(top = 28.dp),
        )
    }
}
