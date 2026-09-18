package com.voxora.app.dub.sync

/**
 * The points in the Live Dub pipeline that are worth a monotonic timestamp.
 *
 * These are the stages a delay can hide in, in the order the audio travels through them. Being
 * able to subtract two of them is what turns "it feels about three seconds behind" into
 * "the WebSocket round trip is 1.9 s and the output buffer adds 0.4 s".
 */
enum class DubEvent {
    /** The capture pipeline was started. */
    CAPTURE_START,

    /** A chunk of source audio was read from the capture. */
    SOURCE_CHUNK,

    /** A chunk was handed to Gemini. */
    PCM_SENT,

    /** The first dubbed payload of the current connection arrived. */
    GEMINI_FIRST_AUDIO,

    /** A dubbed chunk was received from Gemini. */
    DUB_CHUNK,

    /** A dubbed chunk finished being written to the output track. */
    AUDIO_WRITE,

    /** The source was paused by the synchronizer. */
    SOURCE_PAUSE,

    /** The source was resumed by the synchronizer. */
    SOURCE_RESUME,
}

/**
 * A bounded, monotonic record of the pipeline's timing, and the rate-limited log line built
 * from it.
 *
 * Two rules shape it:
 * - **Monotonic only.** Every timestamp comes from a [MonotonicClock], never from wall-clock
 *   time, so a clock change cannot fabricate a delay.
 * - **Rate-limited.** Live Dub runs for hours; a log line per audio chunk would flood the ring
 *   buffer and hide everything else. [maybeLog] emits at most one summary per
 *   [summaryIntervalNanos], and callers can force one at a state change.
 *
 * It keeps the *first* and *last* occurrence of each event, which is enough for both questions
 * that matter: "how long did the very first dubbed word take?" (first-to-first) and "is the
 * pipeline keeping up right now?" (last-to-last).
 *
 * Pure Kotlin: no Android, no coroutines, fully unit-testable.
 *
 * **Thread-safe.** Events are marked from the capture thread, the OkHttp callback thread and the
 * audio consumer, so every accessor is synchronized; a lost or double-counted sample would make
 * the whole measurement untrustworthy.
 */
class LatencyTimeline(
    private val clock: MonotonicClock,
    private val log: (String) -> Unit,
    private val summaryIntervalNanos: Long = DEFAULT_SUMMARY_INTERVAL_NANOS,
) {
    private val first = HashMap<DubEvent, Long>()
    private val last = HashMap<DubEvent, Long>()
    private val counts = HashMap<DubEvent, Long>()

    private var droppedChunks = 0L
    private var lastSummaryNanos = Long.MIN_VALUE

    /** Records one occurrence of [event] at [nowNanos] (defaulting to the clock). */
    @Synchronized
    fun mark(event: DubEvent, nowNanos: Long = clock.nowNanos()) {
        if (!first.containsKey(event)) first[event] = nowNanos
        last[event] = nowNanos
        counts[event] = (counts[event] ?: 0L) + 1L
    }

    /** How many times [event] has been marked. */
    @Synchronized
    fun count(event: DubEvent): Long = counts[event] ?: 0L

    /** Records dubbed chunks the pipeline had to drop. Accounted, never silent. */
    @Synchronized
    fun recordDroppedChunks(total: Long) {
        droppedChunks = total
    }

    /** First-to-first elapsed time, i.e. the end-to-end latency of the first chunk. */
    @Synchronized
    fun firstElapsedNanos(from: DubEvent, to: DubEvent): Long? {
        val a = first[from] ?: return null
        val b = first[to] ?: return null
        return (b - a).takeIf { it >= 0L }
    }

    /** Last-to-last elapsed time, i.e. the current scheduling delay between two stages. */
    @Synchronized
    fun lastElapsedNanos(from: DubEvent, to: DubEvent): Long? {
        val a = last[from] ?: return null
        val b = last[to] ?: return null
        return (b - a).takeIf { it >= 0L }
    }

    /** A one-line, human-readable picture of the pipeline. */
    @Synchronized
    fun summary(): String {
        val captureToSend = lastElapsedNanos(DubEvent.SOURCE_CHUNK, DubEvent.PCM_SENT)
        val endToEnd = firstElapsedNanos(DubEvent.PCM_SENT, DubEvent.GEMINI_FIRST_AUDIO)
        val sendToDub = lastElapsedNanos(DubEvent.PCM_SENT, DubEvent.DUB_CHUNK)
        val dubToWrite = lastElapsedNanos(DubEvent.DUB_CHUNK, DubEvent.AUDIO_WRITE)
        return buildString {
            append("sync: ")
            append("capture→send ").append(ms(captureToSend)).append(", ")
            append("send→first audio ").append(ms(endToEnd)).append(", ")
            append("send→dub ").append(ms(sendToDub)).append(", ")
            append("dub→write ").append(ms(dubToWrite)).append(", ")
            append("source chunks ").append(count(DubEvent.SOURCE_CHUNK)).append(", ")
            append("dub chunks ").append(count(DubEvent.DUB_CHUNK))
            if (droppedChunks > 0) append(", DROPPED ").append(droppedChunks)
        }
    }

    /**
     * Emits [summary] at most once per interval. [context] is appended verbatim so the caller can
     * fold in the current sync state without this class needing to know about it.
     */
    @Synchronized
    fun maybeLog(nowNanos: Long = clock.nowNanos(), context: String = "", force: Boolean = false) {
        if (!force && lastSummaryNanos != Long.MIN_VALUE &&
            nowNanos - lastSummaryNanos < summaryIntervalNanos
        ) {
            return
        }
        lastSummaryNanos = nowNanos
        log(if (context.isBlank()) summary() else "${summary()}, $context")
    }

    /** Clears every sample. Used when a new session starts. */
    @Synchronized
    fun reset() {
        first.clear()
        last.clear()
        counts.clear()
        droppedChunks = 0L
        lastSummaryNanos = Long.MIN_VALUE
    }

    private fun ms(nanos: Long?): String = if (nanos == null) "—" else "${nanos / 1_000_000}ms"

    companion object {
        const val DEFAULT_SUMMARY_INTERVAL_NANOS = 2_000_000_000L
    }
}
