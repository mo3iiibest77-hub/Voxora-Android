package com.voxora.app.dub.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The adaptive synchronizer's contract, driven entirely by a fake clock and a fake player.
 *
 * No Android, no coroutines, no hardware: [Sim] advances monotonic time in fixed ticks and
 * moves two content clocks (captured source audio, delivered dubbed audio) the way the real
 * pipeline does. Every scenario the product cares about — a slow model, a jittery network, a
 * user who pauses the video themselves — is a short script on top of it.
 */
class DubSyncControllerTest {

    // ---------------------------------------------------------------- the model

    private class FakePlayer(
        var controllable: Boolean = true,
        var playing: Boolean = true,
    ) : ExternalPlayer {
        var pauseCount = 0
        var playCount = 0
        override fun isControllable(): Boolean = controllable
        override fun isPlaying(): Boolean = playing
        override fun pause() {
            pauseCount++
            playing = false
        }

        override fun play() {
            playCount++
            playing = true
        }

        override fun release() = Unit
    }

    /**
     * A minimal, faithful model of the Live Dub pipeline.
     *
     * - the source content clock advances in real time while the source is playing and not held,
     * - the dub content clock advances in real time once the model's latency has elapsed, and
     *   keeps advancing while the source is held — that is the backlog draining,
     * - [injectSource] adds or removes source content, which is exactly what "the dub fell
     *   behind" means to the synchronizer.
     */
    private class Sim(
        val player: FakePlayer = FakePlayer(),
        val config: SyncConfig = SyncConfig(),
    ) {
        val controller = DubSyncController(player, config, log = {})
        var now = 0L
        var source = 0L
        var dub = 0L
        var freezeDub = false
        private var dubStartNanos = 0L
        val decisions = mutableListOf<SyncDecision>()

        fun start(latencyNanos: Long) {
            controller.start(now)
            dubStartNanos = now + latencyNanos
        }

        fun advance(byNanos: Long, tickMs: Long = 250L) {
            val end = now + byNanos
            val tick = tickMs * MS
            while (now < end) {
                val tickStart = now
                now += tick
                if (!controller.isHoldingSource && player.playing) source += tick
                if (!freezeDub && now >= dubStartNanos) {
                    dub += (now - maxOf(tickStart, dubStartNanos)).coerceAtLeast(0L)
                }
                decisions += controller.tick(now, source, dub)
            }
        }

        fun injectSource(extraNanos: Long) {
            source += extraNanos
        }

        fun countOf(decision: SyncDecision): Int = decisions.count { it == decision }
    }

    // ---------------------------------------------------------------- baselines

    @Test
    fun `zero latency is synced and never corrected`() {
        val sim = Sim()
        sim.start(latencyNanos = 0L)
        sim.advance(10 * SEC)

        assertEquals(SyncState.SYNCED, sim.controller.state)
        assertEquals(0, sim.player.pauseCount)
        assertEquals(0, sim.controller.correctionCount)
    }

    @Test
    fun `small latency is measured as the floor, not corrected`() {
        val sim = Sim()
        sim.start(latencyNanos = 200 * MS)
        sim.advance(10 * SEC)

        assertEquals(SyncState.SYNCED, sim.controller.state)
        assertEquals(0, sim.player.pauseCount)
        assertNear(200 * MS, sim.controller.floorLatencyNanos, 60 * MS)
    }

    /**
     * The load-bearing case: a three-second model latency is *the pipeline*, not a fault. The
     * controller must measure it, report synced, and never pause the user's video for it — which
     * is precisely what a hardcoded "wait three seconds" would get wrong.
     */
    @Test
    fun `three second latency becomes the measured floor, with no correction`() {
        val sim = Sim()
        sim.start(latencyNanos = 3 * SEC)
        sim.advance(14 * SEC)

        assertEquals(SyncState.SYNCED, sim.controller.state)
        assertEquals(0, sim.player.pauseCount)
        assertEquals(0, sim.controller.correctionCount)
        assertNear(3 * SEC, sim.controller.floorLatencyNanos, 100 * MS)
    }

    @Test
    fun `the floor is measured, so an unusual latency is learned too`() {
        val slow = Sim()
        slow.start(latencyNanos = 4_500 * MS)
        slow.advance(16 * SEC)
        assertNear(4_500 * MS, slow.controller.floorLatencyNanos, 150 * MS)
        assertEquals(0, slow.player.pauseCount)

        val fast = Sim()
        fast.start(latencyNanos = 700 * MS)
        fast.advance(10 * SEC)
        assertNear(700 * MS, fast.controller.floorLatencyNanos, 100 * MS)
        assertEquals(0, fast.player.pauseCount)
    }

    @Test
    fun `latency that wobbles inside the tolerance stays synced`() {
        val sim = Sim()
        sim.start(latencyNanos = 2_500 * MS)
        sim.advance(10 * SEC)
        assertEquals(SyncState.SYNCED, sim.controller.state)

        repeat(12) {
            sim.injectSource(120 * MS)
            sim.advance(1 * SEC)
            sim.injectSource(-120 * MS)
            sim.advance(1 * SEC)
        }

        assertEquals(SyncState.SYNCED, sim.controller.state)
        assertEquals(0, sim.player.pauseCount)
    }

