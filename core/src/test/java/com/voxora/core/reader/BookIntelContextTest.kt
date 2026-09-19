package com.voxora.core.reader

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Book Intelligence search fallback, at the level where the decisions live.
 *
 * ## What is being protected
 *
 * Three rules, in order of how badly breaking them would hurt:
 *
 * 1. **A search result never becomes a bibliographic fact.** The search produces a
 *    [BookSearchContext] and nothing else; only a [BookMetadata] can appear on the card as a fact,
 *    and only [BookMatch] can produce one. These tests pin that the two paths never mix.
 * 2. **Nothing is invented.** With no catalogue record and no search finding there is nothing to
 *    summarise, so nothing is generated — the prompt's own rules would not stop a model from
 *    producing something plausible, and not asking is what stops it.
 * 3. **The cache stays honest.** A text generated for one question is not served as the answer to a
 *    different one, and a cached text for a language short-circuits before any network work.
 *
 * Everything here runs on a plain JVM with fakes; the live search is unverified (see the repository
 * documentation), and no test claims otherwise.
 */
class BookIntelContextTest {

    // ---- fakes ---------------------------------------------------------------------------------

    private class RecordingSearchTransport(
        private val answer: GeminiSearchResult,
    ) : GeminiSearchTransport {
        var calls = 0
        var lastPrompt: String? = null
        var lastKey: String? = null
        override suspend fun search(apiKey: String, model: String, prompt: String): GeminiSearchResult {
            calls++
            lastKey = apiKey
            lastPrompt = prompt
            return answer
        }
    }

    private class RecordingTextTransport(
        private val answer: GeminiTextResult,
    ) : GeminiTextTransport {
        var calls = 0
        var lastPrompt: String? = null
        override suspend fun generate(apiKey: String, model: String, prompt: String): GeminiTextResult {
            calls++
            lastPrompt = prompt
            return answer
        }
    }

    private class ThrowingTextTransport : GeminiTextTransport {
        override suspend fun generate(apiKey: String, model: String, prompt: String): GeminiTextResult =
            throw IllegalStateException("offline")
    }

    private fun signals(
        isbn13: String? = null,
        title: String? = "Ahmad Kasravi and the Constitutional Movement",
        author: String? = "Ahmad Kasravi",
        filename: String = "kasravi.pdf",
    ) = BookSignals(isbn13 = isbn13, isbn10 = null, title = title, author = author, filename = filename)

    private fun metadata(coverUrl: String? = null) = BookMetadata(
        provider = MetadataProvider.GOOGLE_BOOKS,
        providerId = "vol-1",
        title = "A Matched Title",
        subtitle = null,
        authors = listOf("An Author"),
        publisher = null,
        publishedDate = null,
        description = "A catalogue description.",
        categories = listOf("History"),
        language = "en",
        pageCount = null,
        isbn10 = null,
        isbn13 = null,
        coverUrl = coverUrl,
        confidence = MatchConfidence.TITLE_AUTHOR,
        fetchedAt = 1L,
    )

    private fun book(
        id: String = "a",
        metadata: BookMetadata? = null,
        signals: BookSignals? = null,
        overviews: List<BookIntelOverview> = emptyList(),
    ) = ReaderBook(
        id = id,
        localPath = "/data/reader/books/$id.pdf",
        title = "Book $id",
        sourceType = ReaderSourceType.PDF,
        chunkCount = 10,
        currentChunk = 0,
        state = ReaderBookState.NOT_STARTED,
        importedAt = 1_000L,
        lastReadAt = 1_000L,
        metadata = metadata,
        lookup = MetadataLookupState.NONE,
        signals = signals,
        overviews = overviews,
    )

    private fun overview(language: String, fingerprint: String = "", version: Int = BookIntelOverviewPrompt.VERSION) =
        BookIntelOverview(
            language = language,
            text = "A generated overview long enough to be stored and shown on the card.",
            model = "models/test",
            generatedAt = 5_000L,
            promptVersion = version,
            contextFingerprint = fingerprint,
        )

    private val context = BookSearchContext(
        query = "Ahmad Kasravi and the Constitutional Movement — Ahmad Kasravi",
        summary = "The search describes a twentieth-century Iranian historian and his work on the " +
            "constitutional period, published in Tehran.",
        sources = listOf(BookSearchSource("Example", "https://example.org/book")),
    )

