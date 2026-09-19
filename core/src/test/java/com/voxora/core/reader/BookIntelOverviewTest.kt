package com.voxora.core.reader

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The AI-generated Book Intelligence overview.
 *
 * Two things are worth pinning here, and neither is the prose: **what is sent** — the catalogue
 * record and nothing else, never the document — and **what is accepted back**, because a stored
 * overview is shown to the reader as knowledge about their book.
 */
class BookIntelOverviewTest {

    /**
     * A valid answer.
     *
     * Comfortably longer than [BookIntelOverviewPrompt.MIN_CHARS], so a test that expects `null`
     * can only be failing for the reason it is about — a fixture that is too short would make the
     * length rule look like the behaviour under test.
     */
    private val ANSWER =
        "A book about evolution that argues the gene, not the organism, is the unit of selection."

    private fun metadata(
        title: String = "The Selfish Gene",
        authors: List<String> = listOf("Richard Dawkins"),
        publisher: String? = "Oxford University Press",
        publishedDate: String? = "1976",
        description: String? = "A landmark work on evolutionary biology.",
        categories: List<String> = listOf("Science", "Evolution"),
        language: String? = "en",
        pageCount: Int? = 224,
    ) = BookMetadata(
        provider = MetadataProvider.GOOGLE_BOOKS,
        providerId = "abc",
        title = title,
        subtitle = null,
        authors = authors,
        publisher = publisher,
        publishedDate = publishedDate,
        description = description,
        categories = categories,
        language = language,
        pageCount = pageCount,
        isbn10 = null,
        isbn13 = "9780198575191",
        coverUrl = null,
        confidence = MatchConfidence.TITLE_AUTHOR,
        fetchedAt = 1_000L,
    )

    // ---- the prompt -------------------------------------------------------------------------

    @Test
    fun promptCarriesTheRecordAndTheTargetLanguage() {
        val prompt = BookIntelOverviewPrompt.build(metadata(), "Persian")
        assertTrue(prompt.contains("The Selfish Gene"))
        assertTrue(prompt.contains("Richard Dawkins"))
        assertTrue(prompt.contains("Oxford University Press"))
        assertTrue(prompt.contains("1976"))
        assertTrue(prompt.contains("A landmark work on evolutionary biology."))
        assertTrue(prompt.contains("Persian"))
    }

    @Test
    fun promptForbidsOutsideKnowledge() {
        val prompt = BookIntelOverviewPrompt.build(metadata(), "Persian")
        // The instruction that keeps this a restatement rather than an invention.
        assertTrue(prompt.contains("ONLY the information"))
        assertTrue(prompt.contains("Do NOT add facts"))
    }

    @Test
    fun promptOmitsFieldsTheCatalogueDidNotProvide() {
        val prompt = BookIntelOverviewPrompt.build(
            metadata(publisher = null, publishedDate = null, description = null, categories = emptyList(), pageCount = null),
            "Persian",
        )
        assertFalse(prompt.contains("publisher:"))
        assertFalse(prompt.contains("published:"))
        assertFalse(prompt.contains("subjects:"))
        assertFalse(prompt.contains("pages:"))
        assertFalse(prompt.contains("catalogue description:"))
        // The title is always there: it is what the record is anchored on.
        assertTrue(prompt.contains("The Selfish Gene"))
    }

    // ---- reading the answer -----------------------------------------------------------------

    @Test
    fun parseReadsAndJoinsEveryPartOfTheFirstCandidate() {
        val body = """
            {"candidates":[{"content":{"parts":[{"text":"The book sets out the gene-centred "},{"text":"view of natural selection in detail."}]}}]}
        """.trimIndent()
        assertEquals(
            "The book sets out the gene-centred view of natural selection in detail.",
            BookIntelOverviewPrompt.parse(body),
        )
    }

