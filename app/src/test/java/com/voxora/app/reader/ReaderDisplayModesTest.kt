package com.voxora.app.reader

import com.voxora.core.gemini.ReaderNarrationModes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the Reader's narration-mode scoping.
 *
 * The reading text must correspond to the selected language **and** the selected narration
 * style. Faithful and Fluent are different rewrites of the same source — the instruction Gemini
 * receives differs, so the transcript it returns differs — and a rendering produced for one is
 * not a rendering for the other. Serving the other style's wording is the same class of defect
 * as serving another language's, and it is what these tests exist to prevent.
 *
 * The mode is the cache's identity here, so "the other mode's text is on screen" is
 * unrepresentable rather than merely unlikely. The rules pinned below:
 *
 * - each mode owns a distinct cache,
 * - a rendering recorded for one mode is invisible to the other,
 * - switching mode falls back to the extracted source, never to the previous style's wording,
 * - an unknown mode resolves to the default rather than creating a third, unfilled cache,
 * - a new document clears every mode.
 */
class ReaderDisplayModesTest {

    private val faithful = ReaderNarrationModes.FAITHFUL
    private val fluent = ReaderNarrationModes.FLUENT

    @Test
    fun everyModeInTheContractOwnsACache() {
        val modes = ReaderDisplayModes()

        assertEquals(ReaderNarrationModes.all, modes.modes())
        assertEquals(2, modes.modes().distinct().size)
        assertNotSame(modes.forMode(faithful), modes.forMode(fluent))
    }

    @Test
    fun aRenderingIsVisibleOnlyToTheModeItWasRecordedFor() {
        val modes = ReaderDisplayModes()
        modes.forMode(faithful).record("fa", chunk = 0, segment = 0, text = "وفادار")
        modes.forMode(fluent).record("fa", chunk = 0, segment = 0, text = "روان")

        assertEquals("وفادار", modes.forMode(faithful).text("fa", 0, 0))
        assertEquals("روان", modes.forMode(fluent).text("fa", 0, 0))
        // The load-bearing case: neither mode may borrow the other's wording.
        assertNotEquals(
            modes.forMode(faithful).text("fa", 0, 0),
            modes.forMode(fluent).text("fa", 0, 0),
        )
    }

    @Test
    fun switchingModeFallsBackToTheSourceRatherThanThePreviousStyle() {
        val source = listOf("one", "two", "three")
        val modes = ReaderDisplayModes()
        for (segment in source.indices) {
            modes.forMode(faithful).record("fa", 0, segment, "faithful-$segment")
        }

        // The user switches to Fluent before anything has been narrated in it.
        val shown = modes.forMode(fluent).readingText("fa", 0, source)

        assertEquals(source, shown)
        assertTrue(shown.none { it.startsWith("faithful-") })
        // And the Faithful renderings are still keyed to Faithful, not overwritten.
        assertTrue(modes.forMode(faithful).readingText("fa", 0, source).all { it.startsWith("faithful-") })
    }

    @Test
    fun anUnknownModeResolvesToTheDefaultInsteadOfCreatingAThirdCache() {
        val modes = ReaderDisplayModes()

        assertSame(modes.forMode(faithful), modes.forMode(""))
        assertSame(modes.forMode(faithful), modes.forMode("FAITHFUL"))
        assertSame(modes.forMode(faithful), modes.forMode("something-else"))
        assertEquals(ReaderNarrationModes.all, modes.modes())
    }

    @Test
    fun pendingIsScopedToTheModeThatOwnsTheRendering() {
        val modes = ReaderDisplayModes()
        modes.forMode(fluent).record("fa", 0, 1, "fluent-one")

        // Faithful has nothing recorded, so every unit is still pending there.
        assertEquals(setOf(0, 1, 2), modes.forMode(faithful).pending("fa", 0, 3))
        // Fluent has exactly the one unit it recorded.
        assertEquals(setOf(0, 2), modes.forMode(fluent).pending("fa", 0, 3))
    }

    @Test
    fun clearingADocumentDropsEveryModesRenderings() {
        val modes = ReaderDisplayModes()
        modes.forMode(faithful).record("fa", 0, 0, "وفادار")
        modes.forMode(fluent).record("en", 2, 1, "fluent")

        modes.clear()

        assertNull(modes.forMode(faithful).text("fa", 0, 0))
        assertNull(modes.forMode(fluent).text("en", 2, 1))
        for (mode in ReaderNarrationModes.all) {
            assertEquals(0, modes.forMode(mode).count("fa"))
            assertTrue(modes.forMode(mode).languages().isEmpty())
        }
    }

    @Test
    fun recordingIntoOneModeNeverTouchesTheOthersCounts() {
        val modes = ReaderDisplayModes()
        modes.forMode(fluent).record("fa", 0, 0, "a")
        modes.forMode(fluent).record("fa", 0, 1, "b")

        assertEquals(2, modes.forMode(fluent).count("fa"))
        assertEquals(0, modes.forMode(faithful).count("fa"))
        assertFalse(modes.forMode(faithful).languages().contains("fa"))
    }
}
