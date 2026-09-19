package com.voxora.app.dub.sync

/**
 * The source side of the synchronization model: how much source audio has been captured, and how
 * far behind real time the capture pipeline is.
 *
 * ## Why the frame counter and not a sum of chunk lengths
 *
 * The previous model advanced the source clock by adding each captured chunk's own duration. That
 * is a *content* clock built by the app, and it silently loses frames whenever a read returns short
 * or a chunk is dropped. This class advances from the device's own cumulative **frame position**
 * (`AudioRecord.getTimestamp().framePosition`), so the source clock is exactly what the platform
 * captured, in frames, converted once to nanoseconds at [sampleRateHz]. The two are equal on a
 * healthy read loop and differ exactly when the old model was wrong.
 *
 * ## The silence/hold gate, and why it is not optional
 *
 * [contentNanos] only advances for a chunk the caller marks as [counts]. The caller passes `false`
 * for a silent chunk and while the synchronizer is holding the source paused. Without that gate a
 * paused source would look like it kept moving — capture keeps running through silence — and the
 * controller would chase a drift that does not exist, and a user pause would be read as a huge
 * accumulated offset when it ended. Skipped frames are still absorbed into [lastFrames] so they
 * cannot be counted later as a burst.
 *
 * ## The capture timestamp
 *
 * [captureLatencyNanos] is the one number only the device can give: the monotonic time the platform
 * associates with the most recently captured frame, subtracted from now. It is the capture
 * pipeline's own contribution to the end-to-end delay, and it is `null` on a device that does not
 * report a timestamp — reported as unavailable rather than invented.
 *
 * Pure Kotlin, no Android, no clock of its own: the caller supplies every timestamp, so every
 * scenario is a deterministic unit test.
 *
 * **Thread-safe.** The capture thread writes while the sync tick and the diagnostics read, so the
 * mutators are synchronized and the reported values are volatile.
 */
class SourceClock(private val sampleRateHz: Int) {
    init {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
    }

    /**
     * The source content position in nanoseconds of audio: the captured frames the caller counted,
     * converted at [sampleRateHz]. Advanced only for a chunk marked [onCapture]`(counts = true)`.
     */
    @Volatile
    var contentNanos: Long = 0L
        private set

    /** True once the platform has reported at least one real capture timestamp. */
    @Volatile
    var hasDeviceTimestamp: Boolean = false
        private set

    /** The device's cumulative captured frame count at the previous sample, or `-1` before any. */
    private var lastFrames = -1L

    /** The monotonic time the device associated with [lastFrames], or `-1` if unavailable. */
    private var lastCapturedAtNanos = -1L

    /**
     * Records one capture sample.
     *
     * @param frames the device's cumulative captured frame count (`framePosition`), or the caller's
     *   own running count when the platform reports no timestamp. Must not be negative.
     * @param capturedAtNanos the monotonic time the device associates with [frames]; the caller
     *   passes [nowNanos] when it has no device timestamp.
     * @param nowNanos current monotonic time.
     * @param fromDevice whether [capturedAtNanos] is a real platform timestamp.
     * @param counts whether this chunk's frames advance [contentNanos]. False for silence and while
     *   the synchronizer holds the source.
     */
    @Synchronized
    fun onCapture(
        frames: Long,
        capturedAtNanos: Long,
        nowNanos: Long,
        fromDevice: Boolean,
        counts: Boolean,
    ) {
        if (frames < 0L) return
        if (lastFrames >= 0L && counts) {
            val delta = (frames - lastFrames).coerceAtLeast(0L)
            contentNanos += delta * 1_000_000_000L / sampleRateHz
        }
        lastFrames = frames
        lastCapturedAtNanos = capturedAtNanos
        if (fromDevice) hasDeviceTimestamp = true
    }

    /**
     * How long ago the most recently captured frame was actually captured, in nanoseconds, or
     * `null` when the device reports no timestamp. This is the capture pipeline's own latency and
     * is never fabricated.
     */
    @Synchronized
    fun captureLatencyNanos(nowNanos: Long): Long? {
        if (!hasDeviceTimestamp) return null
        val at = lastCapturedAtNanos
        if (at < 0L) return null
        return (nowNanos - at).coerceAtLeast(0L)
    }

    /** Discards the run's history. */
    @Synchronized
    fun reset() {
        contentNanos = 0L
        hasDeviceTimestamp = false
        lastFrames = -1L
        lastCapturedAtNanos = -1L
    }
}
