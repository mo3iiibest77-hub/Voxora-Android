package com.voxora.core.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for classifying a failed request.
 *
 * The category is the *only* thing persisted about a failure. The exception message is never
 * stored, because an HTTP client can include the request URL in it and the Reader's WebSocket URL
 * carries the API key. These tests pin the mapping and the fact that an unrecognised failure is
 * reported as unknown rather than guessed at.
 */
class UsageFailureCategoryTest {

    @Test
    fun aTimeoutIsClassifiedAsATimeout() {
        assertEquals(
            UsageFailureCategory.TIMEOUT,
            UsageFailureCategory.classify("java.net.SocketTimeoutException", "timeout"),
        )
        assertEquals(
            UsageFailureCategory.TIMEOUT,
            UsageFailureCategory.classify("java.io.IOException", "read timed out"),
        )
    }

    @Test
    fun anExhaustedQuotaIsClassifiedAsQuota() {
        assertEquals(
            UsageFailureCategory.QUOTA,
            UsageFailureCategory.classify("java.io.IOException", "RESOURCE_EXHAUSTED"),
        )
        assertEquals(UsageFailureCategory.QUOTA, UsageFailureCategory.classify(null, "rate limit exceeded"))
    }

    @Test
    fun ARefusedKeyIsClassifiedAsUnauthorized() {
        assertEquals(
            UsageFailureCategory.UNAUTHORIZED,
            UsageFailureCategory.classify("java.io.IOException", "API_KEY_INVALID"),
        )
        assertEquals(UsageFailureCategory.UNAUTHORIZED, UsageFailureCategory.classify(null, "PERMISSION_DENIED"))
    }

    @Test
    fun aConnectionProblemIsClassifiedAsNetwork() {
        assertEquals(
            UsageFailureCategory.NETWORK,
            UsageFailureCategory.classify("java.net.ConnectException", "failed to connect"),
        )
        assertEquals(UsageFailureCategory.NETWORK, UsageFailureCategory.classify(null, "network is unreachable"))
    }

    @Test
    fun anAudioFailureIsClassifiedAsAudio() {
        assertEquals(UsageFailureCategory.AUDIO, UsageFailureCategory.classify(null, "audio unavailable"))
    }

    @Test
    fun aSpoolOrSessionFailureIsClassifiedAsSession() {
        assertEquals(
            UsageFailureCategory.SESSION,
            UsageFailureCategory.classify("com.voxora.app.reader.ReaderSpool\$CapacityException", null),
        )
        assertEquals(UsageFailureCategory.SESSION, UsageFailureCategory.classify(null, "session closed"))
    }

    @Test
    fun aRealReaderAudioMessageIsNotMistakenForANetworkProblem() {
        // "Audio output is unavailable" contains "unavailable"; it must not be recorded as network.
        assertEquals(
            UsageFailureCategory.AUDIO,
            UsageFailureCategory.classify(null, "Audio output is unavailable. Close other media apps and try again."),
        )
    }

    @Test
    fun anUnrecognisedFailureIsReportedAsUnknownRatherThanGuessed() {
        assertEquals(UsageFailureCategory.UNKNOWN, UsageFailureCategory.classify(null, null))
        assertEquals(UsageFailureCategory.UNKNOWN, UsageFailureCategory.classify("", ""))
        assertEquals(UsageFailureCategory.UNKNOWN, UsageFailureCategory.classify("java.lang.IllegalStateException", "boom"))
    }

    @Test
    fun everyCategoryIsDistinctAndSafeToPersist() {
        val all = listOf(
            UsageFailureCategory.NETWORK,
            UsageFailureCategory.QUOTA,
            UsageFailureCategory.UNAUTHORIZED,
            UsageFailureCategory.TIMEOUT,
            UsageFailureCategory.AUDIO,
            UsageFailureCategory.SESSION,
            UsageFailureCategory.CONFIGURATION,
            UsageFailureCategory.UNKNOWN,
        )
        assertEquals("categories are persisted identifiers, so they must be unique", all.size, all.toSet().size)
        assertTrue(all.all { it.isNotBlank() && !it.contains(' ') })
    }

    @Test
    fun theCategoryIsShortAndCarriesNoMessageText() {
        val category = UsageFailureCategory.classify("java.io.IOException", "GET https://example/?key=SECRET failed")
        assertEquals(UsageFailureCategory.UNKNOWN, category)
        assertEquals(1, category.split(" ").size)
    }
}
