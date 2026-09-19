package com.voxora.app.dub.sync

/**
 * Every bound the adaptive synchronizer is allowed to use.
 *
 * There is deliberately **no** "the model takes 3 seconds" constant here. The pipeline's latency is
 * *measured* at run time ([PipelineLatencyEstimator] learns its floor) rather than assumed, and it
 * is reported as [SyncState.SYNCED] because it is what the pipeline is. What these values bound is
 * the **excess** — the avoidable delay above that floor — and how aggressively the controller may
 * react to it.
 *
 * The units are nanoseconds of monotonic time because that is what [MonotonicClock] returns; media
 * content positions are also expressed in nanoseconds of audio so the two can be subtracted
 * directly.
 *
 * All values are conservative on purpose: a slightly larger but stable offset is a better product
 * than a pipeline that keeps pausing the user's video.
 */
data class SyncConfig(
    /**
     * Hard ceiling on measurement. The floor is learned as soon as dubbed audio is flowing and the
     * source clock is advancing, so this only matters for a source that never advances (for example
     * a muted app): after this long the current offset is accepted as the floor rather than leaving
     * the controller measuring forever.
     */
    val maxWarmUpNanos: Long = 8_000_000_000L,

    /** Excess below this is noise (jitter, scheduling, chunk granularity) and is ignored. */
    val driftToleranceNanos: Long = 250_000_000L,

    /** The smallest excess worth pausing the user's source for. Must exceed the tolerance. */
    val minCorrectionNanos: Long = 400_000_000L,

    /**
     * The longest a single correction may hold the source. A correction that has not restored the
     * offset by now is not going to, and holding the source longer is worse than the drift.
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
        require(maxWarmUpNanos > 0 && maxPauseNanos > 0 && correctionCooldownNanos > 0 &&
            stallTimeoutNanos > 0) {
            "sync timeouts must be positive"
        }
        require(maxCorrectionsPerMinute > 0) { "maxCorrectionsPerMinute must be positive" }
    }
}
