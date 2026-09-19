package com.voxora.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the gate in front of a run's **first audible frame**.
 *
 * The reported defect this pins: a run could start speaking while the page still showed the
 * document's own language, so the reader heard translated audio against source-language text and
 * watched the selected-language text arrive afterwards.
 *
 * The reading text is the Gemini transcript of a unit, so "the text is ready" and "the rendering
 * exists" are the same statement. The gate therefore turns on one lookup —
 * [ReaderDisplayText.text] for the run's language — and never on the text the page happens to be
 * showing, because that page falls back to the extracted source for a unit with no rendering.
 * That distinction is the whole point of this file: the tests below pair the two lookups so a
 * regression that gates on the visible page instead of the rendering fails here.
 *
 * Pure JVM, matching the rule object it covers.
 */
class ReaderInitialPlaybackTest {

    // ---- the decision itself --------------------------------------------------------

    @Test
    fun aFirstUnitWithNoRenderingYetMustNotPlay() {
        assertEquals(
            ReaderInitialPlayback.Gate.AWAIT,
            ReaderInitialPlayback.gate(rendering = null, failed = false),
        )
    }

    @Test
    fun theFirstUnitMayPlayOnceItsRenderingExists() {
        assertEquals(
            ReaderInitialPlayback.Gate.READY,
            ReaderInitialPlayback.gate(rendering = "متن روایت", failed = false),
        )
    }

    /**
     * `record` rejects blank text, so a blank value can only mean the transcript never arrived.
     * Accepting it would put an empty card where the selected-language text belongs.
     */
    @Test
    fun aBlankRenderingIsNotARendering() {
        assertEquals(
            ReaderInitialPlayback.Gate.AWAIT,
            ReaderInitialPlayback.gate(rendering = "   ", failed = false),
        )
        assertEquals(
            ReaderInitialPlayback.Gate.AWAIT,
            ReaderInitialPlayback.gate(rendering = "", failed = false),
        )
    }

    /**
     * A unit that is finished and still has no rendering can never produce one, so waiting would
     * hang and playing would be the misleading audio the gate exists to prevent.
     */
    @Test
    fun aFinishedUnitWithNoRenderingFailsInsteadOfPlaying() {
        assertEquals(
            ReaderInitialPlayback.Gate.FAILED,
            ReaderInitialPlayback.gate(rendering = null, failed = true),
        )
    }

    /**
     * `failed` also covers "this unit has finished", which is true of every *successful* unit.
     * The rendering must therefore be consulted first, or no unit could ever start.
     */
    @Test
    fun aRenderingWinsOverTheFailureFlag() {
        assertEquals(
            ReaderInitialPlayback.Gate.READY,
            ReaderInitialPlayback.gate(rendering = "translated text", failed = true),
        )
    }

    /**
     * The gate only ever reports one of its three outcomes, so no input can leave a run in a
     * state where it neither plays nor fails.
     */
    @Test
    fun everyInputResolvesToExactlyOneOutcome() {
        val renderings = listOf(null, "", "   ", "text")
        for (rendering in renderings) {
            for (failed in listOf(false, true)) {
                val gate = ReaderInitialPlayback.gate(rendering, failed)
                assertTrue(
                    "gate($rendering, $failed) = $gate",
                    gate in ReaderInitialPlayback.Gate.entries,
                )
                // AWAIT is the only outcome that means "keep waiting", and it must never be
                // returned once the unit is known to be finished.
                if (failed && !rendering.isNullOrBlank()) {
                    assertEquals(ReaderInitialPlayback.Gate.READY, gate)
                } else if (failed) {
                    assertEquals(ReaderInitialPlayback.Gate.FAILED, gate)
                }
            }
        }
    }

    // ---- the source fallback is not a rendering -------------------------------------

    /**
     * The load-bearing test. The page's reading text falls back to the extracted source, so it is
     * non-empty for a unit that has no rendering at all. A gate that read the visible page would
     * report READY and start audio against source-language text; reading the rendering does not.
     */
    @Test
    fun theSourceFallbackIsNotMistakenForASelectedLanguageRendering() {
        val display = ReaderDisplayText()
        val source = listOf("The source sentence, still in English.")

        val page = display.readingText("fa", chunk = 0, units = source)
        assertTrue("the page shows something", page.single().isNotBlank())
        assertEquals("it is the source, not a translation", source.single(), page.single())

        assertNull("and there is no rendering for the selected language", display.text("fa", 0, 0))
        assertEquals(
            ReaderInitialPlayback.Gate.AWAIT,
            ReaderInitialPlayback.gate(display.text("fa", 0, 0), failed = false),
        )
    }

    /**
     * A rendering recorded for a different language must not satisfy the gate for the selected
     * one — the cache is keyed by language so two languages can never share an entry.
     */
    @Test
    fun aRenderingInAnotherLanguageDoesNotSatisfyTheGate() {
        val display = ReaderDisplayText()
        display.record("en", chunk = 0, segment = 0, text = "An English rendering")

        assertEquals(
            ReaderInitialPlayback.Gate.AWAIT,
            ReaderInitialPlayback.gate(display.text("fa", 0, 0), failed = false),
        )
        assertEquals(
            ReaderInitialPlayback.Gate.READY,
            ReaderInitialPlayback.gate(display.text("en", 0, 0), failed = false),
        )
    }

    /**
     * A rendering recorded for another unit must not unlock this one; the gate is about the unit
     * the run starts on, not about the chunk having some text somewhere.
     */
    @Test
    fun aRenderingForAnotherUnitDoesNotSatisfyTheGate() {
        val display = ReaderDisplayText()
        display.record("fa", chunk = 0, segment = 4, text = "قطعهٔ دیگر")

        assertNull(display.text("fa", 0, 0))
        assertEquals(
            ReaderInitialPlayback.Gate.AWAIT,
            ReaderInitialPlayback.gate(display.text("fa", 0, 0), failed = false),
        )
    }

    /**
     * The fast path: a unit rendered by an earlier run — resume, replay, or a segment the reader
     * has already heard — is ready immediately, so nothing waits a second time.
     */
    @Test
    fun aUnitRenderedByAnEarlierRunIsReadyImmediately() {
        val display = ReaderDisplayText()
        display.record("fa", chunk = 2, segment = 3, text = "متن ترجمه‌شده")

        assertEquals(
            ReaderInitialPlayback.Gate.READY,
            ReaderInitialPlayback.gate(display.text("fa", 2, 3), failed = false),
        )
    }

    /**
     * A run that starts mid-chunk is gated on the unit it actually starts on, not on unit zero.
     */
    @Test
    fun aRunStartingMidChunkIsGatedOnTheUnitItStartsOn() {
        val display = ReaderDisplayText()
        display.record("fa", chunk = 1, segment = 0, text = "قطعهٔ اول")

        // Unit 5 is the one the run will speak first, and it has no rendering yet.
        assertNull(display.text("fa", 1, 5))
        assertEquals(
            ReaderInitialPlayback.Gate.AWAIT,
            ReaderInitialPlayback.gate(display.text("fa", 1, 5), failed = false),
        )
    }
}
