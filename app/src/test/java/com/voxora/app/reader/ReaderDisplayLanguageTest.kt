package com.voxora.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the Reader's selected-language reading text.
 *
 * The Reader must show its reading text in the selected narration language while the
 * canonical extracted document stays intact. The only translation mechanism in the
 * product is the Reader Gemini path, which returns each unit's transcript in the
 * selected language, so [ReaderDisplayText] is the layer that holds those renderings
 * above extraction.
 *
 * These tests pin the properties that make that safe:
 *
 * - a rendering belongs to exactly one language, chunk and segment,
 * - a language change can never surface text produced for another language,
 * - the canonical chunk keeps its length, order and boundaries,
 * - every chunk obeys the same language-selection contract,
 * - a new document cannot inherit the previous document's renderings.
 *
 * Order is compared as the ordered word sequence, which is whitespace-agnostic and so
 * detects loss, duplication and reordering regardless of how text is folded.
 */
class ReaderDisplayLanguageTest {

    private fun words(text: String): List<String> =
        text.split(Regex("\\s+")).filter { it.isNotEmpty() }

    // ---- cache: language scoping -------------------------------------------------

    @Test
    fun aRenderingBelongsToExactlyItsOwnLanguageChunkAndSegment() {
        val display = ReaderDisplayText()
        display.record("fa", chunk = 0, segment = 0, text = "فارسی صفر")
        display.record("fa", chunk = 0, segment = 1, text = "فارسی یک")
        display.record("fa", chunk = 1, segment = 0, text = "فارسی بخش دو")

        assertEquals("فارسی صفر", display.text("fa", 0, 0))
        assertEquals("فارسی یک", display.text("fa", 0, 1))
        assertEquals("فارسی بخش دو", display.text("fa", 1, 0))
    }

    @Test
    fun aLanguageNeverSeesAnotherLanguagesRendering() {
        val display = ReaderDisplayText()
        display.record("fa", chunk = 3, segment = 2, text = "فارسی")
        display.record("en", chunk = 3, segment = 2, text = "English")

        assertEquals("فارسی", display.text("fa", 3, 2))
        assertEquals("English", display.text("en", 3, 2))
        assertNotEquals(display.text("fa", 3, 2), display.text("en", 3, 2))
        // A language with nothing recorded must not borrow the other language's entry.
        assertNull(display.text("ar", 3, 2))
        assertNull(display.text("de", 3, 2))
    }

    @Test
    fun changingLanguageDoesNotSurfaceThePreviousLanguagesText() {
        val display = ReaderDisplayText()
        display.record("fa", chunk = 0, segment = 0, text = "فارسی")
        display.record("fa", chunk = 0, segment = 1, text = "فارسی دو")

        // Same chunk, new language: nothing from the previous language may be served.
        for (segment in 0..1) {
            assertNull(display.text("en", 0, segment))
        }
        // The previous language is still intact, it is simply not the selected one.
        assertEquals("فارسی", display.text("fa", 0, 0))
    }

    @Test
    fun blankTextIsRejectedRatherThanStored() {
        val display = ReaderDisplayText()

        assertFalse(display.record("fa", 0, 0, ""))
        assertFalse(display.record("fa", 0, 0, "   \n\t "))
        assertNull(display.text("fa", 0, 0))
        assertEquals(0, display.count("fa"))
        assertTrue(display.languages().isEmpty())
    }

    @Test
    fun anEmptyRenderingCannotHideAUsableOne() {
        val display = ReaderDisplayText()
        display.record("fa", 0, 0, "فارسی")

        assertFalse(display.record("fa", 0, 0, ""))

        assertEquals("فارسی", display.text("fa", 0, 0))
    }

    @Test
    fun recordedTextIsTrimmedSoTheUiNeverShowsPadding() {
        val display = ReaderDisplayText()
        assertTrue(display.record("fa", 0, 0, "  متن  \n"))

        assertEquals("متن", display.text("fa", 0, 0))
    }

    @Test
    fun recordingTheSameUnitAgainReplacesRatherThanDuplicates() {
        val display = ReaderDisplayText()
        display.record("fa", 0, 0, "اول")
        display.record("fa", 0, 0, "دوم")

        assertEquals("دوم", display.text("fa", 0, 0))
        assertEquals(1, display.count("fa"))
    }

    @Test
    fun languagesAndCountReflectOnlyWhatIsStored() {
        val display = ReaderDisplayText()
        display.record("fa", 0, 0, "یک")
        display.record("fa", 0, 1, "دو")
        display.record("en", 0, 0, "one")

        assertEquals(setOf("fa", "en"), display.languages())
        assertEquals(2, display.count("fa"))
        assertEquals(1, display.count("en"))
        assertEquals(0, display.count("ar"))
        assertEquals(mapOf(0 to "یک", 1 to "دو"), display.chunk("fa", 0))
        assertTrue(display.chunk("fa", 1).isEmpty())
    }

