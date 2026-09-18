package com.voxora.app.dub.sync

/**
 * An `AudioTrack` playback head as read from the device: the last raw 32-bit value, and the
 * monotonic total it corresponds to.
 */
data class PlaybackHeadState(val raw: Long = -1L, val total: Long = 0L)

/**
 * Unwraps `AudioTrack.getPlaybackHeadPosition()`.
 *
 * The platform reports the head as a signed 32-bit frame counter, so it wraps — after about
 * 49 hours at 24 kHz, but also, more importantly, whenever the track is recreated. A wrapped
 * value read as a raw total would make the playhead appear to jump backwards by four billion
 * frames, the backlog would look enormous, and the timeline would start discarding audio it
 * should have played. Unwrapping it into a monotonic frame count is therefore not a nicety.
 *
 * Pure Kotlin so the wrap can be tested without waiting 49 hours.
 */
object PlaybackHead {
    private const val WRAP = 1L shl 32

    /** The state after the device reported [raw]. */
    fun advance(state: PlaybackHeadState, raw: Int): PlaybackHeadState {
        val current = raw.toLong() and 0xFFFFFFFFL
        val total = when {
            state.raw < 0L -> current
            current < state.raw -> state.total + (current - state.raw) + WRAP
            else -> state.total + (current - state.raw)
        }
        return PlaybackHeadState(current, total)
    }
}
