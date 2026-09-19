package com.voxora.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Reader's bounded look-ahead.
 *
 * Preparing the next chunk is the difference between narration that pauses at every chunk boundary
 * and narration that does not. The danger is the opposite one: a look-ahead that grows, or that
 * re-opens a session for an artifact already being produced, multiplies memory and API requests for
 * a benefit the reader never sees. So these tests pin the bound, the end of the book, and the
 * deduplication rule — the three things the controller relies on.
 */
class ReaderPrefetchWindowTest {

    @Test
    fun theNextChunkIsTheOnlyOnePreparedAhead() {
        assertEquals(listOf(1), ReaderPrefetchWindow.ahead(current = 0, chunkCount = 7))
        assertEquals(listOf(4), ReaderPrefetchWindow.ahead(current = 3, chunkCount = 7))
        // One chunk wide by default: the prepared spool may hold ~28 MB, so a wider window would
        // multiply memory for a chunk the reader will not reach for minutes.
        assertEquals(1, ReaderPrefetchWindow.LOOK_AHEAD)
    }

    @Test
    fun theEndOfTheBookPreparesNothing() {
        // The last chunk has no successor, so there is nothing to prepare rather than a request for
        // an index that does not exist.
        assertEquals(emptyList<Int>(), ReaderPrefetchWindow.ahead(current = 6, chunkCount = 7))
        assertEquals(emptyList<Int>(), ReaderPrefetchWindow.ahead(current = 99, chunkCount = 7))
    }

    @Test
    fun anEmptyDocumentPreparesNothing() {
        assertEquals(emptyList<Int>(), ReaderPrefetchWindow.ahead(current = 0, chunkCount = 0))
        assertEquals(emptyList<Int>(), ReaderPrefetchWindow.ahead(current = 0, chunkCount = -3))
    }

    @Test
    fun aNonPositiveLookAheadPreparesNothing() {
        assertEquals(emptyList<Int>(), ReaderPrefetchWindow.ahead(current = 0, chunkCount = 7, lookAhead = 0))
        assertEquals(emptyList<Int>(), ReaderPrefetchWindow.ahead(current = 0, chunkCount = 7, lookAhead = -1))
    }

    @Test
    fun anIndexAlreadyBeingPreparedIsNotPreparedAgain() {
        // A duplicate request would open a second Gemini session and a second spool for one chunk —
        // the same words, paid for twice, with two writers racing to publish it.
        assertEquals(
            emptyList<Int>(),
            ReaderPrefetchWindow.ahead(current = 0, chunkCount = 7, prepared = setOf(1)),
        )
    }

    @Test
    fun onlyTheIndicesThatAreNotAlreadyPreparedAreReturned() {
        assertEquals(
            listOf(1, 3),
            ReaderPrefetchWindow.ahead(current = 0, chunkCount = 7, prepared = setOf(2), lookAhead = 3),
        )
    }

    @Test
    fun theWindowNeverReachesPastTheEndOfTheDocument() {
        assertEquals(
            listOf(5, 6),
            ReaderPrefetchWindow.ahead(current = 4, chunkCount = 7, lookAhead = 5),
        )
    }

    @Test
    fun theWindowIsAlwaysTheChunksImmediatelyAfterTheOnePlaying() {
        // Order matters: the caller prepares the first entry first, so the chunk that will be needed
        // soonest is the one that starts first.
        val ahead = ReaderPrefetchWindow.ahead(current = 2, chunkCount = 7, lookAhead = 3)
        assertEquals(listOf(3, 4, 5), ahead)
        assertTrue(ahead.all { it > 2 && it < 7 })
    }
}
