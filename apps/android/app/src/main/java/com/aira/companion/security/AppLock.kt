package com.aira.companion.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * A lock in front of the app, for a phone that is not always only yours.
 *
 * This app holds a symptom log, a pregnancy week, scans, and a conversation
 * about all of it. Phones in this context get handed to a partner to show a
 * photo, left on a kitchen table, shared in a family. Nothing here was behind
 * anything: opening the app showed everything.
 *
 * The system dialog does the authenticating, so no fingerprint or face data
 * ever reaches this process — Aira asks the OS a yes/no question and is told
 * only the answer.
 *
 * ── Device credential is included on purpose ──
 *
 * BIOMETRIC_WEAK **or** DEVICE_CREDENTIAL, so a phone with no enrolled
 * fingerprint still gets a PIN prompt. Biometric-only would quietly exclude
 * anyone whose fingerprint is not reliably read — which after pregnancy and
 * with a newborn is common enough to matter — and would leave the setting
 * available but useless on their device.
 */
object AppLock {

    /** What this device can actually ask for, if anything. */
    fun availability(ctx: Context): Availability {
        val allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return when (BiometricManager.from(ctx).canAuthenticate(allowed)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NOTHING_ENROLLED
            else -> Availability.UNSUPPORTED
        }
    }

    enum class Availability {
        READY,

        /** The hardware is there but the phone has no screen lock set. The
         *  setting must say so rather than offering a switch that cannot work —
         *  the same rule this project applies to the Google button. */
        NOTHING_ENROLLED,

        UNSUPPORTED,
    }

    /**
     * Ask the OS to confirm it is them.
     *
     * [onFailure] is not [onSuccess]'s opposite: a failed or cancelled prompt
     * leaves the app locked and lets them try again. There is deliberately no
     * "skip" — a lock with a way past it is decoration.
     */
    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: () -> Unit = {},
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    onFailure()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Aira")
                // Says what is behind the lock, so the prompt is not a bare
                // demand for a fingerprint with no stated reason.
                .setSubtitle("Your care notes and conversation are private")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
                .build(),
        )
    }
}
