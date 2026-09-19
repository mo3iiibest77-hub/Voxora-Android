package com.voxora.app.dub.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The playback head unwrap.
 *
 * `AudioTrack` reports its head as a signed 32-bit frame counter, so it wraps. A wrapped value
 * read as a raw total would make the playhead appear to jump backwards by four billion frames,
 * the backlog would look enormous, and the playback timeline would start discarding audio it
 * should have played — so the wrap is worth a test even though it takes 49 hours to happen.
 */
class PlaybackHeadTest {

    private val wrap = 1L shl 32

    @Test
    fun `the first read becomes the total`() {
        val state = PlaybackHead.advance(PlaybackHeadState(), 4_800)
        assertEquals(4_800L, state.total)
        assertEquals(4_800L, state.raw)
    }

    @Test
    fun `successive reads accumulate`() {
        var state = PlaybackHeadState()
        state = PlaybackHead.advance(state, 4_800)
        state = PlaybackHead.advance(state, 9_600)
        state = PlaybackHead.advance(state, 14_400)
        assertEquals(14_400L, state.total)
    }

    @Test
    fun `a repeated read does not advance the total`() {
        var state = PlaybackHead.advance(PlaybackHeadState(), 4_800)
        state = PlaybackHead.advance(state, 4_800)
        assertEquals(4_800L, state.total)
    }

    @Test
    fun `a wrapped head continues past four billion frames`() {
        // 0xFFFFFFFF read as an Int is -1; the unwrap must treat it as the unsigned maximum.
        var state = PlaybackHead.advance(PlaybackHeadState(), 0xFFFFFFFF.toInt())
        assertEquals(wrap - 1, state.total)

        state = PlaybackHead.advance(state, 5)
        assertEquals(wrap - 1 + 6, state.total)
    }

    @Test
    fun `a second wrap continues correctly`() {
        var state = PlaybackHeadState()
        state = PlaybackHead.advance(state, 0xFFFFFFFF.toInt())
        state = PlaybackHead.advance(state, 10)
        val afterFirstWrap = state.total
        assertEquals(wrap - 1 + 11, afterFirstWrap)

        state = PlaybackHead.advance(state, 0xFFFFFFFF.toInt())
        state = PlaybackHead.advance(state, 3)
        assertEquals(afterFirstWrap + (wrap - 1 - 10) + 4, state.total)
    }

    @Test
    fun `the total is always monotonic`() {
        var state = PlaybackHeadState()
        var previous = -1L
        val reads = intArrayOf(0, 1_000, 500_000, 2_000_000, -1, 1, 900_000_000, 5)
        for (raw in reads) {
            state = PlaybackHead.advance(state, raw)
            assertEquals(
                "the unwrapped head must never go backwards",
                true,
                state.total >= previous,
            )
            previous = state.total
        }
    }
}
