package com.voxora.core.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for turning a transport answer into a status the user can act on.
 *
 * Every branch is exercised with a synthetic response so no test touches the network, and the
 * important property is that an unreadable answer produces [GeminiKeyStatus.UNKNOWN] rather than a
 * confident wrong classification.
 */
class GeminiKeyClassifierTest {

    private fun errorBody(status: String) =
        """{"error":{"code":400,"message":"$status","status":"$status"}}"""

    @Test
    fun aSuccessfulResponseIsConnected() {
        assertEquals(
            GeminiKeyStatus.CONNECTED,
            GeminiKeyClassifier.classify(GeminiProbeResponse.http(200, """{"models":[]}""")),
        )
    }

    @Test
    fun anInvalidKeyIsReportedAsInvalidNotAsAFailure() {
        assertEquals(GeminiKeyStatus.INVALID_KEY, GeminiKeyClassifier.classify(GeminiProbeResponse.http(400)))
        assertEquals(
            GeminiKeyStatus.INVALID_KEY,
            GeminiKeyClassifier.classify(GeminiProbeResponse.http(400, errorBody("API_KEY_INVALID"))),
        )
    }

    @Test
    fun aRefusedKeyIsUnauthorized() {
        assertEquals(GeminiKeyStatus.UNAUTHORIZED, GeminiKeyClassifier.classify(GeminiProbeResponse.http(401)))
        assertEquals(GeminiKeyStatus.UNAUTHORIZED, GeminiKeyClassifier.classify(GeminiProbeResponse.http(403)))
        assertEquals(
            GeminiKeyStatus.UNAUTHORIZED,
            GeminiKeyClassifier.classify(GeminiProbeResponse.http(403, errorBody("PERMISSION_DENIED"))),
        )
        assertEquals(
            GeminiKeyStatus.UNAUTHORIZED,
            GeminiKeyClassifier.classify(GeminiProbeResponse.http(403, errorBody("API_KEY_SERVICE_BLOCKED"))),
        )
    }

    @Test
    fun anExhaustedQuotaIsReportedAsQuotaLimited() {
        assertEquals(GeminiKeyStatus.QUOTA_LIMITED, GeminiKeyClassifier.classify(GeminiProbeResponse.http(429)))
        assertEquals(
            GeminiKeyStatus.QUOTA_LIMITED,
            GeminiKeyClassifier.classify(GeminiProbeResponse.http(429, errorBody("RESOURCE_EXHAUSTED"))),
        )
    }

    @Test
    fun theBodyReasonWinsOverAnAmbiguousHttpCode() {
        // Google returns 400 for several conditions, so the reason must take precedence.
        assertEquals(
            GeminiKeyStatus.QUOTA_LIMITED,
            GeminiKeyClassifier.classify(GeminiProbeResponse.http(400, errorBody("RESOURCE_EXHAUSTED"))),
        )
        assertEquals(
            GeminiKeyStatus.UNAUTHORIZED,
            GeminiKeyClassifier.classify(GeminiProbeResponse.http(400, errorBody("PERMISSION_DENIED"))),
        )
    }

    @Test
    fun aServerSideFailureIsServiceError() {
        assertEquals(GeminiKeyStatus.SERVICE_ERROR, GeminiKeyClassifier.classify(GeminiProbeResponse.http(500)))
        assertEquals(GeminiKeyStatus.SERVICE_ERROR, GeminiKeyClassifier.classify(GeminiProbeResponse.http(503)))
        assertEquals(
            GeminiKeyStatus.SERVICE_ERROR,
            GeminiKeyClassifier.classify(GeminiProbeResponse.http(503, errorBody("UNAVAILABLE"))),
        )
    }

    @Test
    fun aRequestThatNeverArrivedIsNetworkUnavailable() {
        assertEquals(
            GeminiKeyStatus.NETWORK_UNAVAILABLE,
            GeminiKeyClassifier.classify(GeminiProbeResponse.network()),
        )
        assertEquals(
            GeminiKeyStatus.NETWORK_UNAVAILABLE,
            GeminiKeyClassifier.classify(GeminiProbeResponse.timeout()),
        )
    }

    @Test
    fun anUnreadableAnswerIsUnknownRatherThanGuessed() {
        assertTrue(GeminiKeyClassifier.classify(GeminiProbeResponse.failed()) == GeminiKeyStatus.UNKNOWN)
        assertTrue(GeminiKeyClassifier.classify(GeminiProbeResponse()) == GeminiKeyStatus.UNKNOWN)
        assertTrue(GeminiKeyClassifier.classify(GeminiProbeResponse.http(418)) == GeminiKeyStatus.UNKNOWN)
    }

    @Test
    fun aMalformedErrorBodyFallsBackToTheHttpCode() {
        assertEquals(
            GeminiKeyStatus.UNAUTHORIZED,
            GeminiKeyClassifier.classify(GeminiProbeResponse.http(403, "not json at all")),
        )
        assertEquals(
            GeminiKeyStatus.INVALID_KEY,
            GeminiKeyClassifier.classify(GeminiProbeResponse.http(400, "{\"error\":")),
        )
    }

    @Test
    fun anUnknownErrorReasonFallsBackToTheHttpCode() {
        assertEquals(
            GeminiKeyStatus.QUOTA_LIMITED,
            GeminiKeyClassifier.classify(GeminiProbeResponse.http(429, errorBody("SOMETHING_NEW"))),
        )
    }

    @Test
    fun onlyConnectedIsHealthy() {
        assertTrue(GeminiKeyStatus.CONNECTED.isHealthy)
        assertTrue(
            GeminiKeyStatus.entries.filter { it != GeminiKeyStatus.CONNECTED }.none { it.isHealthy },
        )
    }

    @Test
    fun theModelCountIsReadFromASingleCompletePage() {
        val body = """{"models":[{"name":"models/a"},{"name":"models/b"}]}"""
        assertEquals(2, GeminiKeyClassifier.modelCount(body))
    }

    @Test
    fun aPagedResponseIsReportedAsUnknownRatherThanAsATotal() {
        val body = """{"models":[{"name":"models/a"}],"nextPageToken":"more"}"""
        assertNull("a first page is not the total", GeminiKeyClassifier.modelCount(body))
    }

    @Test
    fun anUnreadableBodyYieldsNoModelCount() {
        assertNull(GeminiKeyClassifier.modelCount(null))
        assertNull(GeminiKeyClassifier.modelCount(""))
        assertNull(GeminiKeyClassifier.modelCount("not json"))
        assertNull(GeminiKeyClassifier.modelCount("""{"other":1}"""))
    }
}
