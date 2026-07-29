package com.aira.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aira.companion.model.AiraUiState
import com.aira.companion.model.AuthMode
import com.aira.companion.ui.components.InfoBanner
import com.aira.companion.ui.components.PrimaryButton
import com.aira.companion.ui.components.SectionLabel
import com.aira.companion.ui.theme.Amber
import com.aira.companion.ui.theme.AmberMist
import com.aira.companion.ui.theme.Ink
import com.aira.companion.ui.theme.InkMuted
import com.aira.companion.ui.theme.Ivory
import com.aira.companion.ui.theme.Plum
import com.aira.companion.ui.theme.Urgent
import com.aira.companion.ui.theme.UrgentMist

/** Matches accounts.MIN_PASSWORD_LENGTH; the server rejects anything shorter. */
private const val MIN_PASSWORD = 12

/**
 * Create an account or sign in. Never a gate — Aira works anonymously, and this
 * screen can always be closed.
 *
 * An account exists for one reason, and the copy says only that: so care context
 * follows you to another device. No streaks, no "unlock features", nothing this
 * build doesn't do.
 */
@Composable
fun AuthScreen(
    state: AiraUiState,
    onModeChange: (AuthMode) -> Unit,
    onSubmit: (email: String, password: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val signUp = state.authMode == AuthMode.SignUp
    val passwordTooShort = signUp && password.isNotEmpty() && password.length < MIN_PASSWORD
    val canSubmit = email.isNotBlank() && password.isNotEmpty() &&
        !state.authBusy && !passwordTooShort

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ivory)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = InkMuted)
            }
        }

        Spacer(Modifier.height(6.dp))
        SectionLabel(if (signUp) "CREATE AN ACCOUNT" else "WELCOME BACK")
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (signUp) "Keep your care with you." else "Sign in to Aira.",
            style = MaterialTheme.typography.headlineMedium,
            color = Ink,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (signUp) {
                "An account means your care context follows you if you change " +
                    "phone. Everything you've already added comes with you."
            } else {
                "Enter the email and password you signed up with."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )

        // Signing IN abandons what's on this device. Say so BEFORE it happens —
        // discovering it afterwards is not recoverable.
        if (!signUp && state.localCareItems > 0) {
            Spacer(Modifier.height(16.dp))
            InfoBanner(
                icon = Icons.Outlined.Lock,
                text = "This device has ${state.localCareItems} care " +
                    (if (state.localCareItems == 1) "item" else "items") +
                    " that aren't part of an account. Signing in switches to your " +
                    "account's data and leaves these behind. To keep them instead, " +
                    "go back and create an account.",
                color = AmberMist,
                contentColor = Amber,
            )
        }

        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(17.dp),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = passwordTooShort,
            shape = RoundedCornerShape(17.dp),
        )
        if (signUp) {
            Spacer(Modifier.height(6.dp))
            Text(
                // Stated up front rather than as an error after submitting.
                text = "At least $MIN_PASSWORD characters. Length matters more than " +
                    "symbols — a short phrase you'll remember beats P@ssw0rd.",
                style = MaterialTheme.typography.bodySmall,
                color = if (passwordTooShort) Urgent else InkMuted,
            )
        }

        state.authError?.let { message ->
            Spacer(Modifier.height(14.dp))
            InfoBanner(
                icon = Icons.Outlined.Lock,
                text = message,
                color = UrgentMist,
                contentColor = Urgent,
            )
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            label = when {
                state.authBusy && signUp -> "Creating your account…"
                state.authBusy -> "Signing in…"
                signUp -> "Create account"
                else -> "Sign in"
            },
            onClick = { onSubmit(email, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSubmit,
        )

        Spacer(Modifier.height(10.dp))
        TextButton(
            onClick = { onModeChange(if (signUp) AuthMode.SignIn else AuthMode.SignUp) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (signUp) {
                    "Already have an account? Sign in"
                } else {
                    "New to Aira? Create an account"
                },
                color = Plum,
            )
        }

        if (!signUp) {
            Spacer(Modifier.height(4.dp))
            Text(
                // Honest about a gap rather than a link that goes nowhere:
                // resetting a password needs to send email, and this build has
                // no provider configured.
                text = "Password resets aren't available in this build yet. If you " +
                    "can't sign in, you can keep using Aira without an account.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}
