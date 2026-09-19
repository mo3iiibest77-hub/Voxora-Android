package com.voxora.app.dub

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.projection.MediaProjection
import android.os.SystemClock
import android.util.Log
import com.voxora.app.util.VoxoraLog
import com.voxora.core.GeminiLiveConfig
import com.voxora.core.audio.PcmUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The device's own timing for one captured chunk, alongside its audio.
 *
 * [frames] is the cumulative captured frame count at the record's sample rate — the source clock —
 * and [capturedAtNanos] is the monotonic time the platform associates with it. [fromDevice] is
 * false when the platform reports no timestamp, in which case [frames] is our own running count and
 * [capturedAtNanos] is just [nowNanos]; the model must never present that as a device measurement.
 */
data class CaptureStamp(
    val frames: Long,
    val capturedAtNanos: Long,
    val nowNanos: Long,
    val fromDevice: Boolean,
)

/** Captures other apps' playback via AudioPlaybackCapture (Android 10+). */
class SystemAudioCapture(
    private val onPcm16k: (FloatArray, CaptureStamp) -> Unit,
) {
    private var record: AudioRecord? = null
    private var job: Job? = null
    private var sampleRate = 44_100

    /**
     * The sample rate the record was actually opened at, or `0` before [start].
     *
     * Exposed so the caller can build the source clock at the right rate. It is the rate the record
     * was *opened* at, which is the rate [frames] is counted in — not the 16 kHz the audio is
     * downsampled to before it is sent.
     */
    @Volatile
    var openedSampleRate: Int = 0
        private set

    fun start(projection: MediaProjection, scope: CoroutineScope, excludeUid: Int = 0) {
        VoxoraLog.i("Capture", "start() excludeUid=$excludeUid")
        stop()
        val configBuilder = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
        if (excludeUid > 0) {
            try {
                configBuilder.excludeUid(excludeUid)
            } catch (e: Exception) {
                VoxoraLog.w("Capture", "excludeUid failed: ${e.message}")
            }
        }
        val config = configBuilder.build()

        val rates = intArrayOf(48_000, 44_100, 16_000)
        var created: AudioRecord? = null
        for (rate in rates) {
            val minBuf = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuf <= 0) continue
            try {
                val r = AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setAudioPlaybackCaptureConfig(config)
                    .build()
                if (r.state == AudioRecord.STATE_INITIALIZED) {
                    created = r
                    sampleRate = rate
                    break
                }
                r.release()
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord $rate failed: ${e.message}")
            }
        }
        val rec = created ?: throw IllegalStateException("Could not open system audio capture")
        record = rec
        openedSampleRate = sampleRate
        rec.startRecording()
        val chunkSamples = (sampleRate * GeminiLiveConfig.CHUNK_MS / 1000).coerceAtLeast(320)
        val buf = ShortArray(chunkSamples)
        job = scope.launch(Dispatchers.IO) {
            var totalFrames = 0L
            val timestamp = AudioTimestamp()
            while (isActive) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                totalFrames += n
                val now = SystemClock.elapsedRealtimeNanos()
                // The platform's own frame position and capture time, when it offers them. A device
                // that cannot report a timestamp falls back to our count and to `now`, and says so
                // through `fromDevice = false` rather than pretending the value was measured.
                // `TIMEBASE_MONOTONIC` is the timebase the synchronizer needs: it cannot be moved
                // by a clock change.
                val hasTimestamp = try {
                    rec.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) ==
                        AudioRecord.SUCCESS
                } catch (e: Exception) {
                    false
                }
                val stamp = if (hasTimestamp && timestamp.framePosition >= 0L) {
                    CaptureStamp(
                        frames = timestamp.framePosition,
                        capturedAtNanos = timestamp.nanoTime,
                        nowNanos = now,
                        fromDevice = true,
                    )
                } else {
                    CaptureStamp(
                        frames = totalFrames,
                        capturedAtNanos = now,
                        nowNanos = now,
                        fromDevice = false,
                    )
                }
                val floats = FloatArray(n) { i ->
                    buf[i] / if (buf[i] < 0) 32768f else 32767f
                }
                val pcm16 = PcmUtils.downsampleTo16k(floats, sampleRate)
                onPcm16k(pcm16, stamp)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        record = null
        openedSampleRate = 0
    }

    companion object {
        private const val TAG = "VoxoraCapture"
    }
}