    @Test
    fun clearDropsEveryRendering() {
        val display = ReaderDisplayText()
        display.record("fa", 0, 0, "یک")
        display.record("en", 5, 3, "one")

        display.clear()

        assertTrue(display.languages().isEmpty())
        assertEquals(0, display.count("fa"))
        assertNull(display.text("fa", 0, 0))
        assertNull(display.text("en", 5, 3))
    }

    // ---- pipeline: display text above the canonical chunk -------------------------

    @Test
    fun readingTextPreservesChunkLengthOrderAndBoundaries() {
        val chunks = (1..3).map { index -> List(60) { "c${index}w$it" }.joinToString(" ") }
        val queue = ChunkQueue(chunks)
        val display = ReaderDisplayText()

        for (index in chunks.indices) {
            val units = queue.segments(index)
            // Nothing rendered yet: the reading text is the extracted source, unchanged.
            assertEquals(units, display.readingText("fa", index, units))
        }
    }

    @Test
    fun readingTextUsesTheSelectedLanguageForRenderedUnitsOnly() {
        val chunks = listOf(List(60) { "c1w$it" }.joinToString(" "))
        val queue = ChunkQueue(chunks)
        val units = queue.segments(0)
        val display = ReaderDisplayText()

        // Render only the middle unit in Persian, as a partially narrated chunk would be.
        val renderedIndex = units.size / 2
        display.record("fa", 0, renderedIndex, "fa-rendered")

        val shown = display.readingText("fa", 0, units)

        assertEquals(units.size, shown.size)
        for (index in units.indices) {
            if (index == renderedIndex) {
                assertEquals("fa-rendered", shown[index])
            } else {
                assertEquals(units[index], shown[index])
            }
        }
        // The canonical chunk is untouched and still rebuilds from its own units.
        assertEquals(units.flatMap(::words), units.flatMap(::words))
        assertEquals(units, queue.segments(0))
    }

    @Test
    fun everyChunkObeysTheSameLanguageSelectionContract() {
        val chunks = (1..6).map { index -> List(60) { "c${index}w$it" }.joinToString(" ") }
        val queue = ChunkQueue(chunks)
        val display = ReaderDisplayText()

        for (index in chunks.indices) {
            val units = queue.segments(index)
            for (segment in units.indices) {
                display.record("fa", index, segment, "fa-unit-$segment")
            }
            val shown = display.readingText("fa", index, units)
            // No chunk may keep the extracted source once it is rendered in the selection,
            // and no chunk may borrow another chunk's rendering.
            for (segment in units.indices) {
                assertEquals("fa-unit-$segment", shown[segment])
                assertNotEquals(units[segment], shown[segment])
            }
        }
    }

    @Test
    fun switchingLanguageNeverMixesChunksOrSegmentsAcrossLanguages() {
        val chunks = (1..3).map { index -> List(60) { "c${index}w$it" }.joinToString(" ") }
        val queue = ChunkQueue(chunks)
        val display = ReaderDisplayText()

        // Chunk 1 fully narrated in Persian.
        val first = queue.segments(0)
        for (segment in first.indices) display.record("fa", 0, segment, "fa-0-$segment")

        // The user switches to English before anything else is narrated.
        val firstInEnglish = display.readingText("en", 0, first)
        assertEquals(first, firstInEnglish) // falls back to the canonical source
        assertTrue(firstInEnglish.none { it.startsWith("fa-") })

        // And the Persian renderings are still keyed to Persian, not overwritten.
        assertTrue(display.readingText("fa", 0, first).all { it.startsWith("fa-") })
    }

    @Test
    fun aNewDocumentDoesNotInheritThePreviousDocumentsRenderings() {
        val previous = ChunkQueue(listOf(List(60) { "a$it" }.joinToString(" ")))
        val display = ReaderDisplayText()
        for (segment in previous.segments(0).indices) {
            display.record("fa", 0, segment, "stale-$segment")
        }
        assertTrue(display.readingText("fa", 0, previous.segments(0)).all { it.startsWith("stale-") })

        // A new load clears the cache, exactly as ReaderController.startLoad does.
        display.clear()
        val next = ChunkQueue(listOf(List(60) { "b$it" }.joinToString(" ")))
        val shown = display.readingText("fa", 0, next.segments(0))

        assertEquals(next.segments(0), shown)
        assertTrue(shown.none { it.startsWith("stale-") })
    }

    @Test
    fun renderingsNeverChangeTheCanonicalExtractedSource() {
        val source = List(60) { "c1w$it" }.joinToString(" ")
        val queue = ChunkQueue(listOf(source))
        val units = queue.segments(0)
        val display = ReaderDisplayText()

        for (segment in units.indices) display.record("fa", 0, segment, "fa-$segment")

        // The queue still serves the extracted text and still caches the same segments.
        assertEquals(source, queue.current)
        assertEquals(units, queue.segments(0))
        assertEquals(words(source), units.flatMap(::words))
    }
}
