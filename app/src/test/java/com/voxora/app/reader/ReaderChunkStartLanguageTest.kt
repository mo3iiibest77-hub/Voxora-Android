package com.voxora.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the displayed language switching at the *start* of a chunk.
 *
 * The requirement: when Chunk N becomes the current chunk, the visible text is already
 * in the selected narration language. It must not wait for Chunk N to finish narrating,
 * and it must not keep showing the previous chunk's language while the new transcript is
 * still being produced.
 *
 * The production sequence is `ReaderController.jumpToChunk` -> `queue.jumpTo` ->
 * `navigate()` (revision bump) -> `publish()`, and `publish()` reads
 * [ReaderDisplayText.readingText] and [ReaderDisplayText.pending] for the current chunk.
 * Because the producer renders whole chunks ahead of playback, a chunk that is about to
 * be narrated usually already has its renderings cached, so `publish()` resolves them in
 * the same synchronous step that makes the chunk current. Nothing on this path waits for
 * audio.
 *
 * [Page] below sequences exactly those production calls. It is not a re-implementation of
 * the pipeline; it is the same four steps in the same order, so these tests exercise the
 * real cache, the real mapping and the real ownership rule.
 */
class ReaderChunkStartLanguageTest {

    /**
     * Stand-in for the controller's publish/navigate/record sequence.
     *
     * `generation` and `revision` mirror `ReaderController.generation` and
     * `navigationRevision`: production writes are only accepted while both still match
     * the position they were started for.
     */
    private class Page(chunks: List<String>, var language: String) {
        val display = ReaderDisplayText()
        val queue = ChunkQueue(chunks)
        var generation = 0L
            private set
        var revision = 0L
            private set
        var published = ReaderState()
            private set
        var publishes = 0
            private set

        init {
            publish(ReaderPhase.READY)
        }

        /** Mirrors ReaderController.publish(). */
        fun publish(phase: ReaderPhase) {
            val units = queue.segments(queue.index)
            publishes++
            published = ReaderState(
                phase = phase,
                chunk = if (queue.size == 0) 0 else queue.index + 1,
                total = queue.size,
                segment = if (units.isEmpty()) 0 else 1,
                segmentTotal = units.size,
                text = queue.current.orEmpty(),
                segments = display.readingText(language, queue.index, units),
                pendingSegments = display.pending(language, queue.index, units.size),
            )
        }

        /** Mirrors ReaderController.jumpToChunk(): move, bump the revision, republish. */
        fun jumpTo(index: Int) {
            if (queue.size == 0) return
            queue.jumpTo(index)
            revision++
            publish(ReaderPhase.PAUSED)
        }

        /** Mirrors ReaderController.setOutputLanguage(). */
        fun select(selected: String) {
            if (selected == language) return
            language = selected
            publish(published.phase)
        }

        /** Mirrors the record step inside ReaderController.produce(), including checkOwned(). */
        fun record(
            run: Long,
            rev: Long,
            recordedLanguage: String,
            chunk: Int,
            segment: Int,
            text: String,
        ) {
            if (run != generation || rev != revision) return
            if (!display.record(recordedLanguage, chunk, segment, text)) return
            if (display.shouldRepublish(recordedLanguage, chunk, language, queue.index)) {
                publish(published.phase)
            }
        }

        /** Records for the position the page is on right now. */
        fun narrate(language: String, chunk: Int, segment: Int, text: String) =
            record(generation, revision, language, chunk, segment, text)

        fun units(index: Int): List<String> = queue.segments(index)
    }

    /** One narration unit: a paragraph of 60 words, which ChunkQueue keeps as one unit. */
    private fun paragraph(tag: String): String = (1..60).joinToString(" ") { "$tag$it" }

    /** A chunk of three paragraphs, i.e. three narration units. */
    private fun chunk(tag: String): String =
        listOf(paragraph("${tag}a"), paragraph("${tag}b"), paragraph("${tag}c")).joinToString("\n\n")

