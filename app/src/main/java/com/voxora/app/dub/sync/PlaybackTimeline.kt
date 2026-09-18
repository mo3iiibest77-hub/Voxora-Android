package com.voxora.app.dub.sync

/**
 * What to do with a chunk of dubbed audio that has just arrived from Gemini.
 *
 * `PLAY` is the normal path. `DROP` means the dub already holds more unheard audio than the
 * timeline is willing to tolerate, and playing this chunk would push it further behind the
 * source; the audio is discarded so the content jumps forward instead.
 */
enum class ChunkAction { PLAY, DROP }

/**
 * The dub-side playback timeline — an explicit playhead for the audio Voxora produces.
 *
 * ## Why this exists
 *
 * [DubSyncController] corrects drift from the *source* side: it pauses the external player so the
 * dub can catch up. That is the right correction, but it needs media-control access, which is
 * frequently unavailable. On those devices the dub had no defence at all against a backlog: if
 * Gemini stalled and then delivered several seconds of audio at once, every one of those seconds
 * was played, and the dub ended up permanently further behind.
 *
 * This class gives the dub a defence of its own, and it needs no permissions. It keeps one
 * number — the **backlog**: dubbed audio that has been received but not yet heard. That number
 * *is* the added delay. In a healthy pipeline it sits around the output buffer's own occupancy
 * and holds still. When Gemini bursts, it spikes; the timeline then refuses to feed the output
 * until it is back inside the tolerance, so the surplus is discarded instead of being played out
 * and becoming permanent drift.
 *
 * ## The playhead
 *
 * Two cumulative positions, both in nanoseconds of audio:
 * - `scheduledNanos` — the content the timeline has accepted for playback (received minus
 *   dropped). This is the write cursor.
 * - `playedNanos` — the content the device has actually presented, supplied by the caller from
 *   the output track. This is the playhead.
 *
 * `backlogNanos = scheduledNanos - playedNanos`. Deriving the backlog from *actual playback*
 * rather than from bytes written is what makes it honest: a chunk sitting in the output buffer is
 * delay, and counting it as progress is how a pipeline convinces itself it is in sync.
 *
 * ## Adaptation
 *
 * The tolerance is not fixed. Running dry means it was too tight, so it loosens by one step; a
 * quiet period with no starvation lets it tighten again. Both moves are clamped to
 * [PlaybackTimelineConfig] and rate-limited, so a single burst cannot make the buffer oscillate.
 *
 * Pure Kotlin: no Android, no coroutines, no clock of its own. The caller supplies every
 * timestamp, which is what makes every scenario below a deterministic unit test.
 */
