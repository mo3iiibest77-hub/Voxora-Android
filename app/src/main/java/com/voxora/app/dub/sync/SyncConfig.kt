package com.voxora.app.dub.sync

/**
 * Every bound the adaptive synchronizer is allowed to use.
 *
 * There is deliberately **no** "the model takes 3 seconds" constant here. The constant offset
 * between the source and the dubbed audio is the model's own latency, and it is *measured* at
 * run time ([DubSyncController] learns it as a baseline) rather than assumed. What these
 * values bound is the **drift** — the amount by which the dub falls *behind* the latency the
 * pipeline actually has — and how aggressively the controller is allowed to react to it.
 *
 * The units are nanoseconds of monotonic time because that is what [MonotonicClock] returns;
 * media content positions are also expressed in nanoseconds of audio so the two can be
 * subtracted directly.
 *
 * All values are conservative on purpose: a slightly larger but stable offset is a better
 * product than a pipeline that keeps pausing the user's video.
 */
data class SyncConfig(
    /**
     * How long the source/dub offset must hold still before it is accepted as "the pipeline's
     * latency". The offset climbs from zero to the model latency and then plateaus; this is
     * the length of the plateau that confirms we have arrived.
     */
    val stabilityWindowNanos: Long = 1_200_000_000L,

    /** Two consecutive offsets within this distance of each other count as "holding still". */
    val stabilityEpsilonNanos: Long = 150_000_000L,

    /**
     * Hard ceiling on measurement. If the offset never settles (a very jittery network) we
     * accept whatever we have and start monitoring rather than waiting forever.
     */
    val maxWarmUpNanos: Long = 8_000_000_000L,

    /** Drift below this is noise (jitter, scheduling, chunk granularity) and is ignored. */
    val driftToleranceNanos: Long = 250_000_000L,

    /** The smallest drift worth pausing the user's source for. Must exceed the tolerance. */
    val minCorrectionNanos: Long = 400_000_000L,

    /**
     * The longest a single correction may hold the source. A correction that has not restored
     * the offset by now is not going to, and holding the source longer is worse than the drift.
     */
    val maxPauseNanos: Long = 5_000_000_000L,

    /** Minimum quiet period between two corrections, so a wobbly pipeline cannot thrash. */
    val correctionCooldownNanos: Long = 6_000_000_000L,

    /** Rate budget: at most this many corrections per minute, regardless of cooldown. */
    val maxCorrectionsPerMinute: Int = 5,

    /** If no dubbed audio arrives for this long, the pipeline is stalled; sync is withdrawn. */
    val stallTimeoutNanos: Long = 6_000_000_000L,
) {
    init {
        require(minCorrectionNanos >= driftToleranceNanos) {
            "minCorrectionNanos ($minCorrectionNanos) must be >= driftToleranceNanos ($driftToleranceNanos)"
        }
        require(maxPauseNanos > 0 && correctionCooldownNanos > 0 && stallTimeoutNanos > 0) {
            "sync timeouts must be positive"
        }
        require(maxCorrectionsPerMinute > 0) { "maxCorrectionsPerMinute must be positive" }
    }
}
