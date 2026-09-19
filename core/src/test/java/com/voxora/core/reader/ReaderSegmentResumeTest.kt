package com.voxora.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exact per-book resume, at **segment** granularity.
 *
 * ## The defect these tests exist for
 *
 * Stop used to reset the segment to zero while keeping the chunk, so a reader who stopped on
 * Chunk 3 / Segment 3 and pressed Play again heard Segment 1 of Chunk 3. The chunk was persisted but
 * the segment was not, and the reset happened in memory on the way out. So the two things worth
 * proving are: a position of chunk *and* segment survives the store, and nothing in the reducer
 * moves a segment except an explicit restart of a finished book.
 *
 * ## Why pure JVM
 *
 * Every decision here — what a position write does, what a legacy record decodes to, how a stale
 * save is refused — is a pure function over the library list. Testing it there is what makes the
 * rules verifiable without a device, and it is the same object the repository persists.
 */
class ReaderSegmentResumeTest {

    private fun book(
        id: String,
        chunkCount: Int = 10,
        currentChunk: Int = 0,
        currentSegment: Int = 0,
        state: ReaderBookState = ReaderBookState.NOT_STARTED,
        lastReadAt: Long = 1_000L,
        positionStamp: Long = 0L,
    ) = ReaderBook(
        id = id,
        localPath = "/data/reader/books/$id.pdf",
        title = "Book $id",
        sourceType = ReaderSourceType.PDF,
        chunkCount = chunkCount,
        currentChunk = currentChunk,
        currentSegment = currentSegment,
        positionStamp = positionStamp,
        state = state,
        importedAt = 1_000L,
        lastReadAt = lastReadAt,
        metadata = null,
        lookup = MetadataLookupState.NONE,
        signals = null,
    )

    // ---- the position survives the store --------------------------------------------------------

    @Test
    fun chunkThreeSegmentThreeSurvivesTheStoredLibrary() {
        val saved = ReaderLibrary.withPosition(
            listOf(book("a")),
            id = "a",
            chunk = 3,
            atMillis = 5_000L,
            segment = 3,
            stamp = 1L,
        )
        val restored = ReaderBookCodec.decode(ReaderBookCodec.encode(saved)).single()

        assertEquals(3, restored.currentChunk)
        assertEquals(3, restored.currentSegment)
        assertEquals(ReaderBookState.IN_PROGRESS, restored.state)
    }

    @Test
    fun chunkSevenSegmentSixSurvivesTheStoredLibrary() {
        val saved = ReaderLibrary.withPosition(
            listOf(book("a", chunkCount = 20)),
            id = "a",
            chunk = 7,
            atMillis = 5_000L,
            segment = 6,
            stamp = 1L,
        )
        val restored = ReaderBookCodec.decode(ReaderBookCodec.encode(saved)).single()

        assertEquals(7, restored.currentChunk)
        assertEquals(6, restored.currentSegment)
    }

    @Test
    fun aStopThenPlayKeepsTheExactSegment() {
        // Stop writes the position it was on; Play reads the same record back. The reducer must not
        // move the segment in between — that reset was the bug.
        val listening = book("a", currentChunk = 3, currentSegment = 3, state = ReaderBookState.IN_PROGRESS)
        val afterStop = ReaderLibrary.withPosition(listOf(listening), "a", 3, 5_000L, segment = 3, stamp = 1L)
        assertEquals(3, ReaderLibrary.find(afterStop, "a")!!.currentSegment)

        val afterPlay = ReaderLibrary.withPosition(afterStop, "a", 3, 6_000L, segment = 3, stamp = 2L)
        assertEquals(3, ReaderLibrary.find(afterPlay, "a")!!.currentChunk)
        assertEquals(3, ReaderLibrary.find(afterPlay, "a")!!.currentSegment)
    }