class PlaybackTimeline(
    private val config: PlaybackTimelineConfig = PlaybackTimelineConfig(),
    private val log: (String) -> Unit = {},
) {
    /** Content accepted for playback: received minus dropped, in nanoseconds of audio. */
    var scheduledNanos: Long = 0L
        private set

    /** Content the device has presented, relative to the position at [start]/[reset]. */
    var playedNanos: Long = 0L
        private set

    /** The current added delay: accepted-but-not-yet-heard audio. */
    val backlogNanos: Long
        get() = (scheduledNanos - playedNanos).coerceAtLeast(0L)

    /** The backlog the timeline is currently willing to tolerate before it discards audio. */
    var toleranceNanos: Long = config.initialToleranceNanos
        private set

    /** Chunks discarded to keep the backlog bounded. */
    var dropCount: Long = 0L
        private set

    /** Total audio discarded, in nanoseconds. */
    var droppedNanos: Long = 0L
        private set

    /** Times the output ran dry before the next chunk arrived. */
    var underrunCount: Long = 0L
        private set

    /** The largest backlog ever observed, for the instrumentation line. */
    var peakBacklogNanos: Long = 0L
        private set

    private var started = false

    /** The device position that [playedNanos] is measured from. */
    private var playedBaseNanos = 0L

    /** The device position at the previous observation, to detect it going backwards. */
    private var lastAbsolutePlayedNanos = -1L

    private var lastArrivalNanos = -1L
    private var lastAdaptationNanos = Long.MIN_VALUE

    /** Begins a run, rebasing the playhead on [absolutePlayedNanos]. */
    fun start(nowNanos: Long, absolutePlayedNanos: Long = 0L) {
        reset(nowNanos, absolutePlayedNanos)
        started = true
    }

    /**
     * Discards the timeline's history and rebases the playhead.
     *
     * Called on a Gemini reconnect, where the audio that was in flight is gone: the counters no
     * longer describe anything real, but the output track is still playing, so the playhead is
     * rebased rather than zeroed. Resetting it to zero would make the backlog look enormous and
     * the timeline would start discarding audio it should have played.
     */
    fun reset(nowNanos: Long, absolutePlayedNanos: Long = 0L) {
        scheduledNanos = 0L
        playedNanos = 0L
        droppedNanos = 0L
        dropCount = 0L
        underrunCount = 0L
        peakBacklogNanos = 0L
        toleranceNanos = config.initialToleranceNanos
        playedBaseNanos = absolutePlayedNanos
        lastAbsolutePlayedNanos = absolutePlayedNanos
        lastArrivalNanos = -1L
        lastAdaptationNanos = nowNanos
    }

    /**
     * Records the device's playback position between chunks, so the backlog reported to the
     * synchronizer and the instrumentation line reflect real playback.
     */
    fun onPlayed(absolutePlayedNanos: Long) {
        if (!started) return
        if (lastAbsolutePlayedNanos >= 0L && absolutePlayedNanos < lastAbsolutePlayedNanos) {
            // The head went backwards: a new output track, or a flush. Move the base so the
            // *relative* playhead keeps its current value — zeroing it would make everything
            // scheduled so far look unplayed, and the timeline would discard audio it should
            // have played.
            playedBaseNanos = absolutePlayedNanos - playedNanos
            lastAbsolutePlayedNanos = absolutePlayedNanos
            return
        }
        lastAbsolutePlayedNanos = absolutePlayedNanos
        playedNanos = (absolutePlayedNanos - playedBaseNanos).coerceAtLeast(0L)
    }

    /**
     * A chunk of dubbed audio arrived. Returns whether it should be written to the output.
     *
     * [durationNanos] is the chunk's own length in nanoseconds of audio; [absolutePlayedNanos]
     * is the device's current playback position, which the caller reads from the output track.
     */
    fun onChunkArrived(
        nowNanos: Long,
        durationNanos: Long,
        absolutePlayedNanos: Long,
    ): ChunkAction {
        if (!started || durationNanos <= 0L) return ChunkAction.PLAY

        onPlayed(absolutePlayedNanos)

        // Starvation is measured *before* this chunk is counted: a backlog already at the floor
        // when new audio arrives means the output had run dry, so the tolerance was too tight.
        if (lastArrivalNanos >= 0L && backlogNanos <= config.underrunNanos) {
            underrunCount++
            loosen(nowNanos)
        }
        lastArrivalNanos = nowNanos

        scheduledNanos += durationNanos
        val backlog = backlogNanos
        if (backlog > peakBacklogNanos) peakBacklogNanos = backlog

        if (backlog > toleranceNanos) {
            // Refuse to feed the output. `scheduledNanos` does not advance, so the backlog stops
            // growing and drains as the device plays; the content the user hears jumps forward.
            scheduledNanos -= durationNanos
            droppedNanos += durationNanos
            dropCount++
            log(
                "playback: dropped ${durationNanos / 1_000_000}ms — backlog " +
                    "${backlog / 1_000_000}ms over tolerance ${toleranceNanos / 1_000_000}ms",
            )
            return ChunkAction.DROP
        }

        tightenIfQuiet(nowNanos)
        return ChunkAction.PLAY
    }

    /** Ends the run. The counters are kept so the final report can still be read. */
    fun stop() {
        started = false
    }

    /** True while the timeline is running. */
    val isRunning: Boolean get() = started

    private fun loosen(nowNanos: Long) {
        if (!mayAdapt(nowNanos)) return
        val next = (toleranceNanos + config.adaptationStepNanos)
            .coerceAtMost(config.maxToleranceNanos)
        if (next != toleranceNanos) {
            log(
                "playback: output ran dry — tolerance " +
                    "${toleranceNanos / 1_000_000}ms → ${next / 1_000_000}ms",
            )
            toleranceNanos = next
        }
    }

    private fun tightenIfQuiet(nowNanos: Long) {
        if (!mayAdapt(nowNanos)) return
        toleranceNanos = (toleranceNanos - config.adaptationStepNanos)
            .coerceAtLeast(config.minToleranceNanos)
    }

    private fun mayAdapt(nowNanos: Long): Boolean {
        if (nowNanos - lastAdaptationNanos < config.adaptationCooldownNanos) return false
        lastAdaptationNanos = nowNanos
        return true
    }
}
