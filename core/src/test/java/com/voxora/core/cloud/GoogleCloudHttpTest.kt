package com.voxora.core.cloud

import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract for the authorized Google Cloud reads.
 *
 * Driven against a local [MockWebServer], so nothing here reaches Google and nothing asserts a
 * real project, key, quota or usage figure. What is pinned is the honest-failure behaviour:
 * a refusal is not a failure, a malformed answer is not a zero, and the credential never appears
 * in a URL.
 */
class GoogleCloudHttpTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun directory(windowDays: Long = 30L) = GoogleCloudHttpDirectory(
        client = OkHttpClient(),
        endpoints = endpoints(),
        usageWindowDays = windowDays,
        clock = { Instant.parse("2026-09-18T00:00:00Z") },
    )

    private fun endpoints(): CloudEndpoints {
        val base = server.url("/").toString().trimEnd('/')
        return CloudEndpoints(
            resourceManagerBase = base,
            apiKeysBase = base,
            monitoringBase = base,
            quotasBase = base,
        )
    }

    private fun enqueue(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    // ---- project discovery ----------------------------------------------------------

    @Test
    fun listsProjectsAndCarriesTheTokenInTheHeaderNotTheUrl() = runBlocking {
        enqueue(200, """{"projects":[{"projectId":"alpha-123","name":"Alpha"}]}""")

        val result = directory().listProjects("ya29.SECRET")

        val value = result.valueOrNull!!
        assertEquals(1, value.size)
        assertEquals("alpha-123", value[0].projectId)

        val recorded = server.takeRequest()
        assertEquals("Bearer ya29.SECRET", recorded.getHeader("Authorization"))
        assertFalse(recorded.path!!.contains("ya29.SECRET"))
        assertFalse(recorded.path!!.contains("key="))
    }

    @Test
    fun a401MeansTheGrantIsGone() = runBlocking {
        enqueue(401, """{"error":{"code":401}}""")
        assertEquals(CloudResult.NotAuthorized, directory().listProjects("t"))
    }

    @Test
    fun a403MeansTheAccountIsFineButNotAllowed() = runBlocking {
        enqueue(403, """{"error":{"code":403}}""")
        assertEquals(CloudResult.PermissionDenied, directory().listProjects("t"))
    }

    @Test
    fun aServerErrorIsAFailureNotAnEmptyList() = runBlocking {
        enqueue(500, """{"error":{"code":500}}""")
        val result = directory().listProjects("t")
        assertEquals(500, (result as CloudResult.Failed).statusCode)
        assertNull(result.valueOrNull)
    }

    @Test
    fun aSuccessWithAnUnreadableBodyIsAFailureNotAnEmptyList() = runBlocking {
        enqueue(200, "this is not json")
        val result = directory().listProjects("t")
        assertEquals(200, (result as CloudResult.Failed).statusCode)
    }

    @Test
    fun anUnreachableHostIsANetworkProblem() = runBlocking {
        val port = server.port
        server.shutdown()
        val unreachable = CloudEndpoints(
            resourceManagerBase = "http://127.0.0.1:$port",
            apiKeysBase = "http://127.0.0.1:$port",
            monitoringBase = "http://127.0.0.1:$port",
            quotasBase = "http://127.0.0.1:$port",
        )
        val result = GoogleCloudHttpDirectory(
            client = OkHttpClient(),
            endpoints = unreachable,
        ).listProjects("t")
        assertEquals(CloudResult.NetworkUnavailable, result)
    }

    // ---- key discovery --------------------------------------------------------------

    @Test
    fun listsKeyMetadataFromTheProjectsKeyEndpoint() = runBlocking {
        enqueue(
            200,
            """{"keys":[{"name":"projects/alpha-123/locations/global/keys/key-1","displayName":"Reader key"}]}""",
        )

        val result = directory().listKeys("t", "alpha-123")

        assertEquals("key-1", result.valueOrNull!![0].keyId)
        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("/v1/projects/alpha-123/locations/global/keys"))
    }

    @Test
    fun keyListingIsRefusedCleanly() = runBlocking {
        enqueue(403, "{}")
        assertEquals(CloudResult.PermissionDenied, directory().listKeys("t", "alpha-123"))
    }

    // ---- project usage --------------------------------------------------------------

    @Test
    fun readsARequestCountAndAQuotaLimitFromTwoIndependentCalls() = runBlocking {
        enqueue(200, """{"timeSeries":[{"points":[{"value":{"int64Value":"120"}}]}]}""")
        enqueue(200, """{"quotaInfos":[{"quotaId":"GenerateContentRequestsPerMinutePerProject","dimensionsInfos":[{"details":{"value":"2000"}}]}]}""")

        val usage = directory().projectUsage("t", "alpha-123").valueOrNull!!

        assertEquals(120L, usage.requests.knownValue)
        assertEquals(2000L, usage.quotaLimit.knownValue)
        assertEquals(CloudUsageSource.GOOGLE_PROJECT, usage.source)
        // Still absent because Google does not expose them, and never rendered as zero.
        assertNull(usage.billing.knownValue)
        assertNull(usage.inputTokens.knownValue)

        val monitoring = server.takeRequest()
        assertTrue(monitoring.path!!.contains("timeSeries"))
        assertTrue(monitoring.path!!.contains("generativelanguage.googleapis.com"))
        val quotas = server.takeRequest()
        assertTrue(quotas.path!!.contains("quotaInfos"))
    }

    @Test
    fun aQuotaRefusalDoesNotDiscardARealRequestCount() = runBlocking {
        enqueue(200, """{"timeSeries":[{"points":[{"value":{"int64Value":"5"}}]}]}""")
        enqueue(403, "{}")

        val usage = directory().projectUsage("t", "alpha-123").valueOrNull!!

        assertEquals(5L, usage.requests.knownValue)
        assertEquals(
            com.voxora.core.usage.UsageUnavailable.NOT_OFFERED,
            usage.quotaLimit.reasonOrNull,
        )
    }

    @Test
    fun aRefusedMonitoringReadRefusesTheWholeUsageRead() = runBlocking {
        enqueue(403, "{}")
        assertEquals(CloudResult.PermissionDenied, directory().projectUsage("t", "alpha-123"))
    }

    @Test
    fun anExpiredGrantOnTheUsageReadIsReportedAsUnauthorized() = runBlocking {
        enqueue(401, "{}")
        assertEquals(CloudResult.NotAuthorized, directory().projectUsage("t", "alpha-123"))
    }
}