    @Test
    fun onlyRestartingAFinishedBookResetsTheSegment() {
        val finished = book("a", currentChunk = 9, currentSegment = 4, state = ReaderBookState.COMPLETED)

        // Completion is a fact: an ordinary move is refused outright, so it cannot reset anything.
        assertEquals(finished, finished.withPosition(0, 0, atMillis = 9_000L, stamp = 5L))

        // Restarting is the one act that goes back to the beginning, segment included.
        val restarted = finished.reopened(atMillis = 9_000L)
        assertEquals(0, restarted.currentChunk)
        assertEquals(0, restarted.currentSegment)
        assertEquals(ReaderBookState.IN_PROGRESS, restarted.state)
    }

    // ---- independent per book -------------------------------------------------------------------

    @Test
    fun twoBooksKeepTheirOwnSegmentPositions() {
        var library = listOf(book("a", chunkCount = 10), book("b", chunkCount = 10))
        // Book A reaches chunk 3 / segment 3, the reader switches to Book B and stops on chunk 2 /
        // segment 1, then returns to Book A.
        library = ReaderLibrary.withPosition(library, "a", 3, 5_000L, segment = 3, stamp = 1L)
        library = ReaderLibrary.withPosition(library, "b", 2, 6_000L, segment = 1, stamp = 1L)
        library = ReaderLibrary.withPosition(library, "a", 3, 7_000L, segment = 3, stamp = 2L)

        val a = ReaderLibrary.find(library, "a")!!
        val b = ReaderLibrary.find(library, "b")!!
        assertEquals(3, a.currentChunk)
        assertEquals(3, a.currentSegment)
        assertEquals(2, b.currentChunk)
        assertEquals(1, b.currentSegment)

        // And the same holds after a process death.
        val restored = ReaderBookCodec.decode(ReaderBookCodec.encode(library))
        assertEquals(3, ReaderLibrary.find(restored, "a")!!.currentSegment)
        assertEquals(1, ReaderLibrary.find(restored, "b")!!.currentSegment)
    }

    @Test
    fun aSaveForOneBookNeverMovesAnothersSegment() {
        val library = listOf(
            book("a", currentChunk = 3, currentSegment = 3, state = ReaderBookState.IN_PROGRESS),
            book("b", currentChunk = 2, currentSegment = 1, state = ReaderBookState.IN_PROGRESS),
        )
        val updated = ReaderLibrary.withPosition(library, "a", 4, 9_000L, segment = 0, stamp = 1L)
        assertEquals(1, ReaderLibrary.find(updated, "b")!!.currentSegment)
        assertEquals(2, ReaderLibrary.find(updated, "b")!!.currentChunk)
    }

    // ---- migration and bounds -------------------------------------------------------------------

    @Test
    fun aRecordWrittenBeforeSegmentsExistedResumesAtItsChunk() {
        // Exactly what the previous build wrote: a chunk, and no segment field at all.
        val legacy = """{"version":1,"books":[{"id":"a","localPath":"/data/a.pdf",""" +
            """"chunkCount":10,"currentChunk":3,"state":"in_progress","importedAt":1000,"lastReadAt":5000}]}"""
        val restored = ReaderBookCodec.decode(legacy).single()

        assertEquals(3, restored.currentChunk)
        // The old build resumed at the start of the chunk, so the migration must do the same rather
        // than invent a position or lose the book.
        assertEquals(0, restored.currentSegment)
        assertEquals(ReaderBookState.IN_PROGRESS, restored.state)
    }

    @Test
    fun aPersistedSegmentPastTheEndOfItsChunkClampsToTheLastUnit() {
        // A record written against a longer extraction: the chunk still opens, on its last unit.
        assertEquals(3, ReaderPosition.clampSegment(persisted = 9, segmentCount = 4))
        assertEquals(4, ReaderPosition.clampSegment(persisted = 4, segmentCount = 5))
        // A chunk that now has no units at all resolves to the beginning rather than crashing.
        assertEquals(0, ReaderPosition.clampSegment(persisted = 7, segmentCount = 0))
        // A negative value — impossible through the codec, but a clamp must be total.
        assertEquals(0, ReaderPosition.clampSegment(persisted = -3, segmentCount = 5))
    }

