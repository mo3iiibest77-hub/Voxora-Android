package com.voxora.core.reader

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
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
 * Contract for the Google Books volume lookup.
 *
 * Driven against a local [MockWebServer], so nothing here reaches Google. What is pinned is the
 * query the document's signals produce, the parse of a real `volumes.list` shape, and the
 * honest-failure behaviour: a quota rejection must not look like a broken network, and a body we
 * cannot read must not look like "no such book".
 */
class GoogleBooksSourceTest {

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

    private fun source(client: OkHttpClient = OkHttpClient()) = GoogleBooksSource(client, base())

    private fun signals(
        isbn13: String? = null,
        isbn10: String? = null,
        title: String? = "The Selfish Gene",
        author: String? = null,
    ) = BookSignals(isbn13 = isbn13, isbn10 = isbn10, title = title, author = author, filename = "book.pdf")

    private fun enqueue(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    // ---- the query ------------------------------------------------------------------

    @Test
    fun anIsbnIsSearchedByIdentifierAndNothingElse() {
        assertEquals(
            "isbn:9780306406157",
            source().queryFor(signals(isbn13 = "9780306406157", author = "Someone")),
        )
    }

    @Test
    fun anIsbnTenIsSearchedAsPrinted() {
        // The catalogue indexes both forms, so the printed one is the right thing to send.
        assertEquals("isbn:0306406152", source().queryFor(signals(isbn10 = "0306406152")))
    }

    @Test
    fun aTitleAndAuthorBecomeFieldedTerms() {
        assertEquals(
            """intitle:"The Selfish Gene" inauthor:"Richard Dawkins"""",
            source().queryFor(signals(author = "Richard Dawkins")),
        )
    }

    @Test
    fun aTitleAloneIsStillFielded() {
        assertEquals("""intitle:"The Selfish Gene"""", source().queryFor(signals()))
    }

    @Test
    fun thereIsNoQueryWhenThereIsNothingToAskAbout() {
        assertNull(source().queryFor(signals(title = null)))
        // A one- or two-character "title" would match anything.
        assertNull(source().queryFor(signals(title = "Ab")))
        assertNull(source().queryFor(signals(title = "   ")))
    }

    @Test
    fun theRequestIsBoundedAndIdentifiesItself() = runBlocking {
        enqueue(200, """{"totalItems":0}""")
        source().search(signals(author = "Richard Dawkins"))

        val request = server.takeRequest()
        assertEquals("/books/v1/volumes", request.path!!.substringBefore('?'))
        assertEquals("""intitle:"The Selfish Gene" inauthor:"Richard Dawkins"""", request.requestUrl!!.queryParameter("q"))
        assertEquals("5", request.requestUrl!!.queryParameter("maxResults"))
        assertEquals("books", request.requestUrl!!.queryParameter("printType"))
        assertEquals("application/json", request.getHeader("Accept"))
        assertTrue(request.getHeader("User-Agent")!!.startsWith("Voxora-Android/"))
    }

    // ---- parsing --------------------------------------------------------------------

    @Test
    fun aVolumeIsParsedIntoACandidate() = runBlocking {
        enqueue(200, VOLUME)
        val result = source().search(signals()) as MetadataResult.Found
        val candidate = result.candidates.single()

        assertEquals(MetadataProvider.GOOGLE_BOOKS, candidate.provider)
        assertEquals("vol-1", candidate.providerId)
        assertEquals("The Selfish Gene", candidate.title)
        assertEquals("40th Anniversary Edition", candidate.subtitle)
        assertEquals(listOf("Richard Dawkins"), candidate.authors)
        assertEquals("Oxford University Press", candidate.publisher)
        assertEquals("1976", candidate.publishedDate)
        assertEquals("A synopsis.", candidate.description)
        assertEquals(listOf("Science", "Life Sciences"), candidate.categories)
        assertEquals("en", candidate.language)
        assertEquals(360, candidate.pageCount)
        assertEquals("0198788606", candidate.isbn10)
        assertEquals("9780198788607", candidate.isbn13)
        assertTrue(candidate.hasSupportingMetadata)
    }

    @Test
    fun aCleartextCoverUrlIsUpgradedToHttps() = runBlocking {
        // Android blocks cleartext traffic, so an http cover would simply never load.
        enqueue(200, VOLUME)
        val candidate = (source().search(signals()) as MetadataResult.Found).candidates.single()
        assertEquals("https://books.google.com/books/content?id=vol-1", candidate.coverUrl)
    }

    @Test
    fun anAbsentItemsArrayIsNoCandidatesRatherThanAFailure() = runBlocking {
        // The API omits `items` entirely when nothing matched.
        enqueue(200, """{"totalItems":0}""")
        assertEquals(MetadataResult.Found(emptyList()), source().search(signals()))
    }

    @Test
    fun aVolumeWithoutATitleIsSkippedRatherThanAttachedToSomething() = runBlocking {
        enqueue(200, """{"items":[{"id":"x","volumeInfo":{"authors":["Nobody"]}}]}""")
        assertEquals(MetadataResult.Found(emptyList()), source().search(signals()))
    }

    @Test
    fun aVolumeWithNoImageLinksHasNoCover() = runBlocking {
        enqueue(200, """{"items":[{"id":"x","volumeInfo":{"title":"A Title"}}]}""")
        val candidate = (source().search(signals()) as MetadataResult.Found).candidates.single()
        assertNull(candidate.coverUrl)
        assertNull(candidate.pageCount)
        assertEquals(emptyList<String>(), candidate.authors)
    }

    // ---- failure classification -------------------------------------------------------

    @Test
    fun aQuotaRejectionIsNotABrokenNetwork() = runBlocking {
        enqueue(429, """{"error":{"message":"quota"}}""")
        assertEquals(
            MetadataResult.Unavailable(MetadataUnavailable.RATE_LIMITED),
            source().search(signals()),
        )
    }

    @Test
    fun aServerErrorIsItsOwnKindOfFailure() = runBlocking {
        enqueue(503, "unavailable")
        assertEquals(
            MetadataResult.Unavailable(MetadataUnavailable.SERVER_ERROR),
            source().search(signals()),
        )
    }

    @Test
    fun aBodyThatCannotBeReadIsNotNoSuchBook() = runBlocking {
        enqueue(200, "<html><body>not json</body></html>")
        assertEquals(
            MetadataResult.Unavailable(MetadataUnavailable.PARSE_ERROR),
            source().search(signals()),
        )
    }

    @Test
    fun anEmptyBodyIsNothingFoundRatherThanAParseFailure() = runBlocking {
        enqueue(200, "")
        assertEquals(MetadataResult.NotFound, source().search(signals()))
    }

    @Test
    fun aSlowCatalogueIsReportedAsATimeout() = runBlocking {
        server.enqueue(MockResponse().setBody("{}").setBodyDelay(3, TimeUnit.SECONDS))
        val impatient = OkHttpClient.Builder()
            .readTimeout(150, TimeUnit.MILLISECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
        assertEquals(
            MetadataResult.Unavailable(MetadataUnavailable.TIMEOUT),
            GoogleBooksSource(impatient, base()).search(signals()),
        )
    }

    @Test
    fun anUnreachableCatalogueIsReportedAsOffline() = runBlocking {
        val offline = GoogleBooksSource(OkHttpClient(), "http://127.0.0.1:1")
        assertEquals(
            MetadataResult.Unavailable(MetadataUnavailable.OFFLINE),
            offline.search(signals()),
        )
    }

    @Test
    fun anUnusableBaseUrlIsAFailureRatherThanACrash() = runBlocking {
        assertEquals(
            MetadataResult.Unavailable(MetadataUnavailable.SERVER_ERROR),
            GoogleBooksSource(OkHttpClient(), "not a url").search(signals()),
        )
    }

    @Test
    fun aDocumentWithNothingToAskAboutIsNotFoundWithoutAnyRequest() = runBlocking {
        assertEquals(MetadataResult.NotFound, source().search(signals(title = null)))
        assertEquals(0, server.requestCount)
    }

    private companion object {
        val VOLUME = """
            {
              "totalItems": 1,
              "items": [
                {
                  "id": "vol-1",
                  "volumeInfo": {
                    "title": "The Selfish Gene",
                    "subtitle": "40th Anniversary Edition",
                    "authors": ["Richard Dawkins"],
                    "publisher": "Oxford University Press",
                    "publishedDate": "1976",
                    "description": "A synopsis.",
                    "categories": ["Science", "Life Sciences"],
                    "language": "en",
                    "pageCount": 360,
                    "industryIdentifiers": [
                      {"type": "ISBN_10", "identifier": "0198788606"},
                      {"type": "ISBN_13", "identifier": "9780198788607"}
                    ],
                    "imageLinks": {"thumbnail": "http://books.google.com/books/content?id=vol-1"}
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