    // ---- the plan: which path, and whether anything is needed at all ----------------------------

    @Test
    fun aBookWithNoCatalogueRecordFallsBackToASearch() {
        val need = BookIntelOverviewPlan.need(book(signals = signals()), "fa")
        assertTrue(need is BookIntelOverviewPlan.Need.FromContext)
        // The fingerprint names the question, so a text generated for a different one is not reused.
        assertEquals(BookSearchPrompt.fingerprint(signals()), (need as BookIntelOverviewPlan.Need.FromContext).fingerprint)
    }

    @Test
    fun aConfidentCatalogueMatchIsPreferredOverASearch() {
        val need = BookIntelOverviewPlan.need(book(metadata = metadata(), signals = signals()), "fa")
        assertEquals(BookIntelOverviewPlan.Need.FromMetadata, need)
    }

    @Test
    fun aBookWithNeitherRecordNorSignalHasNoPlanAtAll() {
        // Nothing to generate from. Generating anyway is the fabrication the product forbids.
        val empty = BookSignals(isbn13 = null, isbn10 = null, title = null, author = null, filename = "")
        assertEquals(BookIntelOverviewPlan.Need.None, BookIntelOverviewPlan.need(book(signals = empty), "fa"))
        assertEquals(BookIntelOverviewPlan.Need.None, BookIntelOverviewPlan.need(book(signals = null), "fa"))
    }

    @Test
    fun aCachedOverviewForTheSameLanguageNeedsNothing() {
        // Catalogue path.
        assertEquals(
            BookIntelOverviewPlan.Need.None,
            BookIntelOverviewPlan.need(book(metadata = metadata(), overviews = listOf(overview("fa"))), "fa"),
        )
        // Search path: the cached text must have answered the same question.
        assertEquals(
            BookIntelOverviewPlan.Need.None,
            BookIntelOverviewPlan.need(
                book(signals = signals(), overviews = listOf(overview("fa", BookSearchPrompt.fingerprint(signals())))),
                "fa",
            ),
        )
    }

    @Test
    fun anotherLanguageGetsItsOwnOverview() {
        val cached = listOf(overview("fa", BookSearchPrompt.fingerprint(signals())))
        val need = BookIntelOverviewPlan.need(book(signals = signals(), overviews = cached), "en")
        assertTrue(need is BookIntelOverviewPlan.Need.FromContext)
    }

    @Test
    fun aCachedOverviewAnsweringADifferentQuestionIsNotReused() {
        val other = signals(title = "A Completely Different Work", author = "Someone Else")
        val cached = listOf(overview("fa", BookSearchPrompt.fingerprint(other)))
        val need = BookIntelOverviewPlan.need(book(signals = signals(), overviews = cached), "fa")
        assertTrue(need is BookIntelOverviewPlan.Need.FromContext)
        assertEquals(
            BookSearchPrompt.fingerprint(signals()),
            (need as BookIntelOverviewPlan.Need.FromContext).fingerprint,
        )
    }

    @Test
    fun aTextWrittenByAnOlderPromptIsNotReused() {
        val stale = listOf(
            overview("fa", BookSearchPrompt.fingerprint(signals()), version = BookIntelOverviewPrompt.VERSION - 1),
        )
        val need = BookIntelOverviewPlan.need(book(signals = signals(), overviews = stale), "fa")
        assertTrue(need is BookIntelOverviewPlan.Need.FromContext)
    }

    // ---- the search: bounded, total, and never a fact -------------------------------------------

    @Test
    fun theSearchPromptCarriesTheBooksOwnSignalsAndNothingElse() = runBlocking {
        val transport = RecordingSearchTransport(GeminiSearchResult.Found(context.summary))
        val search = GeminiGroundedBookSearch(transport, model = "models/test")

        val result = search.search(signals(isbn13 = "9780306406157"), "key-123")

        assertNotNullResult(result)
        val prompt = transport.lastPrompt!!
        assertTrue(prompt.contains("9780306406157"))
        assertTrue(prompt.contains("Ahmad Kasravi"))
        assertTrue(prompt.contains("kasravi.pdf"))
        // The key travels to the same endpoint it always did, and never appears in the prompt.
        assertEquals("key-123", transport.lastKey)
        assertFalse(prompt.contains("key-123"))
    }

