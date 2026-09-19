package com.voxora.app.dub.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dub-side playback timeline's contract, driven entirely by a fake clock and a fake device
 * playhead.
 *
 * The model is deliberately faithful about the one thing that matters: the device can only play
 * audio it has been given, and it plays it at real time. [Sim.advance] moves wall time forward
 * and the playhead with it, capped by what has been written — so if the timeline stops feeding
 * the output, the playhead genuinely stops, and the backlog reflects that.
 */
class PlaybackTimelineTest {

    private val MS = 1_000_000L
    private fun ms(n: Long) = n * MS

    /** One Gemini chunk at the output rate: the pipeline's natural unit. */
    private val chunk = ms(60)

    private class Sim(
        val config: PlaybackTimelineConfig = PlaybackTimelineConfig(),
    ) {
        val timeline = PlaybackTimeline(config, log = {})
        var now = 0L
        var written = 0L
        var played = 0L
        var playedChunks = 0
        var droppedChunks = 0

        fun start() {
            timeline.start(now, played)
        }

        /** Wall time passes and the device plays, but never more than it was given. */
        fun advance(dt: Long) {
            now += dt
            played = minOf(written, played + dt)
        }

        fun arrive(duration: Long): ChunkAction {
            val action = timeline.onChunkArrived(now, duration, played)
            if (action == ChunkAction.PLAY) {
                written += duration
                playedChunks++
            } else {
                droppedChunks++
            }
            return action
        }

        /** An arrival that also reports the audio still sitting in the Gemini hand-off buffer. */
        fun arriveWithHandoff(duration: Long, handoff: Long): ChunkAction {
            val action = timeline.onChunkArrived(now, duration, played, handoff)
            if (action == ChunkAction.PLAY) {
                written += duration
                playedChunks++
            } else {
                droppedChunks++
            }
            return action
        }

        /**
         * A healthy pipeline: the output is primed with a cushion, then chunks arrive at exactly
         * the rate they are consumed.
         *
         * The priming matters. A buffered output never runs with zero cushion — the writer is
         * always a chunk or so ahead — and without modelling that, a perfectly healthy pipeline
         * would look starved at every arrival instant.
         */
        fun steady(count: Int) {
            arrive(60_000_000L)
            arrive(60_000_000L)
            repeat(count) {
                advance(60_000_000L)
                arrive(60_000_000L)
            }
        }

        /** A burst: many chunks arrive with no time passing, as after a stall or a reconnect. */
        fun burst(count: Int, duration: Long = 60_000_000L) {
            repeat(count) { arrive(duration) }
        }
    }

    // ------------------------------------------------------------ steady state

    @Test
    fun `a steady realtime stream is played in full and never dropped`() {
        val sim = Sim()
        sim.start()
        sim.steady(300)

        assertEquals("no chunk may be discarded on a healthy link", 0L, sim.timeline.dropCount)
        assertEquals(0, sim.droppedChunks)
        assertEquals("every chunk, priming included, must reach the output", 302, sim.playedChunks)
        assertEquals("a healthy link must not starve", 0L, sim.timeline.underrunCount)
    }

    @Test
    fun `a steady stream keeps the backlog inside the tolerance`() {
        val sim = Sim()
        sim.start()
        sim.steady(300)

        assertTrue(
            "backlog ${sim.timeline.backlogNanos} exceeded tolerance ${sim.timeline.toleranceNanos}",
            sim.timeline.backlogNanos <= sim.timeline.toleranceNanos,
        )
    }

    @Test
    fun `the backlog reflects the output buffer rather than the write cursor`() {
        val sim = Sim()
        sim.start()
        sim.steady(60)

        // The device lags the writer by the buffer's own lead; a timeline that counted writes as
        // playback would report zero here and would never see a backlog at all.
        assertTrue("expected a non-zero backlog", sim.timeline.backlogNanos > 0L)
        assertEquals(sim.written - sim.played, sim.timeline.backlogNanos)
    }

    // ------------------------------------------------------------ bursts and spikes

    @Test
    fun `a burst is trimmed instead of being played out as permanent delay`() {
        val sim = Sim()
        sim.start()
        sim.steady(60)
        val backlogBeforeBurst = sim.timeline.backlogNanos

        sim.burst(30) // 1.8s of audio arriving at once

        assertTrue("a burst must trigger discards", sim.timeline.dropCount > 0L)
        assertTrue(
            "backlog must be back inside the tolerance, was ${sim.timeline.backlogNanos}",
            sim.timeline.backlogNanos <= sim.timeline.toleranceNanos,
        )
        assertTrue(
            "the backlog must not have grown by the whole burst",
            sim.timeline.backlogNanos < backlogBeforeBurst + 30 * chunk,
        )
    }

    @Test
    fun `a burst still plays what it can rather than discarding everything`() {
        val sim = Sim()
        sim.start()
        sim.steady(60)
        val playedBefore = sim.playedChunks

        sim.burst(30)

        assertTrue("some of the burst must still play", sim.playedChunks > playedBefore)
    }

