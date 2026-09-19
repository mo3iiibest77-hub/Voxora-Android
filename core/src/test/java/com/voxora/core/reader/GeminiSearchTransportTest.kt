package com.voxora.core.reader

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract for the grounded search transport.
 *
 * Driven against a local [MockWebServer], so nothing here reaches Google. What is pinned is the
 * shape of the request — the grounding tool, the header-borne key, no key in the URL — and the
 * reading back of a grounded answer, including the honest-failure behaviour: a quota rejection must
 * not look like a broken network, and a body with no answer must not look like a finding.
 *
 * **The live API is not exercised here.** Whether `gemini-2.0-flash` accepts the `google_search`
 * tool through this endpoint is a device/owner verification item, not something a mock can prove.
 */
class GeminiSearchTransportTest {

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

    private fun transport() = GeminiHttpSearchTransport(OkHttpClient(), server.url("/").toString().trimEnd('/'))

    private fun enqueue(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    private fun groundedBody(text: String, uris: List<Pair<String, String>> = emptyList()): String {
        val chunks = uris.joinToString(",") { (title, uri) ->
            """{"web":{"uri":"$uri","title":"$title"}}"""
        }
        return """{"candidates":[{"content":{"parts":[{"text":"$text"}]},""" +
            """"groundingMetadata":{"webSearchQueries":["q"],"groundingChunks":[$chunks]}}]}"""
    }

    @Test
    fun theRequestAsksForSearchGroundingAndCarriesTheKeyInAHeader() = runBlocking {
        enqueue(200, groundedBody("A finding about the work."))

        val result = transport().search("key-123", "models/test", "Find the book")

        assertTrue(result is GeminiSearchResult.Found)
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        // The tool is what makes this a search rather than a recollection.
        assertTrue(body.contains("google_search"))
        assertTrue(body.contains("Find the book"))
        assertEquals("key-123", request.getHeader("x-goog-api-key"))
        // The key must never travel in the URL, where it would end up in a log.
        assertFalse(request.path!!.contains("key-123"))
        assertFalse(request.path!!.contains("key="))
        assertTrue(request.path!!.endsWith("/models/test:generateContent"))
    }

    @Test
    fun groundingSourcesAreReadBackWithTheFinding() = runBlocking {
        enqueue(
            200,
            groundedBody(
                "The search describes the work.",
                listOf("Example One" to "https://example.org/a", "Example Two" to "https://example.org/b"),
            ),
        )

        val found = transport().search("key", "models/test", "prompt") as GeminiSearchResult.Found

        assertEquals(2, found.sources.size)
        assertEquals("Example One", found.sources[0].title)
        assertEquals("https://example.org/a", found.sources[0].url)
    }

    @Test
    fun aDuplicateSourceIsOnlyCountedOnce() = runBlocking {
        enqueue(
            200,
            groundedBody(
                "The search describes the work.",
                listOf("One" to "https://example.org/a", "One again" to "https://example.org/a"),
            ),
        )
        val found = transport().search("key", "models/test", "prompt") as GeminiSearchResult.Found
        assertEquals(1, found.sources.size)
    }

    @Test
    fun anAnswerWithNoGroundingMetadataIsStillAnAnswer() = runBlocking {
        enqueue(200, """{"candidates":[{"content":{"parts":[{"text":"A finding with no citations."}]}}]}""")
        val found = transport().search("key", "models/test", "prompt") as GeminiSearchResult.Found
        assertTrue(found.sources.isEmpty())
        assertEquals("A finding with no citations.", found.summary)
    }

    @Test
    fun aQuotaRejectionIsNotABrokenNetwork() = runBlocking {
        enqueue(429, """{"error":{"code":429}}""")
        assertEquals(
            GeminiSearchResult.Failed(MetadataUnavailable.RATE_LIMITED),
            transport().search("key", "models/test", "prompt"),
        )
    }

    @Test
    fun aServerErrorIsReportedAsOne() = runBlocking {
        enqueue(500, "boom")
        assertEquals(
            GeminiSearchResult.Failed(MetadataUnavailable.SERVER_ERROR),
            transport().search("key", "models/test", "prompt"),
        )
    }

    @Test
    fun aBodyWithNoCandidateTextIsNotAFinding() = runBlocking {
        enqueue(200, """{"candidates":[]}""")
        assertEquals(
            GeminiSearchResult.Failed(MetadataUnavailable.PARSE_ERROR),
            transport().search("key", "models/test", "prompt"),
        )
    }

    @Test
    fun aSafetyBlockIsNotAFinding() = runBlocking {
        enqueue(200, """{"promptFeedback":{"blockReason":"SAFETY"}}""")
        assertEquals(
            GeminiSearchResult.Failed(MetadataUnavailable.PARSE_ERROR),
            transport().search("key", "models/test", "prompt"),
        )
    }

    @Test
    fun anUnreachableServerIsOfflineRatherThanAnException() = runBlocking {
        // The transport is built first: shutting the server down closes the port, which is what an
        // offline device looks like from the client's side.
        val transport = transport()
        server.shutdown()
        assertTrue(transport.search("key", "models/test", "prompt") is GeminiSearchResult.Failed)
    }
}
