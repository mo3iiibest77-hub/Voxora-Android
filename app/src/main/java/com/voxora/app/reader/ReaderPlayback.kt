package com.voxora.app.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.voxora.app.util.VoxoraLog
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

class ReaderPlayback(context: Context, onFocusLost: () -> Unit) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            if (change != AudioManager.AUDIOFOCUS_GAIN) onFocusLost()
        }, Handler(Looper.getMainLooper()))
        .build()
    private var track: AudioTrack? = null
    private var framesWritten = 0L
    private var lastHead = 0L
    private var headWraps = 0L

    fun start() {
        check(manager?.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val minimum = AudioTrack.getMinBufferSize(24_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        check(minimum > 0)
        track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(24_000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(maxOf(minimum * 2, 24_000))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        check(track?.state == AudioTrack.STATE_INITIALIZED)
        track?.play()
        VoxoraLog.i("ReaderPlayback", "Media playback started")
    }

    suspend fun writeFloats(samples: FloatArray) {
        val output = checkNotNull(track)
        var offset = 0
        withTimeout(30_000) {
            while (offset < samples.size) {
                currentCoroutineContext().ensureActive()
                val count = output.write(samples, offset, minOf(2400, samples.size - offset), AudioTrack.WRITE_NON_BLOCKING)
                check(count >= 0)
                offset += count
                framesWritten += count
                if (count == 0) delay(10)
            }
        }
    }

    fun writeFloatsBlocking(samples: FloatArray) {
        val output = checkNotNull(track)
        var offset = 0
        while (offset < samples.size) {
            val count = output.write(samples, offset, minOf(2400, samples.size - offset), AudioTrack.WRITE_BLOCKING)
            check(count > 0) { "AudioTrack write failed" }
            offset += count
            framesWritten += count
        }
    }

    suspend fun drain() {
        val output = checkNotNull(track)
        withTimeout(30_000) {
            while (true) {
                currentCoroutineContext().ensureActive()
                val head = output.playbackHeadPosition.toLong() and 0xffffffffL
                if (head < lastHead) headWraps += 1L shl 32
                lastHead = head
                if (headWraps + head >= framesWritten) break
                delay(10)
            }
        }
    }

    fun stop() {
        val output = track
        track = null
        try {
            output?.pause()
            output?.flush()
        } catch (e: Exception) {
            VoxoraLog.w("ReaderPlayback", "Audio stop failed: ${e.javaClass.simpleName}")
        } finally {
            try {
                output?.release()
            } finally {
                manager?.abandonAudioFocusRequest(focus)
            }
        }
    }
}
