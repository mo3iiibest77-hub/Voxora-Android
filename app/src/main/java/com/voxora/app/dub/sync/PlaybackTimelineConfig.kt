package com.voxora.app.dub.sync

/**
 * The bounds of the dub-side playback timeline.
 *
 * These describe **backlog**, not the model's latency. The pipeline's latency is measured and is
 * reported as the floor; what is bounded here is how much *already received but not yet heard*
 * dubbed audio the app is willing to hold. That backlog is pure added delay, so the ceiling is
 * deliberately low: a few hundred milliseconds is enough to ride out a late chunk, and anything
 * larger is delay the user pays for and never gets back.
 *
 * There is one control, the **tolerance**: the largest total backlog (queued output audio *plus*
 * audio still sitting in the Gemini hand-off buffer) that is accepted before the timeline stops
 * feeding the output and discards audio instead. It is adaptive, because the right value depends on
 * the link — a steady connection needs very little, a jittery one needs enough to ride out a late
 * chunk without an audible gap. Adaptation is bounded and rate-limited so one burst cannot make it
 * oscillate.
 *
 * All values are in nanoseconds of audio, matching the content clocks the rest of the sync layer
 * uses, so a backlog can be compared with a drift directly.
 */
data class PlaybackTimelineConfig(
    /**
     * The tolerance a session starts with.
     *
     * The output buffer alone accounts for roughly 150–200 ms of this and the hand-off buffer for
     * up to a chunk or two more, so the starting value must clear that or a perfectly healthy
     * pipeline would drop audio on every arrival.
     */
    val initialToleranceNanos: Long = 300_000_000L,

    /** The tightest tolerance the adaptive controller may settle on. */
    val minToleranceNanos: Long = 200_000_000L,

    /**
     * The loosest tolerance the adaptive controller may settle on.
     *
     * A jittery link is allowed to buy stability with a little more delay, but only up to here. The
     * old ceiling was 800 ms, which meant a jittery session could sit most of a second further
     * behind the source for its whole run; this keeps the worst case under half a second.
     */
    val maxToleranceNanos: Long = 450_000_000L,

    /** A backlog at or below this when a chunk arrives means the output ran dry. */
    val underrunNanos: Long = 40_000_000L,

    /** How far the tolerance moves per adjustment. */
    val adaptationStepNanos: Long = 50_000_000L,

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
