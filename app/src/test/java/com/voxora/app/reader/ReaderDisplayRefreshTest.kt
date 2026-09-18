package com.voxora.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the Reader refreshing its reading text as Gemini narrates.
 *
 * The reported defect: with a Persian PDF and English selected, the audio was English
 * but the visible text stayed Persian until something unrelated republished the Reader.
 * The cause was that production stored the transcript in [ReaderDisplayText] without
 * republishing, so the screen only refreshed on the next chunk transition.
 *
 * [shouldRepublish] is the production rule that decides when a stored rendering becomes
 * visible state, and [ReaderDisplayText.readingText] is the production mapping that
 * builds the published list. [Reader] below only sequences those two exactly as
 * `ReaderController.produce`/`publish` do, so these tests exercise the real rule and
 * the real mapping rather than a copy of them.
 */
class ReaderDisplayRefreshTest {

    /**
     * Minimal stand-in for the controller's publish loop: it holds the current
     * position, the displayed language and the last published reading text.
     */
    private class Reader(chunks: List<String>, var language: String) {
        val display = ReaderDisplayText()
        val queue = ChunkQueue(chunks)
        var segments: List<String> = queue.segments(queue.index)
        var refreshes = 0
            private set

        /** Mirrors the record-and-maybe-republish step inside produce(). */
        fun record(recordedLanguage: String, chunk: Int, segment: Int, text: String) {
            if (!display.record(recordedLanguage, chunk, segment, text)) return
            if (display.shouldRepublish(recordedLanguage, chunk, language, queue.index)) publish()
        }

        /** Mirrors publish(): rebuilds the reading text of the current chunk. */
        fun publish() {
            refreshes++
            segments = display.readingText(language, queue.index, queue.segments(queue.index))
        }

        fun select(language: String) {
            this.language = language
            publish()
        }

        fun jumpTo(chunk: Int) {
            queue.jumpTo(chunk)
            publish()
        }
    }

    private fun chunk(index: Int): String = List(60) { "c${index}w$it" }.joinToString(" ")

    // ---- when a stored rendering becomes visible state -----------------------------

    @Test
    fun aRenderingForTheDisplayedChunkAndLanguageIsPublishedImmediately() {
        val reader = Reader(listOf(chunk(1)), language = "en")
        val units = reader.queue.segments(0)
        val before = reader.refreshes

        reader.record("en", chunk = 0, segment = 0, text = "english unit one")

        assertEquals(before + 1, reader.refreshes)
        assertEquals("english unit one", reader.segments[0])
        // The rest of the chunk is still the extracted source, not blank.
        for (index in 1 until units.size) assertEquals(units[index], reader.segments[index])
    }

    @Test
    fun aRenderingForAPrefetchedChunkIsStoredWithoutMovingTheScreen() {
        val reader = Reader(listOf(chunk(1), chunk(2)), language = "en")
        val displayed = reader.segments
        val before = reader.refreshes

        // The producer renders chunk 2 while chunk 1 is on screen.
        reader.record("en", chunk = 1, segment = 0, text = "english chunk two")

        assertEquals(before, reader.refreshes)
        assertEquals(displayed, reader.segments)
        // It is cached, so it is ready the moment the reader moves there.
        assertEquals("english chunk two", reader.display.text("en", 1, 0))
        assertEquals(0, reader.queue.index)
    }

    @Test
    fun aRenderingForAnAbandonedLanguageIsNotPublished() {
        val reader = Reader(listOf(chunk(1)), language = "fa")
        val before = reader.refreshes

        // A run that started in English keeps producing after the reader switched to
        // Persian; its transcript must never become the visible text.
        reader.record("en", chunk = 0, segment = 0, text = "english unit one")

        assertEquals(before, reader.refreshes)
        assertTrue(reader.segments.none { it == "english unit one" })
        assertEquals("english unit one", reader.display.text("en", 0, 0))
    }

    @Test
    fun theRepublishRuleRequiresTheChunkToMatchExactly() {
        val display = ReaderDisplayText()

        assertTrue(display.shouldRepublish("en", 0, "en", 0))
        assertFalse(display.shouldRepublish("en", 1, "en", 0))
        assertFalse(display.shouldRepublish("fa", 0, "en", 0))
        assertFalse(display.shouldRepublish("en", 0, "fa", 0))
        assertFalse(display.shouldRepublish("en", 3, "en", 2))
    }

    @Test
    fun aRejectedRenderingNeverTriggersARefresh() {
        val reader = Reader(listOf(chunk(1)), language = "en")
        val before = reader.refreshes

        reader.record("en", chunk = 0, segment = 0, text = "   ")

        assertEquals(before, reader.refreshes)
        assertEquals(reader.queue.segments(0), reader.segments)
    }

    // ---- what the refreshed text actually shows -----------------------------------

    @Test
    fun eachNarratedUnitAppearsAsItArrivesAndKeepsTheChunkShape() {
        val reader = Reader(listOf(chunk(1)), language = "en")
        val units = reader.queue.segments(0)
        assertEquals(units.size, reader.segments.size)

        for (segment in units.indices) {
            reader.record("en", chunk = 0, segment = segment, text = "en-$segment")
            assertEquals("the published list must keep the chunk's length", units.size, reader.segments.size)
            for (index in 0..segment) assertEquals("en-$index", reader.segments[index])
            // Nothing ahead of the narration may be replaced early.
            for (index in (segment + 1) until units.size) assertEquals(units[index], reader.segments[index])
        }
    }

    @Test
    fun switchingLanguageShowsOnlyTheNewLanguagesRenderings() {
        val reader = Reader(listOf(chunk(1)), language = "fa")
        reader.record("fa", chunk = 0, segment = 0, text = "fa-unit-0")

        reader.select("en")

        // Nothing rendered in English yet: the extracted source, never the Persian text.
        assertEquals(reader.queue.segments(0), reader.segments)
        assertTrue(reader.segments.none { it.startsWith("fa-") })

        reader.record("en", chunk = 0, segment = 0, text = "en-unit-0")
        assertEquals("en-unit-0", reader.segments[0])
        // And switching back still finds the Persian rendering, keyed to Persian.
        reader.select("fa")
        assertEquals("fa-unit-0", reader.segments[0])
    }

    @Test
    fun refreshingNeverChangesTheCanonicalExtractedSource() {
        val source = chunk(1)
        val reader = Reader(listOf(source), language = "en")
        val units = reader.queue.segments(0)
        val canonicalWords = units.flatMap { it.split(" ") }

        for (segment in units.indices) reader.record("en", chunk = 0, segment = segment, text = "en-$segment")

        assertEquals(source, reader.queue.current)
        assertEquals(units, reader.queue.segments(0))
        assertEquals(canonicalWords, reader.queue.segments(0).flatMap { it.split(" ") })
        assertNotEquals(units, reader.segments)
        assertTrue(reader.queue.segments(0).none { it.startsWith("en-") })
    }

    @Test
    fun movingToAnotherChunkPublishesThatChunksOwnRenderings() {
        val reader = Reader(listOf(chunk(1), chunk(2)), language = "en")
        reader.record("en", chunk = 0, segment = 0, text = "en-first")
        reader.record("en", chunk = 1, segment = 0, text = "en-second")

        reader.jumpTo(1)

        val units = reader.queue.segments(1)
        assertEquals(units.size, reader.segments.size)
        assertEquals("en-second", reader.segments[0])
        for (index in 1 until units.size) assertEquals(units[index], reader.segments[index])
        assertTrue(reader.segments.none { it == "en-first" })
    }
}
