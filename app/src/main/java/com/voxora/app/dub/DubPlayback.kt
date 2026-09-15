package com.voxora.app.dub

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.voxora.core.GeminiLiveConfig
import java.util.concurrent.atomic.AtomicBoolean

class DubPlayback {
    private var track: AudioTrack? = null
    private val playing = AtomicBoolean(false)

    fun start() {
        stop()
        val minBuf = AudioTrack.getMinBufferSize(
            GeminiLiveConfig.OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(GeminiLiveConfig.OUTPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track?.play()
        playing.set(true)
    }

    fun writeFloats(samples: FloatArray) {
        if (!playing.get()) return
        val t = track ?: return
        val shorts = ShortArray(samples.size) { i ->
            val s = samples[i].coerceIn(-1f, 1f)
            (if (s < 0) s * 0x8000 else s * 0x7fff).toInt().toShort()
        }
        t.write(shorts, 0, shorts.size)
    }

    fun stop() {
        playing.set(false)
        try {
            track?.pause()
            track?.flush()
            track?.stop()
            track?.release()
        } catch (_: Exception) {
        }
        track = null
    }
}
