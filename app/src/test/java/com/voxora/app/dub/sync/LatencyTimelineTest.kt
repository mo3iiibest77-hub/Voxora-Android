package com.voxora.app.dub.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The instrumentation contract: monotonic timestamps, derived stage latencies, accounted drops,
 * and a log line that is emitted at most once per interval.
 */
class LatencyTimelineTest {

    private class FakeClock(var now: Long = 0L) : MonotonicClock {
        override fun nowNanos(): Long = now
    }

    @Test
    fun `first and last occurrences are tracked separately`() {
        val clock = FakeClock()
        val timeline = LatencyTimeline(clock, log = {})

        clock.now = 100
        timeline.mark(DubEvent.PCM_SENT)
        clock.now = 200
        timeline.mark(DubEvent.PCM_SENT)
        clock.now = 500
        timeline.mark(DubEvent.GEMINI_FIRST_AUDIO)

        assertEquals(2L, timeline.count(DubEvent.PCM_SENT))
        // First-to-first is the end-to-end latency of the very first chunk.
        assertEquals(400L, timeline.firstElapsedNanos(DubEvent.PCM_SENT, DubEvent.GEMINI_FIRST_AUDIO))
        // Last-to-last is the current scheduling delay between the two stages.
        assertEquals(300L, timeline.lastElapsedNanos(DubEvent.PCM_SENT, DubEvent.GEMINI_FIRST_AUDIO))
    }

    @Test
    fun `an event that never happened has no elapsed time`() {
        val timeline = LatencyTimeline(FakeClock(), log = {})
        timeline.mark(DubEvent.PCM_SENT, nowNanos = 10)
        assertNull(timeline.firstElapsedNanos(DubEvent.PCM_SENT, DubEvent.AUDIO_WRITE))
        assertNull(timeline.lastElapsedNanos(DubEvent.DUB_CHUNK, DubEvent.AUDIO_WRITE))
    }

    @Test
    fun `a backwards timestamp is reported as absent rather than negative`() {
        val timeline = LatencyTimeline(FakeClock(), log = {})
        timeline.mark(DubEvent.SOURCE_CHUNK, nowNanos = 900)
        timeline.mark(DubEvent.PCM_SENT, nowNanos = 100)
        assertNull(timeline.firstElapsedNanos(DubEvent.SOURCE_CHUNK, DubEvent.PCM_SENT))
    }

    @Test
    fun `the summary names every stage and the measured milliseconds`() {
        val timeline = LatencyTimeline(FakeClock(), log = {})
        timeline.mark(DubEvent.SOURCE_CHUNK, nowNanos = 1_000_000L)
        timeline.mark(DubEvent.PCM_SENT, nowNanos = 4_000_000L)
        timeline.mark(DubEvent.GEMINI_FIRST_AUDIO, nowNanos = 2_000_000_000L)
        timeline.mark(DubEvent.DUB_CHUNK, nowNanos = 2_500_000_000L)
        timeline.mark(DubEvent.AUDIO_WRITE, nowNanos = 2_600_000_000L)

        val summary = timeline.summary()
        assertTrue(summary, summary.contains("capture→send 3ms"))
        assertTrue(summary, summary.contains("send→first audio 1996ms"))
        assertTrue(summary, summary.contains("send→dub 2496ms"))
        assertTrue(summary, summary.contains("dub→write 100ms"))
        assertTrue(summary, summary.contains("source chunks 1"))
        assertTrue(summary, summary.contains("dub chunks 1"))
    }

    @Test
    fun `a missing stage is shown as a dash, not as zero`() {
        val timeline = LatencyTimeline(FakeClock(), log = {})
        timeline.mark(DubEvent.PCM_SENT, nowNanos = 1)
        assertTrue(timeline.summary().contains("send→first audio —"))
    }

    @Test
    fun `dropped chunks are accounted and surfaced`() {
        val timeline = LatencyTimeline(FakeClock(), log = {})
        timeline.mark(DubEvent.PCM_SENT, nowNanos = 1)
        timeline.recordDroppedChunks(3)
        assertTrue(timeline.summary().contains("DROPPED 3"))
    }

    @Test
    fun `logging is rate limited, and can be forced`() {
        val clock = FakeClock()
        val lines = mutableListOf<String>()
        val timeline = LatencyTimeline(clock, log = { lines += it }, summaryIntervalNanos = 2_000_000_000L)

        timeline.mark(DubEvent.PCM_SENT)
        timeline.maybeLog(nowNanos = 0L)
        assertEquals(1, lines.size)

        timeline.maybeLog(nowNanos = 1_000_000_000L)
        assertEquals(1, lines.size)

        timeline.maybeLog(nowNanos = 2_000_000_000L)
        assertEquals(2, lines.size)

        timeline.maybeLog(nowNanos = 2_100_000_000L, force = true)
        assertEquals(3, lines.size)
    }

    @Test
    fun `the context is appended verbatim`() {
        val lines = mutableListOf<String>()
        val timeline = LatencyTimeline(FakeClock(), log = { lines += it })
        timeline.mark(DubEvent.PCM_SENT, nowNanos = 1)
        timeline.maybeLog(nowNanos = 0L, context = "state=SYNCED")
        assertTrue(lines.single().endsWith("state=SYNCED"))
    }

    @Test
    fun `reset clears every sample`() {
        val timeline = LatencyTimeline(FakeClock(), log = {})
        timeline.mark(DubEvent.PCM_SENT, nowNanos = 1)
        timeline.recordDroppedChunks(5)
        timeline.reset()

        assertEquals(0L, timeline.count(DubEvent.PCM_SENT))
        assertNull(timeline.firstElapsedNanos(DubEvent.PCM_SENT, DubEvent.GEMINI_FIRST_AUDIO))
        assertTrue(!timeline.summary().contains("DROPPED"))
    }

    @Test
    fun `marking without a timestamp uses the injected monotonic clock`() {
        val clock = FakeClock(now = 42L)
        val timeline = LatencyTimeline(clock, log = {})
        timeline.mark(DubEvent.SOURCE_CHUNK)
        clock.now = 84L
        timeline.mark(DubEvent.PCM_SENT)
        assertEquals(42L, timeline.firstElapsedNanos(DubEvent.SOURCE_CHUNK, DubEvent.PCM_SENT))
    }
}