    /**
     * The property that separates a floor from the old baseline: a burst is corrected *back* to the
     * pipeline's demonstrated best, so it can never become the new normal.
     */
    @Test
    fun `a burst cannot raise the target, so the floor stays at the pipeline's best`() {
        val sim = Sim()
        sim.start(latencyNanos = 2_500 * MS)
        sim.advance(10 * SEC)
        val floorBefore = sim.controller.floorLatencyNanos
        assertNear(2_500 * MS, floorBefore, 100 * MS)

        sim.injectSource(3 * SEC)
        sim.advance(8 * SEC)

        assertTrue(
            "the floor must not be raised by a burst, was $floorBefore " +
                "now ${sim.controller.floorLatencyNanos}",
            sim.controller.floorLatencyNanos <= floorBefore + 50 * MS,
        )
    }

    @Test
    fun `the reported excess is the delay above the floor`() {
        val sim = Sim()
        sim.start(latencyNanos = 2 * SEC)
        sim.advance(8 * SEC)
        assertEquals(0L, sim.controller.excessNanos)

        sim.injectSource(1 * SEC)
        sim.advance(250 * MS)

        assertTrue("expected a positive excess", sim.controller.excessNanos >= 900 * MS)
    }

    // ---------------------------------------------------------------- correction

    @Test
    fun `a drift spike pauses the source once and resumes when caught up`() {
        val sim = Sim()
        sim.start(latencyNanos = 2_500 * MS)
        sim.advance(10 * SEC)

        sim.injectSource(2 * SEC)
        sim.advance(500 * MS)
        assertEquals(SyncState.CORRECTING, sim.controller.state)
        assertTrue(sim.controller.isHoldingSource)
        assertEquals(1, sim.player.pauseCount)
        assertEquals(1, sim.countOf(SyncDecision.PAUSE_SOURCE))

        sim.advance(5 * SEC)
        assertEquals(SyncState.SYNCED, sim.controller.state)
        assertFalse(sim.controller.isHoldingSource)
        assertEquals(1, sim.player.playCount)
        assertEquals(1, sim.countOf(SyncDecision.RESUME_SOURCE))
        assertEquals(1, sim.controller.correctionCount)
    }

    @Test
    fun `a correction never holds the source longer than the bound`() {
        val sim = Sim()
        sim.start(latencyNanos = 2 * SEC)
        sim.advance(8 * SEC)

        // A drift so large it cannot be drained inside the bound.
        sim.injectSource(30 * SEC)
        sim.advance(500 * MS)
        assertEquals(SyncState.CORRECTING, sim.controller.state)

        sim.advance(6 * SEC)
        assertFalse(sim.controller.isHoldingSource)
        assertEquals(1, sim.player.playCount)
        assertEquals(SyncState.SYNCED, sim.controller.state)
    }

    @Test
    fun `corrections are rate limited and spaced by the cooldown`() {
        val sim = Sim()
        sim.start(latencyNanos = 2 * SEC)
        sim.advance(8 * SEC)

        sim.injectSource(2 * SEC)
        sim.advance(500 * MS)
        assertEquals(1, sim.player.pauseCount)
        sim.advance(4 * SEC)
        assertEquals(1, sim.player.playCount)

        // A second spike immediately after the first correction must not pause again.
        sim.injectSource(2 * SEC)
        sim.advance(2 * SEC)
        assertEquals(1, sim.player.pauseCount)
    }

    @Test
    fun `a controllable source that is playing may be corrected`() {
        // "Video fallback": a video player is steerable through its media session, so drift can
        // be corrected even though the frames themselves cannot be delayed. Only the transport
        // is touched, and only when it is actually playing.
        val sim = Sim()
        sim.start(latencyNanos = 2 * SEC)
        sim.advance(8 * SEC)
        assertTrue(sim.player.controllable)
        assertTrue(sim.player.playing)

        sim.injectSource(2 * SEC)
        sim.advance(500 * MS)
        assertEquals(1, sim.player.pauseCount)
    }

    // ---------------------------------------------------------------- the user

    @Test
    fun `a user pause is detected and never fought`() {
        val sim = Sim()
        sim.start(latencyNanos = 2 * SEC)
        sim.advance(8 * SEC)

        sim.player.playing = false
        sim.injectSource(2 * SEC)
        sim.advance(6 * SEC)

        assertEquals(0, sim.player.pauseCount)
        assertEquals(0, sim.player.playCount)
        assertEquals(0, sim.controller.correctionCount)
    }

    @Test
    fun `a user resume re-engages the synchronizer`() {
        val sim = Sim()
        sim.start(latencyNanos = 2 * SEC)
        sim.advance(8 * SEC)

        sim.player.playing = false
        sim.advance(4 * SEC)
        sim.player.playing = true
        sim.advance(2 * SEC)

        sim.injectSource(2 * SEC)
        sim.advance(500 * MS)
        assertEquals(1, sim.player.pauseCount)
    }

