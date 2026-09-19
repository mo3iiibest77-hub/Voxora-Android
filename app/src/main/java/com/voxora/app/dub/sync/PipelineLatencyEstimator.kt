package com.voxora.app.dub.sync

/**
 * Learns the pipeline's **latency floor** — the smallest source/dub offset the pipeline has actually
 * demonstrated recently — and separates it from the delay that is merely self-inflicted.
 *
 * ## Why a floor, and not a baseline
 *
 * The previous model learned the *current* steady-state offset as a baseline and corrected only
 * deviations from it. That is correct for drift, but it also means any avoidable delay that had
 * already settled when the baseline was learned became permanent: an output backlog, an oversized
 * hand-off buffer, a tolerance that had adapted upward — all of it was accepted as "the pipeline's
 * latency" and never attacked. A constant 4 s offset reported `SYNCED`.
 *
 * The floor fixes that. The offset is sampled continuously while the pipeline is active; the
 * *minimum* over a sliding window is the best the pipeline has shown, and everything above it is
 * delay we introduced. `excess = current − floor` is the amount worth correcting, and it goes to
 * zero when the dub is running as close to the source as the pipeline allows. The floor still
 * follows a genuinely slower pipeline upward (old, lower samples expire from the window) and a
 * genuinely faster one downward.
 *
 * ## Why a sliding window and not a running minimum
 *
 * A running minimum would be dragged to whatever the offset was at start-up — near zero, before the
 * first dubbed audio had been produced — and every correction would then chase a floor that is
 * physically unreachable. The window forgets; start-up is excluded both by the window and by the
 * caller feeding offsets only while the pipeline is active.
 *
 * ## Why the minimum and not the mean
 *
 * The mean is inflated by every burst and by every correction, so a mean-based target drifts upward
 * over a long session. The minimum is stable: a burst cannot raise it, and it only moves when the
 * pipeline's own best case really moves.
 *
 * Pure Kotlin, no Android, no clock: the caller supplies `nowNanos`, so the window expiry is a
 * deterministic unit test.
 */
class PipelineLatencyEstimator(
    private val windowNanos: Long = DEFAULT_WINDOW_NANOS,
    private val bucketNanos: Long = DEFAULT_BUCKET_NANOS,
    private val currentAlpha: Double = DEFAULT_ALPHA,
) {
    init {
        require(windowNanos > 0L) { "windowNanos must be positive" }
        require(bucketNanos > 0L) { "bucketNanos must be positive" }
        require(currentAlpha > 0.0 && currentAlpha <= 1.0) { "currentAlpha must be in (0, 1]" }
    }

    private val bucketCount: Int = ((windowNanos + bucketNanos - 1) / bucketNanos).toInt().coerceAtLeast(1)
    private val bucketStart = LongArray(bucketCount) { Long.MIN_VALUE }
    private val bucketMin = LongArray(bucketCount) { Long.MAX_VALUE }

    private var hasCurrent = false
    private var current = 0L
    private var lastNow = Long.MIN_VALUE

    /** True once at least one offset has been sampled. */
    val hasCurrentNanos: Boolean get() = hasCurrent

    /** The smoothed current offset, or `0` before the first sample. */
    val currentNanos: Long get() = current

    /** True once the window holds at least one live sample. */
    val hasFloorNanos: Boolean get() = liveBucketCount() > 0

    /**
     * The smallest offset seen inside the window — the pipeline's demonstrated best. Falls back to
     * the current offset before the window has any live sample, and to `0` before any sample at all.
     */
    val floorNanos: Long
        get() {
            if (liveBucketCount() == 0) return if (hasCurrent) current else 0L
            var min = Long.MAX_VALUE
            for (i in 0 until bucketCount) {
                if (!isLive(i)) continue
                if (bucketMin[i] < min) min = bucketMin[i]
            }
            return min
        }

    /** The delay above the floor: the part worth correcting. Never negative. */
    val excessNanos: Long
        get() = if (!hasCurrent || !hasFloorNanos) 0L else (current - floorNanos).coerceAtLeast(0L)

    /**
     * Feeds one offset sample. Call only while the pipeline is active — the source is advancing and
     * dubbed audio is flowing — so a paused source or a stalled dub cannot pollute the floor.
     */
    @Synchronized
    fun onOffset(nowNanos: Long, offsetNanos: Long) {
        lastNow = nowNanos
        current = if (!hasCurrent) {
            hasCurrent = true
            offsetNanos
        } else {
            (current + (offsetNanos - current) * currentAlpha).toLong()
        }

        val index = Math.floorMod(nowNanos / bucketNanos, bucketCount.toLong()).toInt()
        val expectedStart = Math.floorDiv(nowNanos, bucketNanos) * bucketNanos
        if (bucketStart[index] != expectedStart) {
            bucketStart[index] = expectedStart
            bucketMin[index] = offsetNanos
        } else if (offsetNanos < bucketMin[index]) {
            bucketMin[index] = offsetNanos
        }
    }

    /** Discards every sample. Used when a new session starts or the source is re-measured. */
    @Synchronized
    fun reset() {
        for (i in 0 until bucketCount) {
            bucketStart[i] = Long.MIN_VALUE
            bucketMin[i] = Long.MAX_VALUE
        }
        hasCurrent = false
        current = 0L
        lastNow = Long.MIN_VALUE
    }

    private fun liveBucketCount(): Int {
        if (lastNow == Long.MIN_VALUE) return 0
        var n = 0
        for (i in 0 until bucketCount) if (isLive(i)) n++
        return n
    }

    private fun isLive(i: Int): Boolean {
        val start = bucketStart[i]
        if (start == Long.MIN_VALUE) return false
        return lastNow - start < windowNanos
    }

    companion object {
        /** How far back the floor looks. Long enough to outlast a burst, short enough to adapt. */
        const val DEFAULT_WINDOW_NANOS = 30_000_000_000L

        /** The resolution of the window. Sixty buckets of this cover [DEFAULT_WINDOW_NANOS]. */
        const val DEFAULT_BUCKET_NANOS = 500_000_000L

        /** Weight of a new sample in the current-offset EWMA. */
        const val DEFAULT_ALPHA = 0.2
    }
}
