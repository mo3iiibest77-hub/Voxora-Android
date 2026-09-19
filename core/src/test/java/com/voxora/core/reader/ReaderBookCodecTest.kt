package com.voxora.core.reader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The on-disk form of the library.
 *
 * Two properties matter more than the round trip itself. First, decoding is **total**: a corrupted
 * or newer record is dropped on its own rather than taking the whole library down with it. Second,
 * "unknown" stays unknown — a missing page count must not come back as the number zero, and a
 * missing author must not come back as the string `"null"`.
 */
class ReaderBookCodecTest {

    private fun book(
        id: String = "book-1",
        chunkCount: Int = 200,
        currentChunk: Int = 72,
        state: ReaderBookState = ReaderBookState.IN_PROGRESS,
        importedAt: Long = 1_000L,
        lastReadAt: Long = 5_000L,
        metadata: BookMetadata? = null,
        lookup: MetadataLookupState = MetadataLookupState.FOUND,
        signals: BookSignals? = null,
    ) = ReaderBook(
        id = id,
        localPath = "/data/reader/books/$id.pdf",
        title = "The Selfish Gene",
        sourceType = ReaderSourceType.PDF,
        chunkCount = chunkCount,
        currentChunk = currentChunk,
        state = state,
        importedAt = importedAt,
        lastReadAt = lastReadAt,
        metadata = metadata,
        lookup = lookup,
        signals = signals,
    )

    private fun metadata(
        description: String? = "A synopsis.",
        pageCount: Int? = 360,
    ) = BookMetadata(
        provider = MetadataProvider.GOOGLE_BOOKS,
        providerId = "vol-1",
        title = "The Selfish Gene",
        subtitle = "40th Anniversary Edition",
        authors = listOf("Richard Dawkins"),
        publisher = "Oxford University Press",
        publishedDate = "1976",
        description = description,
        categories = listOf("Science", "Life Sciences"),
        language = "en",
        pageCount = pageCount,
        isbn10 = "0198788606",
        isbn13 = "9780198788607",
        coverUrl = "https://books.google.com/cover.jpg",
        confidence = MatchConfidence.TITLE_AUTHOR,
        fetchedAt = 9_000L,
    )

    private fun signals() = BookSignals(
        isbn13 = "9780198788607",
        isbn10 = null,
        title = "The Selfish Gene",
        author = "Richard Dawkins",
        filename = "the-selfish-gene.pdf",
    )

    @Test
    fun anEmptyLibraryIsAValidDocument() {
        assertEquals(emptyList<ReaderBook>(), ReaderBookCodec.decode(ReaderBookCodec.encode(emptyList())))
    }

    @Test
    fun aFullRecordSurvivesTheRoundTrip() {
        val original = book(metadata = metadata(), signals = signals())
        val restored = ReaderBookCodec.decode(ReaderBookCodec.encode(listOf(original)))
        assertEquals(listOf(original), restored)
    }

    @Test
    fun aBookWithoutMetadataOrSignalsRoundTripsToo() {
        val original = book(metadata = null, signals = null, lookup = MetadataLookupState.NONE)
        val restored = ReaderBookCodec.decode(ReaderBookCodec.encode(listOf(original))).single()
        assertEquals(original, restored)
        assertNull(restored.metadata)
        assertNull(restored.signals)
    }

    @Test
    fun theStoredDocumentIsVersioned() {
        val json = JSONObject(ReaderBookCodec.encode(emptyList()))
        assertEquals(ReaderBookCodec.VERSION, json.getInt("version"))
        assertTrue(json.has("books"))
    }

    @Test
    fun aVerboseProviderDescriptionIsCappedRatherThanStoredWhole() {
        val long = "x".repeat(ReaderBookCodec.MAX_DESCRIPTION_CHARS + 500)
        val restored = ReaderBookCodec.decode(
            ReaderBookCodec.encode(listOf(book(metadata = metadata(description = long)))),
        ).single()
        assertEquals(ReaderBookCodec.MAX_DESCRIPTION_CHARS, restored.metadata!!.description!!.length)
    }

    @Test
    fun anUnknownPageCountStaysUnknownInsteadOfBecomingZero() {
        val restored = ReaderBookCodec.decode(
            ReaderBookCodec.encode(listOf(book(metadata = metadata(pageCount = null)))),
        ).single()
        assertNull(restored.metadata!!.pageCount)
    }

