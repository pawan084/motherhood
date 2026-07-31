package com.aira.companion

import com.aira.companion.data.AiraApiException
import com.aira.companion.security.GoogleAuth
import com.aira.companion.security.GoogleAuthException
import com.aira.companion.ui.AiraViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Google sign-in, and specifically the parts that decide what someone is told.
 *
 * The chooser itself is the OS's, and the token exchange is covered on the
 * backend by test_google_signin.py. What is worth pinning here is the rule that
 * an unconfigured build offers nothing, and that a failure is reported as the
 * thing that actually went wrong.
 */
class GoogleSignInTest {

    @Test
    fun anUnconfiguredBuildDoesNotOfferGoogle() {
        // This repository holds no OAuth client id, so a plain build must be
        // unconfigured and AuthScreen must therefore draw no Google button. If
        // a client id is ever committed, this fails — which is the point twice
        // over: secrets don't belong here, and a button appears that most
        // builds cannot honour.
        assertFalse(
            "AIRA_GOOGLE_CLIENT_ID is baked into the default build",
            GoogleAuth.isConfigured,
        )
    }

    @Test
    fun anUnconfiguredBuildRefusesToStartTheFlow() {
        // Belt and braces for the button being hidden: if some other path ever
        // reaches this, it fails immediately rather than opening a chooser whose
        // token the backend can only reject on audience.
        val e = runCatching { kotlinx.coroutines.runBlocking { GoogleAuth.idToken(FakeContext()) } }
        assertTrue(e.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun aGoogleFailureKeepsItsOwnExplanation() {
        // GoogleAuth knows things the generic handler cannot — no Google account
        // on the device, a credential of the wrong kind. Flattening those into
        // "check your connection" sends someone to fix something that isn't
        // broken.
        val message = "No Google account is available on this device."

        val shown = AiraViewModel().readableAuthError(GoogleAuthException(message))

        assertEquals(message, shown)
    }

    @Test
    fun anOrdinaryApiFailureStillGetsTheOrdinaryWording() {
        // The passthrough above must not have swallowed the existing mapping.
        val shown = AiraViewModel().readableAuthError(AiraApiException(409, "email exists"))

        assertTrue(shown.contains("already exists"))
    }

    /** Not an Activity, which is the only thing the assertion under test cares
     *  about — the configured check fires first regardless. */
    private class FakeContext : android.content.ContextWrapper(null)
}
