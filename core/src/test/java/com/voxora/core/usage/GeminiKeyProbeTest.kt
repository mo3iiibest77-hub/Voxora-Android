package com.voxora.core.usage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the key check.
 *
 * A fake transport stands in for the network, so every outcome — including the ones that are hard
 * to provoke for real — is covered deterministically. Nothing here asserts a real quota number or
 * a real model count: the only counts are ones the fake supplied.
 */
class GeminiKeyProbeTest {

    private class FakeTransport(
        private val response: GeminiProbeResponse = GeminiProbeResponse.http(200),
        private val throwOnCall: Exception? = null,
    ) : GeminiProbeTransport {
        var calls = 0
            private set
        var lastKey: String? = null
            private set

        override suspend fun listModels(apiKey: String): GeminiProbeResponse {
            calls++
            lastKey = apiKey
            throwOnCall?.let { throw it }
            return response
        }
    }

    private val realLookingKey = "AIzaSyD1234567890ABCD"
    private val placeholder = "REPLACE_WITH_GOOGLE_WEB_CLIENT_ID"

    @Test
    fun aWorkingKeyIsConnectedAndReportsWhatItCouldSee() = runBlocking {
        val transport = FakeTransport(GeminiProbeResponse.http(200, """{"models":[{"name":"a"},{"name":"b"}]}"""))

        val result = GeminiKeyProbe(transport).probe(realLookingKey)

        assertEquals(GeminiKeyStatus.CONNECTED, result.status)
        assertEquals(2, result.modelCount)
        assertEquals(1, transport.calls)
    }

    @Test
    fun theKeyIsSentTrimmed() = runBlocking {
        val transport = FakeTransport()

        GeminiKeyProbe(transport).probe("  $realLookingKey  ")

        assertEquals(realLookingKey, transport.lastKey)
    }

    @Test
    fun anInvalidKeyIsClassifiedWithoutGuessing() = runBlocking {
        val transport = FakeTransport(GeminiProbeResponse.http(400, """{"error":{"status":"API_KEY_INVALID"}}"""))

        val result = GeminiKeyProbe(transport).probe(realLookingKey)

        assertEquals(GeminiKeyStatus.INVALID_KEY, result.status)
        assertEquals("http-400", result.detail)
        assertNull("no model count is invented for a failure", result.modelCount)
    }

    @Test
    fun aRefusedKeyIsUnauthorized() = runBlocking {
        val transport = FakeTransport(GeminiProbeResponse.http(403, """{"error":{"status":"PERMISSION_DENIED"}}"""))
        assertEquals(GeminiKeyStatus.UNAUTHORIZED, GeminiKeyProbe(transport).probe(realLookingKey).status)
    }

    @Test
    fun anExhaustedQuotaIsQuotaLimited() = runBlocking {
        val transport = FakeTransport(GeminiProbeResponse.http(429, """{"error":{"status":"RESOURCE_EXHAUSTED"}}"""))
        assertEquals(GeminiKeyStatus.QUOTA_LIMITED, GeminiKeyProbe(transport).probe(realLookingKey).status)
    }

    @Test
    fun anUnreachableServiceIsNetworkUnavailable() = runBlocking {
        val transport = FakeTransport(GeminiProbeResponse.network())

        val result = GeminiKeyProbe(transport).probe(realLookingKey)

        assertEquals(GeminiKeyStatus.NETWORK_UNAVAILABLE, result.status)
        assertEquals("network", result.detail)
    }

    @Test
    fun aTransportThatThrowsDoesNotEscapeAndIsClassified() = runBlocking {
        val transport = FakeTransport(throwOnCall = IllegalStateException("boom"))

        val result = GeminiKeyProbe(transport).probe(realLookingKey)

        assertEquals(GeminiKeyStatus.UNKNOWN, result.status)
    }

    @Test
    fun aSuccessWithNoReadableBodyIsStillConnectedButWithNoCount() = runBlocking {
        val transport = FakeTransport(GeminiProbeResponse.http(200, ""))

        val result = GeminiKeyProbe(transport).probe(realLookingKey)

        assertEquals(GeminiKeyStatus.CONNECTED, result.status)
        assertNull("partial data must stay unknown, not become 0", result.modelCount)
    }

    @Test
    fun noRequestIsMadeWhenNoKeyIsConfigured() = runBlocking {
        val transport = FakeTransport()

        val result = GeminiKeyProbe(transport).probe(null)

        assertEquals(GeminiKeyStatus.CONFIGURATION_INCOMPLETE, result.status)
        assertEquals("an unconfigured install must not generate traffic", 0, transport.calls)
    }

    @Test
    fun theShippedPlaceholderIsTreatedAsNoKeyAtAll() = runBlocking {
        val transport = FakeTransport()

        val result = GeminiKeyProbe(transport).probe(placeholder)

        assertEquals(GeminiKeyStatus.CONFIGURATION_INCOMPLETE, result.status)
        assertEquals(0, transport.calls)
    }

    @Test
    fun aBlankKeyIsNotProbed() = runBlocking {
        val transport = FakeTransport()
        assertEquals(GeminiKeyStatus.CONFIGURATION_INCOMPLETE, GeminiKeyProbe(transport).probe("   ").status)
        assertEquals(0, transport.calls)
    }

    @Test
    fun theProbeUsesTheDocumentedNonGenerativeEndpoint() {
        // Guard rail: the check must not point at a generative method, which would consume tokens.
        assertTrue(GeminiKeyProbe.MODELS_URL.endsWith("/v1beta/models"))
        assertTrue(GeminiKeyProbe.MODELS_URL.startsWith("https://"))
    }
}
