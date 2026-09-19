package com.voxora.core.reader

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract for the Open Library fallback.
 *
 * The two conversions this source owns — a MARC language code and a cover *id* — are asserted
 * directly, because getting either wrong would leak a raw `"eng"` or a broken image into the UI.
 * The failure classification is asserted too: the fallback must fail the same way the primary does.
 */
class OpenLibrarySourceTest {

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

    private fun base() = server.url("/").toString().trimEnd('/')

    private fun source() = OpenLibrarySource(OkHttpClient(), base())

    private fun signals(
        isbn13: String? = null,
        title: String? = "The Selfish Gene",
        author: String? = null,
    ) = BookSignals(isbn13 = isbn13, isbn10 = null, title = title, author = author, filename = "book.pdf")

    private fun enqueue(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    // ---- the query ------------------------------------------------------------------

    @Test
    fun anIsbnGoesThroughTheDedicatedIdentifierParameter() {
        val url = source().urlFor(signals(isbn13 = "9780306406157"))!!.toHttpUrl()
        assertEquals("/search.json", url.encodedPath)
        assertEquals("9780306406157", url.queryParameter("isbn"))
        // The title must not also be sent: the identifier is the whole query.
        assertNull(url.queryParameter("q"))
    }

    @Test
    fun aTitleAndAuthorBecomeOneFreeTextQuery() {
        val url = source().urlFor(signals(author = "Richard Dawkins"))!!.toHttpUrl()
        assertEquals("The Selfish Gene Richard Dawkins", url.queryParameter("q"))
    }

    @Test
    fun aTitleAloneIsStillAsked() {
        val url = source().urlFor(signals())!!.toHttpUrl()
        assertEquals("The Selfish Gene", url.queryParameter("q"))
    }

    @Test
    fun thereIsNoQueryWhenThereIsNothingToAskAbout() {
        assertNull(source().urlFor(signals(title = null)))
        assertNull(source().urlFor(signals(title = "Ab")))
    }

    @Test
    fun theRequestAsksOnlyForTheFieldsTheParserReads() {
        val url = source().urlFor(signals())!!.toHttpUrl()
        val fields = url.queryParameter("fields")!!
        for (field in listOf("key", "title", "author_name", "first_publish_year", "isbn", "cover_i")) {
            assertTrue("fields must include $field", fields.contains(field))
        }
        assertEquals("5", url.queryParameter("limit"))
    }

    // ---- parsing --------------------------------------------------------------------

    @Test
    fun aDocumentIsParsedIntoACandidate() = runBlocking {
        enqueue(200, DOCS)
        val candidate = (source().search(signals()) as MetadataResult.Found).candidates.single()

        assertEquals(MetadataProvider.OPEN_LIBRARY, candidate.provider)
        assertEquals("/works/OL123W", candidate.providerId)
        assertEquals("The Selfish Gene", candidate.title)
        assertEquals("40th Anniversary Edition", candidate.subtitle)
        assertEquals(listOf("Richard Dawkins"), candidate.authors)
        assertEquals("Oxford University Press", candidate.publisher)
        assertEquals("1976", candidate.publishedDate)
        assertEquals(listOf("Science", "Evolution"), candidate.categories)
        assertEquals("en", candidate.language)
        assertEquals(360, candidate.pageCount)
        assertEquals("0198788606", candidate.isbn10)
        assertEquals("9780198788607", candidate.isbn13)
    }

    @Test
    fun aCoverIdBecomesTheDocumentedCoverUrl() = runBlocking {
        enqueue(200, DOCS)
        val candidate = (source().search(signals()) as MetadataResult.Found).candidates.single()
        assertEquals("https://covers.openlibrary.org/b/id/12345-M.jpg", candidate.coverUrl)
    }

    @Test
    fun thereIsNoSynopsisBecauseTheSearchIndexHasNone() = runBlocking {
        // A description Voxora made up would be indistinguishable from the publisher's own text.
        enqueue(200, DOCS)
        val candidate = (source().search(signals()) as MetadataResult.Found).candidates.single()
        assertNull(candidate.description)
    }

    @Test
    fun anUnknownLanguageIsDroppedRatherThanShownAsACode() = runBlocking {
        enqueue(200, """{"docs":[{"title":"A Title","language":["zzz"]}]}""")
        val candidate = (source().search(signals()) as MetadataResult.Found).candidates.single()
        assertNull(candidate.language)
    }

    @Test
    fun marcCodesBecomeTheIsoCodesTheAppSpeaks() {
        assertEquals("en", OpenLibrarySource.normalizeLanguage("eng"))
        assertEquals("fa", OpenLibrarySource.normalizeLanguage("per"))
        assertEquals("de", OpenLibrarySource.normalizeLanguage("ger"))
        // Already ISO-639-1: unchanged.
        assertEquals("en", OpenLibrarySource.normalizeLanguage("en"))
        // Unrecognised codes are refused rather than displayed.
        assertNull(OpenLibrarySource.normalizeLanguage("zzz"))
        assertNull(OpenLibrarySource.normalizeLanguage(null))
        assertNull(OpenLibrarySource.normalizeLanguage("  "))
    }

    @Test
    fun anEmptyDocsArrayIsNoCandidatesRatherThanAFailure() = runBlocking {
        enqueue(200, """{"numFound":0,"docs":[]}""")
        assertEquals(MetadataResult.Found(emptyList()), source().search(signals()))
    }

    @Test
    fun aDocumentWithoutATitleIsSkipped() = runBlocking {
        enqueue(200, """{"docs":[{"key":"/works/OL1W","author_name":["Nobody"]}]}""")
        assertEquals(MetadataResult.Found(emptyList()), source().search(signals()))
    }

    @Test
    fun aMissingPageCountAndCoverStayMissing() = runBlocking {
        enqueue(200, """{"docs":[{"title":"A Title"}]}""")
        val candidate = (source().search(signals()) as MetadataResult.Found).candidates.single()
        assertNull(candidate.pageCount)
        assertNull(candidate.coverUrl)
        assertNull(candidate.publishedDate)
        assertEquals(emptyList<String>(), candidate.categories)
    }

    @Test
    fun theFirstPublicationYearIsStoredAsTheCatalogueStatesIt() = runBlocking {
        // It is the *first* publication of the work, which is what the catalogue actually says.
        enqueue(200, """{"docs":[{"title":"A Title","first_publish_year":1976}]}""")
        val candidate = (source().search(signals()) as MetadataResult.Found).candidates.single()
        assertEquals("1976", candidate.publishedDate)
    }

    // ---- failure classification -------------------------------------------------------

    @Test
    fun aQuotaRejectionIsNotABrokenNetwork() = runBlocking {
        enqueue(429, """{"error":"rate limited"}""")
        assertEquals(
            MetadataResult.Unavailable(MetadataUnavailable.RATE_LIMITED),
            source().search(signals()),
        )
    }

    @Test
    fun aServerErrorIsItsOwnKindOfFailure() = runBlocking {
        enqueue(500, "boom")
        assertEquals(
            MetadataResult.Unavailable(MetadataUnavailable.SERVER_ERROR),
            source().search(signals()),
        )
    }

    @Test
    fun aBodyThatCannotBeReadIsNotNoSuchBook() = runBlocking {
        enqueue(200, "<html>not json</html>")
        assertEquals(
            MetadataResult.Unavailable(MetadataUnavailable.PARSE_ERROR),
            source().search(signals()),
        )
    }

    @Test
    fun aDocumentWithNothingToAskAboutIsNotFoundWithoutAnyRequest() = runBlocking {
        assertEquals(MetadataResult.NotFound, source().search(signals(title = null)))
        assertEquals(0, server.requestCount)
    }

    private companion object {
        val DOCS = """
            {
              "numFound": 1,
              "docs": [
                {
                  "key": "/works/OL123W",
                  "title": "The Selfish Gene",
                  "subtitle": "40th Anniversary Edition",
                  "author_name": ["Richard Dawkins"],
                  "first_publish_year": 1976,
                  "publisher": ["Oxford University Press", "Another Press"],
                  "subject": ["Science", "Evolution"],
                  "isbn": ["0198788606", "9780198788607"],
                  "cover_i": 12345,
                  "number_of_pages_median": 360,
                  "language": ["eng"]
                }
              ]
            }
        """.trimIndent()
    }
}
