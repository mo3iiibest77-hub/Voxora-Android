package com.voxora.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The library's rules, as pure functions.
 *
 * These are the assertions that make the persistent library safe to build on: a second import must
 * not disturb the first book, a book that is not there must make a save a no-op rather than a
 * resurrection, and the "continue reading" book must be a decision rather than whatever order the
 * list happens to be in.
 */
class ReaderLibraryTest {

    private fun book(
        id: String,
        currentChunk: Int = 0,
        state: ReaderBookState = ReaderBookState.NOT_STARTED,
        importedAt: Long = 1_000L,
        lastReadAt: Long = 1_000L,
        chunkCount: Int = 100,
    ) = ReaderBook(
        id = id,
        localPath = "/data/reader/books/$id.pdf",
        title = "Book $id",
        sourceType = ReaderSourceType.PDF,
        chunkCount = chunkCount,
        currentChunk = currentChunk,
        state = state,
        importedAt = importedAt,
        lastReadAt = lastReadAt,
        metadata = null,
        lookup = MetadataLookupState.NONE,
        signals = null,
    )

    @Test
    fun importingASecondBookLeavesTheFirstOnesProgressAlone() {
        val first = book("a", currentChunk = 40, state = ReaderBookState.IN_PROGRESS, lastReadAt = 5_000L)
        val library = ReaderLibrary.upsert(emptyList(), first)
        val withSecond = ReaderLibrary.upsert(library, book("b", importedAt = 6_000L))

        val survived = ReaderLibrary.find(withSecond, "a")!!
        assertEquals(40, survived.currentChunk)
        assertEquals(ReaderBookState.IN_PROGRESS, survived.state)
        assertEquals(5_000L, survived.lastReadAt)
        assertEquals(2, withSecond.size)
    }

    @Test
    fun upsertReplacesARecordRatherThanDuplicatingIt() {
        val library = ReaderLibrary.upsert(listOf(book("a")), book("a", currentChunk = 7))
        assertEquals(1, library.size)
        assertEquals(7, library.single().currentChunk)
    }

    @Test
    fun findToleratesAMissingOrNullId() {
        val library = listOf(book("a"))
        assertNull(ReaderLibrary.find(library, null))
        assertNull(ReaderLibrary.find(library, "nope"))
        assertEquals("a", ReaderLibrary.find(library, "a")!!.id)
    }

    @Test
    fun aPositionSaveOnlyTouchesItsOwnBook() {
        val library = listOf(book("a", currentChunk = 10), book("b", currentChunk = 20))
        val updated = ReaderLibrary.withPosition(library, "b", chunk = 55, atMillis = 9_000L)
        assertEquals(10, ReaderLibrary.find(updated, "a")!!.currentChunk)
        assertEquals(55, ReaderLibrary.find(updated, "b")!!.currentChunk)
        assertEquals(9_000L, ReaderLibrary.find(updated, "b")!!.lastReadAt)
    }

    @Test
    fun aSaveArrivingAfterDeletionIsANoOpRatherThanAResurrection() {
        val library = listOf(book("a"))
        val afterRemove = ReaderLibrary.remove(library, "a")
        assertTrue(afterRemove.isEmpty())
        // The stale write must not bring the book back.
        assertTrue(ReaderLibrary.withPosition(afterRemove, "a", 3, 9_000L).isEmpty())
        assertTrue(ReaderLibrary.touched(afterRemove, "a", 9_000L).isEmpty())
        assertTrue(ReaderLibrary.completed(afterRemove, "a", 9_000L).isEmpty())
        assertTrue(ReaderLibrary.reopened(afterRemove, "a", 9_000L).isEmpty())
    }

    @Test
    fun anUnchangedUpdateReturnsTheSameList() {
        // The repository writes only when the reducer says something changed; identity is how it
        // knows, so a no-op must not allocate a new list.
        val library = listOf(book("a", currentChunk = 4))
        assertSame(library, ReaderLibrary.touched(library, "missing", 9_000L))
    }

    @Test
    fun completingAndReopeningAreBothReachableThroughTheLibrary() {
        val library = listOf(book("a", currentChunk = 99, state = ReaderBookState.IN_PROGRESS))
        val done = ReaderLibrary.completed(library, "a", 9_000L)
        assertTrue(ReaderLibrary.find(done, "a")!!.isCompleted)

        val restarted = ReaderLibrary.reopened(done, "a", 10_000L)
        assertEquals(0, ReaderLibrary.find(restarted, "a")!!.currentChunk)
        assertEquals(ReaderBookState.IN_PROGRESS, ReaderLibrary.find(restarted, "a")!!.state)
    }

    @Test
    fun metadataAndLookupStateAreRecordedPerBook() {
        val library = listOf(book("a"), book("b"))
        val metadata = BookMetadata(
            provider = MetadataProvider.OPEN_LIBRARY,
            providerId = "/works/OL1W",
            title = "Matched Title",
            subtitle = null,
            authors = listOf("An Author"),
            publisher = null,
            publishedDate = "1976",
            description = null,
            categories = emptyList(),
            language = null,
            pageCount = null,
            isbn10 = null,
            isbn13 = null,
            coverUrl = null,
            confidence = MatchConfidence.TITLE_AUTHOR,
            fetchedAt = 1L,
        )
        val withMeta = ReaderLibrary.withMetadata(library, "b", metadata)
        assertNull(ReaderLibrary.find(withMeta, "a")!!.metadata)
        assertEquals("Matched Title", ReaderLibrary.find(withMeta, "b")!!.title)
        assertEquals(MetadataLookupState.FOUND, ReaderLibrary.find(withMeta, "b")!!.lookup)

        val withState = ReaderLibrary.withLookup(withMeta, "a", MetadataLookupState.AMBIGUOUS)
        assertEquals(MetadataLookupState.AMBIGUOUS, ReaderLibrary.find(withState, "a")!!.lookup)
    }

