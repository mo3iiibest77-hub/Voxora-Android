package com.voxora.app.dub.sync

/**
 * The bounds of the dub-side playback timeline.
 *
 * These describe **backlog**, not the model's latency. The constant offset between the source and
 * the dub is the Gemini round trip, and it is measured, never bounded here. What is bounded is
 * how much *already received but not yet heard* dubbed audio the app is willing to hold: that
 * backlog is pure added delay, and if it is allowed to grow the dub drifts further behind the
 * source for good.
 *
 * There is one control, the **tolerance**: the largest backlog that is accepted before the
 * timeline stops feeding the output and discards audio instead. It is adaptive, because the
 * right value depends on the link — a steady connection needs very little, a jittery one needs
 * enough to ride out a late chunk without an audible gap. Adaptation is bounded and rate-limited
 * so one burst cannot make it oscillate.
 *
 * All values are in nanoseconds of audio, matching the content clocks the rest of the sync layer
 * uses, so a backlog can be compared with a drift directly.
 */
data class PlaybackTimelineConfig(
    /**
     * The tolerance a session starts with.
     *
     * The output buffer alone accounts for roughly a hundred milliseconds of this, and chunks
     * arrive every sixty, so the starting value leaves room for a late chunk or two before any
     * audio is discarded.
     */
    val initialToleranceNanos: Long = 350_000_000L,

    /** The tightest tolerance the adaptive controller may settle on. */
    val minToleranceNanos: Long = 250_000_000L,

    /**
     * The loosest tolerance the adaptive controller may settle on.
     *
     * A jittery link is allowed to buy stability with a little more delay, but only up to here:
     * past this point the extra buffering costs more in lag than it saves in gaps.
     */
    val maxToleranceNanos: Long = 800_000_000L,

    /** A backlog at or below this when a chunk arrives means the output ran dry. */
    val underrunNanos: Long = 40_000_000L,

    /** How far the tolerance moves per adjustment. */
    val adaptationStepNanos: Long = 75_000_000L,

    /** Minimum spacing between two adjustments, so one burst cannot oscillate the tolerance. */
    val adaptationCooldownNanos: Long = 4_000_000_000L,
) {
    init {
        require(initialToleranceNanos in minToleranceNanos..maxToleranceNanos) {
            "initialToleranceNanos ($initialToleranceNanos) must lie within " +
                "[$minToleranceNanos, $maxToleranceNanos]"
        }
        require(minToleranceNanos > 0L) { "minToleranceNanos must be positive" }
        require(underrunNanos >= 0L) { "underrunNanos must not be negative" }
        require(underrunNanos < minToleranceNanos) {
            "underrunNanos ($underrunNanos) must be below minToleranceNanos ($minToleranceNanos), " +
                "or a healthy pipeline would always look starved"
        }
        require(adaptationStepNanos > 0L) { "adaptationStepNanos must be positive" }
        require(adaptationCooldownNanos > 0L) { "adaptationCooldownNanos must be positive" }
    }
}
