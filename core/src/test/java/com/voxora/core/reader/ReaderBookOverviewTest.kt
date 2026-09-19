package com.voxora.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The record-level rules behind the persistent library: the cached AI overview, and the state a
 * book takes when its document copy can no longer be found.
 *
 * Both are about **not destroying what the reader has**: an overview already paid for survives a
 * language change, and a missing file never costs the reader their saved position or their cached
 * book information.
 */
class ReaderBookOverviewTest {

    private fun book(
        id: String = "book-1",
        title: String = "The Selfish Gene",
        state: ReaderBookState = ReaderBookState.IN_PROGRESS,
        currentChunk: Int = 72,
        chunkCount: Int = 200,
        metadata: BookMetadata? = metadata(),
        overviews: List<BookIntelOverview> = emptyList(),
    ) = ReaderBook(
        id = id,
        localPath = "/data/reader/books/$id.pdf",
        title = title,
        sourceType = ReaderSourceType.PDF,
        chunkCount = chunkCount,
        currentChunk = currentChunk,
        state = state,
        importedAt = 1_000L,
        lastReadAt = 2_000L,
        metadata = metadata,
        lookup = MetadataLookupState.FOUND,
        signals = null,
        overviews = overviews,
    )

    private fun metadata() = BookMetadata(
        provider = MetadataProvider.GOOGLE_BOOKS,
        providerId = "abc",
        title = "The Selfish Gene",
        subtitle = null,
        authors = listOf("Richard Dawkins"),
        publisher = "Oxford University Press",
        publishedDate = "1976",
        description = "A landmark work on evolutionary biology.",
        categories = listOf("Science"),
        language = "en",
        pageCount = 224,
        isbn10 = null,
        isbn13 = null,
        coverUrl = null,
        confidence = MatchConfidence.TITLE_AUTHOR,
        fetchedAt = 1_000L,
    )

    private fun overview(
        language: String,
        text: String = "متن",
        at: Long = 10L,
        themes: List<String> = emptyList(),
    ) = BookIntelOverview(
        language = language,
        text = text,
        model = "m",
        generatedAt = at,
        themes = themes,
    )

    // ---- the cached overview ----------------------------------------------------------------

    @Test
    fun overviewForIsLanguageAware() {
        val book = book(overviews = listOf(overview("fa"), overview("en")))
        assertEquals("fa", book.overviewFor("fa")?.language)
        assertEquals("fa", book.overviewFor("FA")?.language)
        assertNull(book.overviewFor("de"))
    }

    @Test
    fun withOverviewReplacesTheSameLanguageAndKeepsTheOthers() {
        val book = book(overviews = listOf(overview("fa", "قدیمی", at = 1L), overview("en", "old", at = 2L)))
        val updated = book.withOverview(overview("fa", "جدید", at = 3L))
        assertEquals(2, updated.overviews.size)
        assertEquals("جدید", updated.overviewFor("fa")?.text)
        assertEquals("old", updated.overviewFor("en")?.text)
    }

    @Test
    fun withOverviewKeepsTheCacheBounded() {
        var book = book()
        for (language in listOf("fa", "en", "fr", "de")) {
            book = book.withOverview(overview(language, at = language.hashCode().toLong()))
        }
        assertEquals(BookIntelOverviewPrompt.MAX_CACHED, book.overviews.size)
    }

    @Test
    fun theReuseDecisionSkipsGenerationOnAHitAndRunsOnAMiss() {
        // The repository's exact predicate, `isUsableFor(book.overviewFor(language), language)`: a
        // usable overview for the requested language means there is nothing to generate, so a
        // language the reader has already paid for is never paid for twice.
        val cached = book(overviews = listOf(overview("fa")))
        assertTrue(BookIntelOverviewPrompt.isUsableFor(cached.overviewFor("fa"), "fa"))

        // A language that was never generated has no usable overview, so generation runs for it.
        assertFalse(BookIntelOverviewPrompt.isUsableFor(cached.overviewFor("en"), "en"))

        // An entry whose text is blank is a miss as well, rather than a cached "nothing".
        val blank = book(overviews = listOf(overview("en", text = "   ")))
        assertFalse(BookIntelOverviewPrompt.isUsableFor(blank.overviewFor("en"), "en"))
    }

    @Test
    fun storingAnOverviewTouchesNothingElse() {
        val before = book()
        val after = before.withOverview(overview("fa"))
        assertEquals(before.id, after.id)
        assertEquals(before.localPath, after.localPath)
        assertEquals(before.title, after.title)
        assertEquals(before.currentChunk, after.currentChunk)
        assertEquals(before.state, after.state)
        assertEquals(before.metadata, after.metadata)
    }

