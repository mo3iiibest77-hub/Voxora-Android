package com.voxora.app.reader

import com.voxora.core.gemini.ReaderNarrationModes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end ordering contract for the text pipeline the Reader actually runs:
 *
 *   extracted text -> ChunkQueue.documentChunks -> ChunkQueue.segments
 *
 * The reported real-world defect was that the source text shown for chunk 1 was
 * fine while every later chunk was scrambled. The corruption was traced to PDF
 * extraction (see [PdfReadingOrderTest]); these tests pin the guarantee for the
 * layers *after* extraction, so a future change cannot reintroduce scrambling at a
 * chunk or segment boundary:
 *
 * - the document has one canonical ordered representation,
 * - chunks are contiguous and in document order,
 * - every chunk's segments belong to that chunk, in order, and rebuild it,
 * - a new queue never inherits another queue's cached segments.
 *
 * Order is compared as the ordered word sequence, which is whitespace-agnostic and
 * therefore detects loss, duplication and reordering without depending on how
 * `normalize` folds extraction whitespace.
 */
class ReaderPipelineOrderTest {

    /** Page-tagged words: page 1 is "p1w1 p1w2 ...", page 2 "p2w1 ...", etc. */
    private fun document(pages: Int, wordsPerPage: Int): String =
        (1..pages).joinToString(" ") { page ->
            (1..wordsPerPage).joinToString(" ") { word -> "p${page}w$word" }
        }

    private fun words(text: String): List<String> =
        text.split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun joined(chunks: List<String>): List<String> = chunks.flatMap(::words)

    @Test
    fun chunksFollowDocumentOrderAndRebuildTheSource() {
        val source = document(pages = 12, wordsPerPage = 100) // 1200 words -> 3 chunks
        val chunks = ChunkQueue.documentChunks(source)

        assertEquals(3, chunks.size)
        assertEquals(words(source), joined(chunks))
    }

    @Test
    fun chunkOneTwoAndThreeDoNotOverlapOrReachAhead() {
        val source = document(pages = 12, wordsPerPage = 100)
        val chunks = ChunkQueue.documentChunks(source)
        val sourceWords = words(source)

        val first = words(chunks[0])
        val second = words(chunks[1])
        val third = words(chunks[2])

        // Chunk 1 starts the document and holds only its own beginning.
        assertEquals(sourceWords.take(first.size), first)
        // Chunk 2 begins exactly where chunk 1 stopped.
        assertEquals(sourceWords.drop(first.size).take(second.size), second)
        // Chunk 3 begins exactly where chunk 2 stopped.
        assertEquals(sourceWords.drop(first.size + second.size).take(third.size), third)
        // Nothing is lost or duplicated.
        assertEquals(first.size + second.size + third.size, sourceWords.size)
        assertTrue(first.isNotEmpty() && second.isNotEmpty() && third.isNotEmpty())
    }

    @Test
    fun segmentsRebuildTheirOwnChunkInOrder() {
        val source = document(pages = 12, wordsPerPage = 100)
        val chunks = ChunkQueue.documentChunks(source)
        val queue = ChunkQueue(chunks)

        for (index in chunks.indices) {
            val chunkWords = words(chunks[index])
            val units = queue.segments(index)

            assertTrue("chunk ${index + 1} produced no segments", units.isNotEmpty())
            assertEquals("chunk ${index + 1} lost or reordered text", chunkWords, units.flatMap(::words))
        }
    }

    @Test
    fun segmentsNeverContainTextFromAnotherChunk() {
        // Every chunk is built from a single distinct token, so any bleed between
        // chunks is impossible to miss.
        val chunks = (1..6).map { index -> List(60) { "c${index}w$it" }.joinToString(" ") }
        val queue = ChunkQueue(chunks)

        for (index in chunks.indices) {
            val units = queue.segments(index)
            assertTrue(units.isNotEmpty())
            assertTrue(
                "segments of chunk ${index + 1} contain another chunk's text",
                units.flatMap(::words).all { it.startsWith("c${index + 1}w") },
            )
        }
    }