    /**
     * The hand-off buffer used to be invisible: audio received from Gemini but not yet scheduled was
     * not in the backlog, so a full buffer added delay nothing measured. It is now part of the
     * decision.
     */
    @Test
    fun `audio still in the hand-off buffer counts towards the backlog`() {
        val sim = Sim()
        sim.start()
        sim.steady(60)
        assertTrue(
            "the output backlog alone is inside the tolerance",
            sim.timeline.backlogNanos <= sim.timeline.toleranceNanos,
        )

        val action = sim.arriveWithHandoff(chunk, sim.config.maxToleranceNanos)

        assertEquals(ChunkAction.DROP, action)
        assertTrue(sim.timeline.handoffBacklogNanos > 0L)
        assertEquals(
            "the total backlog must include the hand-off buffer",
            sim.timeline.backlogNanos + sim.timeline.handoffBacklogNanos,
            sim.timeline.totalBacklogNanos,
        )
    }

    @Test
    fun `a chunk arriving with an empty hand-off is played`() {
        val sim = Sim()
        sim.start()
        sim.steady(60)

        assertEquals(ChunkAction.PLAY, sim.arriveWithHandoff(chunk, 0L))
    }

    @Test
    fun `a large burst cannot grow the backlog without bound`() {
        val sim = Sim()
        sim.start()
        sim.steady(60)
        sim.burst(500)

        assertTrue(
            "backlog ${sim.timeline.backlogNanos} exceeded tolerance ${sim.timeline.toleranceNanos}",
            sim.timeline.backlogNanos <= sim.timeline.toleranceNanos,
        )
    }

    @Test
    fun `a single late chunk is absorbed without discarding audio`() {
        val sim = Sim()
        sim.start()
        sim.steady(60)

        sim.advance(ms(180)) // the model was late, then catches up
        sim.arrive(chunk)

        assertEquals(0L, sim.timeline.dropCount)
    }

    @Test
    fun `variable chunk sizes do not accumulate backlog`() {
        val sim = Sim()
        sim.start()
        // A model that emits unevenly: short and long chunks alternating, at real time overall.
        var t = 0L
        repeat(200) { i ->
            val d = if (i % 2 == 0) ms(40) else ms(80)
            sim.advance(d)
            sim.arrive(d)
            t += d
        }

        assertTrue("expected real time to have passed", t > 0L)
        assertTrue(
            "backlog ${sim.timeline.backlogNanos} exceeded tolerance ${sim.timeline.toleranceNanos}",
            sim.timeline.backlogNanos <= sim.timeline.toleranceNanos,
        )
        assertEquals(0L, sim.timeline.dropCount)
    }

    // ------------------------------------------------------------ adaptation

    @Test
    fun `running dry loosens the tolerance`() {
        val sim = Sim()
        sim.start()
        sim.steady(5)
        val before = sim.timeline.toleranceNanos

        // A long silence, then audio resumes: the output had nothing to play.
        sim.advance(ms(5_000))
        sim.arrive(chunk)

        assertTrue("starvation must be counted", sim.timeline.underrunCount > 0L)
        assertTrue(
            "tolerance must loosen after starving, was $before now ${sim.timeline.toleranceNanos}",
            sim.timeline.toleranceNanos > before,
        )
    }

    @Test
    fun `the tolerance is clamped at its ceiling`() {
        val sim = Sim()
        sim.start()
        repeat(20) {
            sim.steady(3)
            sim.advance(ms(5_000))
            sim.arrive(chunk)
        }

        assertTrue(
            "tolerance ${sim.timeline.toleranceNanos} exceeded its ceiling",
            sim.timeline.toleranceNanos <= sim.config.maxToleranceNanos,
        )
    }

    @Test
    fun `a quiet pipeline tightens the tolerance back`() {
        val sim = Sim()
        sim.start()
        // Loosen it first.
        repeat(4) {
            sim.steady(3)
            sim.advance(ms(5_000))
            sim.arrive(chunk)
        }
        val loosened = sim.timeline.toleranceNanos
        assertTrue("expected the tolerance to have loosened", loosened > sim.config.minToleranceNanos)

        // Then a long, healthy stretch with no starvation at all.
        sim.steady(2_000)

        assertTrue(
            "tolerance must tighten again, was $loosened now ${sim.timeline.toleranceNanos}",
            sim.timeline.toleranceNanos < loosened,
        )
        assertTrue(
            "tolerance must not tighten past its floor",
            sim.timeline.toleranceNanos >= sim.config.minToleranceNanos,
        )
    }

    // ------------------------------------------------------------ reconnect and rebasing

    @Test
    fun `a reconnect rebases the playhead instead of reporting a backlog`() {
        val sim = Sim()
        sim.start()
        sim.steady(60)
        assertTrue(sim.timeline.backlogNanos > 0L)

        sim.timeline.reset(sim.now, sim.played)

        assertEquals("a reconnect must not leave a phantom backlog", 0L, sim.timeline.backlogNanos)
        assertEquals(0L, sim.timeline.dropCount)
    }