    @Test
    fun theMostRecentBookIsTheOneTheReaderWasLastIn() {
        val library = listOf(
            book("a", currentChunk = 5, state = ReaderBookState.IN_PROGRESS, lastReadAt = 3_000L),
            book("b", currentChunk = 5, state = ReaderBookState.IN_PROGRESS, lastReadAt = 9_000L),
            book("c", lastReadAt = 1_000L),
        )
        assertEquals("b", ReaderLibrary.mostRecent(library)!!.id)
        assertNull(ReaderLibrary.mostRecent(emptyList()))
    }

    @Test
    fun aRecencyTieIsBrokenStablyRatherThanByListOrder() {
        // Two books touched at the same instant: the tie is broken by import time, then by id, so
        // the answer does not depend on how the list happens to be stored.
        val earlierImport = book("z", importedAt = 1_000L, lastReadAt = 5_000L)
        val laterImport = book("a", importedAt = 2_000L, lastReadAt = 5_000L)
        assertEquals("a", ReaderLibrary.mostRecent(listOf(earlierImport, laterImport))!!.id)
        assertEquals("a", ReaderLibrary.mostRecent(listOf(laterImport, earlierImport))!!.id)
    }

    @Test
    fun theLibraryListsMostRecentlyReadFirst() {
        val library = listOf(
            book("old", lastReadAt = 1_000L),
            book("newest", lastReadAt = 9_000L),
            book("middle", lastReadAt = 5_000L),
        )
        assertEquals(listOf("newest", "middle", "old"), ReaderLibrary.byRecency(library).map { it.id })
    }

    @Test
    fun removingIsIdempotent() {
        val library = listOf(book("a"), book("b"))
        assertEquals(listOf("b"), ReaderLibrary.remove(library, "a").map { it.id })
        assertEquals(listOf("b"), ReaderLibrary.remove(ReaderLibrary.remove(library, "a"), "a").map { it.id })
    }

    // ---- independent resume -----------------------------------------------------------------

    @Test
    fun twoBooksKeepIndependentResumePointsAcrossAlternatingSaves() {
        var library = listOf(
            book("a", state = ReaderBookState.NOT_STARTED, importedAt = 1_000L),
            book("b", state = ReaderBookState.NOT_STARTED, importedAt = 2_000L),
        )
        // The reader hears Book A up to its fourth chunk, switches to Book B and stops on its third,
        // then comes back to Book A for one more chunk.
        library = ReaderLibrary.withPosition(library, "a", chunk = 3, atMillis = 5_000L)
        library = ReaderLibrary.withPosition(library, "b", chunk = 2, atMillis = 6_000L)
        library = ReaderLibrary.withPosition(library, "a", chunk = 4, atMillis = 7_000L)

        // Each book resumes exactly where it was left, and neither save moved the other's position.
        assertEquals(4, ReaderLibrary.find(library, "a")!!.currentChunk)
        assertEquals(2, ReaderLibrary.find(library, "b")!!.currentChunk)
        assertEquals(ReaderBookState.IN_PROGRESS, ReaderLibrary.find(library, "a")!!.state)
        assertEquals(ReaderBookState.IN_PROGRESS, ReaderLibrary.find(library, "b")!!.state)
    }

    @Test
    fun eachBooksResumePointSurvivesTheStoredLibraryExactly() {
        var library = ReaderLibrary.upsert(emptyList(), book("a", chunkCount = 7))
        library = ReaderLibrary.upsert(library, book("b", chunkCount = 9))
        library = ReaderLibrary.withPosition(library, "a", chunk = 3, atMillis = 5_000L)
        library = ReaderLibrary.withPosition(library, "b", chunk = 2, atMillis = 6_000L)

        val restored = ReaderBookCodec.decode(ReaderBookCodec.encode(library))

        // Resume names the exact logical chunk of each book: not the first chunk, and not the chunk
        // of whichever book happened to be read last.
        assertEquals(3, ReaderLibrary.find(restored, "a")!!.currentChunk)
        assertEquals(2, ReaderLibrary.find(restored, "b")!!.currentChunk)
        // The position the UI shows for each is its own, one-based.
        assertEquals(4, ReaderLibrary.find(restored, "a")!!.displayChunk)
        assertEquals(3, ReaderLibrary.find(restored, "b")!!.displayChunk)
    }

    @Test
    fun aBookThatWasNeverNarratedHasNothingToResume() {
        // Continue must not offer a position for a book the reader has not started, so an import
        // stays NOT_STARTED until a chunk is actually reached.
        val imported = book("a", state = ReaderBookState.NOT_STARTED)
        assertTrue(!imported.hasResumePoint)
        assertEquals(0, imported.currentChunk)
    }
}
