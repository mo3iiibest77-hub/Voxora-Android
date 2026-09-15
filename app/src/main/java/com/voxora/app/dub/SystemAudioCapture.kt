package com.voxora.app.dub

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.util.Log
import com.voxora.core.GeminiLiveConfig
import com.voxora.core.audio.PcmUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.voxora.app.util.VoxoraLog

/** Captures other apps' playback via AudioPlaybackCapture (Android 10+). */
class SystemAudioCapture(
    private val onPcm16k: (FloatArray) -> Unit,
) {
    private var record: AudioRecord? = null
    private var job: Job? = null
    private var sampleRate = 44_100

    fun start(projection: MediaProjection, scope: CoroutineScope) {
        VoxoraLog.i("Capture", "start()")
        stop()
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

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
        rec.startRecording()
        val chunkSamples = (sampleRate * GeminiLiveConfig.CHUNK_MS / 1000).coerceAtLeast(320)
        val buf = ShortArray(chunkSamples)
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                val floats = FloatArray(n) { i ->
                    buf[i] / if (buf[i] < 0) 32768f else 32767f
                }
                val pcm16 = PcmUtils.downsampleTo16k(floats, sampleRate)
                onPcm16k(pcm16)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        record = null
    }

    companion object {
        private const val TAG = "VoxoraCapture"
    }
}