    // ---------------------------------------------------------------- availability

    @Test
    fun `an uncontrollable source falls back to audio only and is never paused`() {
        val sim = Sim(player = FakePlayer(controllable = false))
        sim.start(latencyNanos = 2_500 * MS)
        sim.advance(14 * SEC)

        assertEquals(SyncState.AUDIO_ONLY, sim.controller.state)
        assertEquals(0, sim.player.pauseCount)
        assertEquals(0, sim.player.playCount)
    }

    @Test
    fun `losing the media session releases the source and falls back`() {
        val sim = Sim()
        sim.start(latencyNanos = 2 * SEC)
        sim.advance(8 * SEC)
        sim.injectSource(2 * SEC)
        sim.advance(500 * MS)
        assertTrue(sim.controller.isHoldingSource)

        sim.player.controllable = false
        sim.advance(500 * MS)

        assertFalse(sim.controller.isHoldingSource)
        assertEquals(1, sim.player.playCount)
        assertEquals(SyncState.AUDIO_ONLY, sim.controller.state)
    }

    /**
     * A dub that stops arriving entirely is not "drift" — the first response (pause the source
     * and wait) is correct, and the safety property is that it does not become a trap: the
     * claim is withdrawn, the source is released, and it never thrashes.
     */
    @Test
    fun `a stalled dub withdraws the sync claim and never leaves the source paused`() {
        val sim = Sim()
        sim.start(latencyNanos = 2 * SEC)
        sim.advance(8 * SEC)

        sim.freezeDub = true
        sim.advance(20 * SEC)

        assertEquals(SyncState.UNAVAILABLE, sim.controller.state)
        assertFalse(sim.controller.isHoldingSource)
        assertTrue("the source must be left playing", sim.player.playing)
        assertTrue("at most one correction for a dead pipeline", sim.player.pauseCount <= 1)
        assertEquals(1, sim.player.playCount)
    }

    @Test
    fun `a stall while holding the source releases it`() {
        val sim = Sim()
        sim.start(latencyNanos = 2 * SEC)
        sim.advance(8 * SEC)
        sim.injectSource(4 * SEC)
        sim.advance(500 * MS)
        assertTrue(sim.controller.isHoldingSource)

        sim.freezeDub = true
        sim.advance(8 * SEC)

        assertFalse(sim.controller.isHoldingSource)
        assertEquals(1, sim.player.playCount)
        assertEquals(SyncState.UNAVAILABLE, sim.controller.state)
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    fun `stopping while synchronized leaves the source alone`() {
        val sim = Sim()
        sim.start(latencyNanos = 2 * SEC)
        sim.advance(8 * SEC)
        assertEquals(SyncState.SYNCED, sim.controller.state)

        sim.controller.stop()

        assertEquals(SyncState.IDLE, sim.controller.state)
        assertEquals(0, sim.player.playCount)
        assertFalse(sim.controller.isHoldingSource)
    }

    @Test
    fun `stopping while correcting resumes the source`() {
        val sim = Sim()
        sim.start(latencyNanos = 2 * SEC)
        sim.advance(8 * SEC)
        sim.injectSource(2 * SEC)
        sim.advance(500 * MS)
        assertTrue(sim.controller.isHoldingSource)

        sim.controller.stop()

        assertEquals(SyncState.IDLE, sim.controller.state)
        assertFalse(sim.controller.isHoldingSource)
        assertEquals(1, sim.player.playCount)
        assertTrue(sim.player.playing)
    }

    @Test
    fun `a gemini reconnect releases the source and re-measures`() {
        val sim = Sim()
        sim.start(latencyNanos = 2 * SEC)
        sim.advance(8 * SEC)
        sim.injectSource(2 * SEC)
        sim.advance(500 * MS)
        assertTrue(sim.controller.isHoldingSource)

        sim.controller.onGeminiReconnect(sim.now)

        assertFalse(sim.controller.isHoldingSource)
        assertEquals(1, sim.player.playCount)
        assertEquals(SyncState.WARMING_UP, sim.controller.state)
        assertEquals(-1L, sim.controller.floorLatencyNanos)

        // It settles again on the new connection rather than staying in warm-up forever.
        sim.advance(10 * SEC)
        assertEquals(SyncState.SYNCED, sim.controller.state)
        assertTrue(sim.controller.floorLatencyNanos > 0L)
    }

    @Test
    fun `ticks before start do nothing`() {
        val sim = Sim()
        assertEquals(SyncDecision.NONE, sim.controller.tick(0L, 0L, 0L))
        assertEquals(SyncState.IDLE, sim.controller.state)
    }

    private fun assertNear(expected: Long, actual: Long, tolerance: Long) {
        assertTrue(
            "expected $expected ns (±$tolerance) but was $actual ns",
            actual >= expected - tolerance && actual <= expected + tolerance,
        )
    }

    private companion object {
        const val MS = 1_000_000L
        const val SEC = 1_000_000_000L
    }
}
