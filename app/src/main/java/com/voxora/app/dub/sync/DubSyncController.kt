package com.voxora.app.dub.sync

import kotlin.math.abs

/**
 * The adaptive Live Dub synchronizer.
 *
 * ## The problem it solves
 *
 * The source (a video, a podcast, a music app) plays in real time. Voxora captures it, sends it
 * to Gemini, and plays the translated audio back. That round trip takes time, so the dubbed
 * audio is always *behind* the source by the model's latency — call it `L`. The audible defect
 * is not `L` itself; it is **drift**: when the dub cannot keep up, the gap grows without bound
 * and the translation slides further and further away from the picture.
 *
 * ## The model
 *
 * Both sides have a *content clock* measured in nanoseconds of audio:
 * - `sourceContentNanos` — how much source audio has been captured,
 * - `dubContentNanos` — how much dubbed audio has been delivered to the output.
 *
 * Their difference is the current offset. In a healthy pipeline the source and the dub both
 * advance at real-time speed, so the offset is **constant** and equal to `L`. That constant is
 * learned here as a *baseline*; nothing in this file assumes its value. A constant offset —
 * whether 200 ms or 3 s — is reported as [SyncState.SYNCED], because there is nothing to
 * correct: the dub is exactly `L` behind, which is what the pipeline is.
 *
 * Drift is `offset - baseline`. When the dub falls behind, the offset rises above the baseline
 * and drift grows. The correction is to pause the source: the source clock stops while the dub
 * keeps consuming its backlog, so the offset falls back to the baseline. The source is then
 * resumed. That is the whole algorithm, and it is why it never needs a fixed "3 second" sleep.
 *
 * ## What it deliberately does not do
 *
 * It cannot remove the constant offset for an external *video*: there is no supported way to
 * delay another app's video frames. Pausing the source corrects drift; it does not make the
 * translation land on the right lip movement. That limit is real and is stated in the UI and in
 * `AGENTS.md` rather than papered over.
 *
 * ## Safety
 *
 * The controller is the only thing that may pause the source, and every path that could leave
 * it paused — control lost, a stall, a Gemini reconnect, a stop — resumes it. It never fights
 * the user: it only pauses a source it has observed to be playing, and it never resumes a
 * source it did not pause.
 *
 * This class has no Android dependency and no coroutines: the service calls [tick] on a timer,
 * and a test calls it with whatever timestamps it likes.
 */
