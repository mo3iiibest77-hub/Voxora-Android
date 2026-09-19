package com.voxora.app.auth

import com.voxora.core.cloud.CloudAuthFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the authorization-failure classification.
 *
 * The reason matters because the next action differs: a cancellation needs nothing, a network
 * problem is worth retrying, a refused scope needs a different account, and a build with no OAuth
 * client is not the user's fault at all. Collapsing any of these into "something went wrong" would
 * tell the user to retry an action that cannot succeed.
 *
 * The Play Services exception classes cannot be loaded on a plain JVM, which is exactly why
 * classification goes by type name, message and status code — and why it is testable here.
 */
class CloudAuthFailureClassifierTest {

    @Test
    fun cancellationIsRecognisedByBothStatusCodesAndByText() {
        assertEquals(CloudAuthFailure.CANCELLED, CloudAuthFailureClassifier.classify(null, null, 12501))
        assertEquals(CloudAuthFailure.CANCELLED, CloudAuthFailureClassifier.classify(null, null, 16))
        assertEquals(CloudAuthFailureClassifier.classify("ApiException", "user cancelled", null), CloudAuthFailure.CANCELLED)
        assertEquals(
            CloudAuthFailureClassifier.classify("com.google.android.gms.common.api.ApiException", "operation cancelled", null),
            CloudAuthFailure.CANCELLED,
        )
    }

    @Test
    fun aNetworkProblemIsRetryableAndKeptApartFromACancellation() {
        assertEquals(CloudAuthFailure.NETWORK, CloudAuthFailureClassifier.classify(null, null, 7))
        assertEquals(CloudAuthFailure.NETWORK, CloudAuthFailureClassifier.classify("java.io.IOException", "network unavailable", null))
        assertEquals(CloudAuthFailure.NETWORK, CloudAuthFailureClassifier.classify(null, "connect timed out", null))
        assertNotEquals(
            CloudAuthFailureClassifier.classify(null, "timed out", null),
            CloudAuthFailureClassifier.classify(null, "user cancelled", null),
        )
    }

    @Test
    fun aMissingAccountIsItsOwnReason() {
        // SIGN_IN_REQUIRED is the status the Authorization API reports when the device has no
        // account to offer; the message form covers a provider that phrases it in words.
        assertEquals(CloudAuthFailure.NO_ACCOUNT, CloudAuthFailureClassifier.classify(null, null, 4))
        assertEquals(CloudAuthFailure.NO_ACCOUNT, CloudAuthFailureClassifier.classify(null, "no credential found", null))
        assertEquals(CloudAuthFailure.NO_ACCOUNT, CloudAuthFailureClassifier.classify(null, "no account available", null))
    }

    @Test
    fun aMissingOrOutdatedProviderIsNotReportedAsAFailedSignIn() {
        // Play Services missing, disabled, invalid or out of date: retrying cannot help, so these
        // must not be reported as a generic sign-in failure.
        assertEquals(CloudAuthFailure.PROVIDER_UNAVAILABLE, CloudAuthFailureClassifier.classify(null, null, 1))
        assertEquals(CloudAuthFailure.PROVIDER_UNAVAILABLE, CloudAuthFailureClassifier.classify(null, null, 2))
        assertEquals(CloudAuthFailure.PROVIDER_UNAVAILABLE, CloudAuthFailureClassifier.classify(null, null, 3))
        assertEquals(CloudAuthFailure.PROVIDER_UNAVAILABLE, CloudAuthFailureClassifier.classify(null, null, 9))
        assertEquals(
            CloudAuthFailure.PROVIDER_UNAVAILABLE,
            CloudAuthFailureClassifier.classify("com.google.android.gms.common.api.ResolvableApiException", null, null),
        )
        assertNotEquals(
            CloudAuthFailureClassifier.classify("com.google.android.gms.common.api.ResolvableApiException", null, null),
            CloudAuthFailure.UNKNOWN,
        )
    }

    @Test
    fun aRefusedScopeIsADecisionRatherThanAnError() {
        assertEquals(
            CloudAuthFailure.PERMISSION_DENIED,
            CloudAuthFailureClassifier.classify(null, "access_denied", null),
        )
        assertEquals(
            CloudAuthFailure.PERMISSION_DENIED,
            CloudAuthFailureClassifier.classify(null, "permission denied for the requested scopes", null),
        )
        assertNotEquals(
            CloudAuthFailureClassifier.classify(null, "access_denied", null),
            CloudAuthFailure.UNKNOWN,
        )
    }

    @Test
    fun anUnreadableAnswerIsUnsupported() {
        assertEquals(
            CloudAuthFailure.UNSUPPORTED,
            CloudAuthFailureClassifier.classify(
                "com.google.android.gms.common.api.ApiException",
                "invalid response",
                null,
            ),
        )
    }

    @Test
    fun anythingElseIsUnknownRatherThanGuessed() {
        assertEquals(CloudAuthFailure.UNKNOWN, CloudAuthFailureClassifier.classify(null, null, null))
        assertEquals(CloudAuthFailure.UNKNOWN, CloudAuthFailureClassifier.classify("", "", null))
        assertEquals(CloudAuthFailure.UNKNOWN, CloudAuthFailureClassifier.classify("java.lang.RuntimeException", "boom", null))
        assertEquals(CloudAuthFailure.UNKNOWN, CloudAuthFailureClassifier.classify("ApiException", null, 12500))
    }

    @Test
    fun anExpiredGrantIsNeverProducedByExceptionClassification() {
        // EXPIRED is a grant-lost signal raised when Google answers 401 to a read, not something an
        // exception message may claim. Keeping it out of the classifier stops a transient error
        // from being shown as "sign in again".
        val reasons = listOf(
            CloudAuthFailureClassifier.classify(null, null, null),
            CloudAuthFailureClassifier.classify(null, "unauthorized", null),
            CloudAuthFailureClassifier.classify(null, "401", null),
            CloudAuthFailureClassifier.classify("ApiException", "token expired", null),
        )
        assertTrue(reasons.none { it == CloudAuthFailure.EXPIRED })
    }

    @Test
    fun aConfigurationGapIsNeverProducedByExceptionClassification() {
        // CONFIGURATION_MISSING is decided before any provider call, so it must not be reachable
        // from a provider exception either.
        val reasons = listOf(
            CloudAuthFailureClassifier.classify(null, null, null),
            CloudAuthFailureClassifier.classify(null, "no client id", null),
            CloudAuthFailureClassifier.classify("IllegalStateException", "configuration missing", null),
        )
        assertTrue(reasons.none { it == CloudAuthFailure.CONFIGURATION_MISSING })
    }
}