    @Test
    fun readingSegmentsInAnyOrderDoesNotChangeWhatAChunkHolds() {
        val chunks = (1..4).map { index -> List(60) { "c${index}w$it" }.joinToString(" ") }
        val queue = ChunkQueue(chunks)

        val first = queue.segments(0)
        // Visit a later chunk first, then come back: the cache must not cross over.
        val third = queue.segments(2)
        assertEquals(first, queue.segments(0))
        assertEquals(third, queue.segments(2))
        assertNotEquals(first, third)
        assertEquals(List(60) { "c1w$it" }, first.flatMap(::words))
        assertEquals(List(60) { "c3w$it" }, third.flatMap(::words))
    }

    @Test
    fun aNewQueueDoesNotInheritThePreviousDocumentsSegments() {
        // Same indices, different content: the segment cache is per queue, so a new
        // document can never be served the previous document's units.
        val firstDocument = ChunkQueue(listOf(List(60) { "a$it" }.joinToString(" ")))
        val secondDocument = ChunkQueue(listOf(List(60) { "b$it" }.joinToString(" ")))

        assertEquals(List(60) { "a$it" }, firstDocument.segments(0).flatMap(::words))
        assertEquals(List(60) { "b$it" }, secondDocument.segments(0).flatMap(::words))
    }

    @Test
    fun advancingTheQueueYieldsTheNextChunkNotAStaleOne() {
        val chunks = (1..3).map { index -> List(60) { "c${index}w$it" }.joinToString(" ") }
        val queue = ChunkQueue(chunks)

        assertEquals(chunks[0], queue.current)
        assertEquals(queue.segments(0), queue.segments(queue.index))
        queue.jumpTo(1)
        assertEquals(chunks[1], queue.current)
        assertEquals(queue.segments(1), queue.segments(queue.index))
        queue.jumpTo(2)
        assertEquals(chunks[2], queue.current)
        assertEquals(queue.segments(2), queue.segments(queue.index))
    }

    @Test
    fun theNarrationInstructionIsIdenticalForEveryChunkAndSegment() {
        // produce() asks for the instruction once per session; it is a pure function
        // of (mode, language), so no chunk can silently switch narration policy.
        for (mode in listOf(ReaderNarrationModes.FAITHFUL, ReaderNarrationModes.FLUENT)) {
            val first = ReaderNarrationModes.instruction(mode, "English")
            repeat(20) { index ->
                assertEquals(
                    "instruction drifted at call $index for $mode",
                    first,
                    ReaderNarrationModes.instruction(mode, "English"),
                )
            }
            assertTrue(first.contains("English"))
        }
    }

    @Test
    fun fluentAndFaithfulRemainDistinctContracts() {
        val fluent = ReaderNarrationModes.instruction(ReaderNarrationModes.FLUENT, "English")
            .replace(Regex("\\s+"), " ")
        val faithful = ReaderNarrationModes.instruction(ReaderNarrationModes.FAITHFUL, "English")
            .replace(Regex("\\s+"), " ")

        assertNotEquals(fluent, faithful)

        // Fluent may restructure the same content; it may never summarize it away.
        assertTrue(fluent.contains("rewrite that same content"))
        assertTrue(fluent.contains("not a summary"))
        assertTrue(fluent.contains("summarizing"))
        assertTrue(fluent.contains("Forbidden in Fluent mode"))

        // Faithful keeps the book's words; free paraphrase is what it forbids.
        assertTrue(faithful.contains("free paraphrasing"))
        assertTrue(faithful.contains("the original words"))
        assertTrue(faithful.contains("Faithful is not a weaker Fluent"))
        assertTrue(faithful.contains("Forbidden in Faithful mode"))

        assertEquals(ReaderNarrationModes.FAITHFUL, ReaderNarrationModes.normalize("anything-else"))
        assertEquals(ReaderNarrationModes.FAITHFUL, ReaderNarrationModes.normalize(null))
    }
}