    // ---- the unavailable state --------------------------------------------------------------

    @Test
    fun unavailableKeepsTheReadersProgressAndInformation() {
        val before = book()
        val after = before.unavailable()
        assertEquals(ReaderBookState.UNAVAILABLE, after.state)
        assertTrue(after.isUnavailable)
        // Nothing else may be lost: the file may come back, and the progress is the reader's.
        assertEquals(before.currentChunk, after.currentChunk)
        assertEquals(before.chunkCount, after.chunkCount)
        assertEquals(before.metadata, after.metadata)
        assertEquals(before.title, after.title)
        assertEquals(before.localPath, after.localPath)
    }

    @Test
    fun unavailableIsIdempotent() {
        val once = book().unavailable()
        assertTrue(once === once.unavailable())
    }

    @Test
    fun anUnavailableBookOffersNoResume() {
        assertTrue(book().hasResumePoint)
        assertFalse(book().unavailable().hasResumePoint)
    }

    @Test
    fun anUnavailableBookStillRoundTripsThroughItsId() {
        assertEquals(ReaderBookState.UNAVAILABLE, ReaderBookState.normalize("unavailable"))
        assertEquals(ReaderBookState.UNAVAILABLE, ReaderBookState.normalize("UNAVAILABLE"))
        // An unknown value is still not allowed to break the library.
        assertEquals(ReaderBookState.NOT_STARTED, ReaderBookState.normalize("something-new"))
    }

    // ---- the library reducers ---------------------------------------------------------------

    @Test
    fun libraryOverviewAndUnavailableTargetOnlyTheNamedBook() {
        val first = book(id = "a")
        val second = book(id = "b")
        val withOverview = ReaderLibrary.withOverview(listOf(first, second), "a", overview("fa"))
        assertEquals(1, withOverview.first { it.id == "a" }.overviews.size)
        assertTrue(withOverview.first { it.id == "b" }.overviews.isEmpty())

        val marked = ReaderLibrary.markedUnavailable(listOf(first, second), "b")
        assertFalse(marked.first { it.id == "a" }.isUnavailable)
        assertTrue(marked.first { it.id == "b" }.isUnavailable)
        // The other book's position is untouched by either operation.
        assertEquals(72, marked.first { it.id == "a" }.currentChunk)
    }

    @Test
    fun libraryOperationsOnAnUnknownIdAreNoOps() {
        val books = listOf(book(id = "a"))
        assertEquals(books, ReaderLibrary.withOverview(books, "missing", overview("fa")))
        assertEquals(books, ReaderLibrary.markedUnavailable(books, "missing"))
    }

    // ---- persistence ------------------------------------------------------------------------

    @Test
    fun codecRoundTripsTheCachedOverviews() {
        val original = book(overviews = listOf(overview("fa", "متن فارسی", at = 5L), overview("en", "english", at = 6L)))
        val decoded = ReaderBookCodec.decode(ReaderBookCodec.encode(listOf(original)))
        assertEquals(1, decoded.size)
        val restored = decoded.single()
        assertEquals(2, restored.overviews.size)
        assertEquals("متن فارسی", restored.overviewFor("fa")?.text)
        assertEquals("m", restored.overviewFor("fa")?.model)
        assertEquals(5L, restored.overviewFor("fa")?.generatedAt)
        assertEquals(BookIntelOverviewPrompt.VERSION, restored.overviewFor("fa")?.promptVersion)
        assertEquals("english", restored.overviewFor("en")?.text)
    }

    @Test
    fun codecRoundTripsTheThemesInsideTheirLanguage() {
        val original = book(
            overviews = listOf(
                overview("fa", "متن فارسی", at = 5L, themes = listOf("فرگشت", "زیست‌شناسی")),
                overview("en", "english", at = 6L, themes = listOf("Evolution", "Biology")),
            ),
        )
        val restored = ReaderBookCodec.decode(ReaderBookCodec.encode(listOf(original))).single()
        assertEquals(listOf("فرگشت", "زیست‌شناسی"), restored.overviewFor("fa")?.themes)
        assertEquals(listOf("Evolution", "Biology"), restored.overviewFor("en")?.themes)
    }