    @Test
    fun parseRejectsABodyWithNoUsableText() {
        assertNull(BookIntelOverviewPrompt.parse(null))
        assertNull(BookIntelOverviewPrompt.parse(""))
        assertNull(BookIntelOverviewPrompt.parse("not json"))
        // A blocked or empty candidate list is not an answer.
        assertNull(BookIntelOverviewPrompt.parse("""{"candidates":[]}"""))
        assertNull(BookIntelOverviewPrompt.parse("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        assertNull(BookIntelOverviewPrompt.parse("""{"candidates":[{"content":{"parts":[]}}]}"""))
    }

    @Test
    fun sanitizeStripsCodeFencesAndTrims() {
        val raw = "```\n  A book about evolution and the gene-centred view of selection.  \n```"
        assertEquals(
            "A book about evolution and the gene-centred view of selection.",
            BookIntelOverviewPrompt.sanitize(raw),
        )
    }

    @Test
    fun sanitizeRefusesATextTooShortToBeAnAnswer() {
        assertNull(BookIntelOverviewPrompt.sanitize(null))
        assertNull(BookIntelOverviewPrompt.sanitize(""))
        assertNull(BookIntelOverviewPrompt.sanitize("   "))
        assertNull(BookIntelOverviewPrompt.sanitize("Too short."))
    }

    @Test
    fun sanitizeCapsALongAnswerRatherThanDiscardingIt() {
        val long = "x".repeat(BookIntelOverviewPrompt.MAX_CHARS + 500)
        val capped = BookIntelOverviewPrompt.sanitize(long)
        assertNotNull(capped)
        assertEquals(BookIntelOverviewPrompt.MAX_CHARS, capped!!.length)
    }

    // ---- the cache --------------------------------------------------------------------------

    @Test
    fun isUsableForMatchesTheLanguageCaseInsensitivelyAndRejectsBlankText() {
        val overview = BookIntelOverview("fa", "متن", "m", 1L)
        assertTrue(BookIntelOverviewPrompt.isUsableFor(overview, "fa"))
        assertTrue(BookIntelOverviewPrompt.isUsableFor(overview, "FA"))
        assertFalse(BookIntelOverviewPrompt.isUsableFor(overview, "en"))
        assertFalse(BookIntelOverviewPrompt.isUsableFor(null, "fa"))
        assertFalse(BookIntelOverviewPrompt.isUsableFor(overview.copy(text = "  "), "fa"))
    }

    @Test
    fun aTextWrittenByAnOlderPromptIsNotReused() {
        val current = BookIntelOverview("fa", "متن", "m", 1L)
        assertEquals(BookIntelOverviewPrompt.VERSION, current.promptVersion)
        assertTrue(BookIntelOverviewPrompt.isUsableFor(current, "fa"))
        // Otherwise improving the prompt would be masked by an answer already paid for.
        assertFalse(
            BookIntelOverviewPrompt.isUsableFor(
                current.copy(promptVersion = BookIntelOverviewPrompt.VERSION - 1),
                "fa",
            ),
        )
    }

    @Test
    fun pruneKeepsTheNewestLanguagesAndDropsTheOldest() {
        val overviews = listOf(
            BookIntelOverview("fa", "a", "m", 100L),
            BookIntelOverview("en", "b", "m", 300L),
            BookIntelOverview("fr", "c", "m", 200L),
            BookIntelOverview("de", "d", "m", 400L),
        )
        val pruned = BookIntelOverviewPrompt.prune(overviews, keep = 3)
        assertEquals(3, pruned.size)
        assertEquals(listOf("de", "en", "fr"), pruned.map { it.language })
    }

    // ---- the generator ----------------------------------------------------------------------

    private class FakeTransport(private val result: GeminiTextResult) : GeminiTextTransport {
        var calls = 0
            private set
        var lastModel: String? = null
            private set
        override suspend fun generate(apiKey: String, model: String, prompt: String): GeminiTextResult {
            calls++
            lastModel = model
            return result
        }
    }

    @Test
    fun generatorStoresTheTextWithItsLanguageAndModel() = runBlocking {
        val transport = FakeTransport(GeminiTextResult.Text(ANSWER))
        val generator = BookIntelOverviewGenerator(transport, model = "models/text", clock = { 42L })
        val overview = generator.generate("key", metadata(), "fa")
        assertNotNull(overview)
        assertEquals("fa", overview!!.language)
        assertEquals(ANSWER, overview.text)
        assertEquals("models/text", overview.model)
        assertEquals(42L, overview.generatedAt)
        assertEquals("models/text", transport.lastModel)
    }

    @Test
    fun generatorMakesNoRequestWithoutAKey() = runBlocking {
        val transport = FakeTransport(GeminiTextResult.Text(ANSWER))
        val generator = BookIntelOverviewGenerator(transport)
        assertNull(generator.generate(null, metadata(), "fa"))
        assertNull(generator.generate("   ", metadata(), "fa"))
        assertNull(generator.generate("key", metadata(), ""))
        assertEquals(0, transport.calls)
    }

    @Test
    fun generatorReturnsNothingWhenTheCallFails() = runBlocking {
        val transport = FakeTransport(GeminiTextResult.Failed(MetadataUnavailable.OFFLINE))
        val generator = BookIntelOverviewGenerator(transport)
        assertNull(generator.generate("key", metadata(), "fa"))
        assertEquals(1, transport.calls)
    }

    @Test
    fun generatorRefusesATextTooShortToStore() = runBlocking {
        val transport = FakeTransport(GeminiTextResult.Text("Nope."))
        val generator = BookIntelOverviewGenerator(transport)
        assertNull(generator.generate("key", metadata(), "fa"))
    }

    // ---- the transport ----------------------------------------------------------------------

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

    private fun transportTo(server: MockWebServer) = GeminiHttpTextTransport(
        client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build(),
        endpoint = server.url("/v1beta").toString().trimEnd('/'),
    )

    @Test
    fun transportSendsTheKeyInAHeaderAndThePromptInTheBody() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"content":{"parts":[{"text":"$ANSWER"}]}}]}""",
            ),
        )
        val result = transportTo(server).generate("secret-key", "models/text", "PROMPT")
        assertTrue(result is GeminiTextResult.Text)
        val recorded = server.takeRequest()
        assertEquals("/v1beta/models/text:generateContent", recorded.path)
        // The key must never travel in the URL, where it would end up in a log.
        assertEquals("secret-key", recorded.getHeader("x-goog-api-key"))
        assertFalse(recorded.path!!.contains("secret-key"))
        assertTrue(recorded.body.readUtf8().contains("PROMPT"))
    }

    @Test
    fun transportClassifiesFailuresLikeTheCataloguesDo() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(
            GeminiTextResult.Failed(MetadataUnavailable.RATE_LIMITED),
            transportTo(server).generate("k", "m", "p"),
        )
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(
            GeminiTextResult.Failed(MetadataUnavailable.SERVER_ERROR),
            transportTo(server).generate("k", "m", "p"),
        )
        server.enqueue(MockResponse().setBody("not json"))
        assertEquals(
            GeminiTextResult.Failed(MetadataUnavailable.PARSE_ERROR),
            transportTo(server).generate("k", "m", "p"),
        )
    }
}
