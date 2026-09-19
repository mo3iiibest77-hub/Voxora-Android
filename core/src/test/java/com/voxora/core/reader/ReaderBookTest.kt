package com.voxora.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The persisted book record.
 *
 * The interesting behaviour here is not the data class but its **rules**: what a position move is
 * allowed to do to a finished book, how progress is derived, and which fields survive a move. These
 * are the rules the resume feature rests on, and they are asserted directly rather than through a
 * device.
 */
class ReaderBookTest {

    private fun book(
        id: String = "book-1",
        chunkCount: Int = 200,
        currentChunk: Int = 0,
        state: ReaderBookState = ReaderBookState.NOT_STARTED,
        importedAt: Long = 1_000L,
        lastReadAt: Long = 1_000L,
        metadata: BookMetadata? = null,
    ) = ReaderBook(
        id = id,
        localPath = "/data/reader/books/$id.pdf",
        title = "A Title",
        sourceType = ReaderSourceType.PDF,
        chunkCount = chunkCount,
        currentChunk = currentChunk,
        state = state,
        importedAt = importedAt,
        lastReadAt = lastReadAt,
        metadata = metadata,
        lookup = MetadataLookupState.NONE,
        signals = null,
    )

    @Test
    fun progressCountsTheChunkBeingReadNotTheOneAlreadyFinished() {
        // Chunk index 72 is the 73rd chunk, so the UI shows "73 of 200" and the bar is at 36.5%.
        val progress = book(currentChunk = 72).progressFraction
        assertEquals(0.365f, progress, 0.0001f)
    }

    @Test
    fun aCompletedBookReportsFullProgressWhateverItsLastIndexWas() {
        assertEquals(
            1f,
            book(currentChunk = 12, state = ReaderBookState.COMPLETED).progressFraction,
            0f,
        )
    }

    @Test
    fun aBookWithNoChunksHasNoProgress() {
        assertEquals(0f, book(chunkCount = 0).progressFraction, 0f)
        assertEquals(0, book(chunkCount = 0).displayChunk)
    }

    @Test
    fun displayChunkIsOneBasedAndClamped() {
        assertEquals(73, book(currentChunk = 72).displayChunk)
        assertEquals(1, book(currentChunk = 0).displayChunk)
        // A stale index from a longer extraction must not render as "chunk 999".
        assertEquals(200, book(currentChunk = 999).displayChunk)
    }

    @Test
    fun thereIsNothingToResumeBeforeNarrationStarts() {
        assertFalse(book(state = ReaderBookState.NOT_STARTED).hasResumePoint)
        assertTrue(book(currentChunk = 5, state = ReaderBookState.IN_PROGRESS).hasResumePoint)
        assertTrue(book(currentChunk = 5, state = ReaderBookState.COMPLETED).hasResumePoint)
        // No chunks means no position, whatever the state claims.
        assertFalse(book(chunkCount = 0, state = ReaderBookState.IN_PROGRESS).hasResumePoint)
    }

    @Test
    fun movingThePositionMarksTheBookInProgressAndStampsRecency() {
        val moved = book().withPosition(9, atMillis = 5_000L)
        assertEquals(9, moved.currentChunk)
        assertEquals(ReaderBookState.IN_PROGRESS, moved.state)
        assertEquals(5_000L, moved.lastReadAt)
    }

    @Test
    fun movingThePositionClampsToTheDocument() {
        assertEquals(0, book().withPosition(-4, 1L).currentChunk)
        assertEquals(199, book().withPosition(10_000, 1L).currentChunk)
    }

    @Test
    fun recencyNeverGoesBackwards() {
        // A save that arrives out of order must not make the book look older than it is.
        val stamped = book(lastReadAt = 9_000L).withPosition(3, atMillis = 4_000L)
        assertEquals(9_000L, stamped.lastReadAt)
    }

