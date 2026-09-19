package com.voxora.app.dub.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The diagnostic line's contract: every requested field is present, an unavailable device
 * measurement says so instead of showing a fabricated zero, and the rate is locale-independent.
 */
class SyncDiagnosticsTest {

    private val MS = 1_000_000L

    private fun snapshot(capture: Long? = 10 * MS, firstAudio: Long? = 500 * MS) = SyncSnapshot(
        sourceNanos = 4_000_000_000L,
        dubNanos = 1_000_000_000L,
        driftNanos = 3_000_000_000L,
        estimatedPipelineLatencyNanos = 3_000_000_000L,
        floorNanos = 2_800_000_000L,
        excessNanos = 200_000_000L,
        captureTimestampNanos = capture,
        geminiFirstAudioNanos = firstAudio,
        handoffNanos = 120_000_000L,
        outputQueuedNanos = 1_200_000_000L,
        outputPlayedNanos = 1_000_000_000L,
        bufferNanos = 200_000_000L,
        rate = 1.03f,
        correction = "SYNCED",
        drops = 2L,
        underruns = 1L,
    )

    @Test
    fun `the line carries every requested field`() {
        val line = SyncDiagnostics.format(snapshot())

        assertTrue(line.startsWith("[DUB_SYNC] "))
        for (field in listOf(
            "sourceMs=4000",
            "dubMs=1000",
            "driftMs=3000",
            "estimatedPipelineLatencyMs=3000",
            "floorMs=2800",
            "excessMs=200",
            "captureTimestampMs=10",
            "geminiFirstAudioMs=500",
            "handoffMs=120",
            "outputQueuedMs=1200",
            "outputPlayedMs=1000",
            "bufferMs=200",
            "rate=1.03",
            "correction=SYNCED",
            "drops=2",
            "underruns=1",
        )) {
            assertTrue("missing $field in: $line", line.contains(field))
        }
    }

    @Test
    fun `an unavailable device measurement is rendered as unavailable, not zero`() {
        val line = SyncDiagnostics.format(snapshot(capture = null, firstAudio = null))

        assertTrue(line.contains("captureTimestampMs=—"))
        assertTrue(line.contains("geminiFirstAudioMs=—"))
        assertFalse("a missing timestamp must not look like a zero", line.contains("captureTimestampMs=0"))
    }

    @Test
    fun `the rate uses a dot whatever the device locale`() {
        val line = SyncDiagnostics.format(snapshot())

        assertTrue(line.contains("rate=1.03"))
        assertFalse(line.contains("rate=1,03"))
    }
}