    @Test
    fun anAbsurdPersistedSegmentStillDecodesWithoutCrashing() {
        val raw = """{"version":1,"books":[{"id":"a","localPath":"/data/a.pdf",""" +
            """"chunkCount":4,"currentChunk":2,"currentSegment":99999,"state":"in_progress"}]}"""
        val restored = ReaderBookCodec.decode(raw).single()
        assertEquals(2, restored.currentChunk)
        // The codec does not know unit counts, so it stores what it was given; the controller is
        // where the real bound exists. Decoding must simply not fail.
        assertEquals(99999, restored.currentSegment)
        assertEquals(3, ReaderPosition.clampSegment(restored.currentSegment, segmentCount = 4))
    }

    // ---- ordering -------------------------------------------------------------------------------

    @Test
    fun anOlderSaveCannotOverwriteANewerPosition() {
        // Both saves were launched from the same run; the slow one for the earlier segment lands
        // last. It carries an older stamp, so it must be dropped rather than applied.
        var library = listOf(book("a", currentChunk = 3, currentSegment = 3, state = ReaderBookState.IN_PROGRESS))
        library = ReaderLibrary.withPosition(library, "a", 4, 9_000L, segment = 2, stamp = 7L)
        library = ReaderLibrary.withPosition(library, "a", 3, 9_001L, segment = 3, stamp = 6L)

        val book = ReaderLibrary.find(library, "a")!!
        assertEquals(4, book.currentChunk)
        assertEquals(2, book.currentSegment)
        assertEquals(7L, book.positionStamp)
    }

    @Test
    fun aSaveWithoutAnOrderingClaimIsAlwaysApplied() {
        // The stamp is opt-in: a caller that does not participate (the default, and every existing
        // caller) must keep behaving exactly as it did before stamps existed.
        var library = listOf(book("a", currentChunk = 3, currentSegment = 3, state = ReaderBookState.IN_PROGRESS))
        library = ReaderLibrary.withPosition(library, "a", 5, 9_000L, segment = 4, stamp = 9L)
        library = ReaderLibrary.withPosition(library, "a", 2, 9_001L, segment = 1)

        val book = ReaderLibrary.find(library, "a")!!
        assertEquals(2, book.currentChunk)
        assertEquals(1, book.currentSegment)
    }

    @Test
    fun theStampSurvivesTheStoreSoARestartCannotMakeAFreshSaveLookOld() {
        val saved = ReaderLibrary.withPosition(listOf(book("a")), "a", 3, 5_000L, segment = 3, stamp = 12L)
        val restored = ReaderBookCodec.decode(ReaderBookCodec.encode(saved)).single()
        assertEquals(12L, restored.positionStamp)

        // The controller seeds its counter from this, so the next save is 13 and is accepted.
        val next = ReaderLibrary.withPosition(listOf(restored), "a", 3, 6_000L, segment = 4, stamp = 13L)
        assertEquals(4, ReaderLibrary.find(next, "a")!!.currentSegment)
    }

    @Test
    fun aRejectedStaleSaveIsNotAChange() {
        // The repository writes only when the reducer returns a different list; a dropped save must
        // therefore be identity, or every stale write would rewrite the store for nothing.
        val current = book("a", currentChunk = 4, currentSegment = 2, state = ReaderBookState.IN_PROGRESS, positionStamp = 7L)
        val library = listOf(current)
        assertTrue(library === ReaderLibrary.withPosition(library, "a", 1, 9_000L, segment = 0, stamp = 6L))
    }

    @Test
    fun theChunkStillClampsIndependentlyOfTheSegment() {
        val saved = ReaderLibrary.withPosition(listOf(book("a", chunkCount = 4)), "a", 99, 5_000L, segment = 2, stamp = 1L)
        val book = ReaderLibrary.find(saved, "a")!!
        assertEquals(3, book.currentChunk)
        assertEquals(2, book.currentSegment)
        // A chunk clamped down to the end must not have silently kept a segment from the old chunk.
        assertNotEquals(0, book.currentSegment)
    }
}
