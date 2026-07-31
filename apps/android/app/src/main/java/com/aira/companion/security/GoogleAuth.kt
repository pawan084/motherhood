package com.aira.companion.security

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.aira.companion.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Obtains a Google ID token through the system Credential Manager.
 *
 * The app never handles a Google password. The account chooser is drawn by the
 * OS, and what comes back is a short-lived token signed by Google, which is
 * forwarded to the backend and verified there (`accounts._verify_google_id_token`).
 * Nothing here decides who anyone is; it only carries an assertion.
 *
 * ## Why this can be absent
 *
 * Google Sign-In needs an OAuth **web/server** client id, which is deployment
 * configuration this repository cannot contain. When [isConfigured] is false the
 * button is not rendered at all — a visible Google button that can only ever
 * return `invalid audience` is the "control that claims something the system
 * never did" pattern this codebase keeps removing. Email sign-in is unaffected.
 */
object GoogleAuth {

    /** The OAuth web client id, or blank when this build has none. */
    private val clientId: String get() = BuildConfig.AIRA_GOOGLE_CLIENT_ID

    /** Whether to offer Google at all. See the class note — false hides it. */
    val isConfigured: Boolean get() = clientId.isNotBlank()

    /**
     * Ask the OS for a Google ID token.
     *
     * Returns null when the person dismissed the chooser. That is a decision,
     * not a failure, and it must not raise an error banner at them — telling
     * someone "something went wrong" because they changed their mind is the
     * app misreporting its own state.
     *
     * @throws IllegalStateException if called in a build with no client id.
     * @throws GoogleAuthException for anything genuinely wrong, with a message
     *         written for the person reading it rather than for a log.
     */
    suspend fun idToken(context: Context): String? {
        check(isConfigured) { "Google sign-in is not configured in this build" }
        // Credential Manager draws a system dialog, so it needs an Activity, not
        // the application context. Caught here because the failure otherwise
        // arrives as a generic GetCredentialException and gets reported to the
        // person as "try again" — advice that could never work.
        check(context is android.app.Activity) {
            "Google sign-in needs an Activity context, got ${context.javaClass.simpleName}"
        }

        // setFilterByAuthorizedAccounts(false) so someone who has never used
        // Aira before still sees their accounts. Filtering to already-authorized
        // ones shows an empty chooser on first run, which reads as broken.
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .build()

        val response = try {
            CredentialManager.create(context).getCredential(
                context = context,
                request = GetCredentialRequest.Builder().addCredentialOption(option).build(),
            )
        } catch (_: GetCredentialCancellationException) {
            return null
        } catch (_: NoCredentialException) {
            throw GoogleAuthException(
                "No Google account is available on this device. You can add one " +
                    "in Settings, or create an Aira account with your email."
            )
        } catch (e: GetCredentialException) {
            throw GoogleAuthException(
                "Google sign-in couldn't complete. You can try again, or use " +
                    "your email instead."
            )
        }

        val credential = response.credential
        // Credential Manager is deliberately open-ended about what it returns —
        // a passkey or a saved password can arrive through the same call. Only a
        // Google ID token is usable here, so anything else is refused rather
        // than cast and allowed to fail somewhere less obvious.
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw GoogleAuthException(
                "That wasn't a Google account Aira can use. Try again, or sign " +
                    "in with your email."
            )
        }

        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}

/** A Google sign-in failure whose message is already fit to show someone. */
class GoogleAuthException(message: String) : Exception(message)
