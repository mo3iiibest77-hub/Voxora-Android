package com.voxora.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the Reader's status indicator.
 *
 * The indicator is the only thing on screen that says "audio is happening right now", so
 * the rule it encodes is deliberately narrow: green — and only green — while audio is
 * actually being spoken, and a breathing animation only in that same state. Connecting,
 * extracting and the hand-off between chunks must not claim playback is running, because
 * the user would then trust a light instead of listening.
 *
 * [ReaderStatusTone] carries the colour contract in its name — ACTIVE is the success
 * green, READY the Voxora gold, STOPPED the error red, NEUTRAL the plain outline — and
 * the screen maps tones to `VoxoraColors` with an exhaustive `when`, so a tone cannot be
 * added without deciding its colour.
 */
class ReaderStatusVisualTest {

    @Test
    fun speakingIsTheActiveTone() {
        assertEquals(ReaderStatusTone.ACTIVE, ReaderStatusVisual.tone(ReaderPhase.SPEAKING))
    }

    @Test
    fun onlyActiveNarrationPulses() {
        assertTrue(ReaderStatusVisual.pulses(ReaderPhase.SPEAKING))
        for (phase in ReaderPhase.entries) {
            if (phase == ReaderPhase.SPEAKING) continue
            assertFalse("$phase must not animate as if it were playing", ReaderStatusVisual.pulses(phase))
        }
    }

    @Test
    fun pausedIsGoldNotGreen() {
        assertEquals(ReaderStatusTone.READY, ReaderStatusVisual.tone(ReaderPhase.PAUSED))
        assertNotEquals(ReaderStatusTone.ACTIVE, ReaderStatusVisual.tone(ReaderPhase.PAUSED))
        assertFalse(ReaderStatusVisual.pulses(ReaderPhase.PAUSED))
    }

    @Test
    fun stoppedAndFailedAreTheStopTone() {
        assertEquals(ReaderStatusTone.STOPPED, ReaderStatusVisual.tone(ReaderPhase.STOPPED))
        assertEquals(ReaderStatusTone.STOPPED, ReaderStatusVisual.tone(ReaderPhase.ERROR))
        assertFalse(ReaderStatusVisual.pulses(ReaderPhase.STOPPED))
    }

    @Test
    fun preparingNeverClaimsActivePlayback() {
        for (phase in listOf(
            ReaderPhase.EXTRACTING,
            ReaderPhase.CONNECTING,
            ReaderPhase.REWRITING,
            ReaderPhase.NEXT,
        )) {
            assertEquals("$phase is not active playback", ReaderStatusTone.NEUTRAL, ReaderStatusVisual.tone(phase))
            assertFalse("$phase must not pulse", ReaderStatusVisual.pulses(phase))
        }
    }

    @Test
    fun idleAndReadyAreSettledStates() {
        assertEquals(ReaderStatusTone.NEUTRAL, ReaderStatusVisual.tone(ReaderPhase.IDLE))
        assertEquals(ReaderStatusTone.READY, ReaderStatusVisual.tone(ReaderPhase.READY))
        assertEquals(ReaderStatusTone.READY, ReaderStatusVisual.tone(ReaderPhase.COMPLETE))
    }

    @Test
    fun everyPhaseHasATone() {
        for (phase in ReaderPhase.entries) {
            assertNotNull("$phase has no status tone", ReaderStatusVisual.tone(phase))
        }
    }

    @Test
    fun theFourVisualStatesRemainDistinct() {
        // Collapsing any two of these is how "paused" ends up looking like "playing".
        val tones = ReaderPhase.entries.map { ReaderStatusVisual.tone(it) }.toSet()
        assertEquals(
            setOf(
                ReaderStatusTone.ACTIVE,
                ReaderStatusTone.READY,
                ReaderStatusTone.NEUTRAL,
                ReaderStatusTone.STOPPED,
            ),
            tones,
        )
    }
}
