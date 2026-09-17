package com.voxora.app.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.voxora.app.util.VoxoraLog
import com.voxora.core.GeminiLiveConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streams Gemini narration PCM (16-bit mono @ [GeminiLiveConfig.OUTPUT_SAMPLE_RATE])
 * via [AudioTrack]. Reader is a foreground listening experience — no source-music
 * ducking and no volume-provider session; plain media focus is enough.
 */
class ReaderPlayback(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            if (change != AudioManager.AUDIOFOCUS_GAIN) VoxoraLog.i("ReaderPlayback", "focus lost ($change); Reader pauses via service")
        }
        .build()
    private var track: AudioTrack? = null
    private val playing = AtomicBoolean(false)
    private var framesWritten = 0L

    fun start() {
        stop()
        if (audioManager?.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            throw IllegalStateException("audio focus denied")
        }
        val minBuf = AudioTrack.getMinBufferSize(
            GeminiLiveConfig.OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufSize = (minBuf * 8).coerceAtLeast(GeminiLiveConfig.OUTPUT_SAMPLE_RATE * 2)
        track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(GeminiLiveConfig.OUTPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track?.play()
        playing.set(true)
        framesWritten = 0
        VoxoraLog.i("ReaderPlayback", "started, buf=$bufSize")
    }

    /** Frames written but not yet played out (approximate playback buffer backlog). */
    fun undrainedFrames(): Long {
        val t = track ?: return 0
        return if (!playing.get()) 0 else (framesWritten - t.playbackHeadPosition.toLong()).coerceAtLeast(0)
    }

    /** Blocking write of one PCM chunk; safe to call repeatedly while playing. */
    fun writeFloats(samples: FloatArray) {
        if (!playing.get()) return
        val t = track ?: return
        val shorts = ShortArray(samples.size) { i ->
            val s = samples[i].coerceIn(-1f, 1f)
            (if (s < 0) s * 0x8000 else s * 0x7fff).toInt().toShort()
        }
        var offset = 0
        while (offset < shorts.size && playing.get()) {
            val written = t.write(shorts, offset, shorts.size - offset, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                VoxoraLog.w("ReaderPlayback", "write error $written")
                break
            }
            offset += written
            framesWritten += written
        }
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
        try {
            audioManager?.abandonAudioFocusRequest(focusRequest)
        } catch (_: Exception) {
        }
        VoxoraLog.i("ReaderPlayback", "stopped")
    }
}
