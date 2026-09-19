package com.voxora.app.dub.sync

import java.util.Locale

/**
 * One reading of the whole Live Dub timing pipeline, in the units the log line needs.
 *
 * Every field is a *duration* in nanoseconds except the identifiers, and every optional field is
 * nullable so a device that cannot report it says so instead of showing a fabricated zero. The
 * capture timestamp and the Gemini first-audio mark are the two the platform may not provide.
 */
data class SyncSnapshot(
    /** Source content captured, in nanoseconds of audio. */
    val sourceNanos: Long,
    /** Dubbed audio the device has actually presented, in nanoseconds of audio. */
    val dubNanos: Long,
    /** `sourceNanos − dubNanos`: the current source/dub offset. */
    val driftNanos: Long,
    /** The smoothed current offset — the end-to-end pipeline latency as measured right now. */
    val estimatedPipelineLatencyNanos: Long,
    /** The smallest offset the window has seen: the pipeline's demonstrated best. */
    val floorNanos: Long,
    /** `estimatedPipelineLatencyNanos − floorNanos`: the avoidable delay. */
    val excessNanos: Long,
    /** How long ago the device captured the audio just read, or `null` if it cannot report it. */
    val captureTimestampNanos: Long?,
    /** Start-of-session to first dubbed audio from Gemini, or `null` before it has arrived. */
    val geminiFirstAudioNanos: Long?,
    /** Audio sitting in the Gemini hand-off buffer, received but not yet handed to the timeline. */
    val handoffNanos: Long,
    /** Audio accepted for playback (`scheduledNanos`). */
    val outputQueuedNanos: Long,
    /** Audio the output has presented (`playedNanos`). */
    val outputPlayedNanos: Long,
    /** `written − played`: the output buffer's own occupancy. */
    val bufferNanos: Long,
    /** The playback-rate trim currently applied to the output. `1.0` means none. */
    val rate: Float,
    /** A short label for what the synchronizer is doing: a [SyncState] name or `none`. */
    val correction: String,
    /** Chunks the playback timeline discarded to bound the backlog. */
    val drops: Long,
    /** Times the output ran dry before the next chunk arrived. */
    val underruns: Long,
)

/**
 * Formats the single, bounded `[DUB_SYNC]` line.
 *
 * The line exists to answer, with real numbers and without a debugger, the two questions the
 * pipeline raises: *where is the delay* and *is it being corrected*. It is deliberately one line —
 * `LatencyTimeline` rate-limits how often it is emitted, because Live Dub runs for hours.
 *
 * A field the device cannot report is rendered `—`, never `0`: "the platform did not tell us" and
 * "the value is zero" are different answers and must not look the same.
 */
object SyncDiagnostics {
    fun format(snapshot: SyncSnapshot): String = buildString {
        append("[DUB_SYNC] ")
        append("sourceMs=").append(ms(snapshot.sourceNanos)).append(' ')
        append("dubMs=").append(ms(snapshot.dubNanos)).append(' ')
        append("driftMs=").append(ms(snapshot.driftNanos)).append(' ')
        append("estimatedPipelineLatencyMs=").append(ms(snapshot.estimatedPipelineLatencyNanos))
        append(' ')
        append("floorMs=").append(ms(snapshot.floorNanos)).append(' ')
        append("excessMs=").append(ms(snapshot.excessNanos)).append(' ')
        append("captureTimestampMs=").append(ms(snapshot.captureTimestampNanos)).append(' ')
        append("geminiFirstAudioMs=").append(ms(snapshot.geminiFirstAudioNanos)).append(' ')
        append("handoffMs=").append(ms(snapshot.handoffNanos)).append(' ')
        append("outputQueuedMs=").append(ms(snapshot.outputQueuedNanos)).append(' ')
        append("outputPlayedMs=").append(ms(snapshot.outputPlayedNanos)).append(' ')
        append("bufferMs=").append(ms(snapshot.bufferNanos)).append(' ')
        append("rate=").append(String.format(Locale.US, "%.2f", snapshot.rate)).append(' ')
        append("correction=").append(snapshot.correction.ifBlank { "none" }).append(' ')
        append("drops=").append(snapshot.drops).append(' ')
        append("underruns=").append(snapshot.underruns)
    }

    private fun ms(nanos: Long?): String = if (nanos == null) "—" else (nanos / 1_000_000L).toString()

    private fun ms(nanos: Long): String = (nanos / 1_000_000L).toString()
}
