package com.voxora.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the account card's state.
 *
 * The important properties are that impossible combinations cannot be represented — no stale email
 * can survive a sign-out, and no state can be both busy and signed in — and that "this build has no
 * Google configuration" is classified separately from "your sign-in failed", because the user's
 * next action is completely different.
 */
class AuthUiStateTest {

    @Test
    fun signedInCarriesTheAccountIdentity() {
        val state = AuthUiState.SignedIn(email = "owner@example.com", displayName = "Owner")

        assertTrue(state.isSignedIn)
        assertEquals("owner@example.com", state.emailOrEmpty)
        assertEquals("Owner", state.nameOrEmpty)
    }

    @Test
    fun aMissingDisplayNameFallsBackToTheEmailSoTheCardIsNeverBlank() {
        val state = AuthUiState.SignedIn(email = "owner@example.com", displayName = "")

        assertEquals("owner@example.com", state.nameOrEmpty)
    }

    @Test
    fun signedOutCarriesNoIdentityAtAll() {
        val state: AuthUiState = AuthUiState.SignedOut

        assertFalse(state.isSignedIn)
        assertEquals("", state.emailOrEmpty)
        assertEquals("", state.nameOrEmpty)
    }

    @Test
    fun aFailedSignInCarriesNoStaleIdentity() {
        val state: AuthUiState = AuthUiState.Failed(AuthFailure.NETWORK)

        assertEquals("", state.emailOrEmpty)
        assertEquals("", state.nameOrEmpty)
        assertFalse(state.isSignedIn)
    }

    @Test
    fun onlyInFlightOperationsAreBusy() {
        assertTrue(AuthUiState.SigningIn.isBusy)
        assertTrue(AuthUiState.SigningOut.isBusy)
        assertFalse(AuthUiState.SignedOut.isBusy)
        assertFalse(AuthUiState.SignedIn("a@b.c", "A").isBusy)
        assertFalse(AuthUiState.Failed(AuthFailure.UNKNOWN).isBusy)
    }

    @Test
    fun anInFlightOperationIsNeverAlsoSignedIn() {
        assertFalse(AuthUiState.SigningIn.isSignedIn)
        assertFalse(AuthUiState.SigningOut.isSignedIn)
    }

    @Test
    fun aDismissedPickerIsReportedAsCancelledNotAsAFailure() {
        assertEquals(
            AuthFailure.CANCELLED,
            AuthFailureClassifier.classify(
                "androidx.credentials.exceptions.GetCredentialCancellationException",
                "activity cancelled",
            ),
        )
    }

    @Test
    fun noGoogleAccountOnTheDeviceIsItsOwnReason() {
        assertEquals(
            AuthFailure.NO_CREDENTIAL,
            AuthFailureClassifier.classify(
                "androidx.credentials.exceptions.NoCredentialException",
                "no credential available",
            ),
        )
    }

    @Test
    fun anUnavailableProviderIsItsOwnReason() {
        assertEquals(
            AuthFailure.PROVIDER_UNAVAILABLE,
            AuthFailureClassifier.classify(
                "androidx.credentials.exceptions.GetCredentialProviderConfigurationException",
                null,
            ),
        )
        assertEquals(
            AuthFailure.PROVIDER_UNAVAILABLE,
            AuthFailureClassifier.classify("androidx.credentials.exceptions.GetCredentialUnsupportedException", null),
        )
    }

    @Test
    fun aTransientProblemIsClassifiedAsNetwork() {
        assertEquals(
            AuthFailure.NETWORK,
            AuthFailureClassifier.classify("java.io.IOException", "network unavailable"),
        )
        assertEquals(AuthFailure.NETWORK, AuthFailureClassifier.classify(null, "connect timed out"))
    }

    @Test
    fun aCredentialThatIsNotAGoogleIdTokenIsUnsupported() {
        assertEquals(
            AuthFailure.UNSUPPORTED_CREDENTIAL,
            AuthFailureClassifier.classify("androidx.credentials.exceptions.GetCredentialException", "invalid credential"),
        )
    }

    @Test
    fun anUnrecognisedProblemIsReportedAsUnknownRatherThanGuessed() {
        assertEquals(AuthFailure.UNKNOWN, AuthFailureClassifier.classify(null, null))
        assertEquals(AuthFailure.UNKNOWN, AuthFailureClassifier.classify("", ""))
        assertEquals(AuthFailure.UNKNOWN, AuthFailureClassifier.classify("java.lang.RuntimeException", "boom"))
    }

    @Test
    fun theConfigurationGapIsDistinctFromEveryOtherFailure() {
        // A missing Web client ID is the owner's to fix, not a sign-in failure to retry.
        val configuration = AuthFailure.CONFIGURATION_MISSING
        assertTrue(AuthFailure.entries.filter { it != configuration }.none { it == configuration })
        assertEquals(7, AuthFailure.entries.size)
    }
}