    @Test
    fun `the first chunk after a reconnect is played, not discarded`() {
        val sim = Sim()
        sim.start()
        sim.steady(60)

        sim.timeline.reset(sim.now, sim.played)

        assertEquals(ChunkAction.PLAY, sim.arrive(chunk))
    }

    @Test
    fun `a reconnect does not carry the previous session's drop count`() {
        val sim = Sim()
        sim.start()
        sim.steady(60)
        sim.burst(30)
        assertTrue(sim.timeline.dropCount > 0L)

        sim.timeline.reset(sim.now, sim.played)

        assertEquals(0L, sim.timeline.dropCount)
        assertEquals(0L, sim.timeline.droppedNanos)
        assertEquals(0L, sim.timeline.underrunCount)
    }

    @Test
    fun `a playhead that goes backwards is rebased, not read as a huge backlog`() {
        val sim = Sim()
        sim.start()
        sim.steady(60)
        val backlogBefore = sim.timeline.backlogNanos

        // A new output track reports a head that starts again from a lower value.
        sim.timeline.onPlayed(0L)

        assertEquals(
            "a backwards head must not fabricate a backlog",
            backlogBefore,
            sim.timeline.backlogNanos,
        )
        assertEquals(ChunkAction.PLAY, sim.arrive(chunk))
    }

    // ------------------------------------------------------------ lifecycle

    @Test
    fun `stopping withdraws the timeline and stops discarding`() {
        val sim = Sim()
        sim.start()
        sim.steady(60)
        sim.burst(30)
        val dropsWhileRunning = sim.timeline.dropCount
        assertTrue(dropsWhileRunning > 0L)

        sim.timeline.stop()
        sim.burst(50)

        assertFalse(sim.timeline.isRunning)
        assertEquals(
            "a stopped timeline must not keep discarding audio",
            dropsWhileRunning,
            sim.timeline.dropCount,
        )
    }

    @Test
    fun `a zero length chunk is ignored`() {
        val sim = Sim()
        sim.start()
        sim.steady(5)
        val scheduled = sim.timeline.scheduledNanos

        assertEquals(ChunkAction.PLAY, sim.arrive(0L))
        assertEquals(scheduled, sim.timeline.scheduledNanos)
    }

    @Test
    fun `chunks arriving before start are played and change nothing`() {
        val sim = Sim()
        assertEquals(ChunkAction.PLAY, sim.arrive(chunk))
        assertEquals(0L, sim.timeline.scheduledNanos)
        assertFalse(sim.timeline.isRunning)
    }

    // ------------------------------------------------------------ accounting

    @Test
    fun `discarded audio is accounted exactly`() {
        val sim = Sim()
        sim.start()
        sim.steady(60)
        sim.burst(30)

        assertEquals(
            "dropped duration must equal the dropped chunks",
            sim.droppedChunks * chunk,
            sim.timeline.droppedNanos,
        )
        assertEquals(sim.droppedChunks.toLong(), sim.timeline.dropCount)
    }

    @Test
    fun `the peak backlog is reported for the instrumentation line`() {
        val sim = Sim()
        sim.start()
        sim.steady(60)
        sim.burst(30)

        assertTrue("peak backlog must be observable", sim.timeline.peakBacklogNanos > 0L)
    }

    // ------------------------------------------------------------ concurrency

    @Test
    fun `concurrent arrivals and playhead updates keep the backlog bounded`() {
        // The real pipeline calls onChunkArrived from the audio consumer and onPlayed from the
        // synchronizer's tick. The assertion is scheduling-independent: the playhead only ever
        // moves forward, so the backlog after the last arrival cannot exceed the tolerance.
        val timeline = PlaybackTimeline(log = {})
        timeline.start(0L, 0L)
        val playhead = java.util.concurrent.atomic.AtomicLong(0L)
        val now = java.util.concurrent.atomic.AtomicLong(0L)

        val arrivals = Thread {
            repeat(2_000) {
                timeline.onChunkArrived(now.addAndGet(1_000_000L), chunk, playhead.get())
            }
        }
        val advances = Thread {
            repeat(2_000) { playhead.addAndGet(chunk) }
        }

        arrivals.start()
        advances.start()
        arrivals.join()
        advances.join()

        assertTrue(
            "backlog ${timeline.backlogNanos} exceeded tolerance ${timeline.toleranceNanos}",
            timeline.backlogNanos <= timeline.toleranceNanos,
        )
    }

    // ------------------------------------------------------------ configuration
    @Test(expected = IllegalArgumentException::class)
    fun `an underrun threshold at the minimum tolerance is rejected`() {
        // A threshold that high would make every healthy pipeline look starved.
        PlaybackTimelineConfig(
            minToleranceNanos = 40_000_000L,
            initialToleranceNanos = 350_000_000L,
            maxToleranceNanos = 800_000_000L,
            underrunNanos = 40_000_000L,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an initial tolerance outside the bounds is rejected`() {
        PlaybackTimelineConfig(
            initialToleranceNanos = 1_000_000_000L,
            maxToleranceNanos = 800_000_000L,
        )
    }
}
