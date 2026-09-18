package com.voxora.app.reader

/**
 * What the Reader's status indicator should communicate.
 *
 * The phase enum says what the *engine* is doing; this says what the *user* needs to
 * see. Keeping it separate is what stops the screen from re-deriving colours inline
 * and drifting out of step with the pipeline.
 *
 * - [ACTIVE] — audio is being spoken right now. The only state that earns green, and
 *   the only state that pulses.
 * - [READY] — a settled, positive state the user is in control of: paused, ready to
 *   start, or finished. Voxora gold.
 * - [NEUTRAL] — nothing is being spoken yet: idle, extracting, connecting, or the
 *   hand-off between chunks. Deliberately *not* green, so the UI never claims playback
 *   is running while it is still preparing.
 * - [STOPPED] — narration was stopped or failed. Red.
 *
 * Pure JVM (no `android.*`) so the mapping is unit-testable.
 */
internal enum class ReaderStatusTone { ACTIVE, READY, NEUTRAL, STOPPED }

internal object ReaderStatusVisual {

    fun tone(phase: ReaderPhase): ReaderStatusTone = when (phase) {
        ReaderPhase.SPEAKING -> ReaderStatusTone.ACTIVE
        ReaderPhase.PAUSED, ReaderPhase.READY, ReaderPhase.COMPLETE -> ReaderStatusTone.READY
        ReaderPhase.STOPPED, ReaderPhase.ERROR -> ReaderStatusTone.STOPPED
        ReaderPhase.IDLE,
        ReaderPhase.EXTRACTING,
        ReaderPhase.CONNECTING,
        ReaderPhase.REWRITING,
        ReaderPhase.NEXT,
        -> ReaderStatusTone.NEUTRAL
    }

    /**
     * Whether the indicator should breathe.
     *
     * Only while audio is actually being spoken. A pulse during connecting or the
     * between-chunk hand-off would read as "playing" when nothing is audible yet, and
     * an animation that keeps running while paused is both wrong and a wasted frame
     * budget — the screen drops the animation entirely rather than freezing it.
     */
    fun pulses(phase: ReaderPhase): Boolean = tone(phase) == ReaderStatusTone.ACTIVE
}