    @Test
    fun aFinishedBookRefusesToBeUnCompletedByAStaleSave() {
        val finished = book(currentChunk = 199, state = ReaderBookState.COMPLETED)
        assertSame(finished, finished.withPosition(4, atMillis = 9_000L))
    }

    @Test
    fun reopeningIsTheOneWayBackFromCompletion() {
        val restarted = book(currentChunk = 199, state = ReaderBookState.COMPLETED).reopened(7_000L)
        assertEquals(0, restarted.currentChunk)
        assertEquals(ReaderBookState.IN_PROGRESS, restarted.state)
        assertEquals(7_000L, restarted.lastReadAt)
    }

    @Test
    fun completionKeepsThePositionSoTheBookCanBeListedAsFinished() {
        val done = book(currentChunk = 199).completed(8_000L)
        assertEquals(ReaderBookState.COMPLETED, done.state)
        assertTrue(done.isCompleted)
        assertEquals(199, done.currentChunk)
        assertEquals(8_000L, done.lastReadAt)
    }

    @Test
    fun touchingABookRecordsRecencyWithoutClaimingAPosition() {
        val touched = book(currentChunk = 12, state = ReaderBookState.IN_PROGRESS).touched(6_000L)
        assertEquals(12, touched.currentChunk)
        assertEquals(6_000L, touched.lastReadAt)
    }

    @Test
    fun aMatchedTitleReplacesTheFileNameButAnEmptyOneDoesNot() {
        val named = book().withMetadata(metadata(title = "The Selfish Gene"))
        assertEquals("The Selfish Gene", named.title)
        assertEquals(MetadataLookupState.FOUND, named.lookup)

        // A provider record with a blank title must not blank out the title we already show.
        val blank = book().withMetadata(metadata(title = "   "))
        assertEquals("A Title", blank.title)
    }

    @Test
    fun aFailedLookupIsRecordedWithoutTouchingTheMetadata() {
        val state = book().withLookup(MetadataLookupState.UNAVAILABLE)
        assertEquals(MetadataLookupState.UNAVAILABLE, state.lookup)
        assertEquals(null, state.metadata)
    }

    @Test
    fun sourceTypeIsPersistedByItsOwnIdAndNormalizesTotally() {
        assertEquals(ReaderSourceType.PDF, ReaderSourceType.normalize("pdf"))
        assertEquals(ReaderSourceType.PDF, ReaderSourceType.normalize("PDF"))
        assertEquals(ReaderSourceType.TXT, ReaderSourceType.normalize("txt"))
        // An unknown or missing value must not crash the library; text is the safe reading.
        assertEquals(ReaderSourceType.TXT, ReaderSourceType.normalize(null))
        assertEquals(ReaderSourceType.TXT, ReaderSourceType.normalize("epub"))
    }

    @Test
    fun sourceTypeIsInferredFromTheFileExtension() {
        assertEquals(ReaderSourceType.PDF, ReaderSourceType.fromFileName("book.PDF"))
        assertEquals(ReaderSourceType.TXT, ReaderSourceType.fromFileName("book.txt"))
        assertEquals(ReaderSourceType.TXT, ReaderSourceType.fromFileName("no-extension"))
    }

    @Test
    fun bookStateNormalizesTotally() {
        assertEquals(ReaderBookState.IN_PROGRESS, ReaderBookState.normalize("in_progress"))
        assertEquals(ReaderBookState.COMPLETED, ReaderBookState.normalize("COMPLETED"))
        assertEquals(ReaderBookState.NOT_STARTED, ReaderBookState.normalize(null))
        assertEquals(ReaderBookState.NOT_STARTED, ReaderBookState.normalize("playing"))
    }

    private fun metadata(title: String) = BookMetadata(
        provider = MetadataProvider.GOOGLE_BOOKS,
        providerId = "abc",
        title = title,
        subtitle = null,
        authors = listOf("Richard Dawkins"),
        publisher = null,
        publishedDate = null,
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
}
