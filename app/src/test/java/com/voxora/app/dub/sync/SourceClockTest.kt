package com.voxora.app.dub.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The source clock's contract: it advances from the device's own frame count, it refuses to count
 * silence or a held source, and it reports the capture pipeline's latency only when the platform
 * actually provides a timestamp.
 */
class SourceClockTest {

    private val MS = 1_000_000L

    @Test
    fun `the content clock converts the device frame count to audio time`() {
        val clock = SourceClock(48_000)
        clock.onCapture(frames = 0L, capturedAtNanos = 0L, nowNanos = 0L, fromDevice = true, counts = true)
        clock.onCapture(
            frames = 48_000L,
            capturedAtNanos = 0L,
            nowNanos = 0L,
            fromDevice = true,
            counts = true,
        )

        assertEquals(1_000 * MS, clock.contentNanos)
    }

    @Test
    fun `frames counted while silent do not advance the content clock`() {
        val clock = SourceClock(48_000)
        clock.onCapture(0L, 0L, 0L, fromDevice = true, counts = true)
        // A full second of silence: the device captured it, but it is not source progress.
        clock.onCapture(48_000L, 0L, 0L, fromDevice = true, counts = false)
        assertEquals(0L, clock.contentNanos)

        // The next audible second advances by exactly one second, not two.
        clock.onCapture(96_000L, 0L, 0L, fromDevice = true, counts = true)
        assertEquals(1_000 * MS, clock.contentNanos)
    }

    @Test
    fun `uneven reads do not lose frames`() {
        // The clock tracks the device's cumulative frame count, so a short read is invisible to it.
        val clock = SourceClock(16_000)
        clock.onCapture(0L, 0L, 0L, fromDevice = true, counts = true)
        clock.onCapture(160L, 0L, 0L, fromDevice = true, counts = true)
        clock.onCapture(16_000L, 0L, 0L, fromDevice = true, counts = true)

        assertEquals(1_000 * MS, clock.contentNanos)
    }

    @Test
    fun `the capture latency is the device timestamp subtracted from now`() {
        val clock = SourceClock(48_000)
        clock.onCapture(
            frames = 0L,
            capturedAtNanos = 10 * MS,
            nowNanos = 10 * MS,
            fromDevice = true,
            counts = true,
        )

        assertTrue(clock.hasDeviceTimestamp)
        assertEquals(90 * MS, clock.captureLatencyNanos(100 * MS))
    }

    @Test
    fun `without a device timestamp the capture latency is unavailable, never zero`() {
        val clock = SourceClock(48_000)
        clock.onCapture(
            frames = 0L,
            capturedAtNanos = 5 * MS,
            nowNanos = 5 * MS,
            fromDevice = false,
            counts = true,
        )

        assertFalse(clock.hasDeviceTimestamp)
        assertNull(clock.captureLatencyNanos(100 * MS))
    }

    @Test
    fun `a frame counter that goes backwards cannot shrink the content clock`() {
        val clock = SourceClock(48_000)
        clock.onCapture(48_000L, 0L, 0L, fromDevice = true, counts = true)
        clock.onCapture(96_000L, 0L, 0L, fromDevice = true, counts = true)
        val before = clock.contentNanos

        clock.onCapture(0L, 0L, 0L, fromDevice = true, counts = true)

        assertEquals(before, clock.contentNanos)
    }

    @Test
    fun `reset clears the run`() {
        val clock = SourceClock(48_000)
        clock.onCapture(0L, 0L, 0L, fromDevice = true, counts = true)
        clock.onCapture(48_000L, 0L, 0L, fromDevice = true, counts = true)

        clock.reset()

        assertEquals(0L, clock.contentNanos)
        assertFalse(clock.hasDeviceTimestamp)
        assertNull(clock.captureLatencyNanos(1 * MS))
    }
}