    @Test
    fun anUnconfiguredKeyMakesNoRequestAtAll() = runBlocking {
        val transport = RecordingSearchTransport(GeminiSearchResult.Found("something"))
        assertNull(GeminiGroundedBookSearch(transport).search(signals(), apiKey = "  "))
        assertEquals(0, transport.calls)
    }

    @Test
    fun aSearchThatFoundNothingIsNotAFinding() = runBlocking {
        val notFound = RecordingSearchTransport(GeminiSearchResult.Found(BookSearchPrompt.NOT_FOUND_MARKER))
        assertNull(GeminiGroundedBookSearch(notFound).search(signals(), "key"))
        assertEquals(1, notFound.calls)
    }

    @Test
    fun aSearchFailureIsAValueNotAnException() = runBlocking {
        val failed = RecordingSearchTransport(GeminiSearchResult.Failed(MetadataUnavailable.OFFLINE))
        assertNull(GeminiGroundedBookSearch(failed).search(signals(), "key"))
    }

    @Test
    fun searchSourcesTravelWithTheFindingAndAreBounded() {
        val parsed = BookSearchPrompt.parse(
            query = "q",
            answer = "A finding about the work that is comfortably longer than the minimum length.",
            sources = (1..20).map { BookSearchSource("s$it", "https://example.org/$it") },
        )!!
        assertEquals(BookSearchPrompt.MAX_SOURCES, parsed.sources.size)
    }

    @Test
    fun aFindingIsCappedRatherThanStoredWhole() {
        val parsed = BookSearchPrompt.parse("q", "x".repeat(BookSearchPrompt.MAX_SUMMARY_CHARS + 5_000))!!
        assertEquals(BookSearchPrompt.MAX_SUMMARY_CHARS, parsed.summary.length)
    }

    @Test
    fun aFragmentTooShortToBeAFindingIsRejected() {
        assertNull(BookSearchPrompt.parse("q", "Yes."))
        assertNull(BookSearchPrompt.parse("q", "   "))
        assertNull(BookSearchPrompt.parse("q", null))
    }

    @Test
    fun theFingerprintChangesWithTheQuestionAndIsStableForTheSameOne() {
        assertEquals(BookSearchPrompt.fingerprint(signals()), BookSearchPrompt.fingerprint(signals()))
        assertNotEquals(BookSearchPrompt.fingerprint(signals()), BookSearchPrompt.fingerprint(signals(title = "Other")))
        assertNotEquals(
            BookSearchPrompt.fingerprint(signals()),
            BookSearchPrompt.fingerprint(signals(author = "Someone Else")),
        )
    }

    // ---- the overview prompt: evidence in, no invented facts out ---------------------------------

    @Test
    fun theContextPromptCarriesTheSignalsAndTheFindingsAndForbidsInventing() {
        val prompt = BookIntelOverviewPrompt.buildFromContext(signals(isbn13 = "9780306406157"), context, "Persian")

        assertTrue(prompt.contains("Ahmad Kasravi"))
        assertTrue(prompt.contains("9780306406157"))
        assertTrue(prompt.contains(context.summary))
        assertTrue(prompt.contains(context.query))
        // The rule that makes a search result evidence rather than a fact, and the rule that makes
        // disagreement something to report rather than to resolve.
        assertTrue(prompt.contains("second-hand evidence"))
        assertTrue(prompt.contains("disagree"))
        assertTrue(prompt.contains("Persian"))
    }

    @Test
    fun theContextPromptIsBoundedByTheSignalsAndTheCappedFinding() {
        // The only inputs are the signals and a summary the search layer already capped, so there is
        // no path by which a document body could reach the model.
        val long = BookSearchPrompt.parse("q", "y".repeat(BookSearchPrompt.MAX_SUMMARY_CHARS))!!
        val prompt = BookIntelOverviewPrompt.buildFromContext(signals(), long, "English")
        assertTrue(prompt.length < 4_000)
    }

    @Test
    fun anEmptyFindingGeneratesNothingRatherThanGuessing() = runBlocking {
        val transport = RecordingTextTransport(GeminiTextResult.Text(GeneratedOverview("unused")))
        val generator = BookIntelOverviewGenerator(transport, model = "models/test")

        val empty = BookSearchContext(query = "q", summary = "")
        assertNull(generator.generateFromContext("key", signals(), empty, "fa"))
        assertEquals(0, transport.calls)
    }

