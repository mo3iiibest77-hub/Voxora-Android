package com.voxora.app.dub.sync

/**
 * Decides a small, bounded output playback-rate trim that drains a backlog without skipping audio.
 *
 * ## Where this sits in the correction order
 *
 * The correction order is: **bound the backlog first, then trim the rate, never seek.** Dropping a
 * chunk skips the content it held and is audible; a rate trim of a few percent is not, and it
 * removes the same delay more gently. So the rate trim only ever engages for a *small* excess that
 * still has queued audio behind it, and the drop policy in [PlaybackTimeline] handles anything
 * larger. This class never asks for a rate outside [minRate]..[maxRate].
 *
 * ## Why it is rate-limited and hysteretic
 *
 * Playing faster than the model produces audio only drains what is already queued; with nothing
 * queued it would underrun and stutter. So the rate returns to exactly `1.0` the moment the backlog
 * reaches zero or the excess falls back inside the dead band, and it may only move one step per
 * cooldown, so a wobbling offset cannot make the pitch oscillate.
 *
 * Pure Kotlin, no Android: the caller applies the returned rate to the output track and supplies the
 * clock, so the ramp is a deterministic unit test.
 */
class PlaybackRatePolicy(
    private val deadBandNanos: Long = DEFAULT_DEAD_BAND_NANOS,
    private val maxRate: Float = DEFAULT_MAX_RATE,
    private val minRate: Float = DEFAULT_MIN_RATE,
    private val step: Float = DEFAULT_STEP,
    private val cooldownNanos: Long = DEFAULT_COOLDOWN_NANOS,
) {
    init {
        require(deadBandNanos >= 0L) { "deadBandNanos must not be negative" }
        require(minRate in 0.5f..1.0f) { "minRate must be in [0.5, 1.0]" }
        require(maxRate in 1.0f..1.5f) { "maxRate must be in [1.0, 1.5]" }
        require(step > 0f) { "step must be positive" }
        require(cooldownNanos > 0L) { "cooldownNanos must be positive" }
    }

    private var current = 1.0f
    private var lastChangeNanos = Long.MIN_VALUE

    /** The rate currently requested. Always within `[minRate, maxRate]`. */
    val currentRate: Float get() = current

    /**
     * The rate to apply now.
     *
     * @param nowNanos current monotonic time.
     * @param excessNanos the delay above the learned floor, from [PipelineLatencyEstimator].
     * @param backlogNanos dubbed audio already queued for playback. Speeding up with nothing queued
     *   would only underrun, so a non-positive backlog forces the rate back to `1.0`.
     */
    @Synchronized
    fun rateFor(nowNanos: Long, excessNanos: Long, backlogNanos: Long): Float {
        val target = if (excessNanos <= deadBandNanos || backlogNanos <= 0L) {
            1.0f
        } else {
            maxRate
        }
        if (lastChangeNanos != Long.MIN_VALUE && nowNanos - lastChangeNanos < cooldownNanos) {
            return current
        }
        lastChangeNanos = nowNanos
        current = when {
            current < target -> (current + step).coerceAtMost(target)
            current > target -> (current - step).coerceAtLeast(target)
            else -> current
        }.coerceIn(minRate, maxRate)
        return current
    }

    /** Returns to `1.0` and clears the cooldown. Used when a session starts or stops. */
    @Synchronized
    fun reset() {
        current = 1.0f
        lastChangeNanos = Long.MIN_VALUE
    }

    companion object {
        /** Excess inside this band is noise and is never corrected. */
        const val DEFAULT_DEAD_BAND_NANOS = 80_000_000L

        /** The fastest the dub may play to drain a backlog: a three-percent trim. */
        const val DEFAULT_MAX_RATE = 1.03f

        /** The slowest the dub may play. Reserved for a future "dub is ahead" correction. */
        const val DEFAULT_MIN_RATE = 0.97f

        /** How far the rate may move per adjustment. */
        const val DEFAULT_STEP = 0.01f

        /** Minimum spacing between adjustments, so the pitch cannot oscillate. */
        const val DEFAULT_COOLDOWN_NANOS = 2_000_000_000L
    }
}