    private fun page(vararg tags: String, language: String = "en"): Page =
        Page(tags.map(::chunk), language)

    private fun Page.unitCount(index: Int): Int = units(index).size

    // ---- the language is right from the first frame of the chunk --------------------

    @Test
    fun theFixtureReallyHasSeveralUnitsPerChunk() {
        // Guards the rest of the file: a single-unit chunk would make the "one unit at a
        // time" assertions vacuous.
        val page = page("one")
        assertEquals(3, page.unitCount(0))
    }

    @Test
    fun arrivingAtAChunkShowsItsSelectedLanguageTextWithoutWaitingForNarration() {
        val page = page("one", "two")
        // Chunk 2 was rendered by the producer while chunk 1 was still on screen.
        page.narrate("en", chunk = 1, segment = 0, text = "cached second chunk")
        val recordsBefore = page.display.count("en")

        page.jumpTo(1)

        assertEquals("cached second chunk", page.published.segments[0])
        assertEquals(2, page.published.chunk)
        // The move resolved the cached rendering; it did not render anything new.
        assertEquals(recordsBefore, page.display.count("en"))
    }

    @Test
    fun theNewChunkStartsInTheSelectedLanguageAndNeverInThePreviousChunks() {
        val page = page("one", "two", language = "en")
        // Chunk 1 was narrated in Persian; the reader has since selected English.
        for (segment in 0 until page.unitCount(0)) {
            page.narrate("fa", chunk = 0, segment = segment, text = "fa-$segment")
        }
        assertTrue(page.published.segments.none { it.startsWith("fa-") })

        page.jumpTo(1)

        // Nothing in English for chunk 2 yet: the page must show the extracted source and
        // say it is still being prepared, never the Persian text of the chunk it left.
        assertEquals(page.units(1), page.published.segments)
        assertTrue(page.published.segments.none { it.startsWith("fa-") })
        assertEquals(page.unitCount(1), page.published.pendingSegments.size)
    }

    @Test
    fun aChunkWithNoRenderingYetIsReportedAsPreparingRatherThanSettled() {
        val page = page("one", "two")
        page.jumpTo(1)

        assertEquals((0 until page.unitCount(1)).toSet(), page.published.pendingSegments)
        assertTrue(
            ReaderPageText.isPreparingWholePage(
                ReaderPhase.SPEAKING,
                page.published.pendingSegments,
                page.unitCount(1),
            ),
        )
    }

    @Test
    fun thePendingSetShrinksUnitByUnitAsTheChunkIsNarrated() {
        val page = page("one", "two")
        page.jumpTo(1)
        val count = page.unitCount(1)

        for (segment in 0 until count) {
            page.narrate("en", chunk = 1, segment = segment, text = "en-$segment")
            assertEquals("segment $segment must have left the pending set", count - segment - 1, page.published.pendingSegments.size)
            assertFalse(segment in page.published.pendingSegments)
            assertEquals("en-$segment", page.published.segments[segment])
        }
        assertTrue(page.published.pendingSegments.isEmpty())
    }

    // ---- late transcripts -----------------------------------------------------------

    @Test
    fun aTranscriptArrivingAfterTheChunkStartsUpdatesItImmediately() {
        val page = page("one", "two")
        page.jumpTo(1)
        val before = page.publishes

        page.narrate("en", chunk = 1, segment = 0, text = "arrived late")

        assertTrue("the page must republish for its own chunk", page.publishes > before)
        assertEquals("arrived late", page.published.segments[0])
        assertEquals(2, page.published.chunk)
    }

    @Test
    fun aTranscriptFromAnAbandonedGenerationCannotReachThePage() {
        val page = page("one", "two")
        val abandoned = page.generation - 1

        page.record(abandoned, page.revision, "en", chunk = 0, segment = 0, text = "stale run")

        assertNull(page.display.text("en", 0, 0))
        assertTrue(page.published.segments.none { it == "stale run" })
    }

