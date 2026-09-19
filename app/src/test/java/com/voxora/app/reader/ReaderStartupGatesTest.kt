package com.voxora.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the Reader while a document is being extracted.
 *
 * Restoring the last PDF and extracting a large one both leave the Reader in
 * [ReaderPhase.EXTRACTING] for a while. The reported problem was that the screen looked
 * frozen for that whole window: the document card claimed nothing was loaded, and the
 * reading mode, the narration language and the file picker were all disabled even
 * though none of them can interfere with extraction.
 *
 * These tests pin the two halves of the fix:
 *
 * - the controls that are safe stay available, so the Reader is never fully locked;
 * - the controls that guard the pipeline stay closed — playback must not start before
 *   the queue is ready, and chunk navigation needs a document.
 */
class ReaderStartupGatesTest {

    private val allPhases = ReaderPhase.entries.toList()

    // ---- what stays available during extraction -----------------------------------

    @Test
    fun readingModeAndLanguageStayEditableWhileExtracting() {
        assertTrue(ReaderGates.canConfigure(ready = true, phase = ReaderPhase.EXTRACTING))
        assertTrue(ReaderGates.isExtracting(ReaderPhase.EXTRACTING))
    }

    @Test
    fun anotherDocumentCanBePickedWhileExtracting() {
        // startLoad already serializes loads behind a generation bump, so choosing a
        // file during extraction is a supported cancellation rather than a race.
        assertTrue(ReaderGates.canPickDocument(ready = true))
        assertTrue(ReaderGates.canStop(ReaderPhase.EXTRACTING, hasDocument = false))
    }

    @Test
    fun settingsStayUnavailableUntilTheyAreLoaded() {
        for (phase in allPhases) {
            assertFalse(ReaderGates.canConfigure(ready = false, phase = phase))
            assertFalse(ReaderGates.canPickDocument(ready = false))
        }
    }

    // ---- what stays closed during extraction --------------------------------------

    @Test
    fun playbackNeverStartsBeforeTheQueueIsReady() {
        // Even with a document already loaded (re-extraction of the same file), an
        // empty or in-flight queue must not accept a transport request.
        assertFalse(ReaderGates.canPlay(ready = true, phase = ReaderPhase.EXTRACTING, hasDocument = true))
        assertFalse(ReaderGates.canPlay(ready = true, phase = ReaderPhase.EXTRACTING, hasDocument = false))
        assertFalse(ReaderGates.canPlay(ready = true, phase = ReaderPhase.READY, hasDocument = false))
        assertFalse(ReaderGates.canPlay(ready = false, phase = ReaderPhase.READY, hasDocument = true))
    }

    @Test
    fun chunkNavigationNeedsADocumentAndNoExtraction() {
        assertFalse(ReaderGates.canNavigate(ReaderPhase.EXTRACTING, hasDocument = false))
        assertFalse(ReaderGates.canNavigate(ReaderPhase.EXTRACTING, hasDocument = true))
        assertFalse(ReaderGates.canNavigate(ReaderPhase.READY, hasDocument = false))
        assertTrue(ReaderGates.canNavigate(ReaderPhase.READY, hasDocument = true))
        assertTrue(ReaderGates.canNavigate(ReaderPhase.PAUSED, hasDocument = true))
    }

    // ---- the Reader is never fully locked -----------------------------------------

    @Test
    fun everyPhaseOffersEitherConfigurationOrPlayback() {
        for (phase in allPhases) {
            val configurable = ReaderGates.canConfigure(ready = true, phase = phase)
            val playable = ReaderGates.canPlay(ready = true, phase = phase, hasDocument = true)
            assertTrue(
                "the Reader is locked in $phase",
                configurable || playable,
            )
        }
    }

    @Test
    fun configurationIsFrozenOnlyWhileNarrationIsRunning() {
        val narrating = listOf(
            ReaderPhase.CONNECTING,
            ReaderPhase.REWRITING,
            ReaderPhase.SPEAKING,
            ReaderPhase.NEXT,
        )
        for (phase in narrating) {
            assertTrue("expected $phase to narrate", ReaderGates.isNarrating(phase))
            assertFalse("expected $phase to freeze settings", ReaderGates.canConfigure(ready = true, phase = phase))
            // Transport stays live while narrating: that button is Pause.
            assertTrue(ReaderGates.canPlay(ready = true, phase = phase, hasDocument = true))
        }
        val idle = allPhases - narrating.toSet()
        for (phase in idle) {
            assertFalse("expected $phase not to narrate", ReaderGates.isNarrating(phase))
            assertTrue("expected $phase to allow settings", ReaderGates.canConfigure(ready = true, phase = phase))
        }
    }

    @Test
    fun phaseClassificationCoversTheWholeLifecycle() {
        assertEquals(allPhases.size, ReaderPhase.entries.size)
        assertEquals(
            allPhases.filter { ReaderGates.isExtracting(it) },
            listOf(ReaderPhase.EXTRACTING),
        )
        // Extraction and narration are disjoint, so "busy" never double-counts.
        for (phase in allPhases) {
            assertFalse(ReaderGates.isExtracting(phase) && ReaderGates.isNarrating(phase))
        }
    }
}