    @Test
    fun aFindingProducesAnOverviewCarryingTheQuestionItAnswered() = runBlocking {
        val transport = RecordingTextTransport(
            GeminiTextResult.Text(
                GeneratedOverview(
                    text = "An overview written from the search findings, long enough to be stored.",
                    themes = listOf("History"),
                ),
            ),
        )
        val generator = BookIntelOverviewGenerator(transport, model = "models/test")

        val generated = generator.generateFromContext("key", signals(), context, "fa")!!

        assertEquals("fa", generated.language)
        assertEquals(BookIntelOverviewPrompt.VERSION, generated.promptVersion)
        assertEquals(listOf("History"), generated.themes)
        assertEquals(BookSearchPrompt.fingerprint(signals()), generated.contextFingerprint)
        // The prompt the model actually received is the context one, not the record one.
        assertTrue(transport.lastPrompt!!.contains(context.summary))
    }

    @Test
    fun aGenerationFailureLeavesNothingBehind() = runBlocking {
        val generator = BookIntelOverviewGenerator(ThrowingTextTransport(), model = "models/test")
        assertNull(generator.generateFromContext("key", signals(), context, "fa"))
    }

    @Test
    fun aGeneratedContextOverviewIsReusableForTheSameQuestionOnly() {
        val generated = overview("fa", BookSearchPrompt.fingerprint(signals()))
        assertTrue(BookIntelOverviewPrompt.isUsableFor(generated, "fa", BookSearchPrompt.fingerprint(signals())))
        assertFalse(
            BookIntelOverviewPrompt.isUsableFor(
                generated,
                "fa",
                BookSearchPrompt.fingerprint(signals(title = "Something Else")),
            ),
        )
        // An empty fingerprint means the caller is on the catalogue path, where the question does not
        // apply — every text written before the search existed stays usable there.
        assertTrue(BookIntelOverviewPrompt.isUsableFor(generated, "fa"))
    }

    // ---- the cover fallback ---------------------------------------------------------------------

    @Test
    fun aVerifiedCoverIsNeverDisplacedByAFallback() {
        assertEquals(
            "https://books.google.com/cover.jpg",
            OpenLibraryCover.preferred("https://books.google.com/cover.jpg", "https://covers.openlibrary.org/x.jpg"),
        )
    }

    @Test
    fun aMissingCoverCanBeFilledByIdentifierOnly() {
        assertEquals("https://covers.openlibrary.org/x.jpg", OpenLibraryCover.preferred(null, "https://covers.openlibrary.org/x.jpg"))
        assertNull(OpenLibraryCover.preferred(null, null))
    }

    @Test
    fun theFallbackCoverUrlIsTiedToAValidIsbnAndRefusesAnythingElse() {
        assertEquals(
            "https://covers.openlibrary.org/b/isbn/9780306406157-M.jpg?default=false",
            OpenLibraryCover.urlForIsbn("9780306406157"),
        )
        // The ISBN-10 form of the same book resolves to its own identifier, not a guess.
        assertTrue(OpenLibraryCover.urlForIsbn("0306406152")!!.contains("/b/isbn/0306406152-"))
        // A number that is not a valid ISBN — a page number, a year — is not an identifier.
        assertNull(OpenLibraryCover.urlForIsbn("1234567890"))
        assertNull(OpenLibraryCover.urlForIsbn(""))
    }

    // ---- the record is never written by the search ----------------------------------------------

    @Test
    fun aSearchFindingNeverBecomesBibliographicMetadata() {
        // The only writer of metadata is the catalogue path; the search returns a different type
        // entirely, and a book that was not matched keeps a null record however good its search was.
        val searched = book(signals = signals())
        assertNull(searched.metadata)
        assertEquals(MetadataLookupState.NONE, searched.lookup)
        // And the overview is the only thing a search can add to the record.
        val withOverview = searched.withOverview(overview("fa", BookSearchPrompt.fingerprint(signals())))
        assertNull(withOverview.metadata)
        assertEquals(1, withOverview.overviews.size)
    }

    private fun assertNotNullResult(result: BookSearchContext?) {
        assertTrue("expected a finding, got null", result != null)
        assertTrue(result!!.summary.isNotBlank())
    }
}