    @Test
    fun aTranscriptFromAnAbandonedRevisionCannotOverwriteANewerChunk() {
        val page = page("one", "two")
        val abandonedRevision = page.revision
        page.jumpTo(1)

        // A producer that was started for chunk 1 before the turn keeps running and
        // finishes its unit afterwards. Its revision is stale, so it must be dropped.
        page.record(page.generation, abandonedRevision, "en", chunk = 0, segment = 0, text = "late old chunk")

        assertNull(page.display.text("en", 0, 0))
        assertEquals(2, page.published.chunk)
        assertTrue(page.published.segments.none { it == "late old chunk" })
    }

    @Test
    fun prefetchNeverMovesThePageAheadOfTheNarration() {
        val page = page("one", "two", "three")
        val onScreen = page.published

        // The producer renders chunk 3 while chunk 1 is displayed.
        page.narrate("en", chunk = 2, segment = 0, text = "rendered ahead")

        assertEquals(0, page.queue.index)
        assertEquals(1, page.published.chunk)
        assertEquals(onScreen.segments, page.published.segments)
        // It is cached and ready for the moment the reader does arrive.
        assertEquals("rendered ahead", page.display.text("en", 2, 0))
    }

    // ---- language switching ---------------------------------------------------------

    @Test
    fun switchingLanguageNeverLeavesTextFromTheOtherLanguageOnThePage() {
        val page = page("one", language = "fa")
        for (segment in 0 until page.unitCount(0)) {
            page.narrate("fa", chunk = 0, segment = segment, text = "fa-$segment")
        }
        assertTrue(page.published.segments.any { it.startsWith("fa-") })

        page.select("en")

        assertEquals(page.units(0), page.published.segments)
        assertTrue(page.published.segments.none { it.startsWith("fa-") })
        assertEquals(page.unitCount(0), page.published.pendingSegments.size)
    }

    @Test
    fun switchingBackToAPreviouslyNarratedLanguageRestoresItsTextImmediately() {
        val page = page("one", language = "fa")
        val count = page.unitCount(0)
        for (segment in 0 until count) {
            page.narrate("fa", chunk = 0, segment = segment, text = "fa-$segment")
        }
        page.select("en")
        for (segment in 0 until count) {
            page.narrate("en", chunk = 0, segment = segment, text = "en-$segment")
        }

        page.select("fa")

        // No narration and no waiting: the Persian rendering is keyed to Persian.
        assertEquals(List(count) { "fa-$it" }, page.published.segments)
        assertTrue(page.published.pendingSegments.isEmpty())
    }

    @Test
    fun anAbandonedLanguageKeepsProducingWithoutTouchingThePage() {
        val page = page("one", language = "fa")
        page.select("en")

        // A run that started in Persian finishes its unit after the switch.
        page.narrate("fa", chunk = 0, segment = 0, text = "fa-late")

        assertTrue(page.published.segments.none { it == "fa-late" })
        assertNotNull(page.display.text("fa", 0, 0))
    }

    // ---- the page never rewrites the document ---------------------------------------

    @Test
    fun thePageKeepsTheCanonicalExtractedChunkUntouched() {
        val source = chunk("one")
        val page = Page(listOf(source), "en")
        val units = page.units(0)

        for (segment in units.indices) {
            page.narrate("en", chunk = 0, segment = segment, text = "en-$segment")
        }

        assertEquals(source, page.queue.current)
        assertEquals(units, page.units(0))
        assertTrue(page.units(0).none { it.startsWith("en-") })
    }

    @Test
    fun arrivingAtAChunkPublishesThatChunksOwnBoundaries() {
        val page = page("one", "two")
        page.jumpTo(1)

        assertEquals(chunk("two"), page.published.text)
        assertEquals(page.unitCount(1), page.published.segmentTotal)
        assertEquals(2, page.published.chunk)
        assertEquals(2, page.published.total)
    }
}
