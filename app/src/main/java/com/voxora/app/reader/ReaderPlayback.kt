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
import kotlinx.coroutines.CancellationException
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
    @Volatile private var acceptingFocusEvents = false
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change ->
            if (acceptingFocusEvents && change != AudioManager.AUDIOFOCUS_GAIN) onFocusLost()
        }, Handler(Looper.getMainLooper()))
        .build()
    private val lock = Any()
    private var stopped = false
    private var track: AudioTrack? = null
    @Volatile var writtenFrames = 0L
        private set
    private var lastHead = 0L
    private var headWraps = 0L

    fun start() = synchronized(lock) {
        check(!stopped && track == null)
        try {
            startOutput()
        } catch (e: Exception) {
            VoxoraLog.w("ReaderPlayback", "Audio start failed: ${e.javaClass.simpleName}")
            stop()
            throw e
        }
    }

    private fun startOutput() {
        acceptingFocusEvents = true
        check(manager?.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val minimum = AudioTrack.getMinBufferSize(24_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0)
        track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(24_000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(maxOf(minimum * 2, 12_000))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        check(track?.state == AudioTrack.STATE_INITIALIZED)
        track?.play()
        VoxoraLog.i("ReaderPlayback", "Media playback started")
    }

    suspend fun writePcm(bytes: ByteArray, onProgress: () -> Unit) {
        require(bytes.size % 2 == 0)
        var offset = 0
        withTimeout(30_000) {
            while (offset < bytes.size) {
                currentCoroutineContext().ensureActive()
                val count = synchronized(lock) {
                    if (stopped) throw CancellationException("Reader playback stopped")
                    val requested = minOf(4_800, bytes.size - offset)
                    checkNotNull(track).write(bytes, offset, requested, AudioTrack.WRITE_NON_BLOCKING).also {
                        check(it in 0..requested && it % 2 == 0)
                        writtenFrames += it / 2
                    }
                }
                offset += count
                currentCoroutineContext().ensureActive()
                onProgress()
                if (count == 0) delay(10)
            }
        }
    }

    fun playedFrames(): Long = synchronized(lock) {
        if (stopped) throw CancellationException("Reader playback stopped")
        val head = checkNotNull(track).playbackHeadPosition.toLong() and 0xffffffffL
        if (head < lastHead) headWraps += 1L shl 32
        lastHead = head
        return headWraps + head
    }

    suspend fun drain(onProgress: () -> Unit) {
        withTimeout(30_000) {
            while (true) {
                currentCoroutineContext().ensureActive()
                onProgress()
                if (playedFrames() >= writtenFrames) break
                delay(10)
            }
        }
    }

    fun stop() = synchronized(lock) {
        if (stopped) return@synchronized
        stopped = true
        acceptingFocusEvents = false
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
            } catch (e: Exception) {
                VoxoraLog.w("ReaderPlayback", "Audio release failed: ${e.javaClass.simpleName}")
            } finally {
                try {
                    manager?.abandonAudioFocusRequest(focus)
                } catch (e: Exception) {
                    VoxoraLog.w("ReaderPlayback", "Audio focus release failed: ${e.javaClass.simpleName}")
                }
            }
        }
    }
}
