package com.voxora.app.dub.sync

/**
 * The adaptive Live Dub synchronizer.
 *
 * ## The problem it solves
 *
 * The source (a video, a podcast, a music app) plays in real time. Voxora captures it, sends it to
 * Gemini, and plays the translated audio back. That round trip takes time, so the dubbed audio is
 * always *behind* the source by the pipeline's latency — call it `L`. The audible defect is not
 * `L` itself; it is **avoidable delay on top of `L`**: when the dub cannot keep up, the gap grows
 * without bound, and when the app buffers more than it must, the gap never comes back.
 *
 * ## The model: a measured floor, not an accepted baseline
 *
 * Both sides have a *content clock* measured in nanoseconds of audio: `sourceContentNanos` (how
 * much source audio was captured) and `dubContentNanos` (how much dubbed audio the device has
 * actually presented). Their difference is the current offset.
 *
 * In a healthy pipeline both advance at real time, so the offset is constant. The earlier version
 * of this class learned that constant as a *baseline* and corrected only deviations from it — which
 * meant that whatever avoidable delay had settled by the time the baseline was learned became
 * permanent. A constant 4 s offset reported `SYNCED` forever.
 *
 * This version learns a **[PipelineLatencyEstimator] floor** instead: the smallest offset the
 * pipeline has demonstrated inside a sliding window. The floor *is* the best the pipeline can do
 * right now, and everything above it is delay we introduced:
 *
 * ```
 * excess = offset − floor
 * ```
 *
 * `excess` is what the controller corrects, by pausing the source so the dub can drain its backlog
 * until the offset is back at the floor. A constant pipeline latency is still reported
 * [SyncState.SYNCED] — it is the pipeline, not a fault — but it is no longer accepted when it is
 * merely the app's own backlog in disguise, because the floor keeps moving down as that backlog is
 * removed.
 *
 * ## What it deliberately does not do
 *
 * It cannot remove the pipeline's own floor for an external *video*: there is no supported way to
 * delay another app's video frames, and no way to make Gemini answer faster. Pausing the source
 * corrects excess delay; it does not make the translation land on the right lip movement. That
 * limit is real and is stated in the UI and in `AGENTS.md` rather than papered over.
 *
 * ## Safety
 *
 * The controller is the only thing that may pause the source, and every path that could leave it
 * paused — control lost, a stall, a Gemini reconnect, a stop — resumes it. It never fights the
 * user: it only pauses a source it has observed to be playing, and it never resumes a source it
 * did not pause.
 *
 * This class has no Android dependency and no coroutines: the service calls [tick] on a timer, and
 * a test calls it with whatever timestamps it likes.
 */
class DubSyncController(
    private val player: ExternalPlayer,
    private val config: SyncConfig = SyncConfig(),
    private val log: (String) -> Unit = {},
    /** The shared latency floor estimator; the service reads it for the diagnostic line. */
    val latency: PipelineLatencyEstimator = PipelineLatencyEstimator(),
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
     * The learned pipeline floor in nanoseconds, or `-1` before it has been measured.
     *
     * This is the smallest source/dub offset the window has seen — the pipeline's demonstrated best
     * — and it is the target the controller drives toward. Reported in the logs so the measured
     * value is visible rather than assumed. The offset can legitimately be negative (after the user
     * pauses and resumes the source the dub can be ahead), so "not measured" is a dedicated
     * sentinel, never a sign test.
     */
    val floorLatencyNanos: Long
        get() = if (!latency.hasCurrentNanos || !latency.hasFloorNanos) -1L else latency.floorNanos

    /** The most recent instantaneous delay above the floor: the part being corrected. */
    @Volatile
    var excessNanos: Long = 0L
        private set

    /** Number of corrections performed since [start]. */
    var correctionCount: Int = 0
        private set

    private var started = false
    private var startNanos = 0L

    private var lastSourceContentNanos = -1L
    private var lastDubContentNanos = -1L
    private var lastDubAtNanos = -1L

    @Volatile
    private var pausedByUs = false
    private var pausedAtNanos = 0L
    private var lastCorrectionNanos = Long.MIN_VALUE
    private var correctionWindowStartNanos = 0L
    private var correctionsThisWindow = 0

    /**
     * The source's play state as we last understood it. A change we did not cause is the user's
     * own pause or resume, which invalidates the measured floor and must be re-measured.
     */
    private var observedPlaying = true

    /** Begins a run. Safe to call again; it resets the measured floor. */
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
     * A Gemini reconnect invalidates the measured floor: the new connection may have a different
     * round-trip time, and the audio that was in flight is gone. The source must not be left paused
     * across a reconnect, so it is resumed and the controller re-warms.
     */
    fun onGeminiReconnect(nowNanos: Long) {
        if (!started) return
        releaseSourceIfHeld("Gemini reconnected")
        resetMeasurement(nowNanos)
    }

    /** Discards the measured floor and starts measuring again. */
    private fun resetMeasurement(nowNanos: Long) {
        latency.reset()
        lastSourceContentNanos = -1L
        lastDubContentNanos = -1L
        lastDubAtNanos = -1L
        excessNanos = 0L
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
     * @param dubContentNanos cumulative dubbed audio the device has presented, in nanoseconds.
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

        // 2. The user's own pause or resume invalidates the measured floor: the source clock
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

        // 4. Feed the floor estimator, but only while the pipeline is genuinely active: dubbed
        // audio has started, we are not holding the source, the user is playing, and the source
        // clock is advancing. Feeding it during start-up (before the dub exists), during a hold, or
        // during a user pause would drag the floor down to a value that is not the pipeline's
        // latency at all. The warm-up timeout is the one exception, so a source that never advances
        // cannot leave the controller measuring forever.
        val sourceAdvanced =
            lastSourceContentNanos >= 0L && sourceContentNanos > lastSourceContentNanos
        lastSourceContentNanos = sourceContentNanos
        val warmTimedOut = nowNanos - startNanos >= config.maxWarmUpNanos
        val active = dubContentNanos > 0L && !pausedByUs && observedPlaying &&
            (sourceAdvanced || warmTimedOut)
        if (active) latency.onOffset(nowNanos, offsetNanos)

        // 5. Nothing may be corrected until a floor exists. Until then we are warming up.
        if (!latency.hasCurrentNanos || !latency.hasFloorNanos || dubContentNanos <= 0L) {
            state = SyncState.WARMING_UP
            return SyncDecision.NONE
        }

        val floorNanos = latency.floorNanos
        val excess = (offsetNanos - floorNanos).coerceAtLeast(0L)
        excessNanos = excess

        // 6. A correction in flight ends when the offset is back at the floor. Hysteresis: entering
        // a correction needs a real excess, leaving it only needs to be back inside the noise band.
        // Without this the controller chatters on the boundary.
        if (pausedByUs) {
            val caughtUp = offsetNanos <= floorNanos + config.driftToleranceNanos
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
                        "excess ${excess / 1_000_000}ms",
                )
                return SyncDecision.RESUME_SOURCE
            }
            state = SyncState.CORRECTING
            return SyncDecision.NONE
        }

        // 7. Correct avoidable delay above the floor, rarely, and never against the user's pause.
        if (excess >= config.minCorrectionNanos && mayCorrect(nowNanos) && player.isPlaying()) {
            player.pause()
            observedPlaying = false
            pausedByUs = true
            pausedAtNanos = nowNanos
            correctionCount++
            correctionsThisWindow++
            state = SyncState.CORRECTING
            log("sync: pausing source to correct ${excess / 1_000_000}ms above the floor")
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
    }
}