class DubSyncController(
    private val player: ExternalPlayer,
    private val config: SyncConfig = SyncConfig(),
    private val log: (String) -> Unit = {},
) {
    var state: SyncState = SyncState.IDLE
        private set

    /**
     * True while the controller is holding the source paused for a correction.
     *
     * Read from the capture thread (to freeze the source content clock) as well as from the tick
     * thread, so it must be volatile: a stale read would count source audio that the pause was
     * meant to stop, and the correction would never appear to converge.
     */
    val isHoldingSource: Boolean get() = pausedByUs

    /**
     * The learned pipeline latency in nanoseconds, or `-1` before it has been measured.
     * Reported in the logs so the measured value is visible rather than assumed.
     *
     * The offset can legitimately be *negative* — after the user pauses and resumes the source,
     * the dub can be ahead — so "not measured" is a dedicated sentinel, never a sign test.
     */
    val baselineLatencyNanos: Long
        get() = if (baselineNanos == UNMEASURED) -1L else baselineNanos

    /** Number of corrections performed since [start]. */
    var correctionCount: Int = 0
        private set

    private var started = false
    private var startNanos = 0L

    /** The measured source/dub offset, or [UNMEASURED]. May legitimately be negative. */
    private var baselineNanos = UNMEASURED

    private var lastOffsetNanos = Long.MIN_VALUE
    private var stableSinceNanos = -1L

    @Volatile
    private var pausedByUs = false
    private var pausedAtNanos = 0L
    private var lastCorrectionNanos = Long.MIN_VALUE
    private var correctionWindowStartNanos = 0L
    private var correctionsThisWindow = 0

    private var lastDubContentNanos = -1L
    private var lastDubAtNanos = -1L

    /**
     * The source's play state as we last understood it. A change we did not cause is the user's
     * own pause or resume, which invalidates the learned offset and must be re-measured.
     */
    private var observedPlaying = true

    /** Begins a run. Safe to call again; it resets the learned baseline. */
    fun start(nowNanos: Long) {
        started = true
        // Correction bookkeeping is per-run; the measurement itself is reset below.
        pausedByUs = false
        pausedAtNanos = 0L
        lastCorrectionNanos = Long.MIN_VALUE
        correctionWindowStartNanos = nowNanos
        correctionsThisWindow = 0
        correctionCount = 0
        observedPlaying = true
        resetMeasurement(nowNanos)
    }

    /**
     * A Gemini reconnect invalidates the learned latency: the new connection may have a
     * different round-trip time, and the audio that was in flight is gone. The source must not
     * be left paused across a reconnect, so it is resumed and the controller re-warms.
     */
    fun onGeminiReconnect(nowNanos: Long) {
        if (!started) return
        releaseSourceIfHeld("Gemini reconnected")
        resetMeasurement(nowNanos)
    }

    /** Discards the learned latency and starts measuring again. */
    private fun resetMeasurement(nowNanos: Long) {
        baselineNanos = UNMEASURED
        lastOffsetNanos = Long.MIN_VALUE
        stableSinceNanos = -1L
        lastDubContentNanos = -1L
        lastDubAtNanos = -1L
        startNanos = nowNanos
        state = SyncState.WARMING_UP
    }

    /**
     * One control step.
     *
     * @param nowNanos current monotonic time.
     * @param sourceContentNanos cumulative source audio captured, in nanoseconds of audio. The
     *   caller must *not* advance this while [isHoldingSource] is true — the whole point of a
     *   correction is that the source clock stops.
     * @param dubContentNanos cumulative dubbed audio handed to the output, in nanoseconds.
     */
    fun tick(nowNanos: Long, sourceContentNanos: Long, dubContentNanos: Long): SyncDecision {
        if (!started) {
            state = SyncState.IDLE
            return SyncDecision.NONE
        }

        trackDubLiveness(nowNanos, dubContentNanos)

        // 1. Control availability. Losing it must never leave the source paused.
        if (!player.isControllable()) {
            if (pausedByUs) {
                releaseSourceIfHeld("source control lost")
                state = SyncState.AUDIO_ONLY
                return SyncDecision.RESUME_SOURCE
            }
            // Before any dub has arrived we are still warming up — we simply already know that
            // no correction will be possible once it does.
            state = if (dubContentNanos > 0) SyncState.AUDIO_ONLY else SyncState.WARMING_UP
            return SyncDecision.NONE
        }

        // 2. The user's own pause or resume invalidates the learned offset: the source clock
        // stopped (or restarted) for a reason that has nothing to do with the pipeline. We never
        // fight it — we simply re-measure. Our own transport calls are excluded by keeping
        // `observedPlaying` in step with them.
        val playing = player.isPlaying()
        if (playing != observedPlaying) {
            observedPlaying = playing
            log("sync: source ${if (playing) "resumed" else "paused"} by user — re-measuring")
            resetMeasurement(nowNanos)
            return SyncDecision.NONE
        }

        // 3. A stalled pipeline cannot be synchronized; withdraw the claim, never the audio.
        if (isStalled(nowNanos, dubContentNanos)) {
            if (pausedByUs) {
                releaseSourceIfHeld("dub stalled")
                state = SyncState.UNAVAILABLE
                return SyncDecision.RESUME_SOURCE
            }
            state = SyncState.UNAVAILABLE
            return SyncDecision.NONE
        }

        val offsetNanos = sourceContentNanos - dubContentNanos

        // 4. Learn the pipeline's own latency. Nothing is corrected until it is known.
        if (baselineNanos == UNMEASURED) {
            if (dubContentNanos <= 0) {
                state = SyncState.WARMING_UP
                return SyncDecision.NONE
            }
            val held = lastOffsetNanos != Long.MIN_VALUE &&
                abs(offsetNanos - lastOffsetNanos) <= config.stabilityEpsilonNanos
            if (held) {
                if (stableSinceNanos < 0L) stableSinceNanos = nowNanos
            } else {
                stableSinceNanos = -1L
            }
            lastOffsetNanos = offsetNanos
            val settled = stableSinceNanos >= 0L &&
                nowNanos - stableSinceNanos >= config.stabilityWindowNanos
            val timedOut = nowNanos - startNanos >= config.maxWarmUpNanos
            if (settled || timedOut) {
                baselineNanos = offsetNanos
                log(
                    "sync: measured pipeline latency ${offsetNanos / 1_000_000}ms " +
                        "(${if (settled) "settled" else "timeout"})",
                )
            } else {
                state = SyncState.WARMING_UP
                return SyncDecision.NONE
            }
        }

        val driftNanos = offsetNanos - baselineNanos

        // 4. A correction in flight ends when the offset is back to the measured baseline.
        if (pausedByUs) {
            // Hysteresis: entering a correction needs a real drift, leaving it only needs to be
            // back inside the noise band. Without this the controller chatters on the boundary.
            val caughtUp = offsetNanos <= baselineNanos + config.driftToleranceNanos
            val expired = nowNanos - pausedAtNanos >= config.maxPauseNanos
            if (caughtUp || expired) {
                player.play()
                observedPlaying = true
                pausedByUs = false
                lastCorrectionNanos = nowNanos
                state = SyncState.SYNCED
                log(
                    "sync: source resumed after ${(nowNanos - pausedAtNanos) / 1_000_000}ms " +
                        "(${if (caughtUp) "caught up" else "bounded pause"}), " +
                        "drift ${driftNanos / 1_000_000}ms",
                )
                return SyncDecision.RESUME_SOURCE
            }
            state = SyncState.CORRECTING
            return SyncDecision.NONE
        }

        // 5. Correct real drift, rarely, and never against the user's own pause.
        if (driftNanos >= config.minCorrectionNanos && mayCorrect(nowNanos) && player.isPlaying()) {
            player.pause()
            observedPlaying = false
            pausedByUs = true
            pausedAtNanos = nowNanos
            correctionCount++
            correctionsThisWindow++
            state = SyncState.CORRECTING
            log("sync: pausing source to correct ${driftNanos / 1_000_000}ms of drift")
            return SyncDecision.PAUSE_SOURCE
        }

        state = SyncState.SYNCED
        return SyncDecision.NONE
    }

    /**
     * Ends the run. If a correction is in flight the source is resumed, so stopping Live Dub can
     * never leave the user's video paused.
     */
    fun stop() {
        releaseSourceIfHeld("session stopped")
        started = false
        state = SyncState.IDLE
    }

    private fun trackDubLiveness(nowNanos: Long, dubContentNanos: Long) {
        if (dubContentNanos > lastDubContentNanos) {
            lastDubContentNanos = dubContentNanos
            lastDubAtNanos = nowNanos
        } else if (lastDubAtNanos < 0L) {
            lastDubAtNanos = nowNanos
        }
    }

    private fun isStalled(nowNanos: Long, dubContentNanos: Long): Boolean {
        if (dubContentNanos <= 0) return false
        if (lastDubAtNanos < 0L) return false
        return nowNanos - lastDubAtNanos > config.stallTimeoutNanos
    }

    private fun mayCorrect(nowNanos: Long): Boolean {
        if (nowNanos - correctionWindowStartNanos >= WINDOW_NANOS) {
            correctionWindowStartNanos = nowNanos
            correctionsThisWindow = 0
        }
        if (correctionsThisWindow >= config.maxCorrectionsPerMinute) return false
        if (lastCorrectionNanos == Long.MIN_VALUE) return true
        return nowNanos - lastCorrectionNanos >= config.correctionCooldownNanos
    }

    private fun releaseSourceIfHeld(reason: String) {
        if (!pausedByUs) return
        player.play()
        observedPlaying = true
        pausedByUs = false
        log("sync: source resumed ($reason)")
    }

    private companion object {
        const val WINDOW_NANOS = 60_000_000_000L

        /** Sentinel for "the offset has not been measured yet". Never a sign test. */
        const val UNMEASURED = Long.MIN_VALUE
    }
}
