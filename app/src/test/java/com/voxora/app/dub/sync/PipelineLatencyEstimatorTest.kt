package com.voxora.app.dub.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The latency floor's contract: it is the smallest offset in the window, a burst cannot raise it,
 * the window forgets old samples, and the excess it reports is the avoidable delay.
 */
class PipelineLatencyEstimatorTest {

    private val MS = 1_000_000L
    private val SEC = 1_000_000_000L

    @Test
    fun `there is no floor before the first sample`() {
        val estimator = PipelineLatencyEstimator()

        assertFalse(estimator.hasCurrentNanos)
        assertFalse(estimator.hasFloorNanos)
        assertEquals(0L, estimator.excessNanos)
    }

    @Test
    fun `the floor is the smallest offset in the window`() {
        val estimator = PipelineLatencyEstimator()
        estimator.onOffset(0L, 3 * SEC)
        estimator.onOffset(500 * MS, 3 * SEC + 500 * MS)
        estimator.onOffset(1 * SEC, 2_500 * MS)

        assertEquals(2_500 * MS, estimator.floorNanos)
    }

    /**
     * The load-bearing property: a burst of late audio inflates the current offset but must never
     * move the target, or the controller would accept the burst as the new normal.
     */
    @Test
    fun `a burst raises the current offset but never the floor`() {
        val estimator = PipelineLatencyEstimator()
        estimator.onOffset(0L, 2 * SEC)
        estimator.onOffset(1 * SEC, 2 * SEC)
        estimator.onOffset(2 * SEC, 8 * SEC)

        assertEquals(2 * SEC, estimator.floorNanos)
        assertTrue("the current offset must reflect the burst", estimator.currentNanos > 2 * SEC)
        assertTrue("the burst is avoidable delay", estimator.excessNanos > 0L)
    }

    @Test
    fun `the floor forgets an old sample once the window has passed`() {
        val estimator = PipelineLatencyEstimator(windowNanos = 10 * SEC, bucketNanos = 1 * SEC)
        estimator.onOffset(0L, 1 * SEC)

        // Twenty seconds later the low sample is outside the window, so the floor rises to the
        // pipeline's current, genuinely slower, latency.
        estimator.onOffset(20 * SEC, 4 * SEC)

        assertEquals(4 * SEC, estimator.floorNanos)
    }

    @Test
    fun `the current offset is smoothed rather than taken raw`() {
        val estimator = PipelineLatencyEstimator(currentAlpha = 0.5)
        estimator.onOffset(0L, 0L)
        estimator.onOffset(1 * SEC, 1 * SEC)

        assertEquals(500 * MS, estimator.currentNanos)
    }

    @Test
    fun `excess is zero when the offset holds at the floor`() {
        val estimator = PipelineLatencyEstimator()
        estimator.onOffset(0L, 2 * SEC)
        estimator.onOffset(1 * SEC, 2 * SEC)

        assertEquals(0L, estimator.excessNanos)
    }

    @Test
    fun `reset clears the floor`() {
        val estimator = PipelineLatencyEstimator()
        estimator.onOffset(0L, 2 * SEC)

        estimator.reset()

        assertFalse(estimator.hasFloorNanos)
        assertFalse(estimator.hasCurrentNanos)
        assertEquals(0L, estimator.excessNanos)
    }
}
