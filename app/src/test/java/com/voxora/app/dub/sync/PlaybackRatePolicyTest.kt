package com.voxora.app.dub.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rate trim's contract: a dead band, a bounded ramp, and a hard rule that the dub is never sped
 * up past the audio the model has actually produced.
 */
class PlaybackRatePolicyTest {

    private val MS = 1_000_000L
    private val SEC = 1_000_000_000L

    @Test
    fun `a small excess inside the dead band leaves the rate at unity`() {
        val policy = PlaybackRatePolicy()

        assertEquals(1.0f, policy.rateFor(0L, 50 * MS, 500 * MS), 0.0001f)
    }

    @Test
    fun `an excess with a backlog ramps the rate up to the ceiling`() {
        val policy = PlaybackRatePolicy()
        var now = 0L
        var last = 1.0f
        repeat(5) {
            now += 3 * SEC
            last = policy.rateFor(now, 500 * MS, 500 * MS)
        }

        assertEquals(1.03f, last, 0.0001f)
    }

    /**
     * Speeding up with nothing queued would drain the model's own output and underrun. The policy
     * must refuse, however large the excess looks.
     */
    @Test
    fun `an excess with nothing queued keeps the rate at unity`() {
        val policy = PlaybackRatePolicy()
        var now = 0L
        repeat(5) {
            now += 3 * SEC
            policy.rateFor(now, 2 * SEC, 0L)
        }

        assertEquals(1.0f, policy.currentRate, 0.0001f)
    }

    @Test
    fun `the rate cannot move more than one step per cooldown`() {
        val policy = PlaybackRatePolicy()

        assertEquals(1.01f, policy.rateFor(0L, 1 * SEC, 1 * SEC), 0.0001f)
        // Too soon: the rate must not move again, so a wobbling offset cannot oscillate the pitch.
        assertEquals(1.01f, policy.rateFor(1 * SEC, 1 * SEC, 1 * SEC), 0.0001f)
    }

    @Test
    fun `the rate returns to unity when the excess clears`() {
        val policy = PlaybackRatePolicy()
        var now = 0L
        repeat(5) {
            now += 3 * SEC
            policy.rateFor(now, 1 * SEC, 1 * SEC)
        }
        assertTrue(policy.currentRate > 1.0f)

        repeat(5) {
            now += 3 * SEC
            policy.rateFor(now, 0L, 0L)
        }

        assertEquals(1.0f, policy.currentRate, 0.0001f)
    }

    @Test
    fun `the rate never leaves its bounds`() {
        val policy = PlaybackRatePolicy()
        var now = 0L
        repeat(50) {
            now += 3 * SEC
            policy.rateFor(now, 60 * SEC, 60 * SEC)
        }

        assertTrue(policy.currentRate <= 1.03f)
        assertTrue(policy.currentRate >= 0.97f)
    }

    @Test
    fun `reset returns to unity`() {
        val policy = PlaybackRatePolicy()
        policy.rateFor(0L, 1 * SEC, 1 * SEC)

        policy.reset()

        assertEquals(1.0f, policy.currentRate, 0.0001f)
    }
}
