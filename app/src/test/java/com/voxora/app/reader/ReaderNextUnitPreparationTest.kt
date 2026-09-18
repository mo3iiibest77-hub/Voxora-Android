package com.voxora.app.reader

import com.voxora.core.gemini.ReaderNarrationModes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contract for the look-ahead that runs *between* units: before the narration of unit N+1 can
 * begin, its text must already exist in the selected language **and** the selected narration
 * mode.
 *
 * The initial gate ([ReaderInitialPlaybackTest]) only covers the first unit of a run. The reported
 * defect this file pins is the same one one step later: while unit N is being spoken the reader
 * may still be looking at the source-language (or other-style) text of unit N+1, and watches it
 * change to the selected wording after the audio has already started. The lifecycle the Reader
 * must hold is therefore
 *
 *   current audio -> prepare N+1 -> N+1 text correct -> N+1 audio
 *
 * and never
 *
 *   source-language N+1 -> wait -> text changes -> audio.
 *
 * [ReaderController] realises this by calling [ReaderInitialPlayback.gate] on the N+1 rendering
 * taken from [ReaderDisplayModes] for the run's language and mode. Both halves are unit-tested on
 * their own; the tests below pin their *composition*, because that is where the defect lived — a
 * correct rule reading a cache that was not scoped to the mode, or a cache that was scoped but
 * read with the visible page's fallback instead of the rendering.
 *
 * Pure JVM: the rule and the cache are both plain objects.
 */
class ReaderNextUnitPreparationTest {

    private val faithful = ReaderNarrationModes.FAITHFUL
    private val fluent = ReaderNarrationModes.FLUENT

    /** The gate [ReaderController.awaitNextUnitRendering] applies to the upcoming unit. */
    private fun nextUnitGate(
        modes: ReaderDisplayModes,
        mode: String,
        language: String,
        chunk: Int,
        segment: Int,
    ): ReaderInitialPlayback.Gate =
        ReaderInitialPlayback.gate(
            rendering = modes.forMode(mode).text(language, chunk, segment),
            failed = false,
        )

    @Test
    fun theNextUnitIsNotReadyUntilItsRenderingExistsInTheSelectedLanguageAndMode() {
        val modes = ReaderDisplayModes()

        assertEquals(
            ReaderInitialPlayback.Gate.AWAIT,
            nextUnitGate(modes, faithful, "fa", chunk = 0, segment = 1),
        )

        // Preparing exactly that unit for exactly that language and mode is what releases it.
        modes.forMode(faithful).record("fa", chunk = 0, segment = 1, text = "واحد بعدی")
        assertEquals(
            ReaderInitialPlayback.Gate.READY,
            nextUnitGate(modes, faithful, "fa", chunk = 0, segment = 1),
        )
    }

    @Test
    fun aRenderingPreparedForTheOtherNarrationModeDoesNotReleaseTheNextUnit() {
        val modes = ReaderDisplayModes()
        // Faithful was prepared for the upcoming unit, but the reader is listening in Fluent.
        modes.forMode(faithful).record("fa", chunk = 0, segment = 1, text = "وفادار")

        assertEquals(
            ReaderInitialPlayback.Gate.AWAIT,
            nextUnitGate(modes, fluent, "fa", chunk = 0, segment = 1),
        )
    }

    @Test
    fun aRenderingPreparedForAnotherLanguageDoesNotReleaseTheNextUnit() {
        val modes = ReaderDisplayModes()
        modes.forMode(faithful).record("en", chunk = 0, segment = 1, text = "English")

        assertEquals(
            ReaderInitialPlayback.Gate.AWAIT,
            nextUnitGate(modes, faithful, "fa", chunk = 0, segment = 1),
        )
    }

    /**
     * The page falls back to the extracted source for a unit with no rendering, so the look-ahead
     * must read the rendering rather than what is currently on screen — otherwise it would report
     * READY and start N+1's audio against source-language text.
     */
    @Test
    fun theSourceFallbackOnThePageDoesNotCountAsAPreparedNextUnit() {
        val modes = ReaderDisplayModes()
        val source = listOf("first", "second")

        val page = modes.forMode(faithful).readingText("fa", chunk = 0, units = source)
        assertEquals(source, page)
        assertNull(modes.forMode(faithful).text("fa", 0, 1))

        assertEquals(
            ReaderInitialPlayback.Gate.AWAIT,
            nextUnitGate(modes, faithful, "fa", chunk = 0, segment = 1),
        )
    }

    /**
     * A unit rendered by an earlier run — replay, resume, or a mode the reader has already heard —
     * is ready immediately, so the look-ahead never inserts a wait that has already been paid for.
     */
    @Test
    fun aUnitPreparedByAnEarlierRunReleasesImmediately() {
        val modes = ReaderDisplayModes()
        modes.forMode(fluent).record("fa", chunk = 2, segment = 3, text = "از پیش آماده")

        assertEquals(
            ReaderInitialPlayback.Gate.READY,
            nextUnitGate(modes, fluent, "fa", chunk = 2, segment = 3),
        )
    }

    /**
     * Preparation is scoped to the unit, not to the chunk: having unit 0 rendered must not let the
     * look-ahead skip unit 1, or the reader would hear N+1 against N's text.
     */
    @Test
    fun preparingOneUnitDoesNotReleaseTheNext() {
        val modes = ReaderDisplayModes()
        modes.forMode(faithful).record("fa", chunk = 0, segment = 0, text = "واحد صفر")

        assertEquals(
            ReaderInitialPlayback.Gate.READY,
            nextUnitGate(modes, faithful, "fa", chunk = 0, segment = 0),
        )
        assertEquals(
            ReaderInitialPlayback.Gate.AWAIT,
            nextUnitGate(modes, faithful, "fa", chunk = 0, segment = 1),
        )
    }
}