    @Test
    fun aLanguageSwitchCanNeverShowTheOtherLanguagesThemes() {
        // The themes live inside the per-language record, so the only headings a given language can
        // show are the ones generated for that language — there is no shared list to leak from.
        val book = book(
            overviews = listOf(
                overview("fa", at = 5L, themes = listOf("فرگشت")),
                overview("en", at = 6L, themes = listOf("Evolution")),
            ),
        )
        assertEquals(listOf("فرگشت"), book.overviewFor("fa")?.themes)
        assertEquals(listOf("Evolution"), book.overviewFor("en")?.themes)
        assertNull(book.overviewFor("de")?.themes)
    }

    @Test
    fun codecTreatsAnEntryWithNoPromptVersionAsStale() {
        // An overview written before the version was persisted: it must not be shown as current.
        val raw = """
            {"version":1,"books":[{
              "id":"book-1","localPath":"/data/reader/books/book-1.pdf","title":"T",
              "sourceType":"pdf","chunkCount":10,"currentChunk":0,"state":"in_progress",
              "importedAt":1,"lastReadAt":1,"lookup":"found",
              "overviews":[{"language":"fa","text":"متن","model":"m","generatedAt":3}]
            }]}
        """.trimIndent()
        val restored = ReaderBookCodec.decode(raw).single()
        val overview = restored.overviewFor("fa")
        assertNotNull(overview)
        assertEquals(0, overview?.promptVersion)
        assertFalse(BookIntelOverviewPrompt.isUsableFor(overview, "fa"))
    }

    @Test
    fun codecRoundTripsAnUnavailableBook() {
        val decoded = ReaderBookCodec.decode(ReaderBookCodec.encode(listOf(book().unavailable())))
        val restored = decoded.single()
        assertEquals(ReaderBookState.UNAVAILABLE, restored.state)
        // The progress survived the round trip, which is the point of not deleting the record.
        assertEquals(72, restored.currentChunk)
        assertNotNull(restored.metadata)
    }

    @Test
    fun codecDecodesARecordWrittenBeforeOverviewsExisted() {
        // A record with no `overviews` field at all: the shape every existing install has on disk.
        val raw = """
            {"version":1,"books":[{
              "id":"book-1","localPath":"/data/reader/books/book-1.pdf","title":"The Selfish Gene",
              "sourceType":"pdf","chunkCount":200,"currentChunk":72,"state":"in_progress",
              "importedAt":1000,"lastReadAt":2000,"lookup":"found"
            }]}
        """.trimIndent()
        val restored = ReaderBookCodec.decode(raw).single()
        assertTrue(restored.overviews.isEmpty())
        assertEquals(72, restored.currentChunk)
    }

    @Test
    fun codecDropsAnOverviewEntryThatCannotBeShown() {
        val raw = """
            {"version":1,"books":[{
              "id":"book-1","localPath":"/data/reader/books/book-1.pdf","title":"T",
              "sourceType":"pdf","chunkCount":10,"currentChunk":0,"state":"in_progress",
              "importedAt":1,"lastReadAt":1,"lookup":"found",
              "overviews":[{"language":"fa"},{"text":"no language"},{"language":"en","text":"english","model":"m","generatedAt":3}]
            }]}
        """.trimIndent()
        val restored = ReaderBookCodec.decode(raw).single()
        assertEquals(1, restored.overviews.size)
        assertEquals("en", restored.overviews.single().language)
    }

    @Test
    fun codecOmitsTheFieldEntirelyWhenThereAreNoOverviews() {
        // Keeps the stored bytes of a record that never generated anything identical to before.
        val encoded = ReaderBookCodec.encode(listOf(book()))
        assertFalse(encoded.contains("overviews"))
    }

    @Test
    fun codecOmitsThemesWhenThereAreNoneAndDecodesTheirAbsenceAsEmpty() {
        // A record written before themes existed must decode to "no themes", which the card reads as
        // a reason to fall back to the catalogue's own headings — never as a failure.
        val encoded = ReaderBookCodec.encode(listOf(book(overviews = listOf(overview("fa")))))
        assertTrue(encoded.contains("overviews"))
        assertFalse(encoded.contains("themes"))
        assertTrue(ReaderBookCodec.decode(encoded).single().overviewFor("fa")!!.themes.isEmpty())

        val raw = """
            {"version":1,"books":[{
              "id":"book-1","localPath":"/data/reader/books/book-1.pdf","title":"T",
              "sourceType":"pdf","chunkCount":10,"currentChunk":0,"state":"in_progress",
              "importedAt":1,"lastReadAt":1,"lookup":"found",
              "overviews":[{"language":"fa","text":"متن","model":"m","generatedAt":3,"promptVersion":2}]
            }]}
        """.trimIndent()
        assertTrue(ReaderBookCodec.decode(raw).single().overviewFor("fa")!!.themes.isEmpty())
    }
}