    @Test
    fun aZeroPageCountIsNotARealPageCount() {
        val restored = ReaderBookCodec.decode(
            ReaderBookCodec.encode(listOf(book(metadata = metadata(pageCount = 0)))),
        ).single()
        assertNull(restored.metadata!!.pageCount)
    }

    @Test
    fun aStaleChunkIndexIsClampedToTheStoredDocument() {
        // A record written against a longer extraction must not point past the end of this one.
        val encoded = ReaderBookCodec.encode(listOf(book(chunkCount = 10, currentChunk = 999)))
        assertEquals(9, ReaderBookCodec.decode(encoded).single().currentChunk)
    }

    @Test
    fun blankAndMalformedInputYieldsAnEmptyLibraryRatherThanAnException() {
        assertEquals(emptyList<ReaderBook>(), ReaderBookCodec.decode(null))
        assertEquals(emptyList<ReaderBook>(), ReaderBookCodec.decode(""))
        assertEquals(emptyList<ReaderBook>(), ReaderBookCodec.decode("   "))
        assertEquals(emptyList<ReaderBook>(), ReaderBookCodec.decode("not json at all"))
        assertEquals(emptyList<ReaderBook>(), ReaderBookCodec.decode("[1,2,3]"))
        assertEquals(emptyList<ReaderBook>(), ReaderBookCodec.decode("""{"version":1}"""))
        assertEquals(emptyList<ReaderBook>(), ReaderBookCodec.decode("""{"version":1,"books":"no"}"""))
    }

    @Test
    fun oneMalformedRecordDoesNotTakeTheRestOfTheLibraryWithIt() {
        val good = ReaderBookCodec.encode(listOf(book(id = "good")))
        // Splice a record with no localPath in beside the valid one.
        val raw = good.replace(
            """"books":[""",
            """"books":[{"id":"broken"},{"id":"also-broken","localPath":""},""",
        )
        val restored = ReaderBookCodec.decode(raw)
        assertEquals(listOf("good"), restored.map { it.id })
    }

    @Test
    fun aRecordWithoutIdentityIsDroppedButOneWithoutOptionalFieldsIsKept() {
        val minimal = """{"version":1,"books":[{"id":"a","localPath":"/data/a.pdf"}]}"""
        val restored = ReaderBookCodec.decode(minimal).single()
        assertEquals("a", restored.id)
        assertEquals(ReaderSourceType.TXT, restored.sourceType)
        assertEquals(ReaderBookState.NOT_STARTED, restored.state)
        assertEquals(MetadataLookupState.NONE, restored.lookup)
        // With no explicit lastReadAt, "last read" is the import time rather than the epoch.
        assertEquals(restored.importedAt, restored.lastReadAt)
    }

    @Test
    fun valuesWrittenByANewerBuildDegradeToTheirDefaults() {
        val future = """{"version":99,"books":[{"id":"a","localPath":"/data/a.pdf",""" +
            """"sourceType":"epub","state":"playing","lookup":"queued","title":"T"}]}"""
        val restored = ReaderBookCodec.decode(future).single()
        assertEquals(ReaderSourceType.TXT, restored.sourceType)
        assertEquals(ReaderBookState.NOT_STARTED, restored.state)
        assertEquals(MetadataLookupState.NONE, restored.lookup)
    }

    @Test
    fun aJsonNullStringDoesNotBecomeTheWordNull() {
        val raw = """{"version":1,"books":[{"id":"a","localPath":"/data/a.pdf","title":null,""" +
            """"signals":{"isbn13":null,"isbn10":null,"title":null,"author":null,"filename":"a.pdf"}}]}"""
        val restored = ReaderBookCodec.decode(raw).single()
        assertEquals("", restored.title)
        assertNull(restored.signals!!.isbn13)
        assertNull(restored.signals!!.author)
    }

    @Test
    fun metadataWithoutAProviderIsNotAttached() {
        // A metadata object that cannot say where it came from is not source-backed, so it is
        // dropped rather than rendered as anonymous fact.
        val raw = """{"version":1,"books":[{"id":"a","localPath":"/data/a.pdf","metadata":{"title":"T"}}]}"""
        assertNull(ReaderBookCodec.decode(raw).single().metadata)
    }
}
