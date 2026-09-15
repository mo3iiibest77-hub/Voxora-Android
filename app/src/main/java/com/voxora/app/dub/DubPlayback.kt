package com.voxora.app.dub

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.voxora.core.GeminiLiveConfig
import java.util.concurrent.atomic.AtomicBoolean
import com.voxora.app.util.VoxoraLog

/**
 * Plays Gemini PCM on a dedicated path.
 * - ALLOW_CAPTURE_BY_NONE: do not re-capture our own output
 * - Audio focus MAY_DUCK + soft STREAM_MUSIC duck (~30% of current) so source is quieter without mute
 */
class DubPlayback(context: Context? = null) {
    private val appContext = context?.applicationContext
    private val audioManager = appContext?.getSystemService(AudioManager::class.java)
    private var track: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null
    private val playing = AtomicBoolean(false)
    private var savedMusicVolume: Int = -1

    fun start() {
        VoxoraLog.i("Playback", "start()")
        stop()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
            .build()

        requestFocus(attrs)
        lowerSourceVolume()

        val minBuf = AudioTrack.getMinBufferSize(
            GeminiLiveConfig.OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufSize = (minBuf * 6).coerceAtLeast(GeminiLiveConfig.OUTPUT_SAMPLE_RATE)
        track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
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
        VoxoraLog.i("Playback", "AudioTrack playing buf=${bufSize}")
    }

    /** Must be called off the main thread. */
    fun writeFloats(samples: FloatArray) {
        if (!playing.get()) return
        val t = track ?: return
        val shorts = ShortArray(samples.size) { i ->
            val s = samples[i].coerceIn(-1f, 1f)
            (if (s < 0) s * 0x8000 else s * 0x7fff).toInt().toShort()
        }
        var offset = 0
        while (offset < shorts.size && playing.get()) {
            val written = t.write(shorts, offset, shorts.size - offset)
            if (written < 0) break
            offset += written
        }
    }

    fun stop() {
        VoxoraLog.i("Playback", "stop()")
        playing.set(false)
        try {
            track?.pause()
            track?.flush()
            track?.stop()
            track?.release()
        } catch (_: Exception) {
        }
        track = null
        abandonFocus()
        restoreSourceVolume()
    }

    private fun requestFocus(attrs: AudioAttributes) {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                focusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "requestAudioFocus: ${e.message}")
        }
    }

    private fun abandonFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Soft-duck original media to ~30% of the *current* volume.
     * Avoid absolute ~5% of max — that feels like mute and forces the user
     * to press volume-up (which undoes the duck).
     */
    private fun lowerSourceVolume() {
        val am = audioManager ?: return
        try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (cur <= 0 || max <= 0) return
            savedMusicVolume = cur
            val target = (cur * 30 / 100).coerceAtLeast(1).coerceAtMost(cur - 1)
            if (target < cur) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                VoxoraLog.i("Playback", "duck STREAM_MUSIC $cur → $target (max=$max)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "lowerSourceVolume: ${e.message}")
            VoxoraLog.w("Playback", "duck failed: ${e.message}")
            savedMusicVolume = -1
        }
    }

    private fun restoreSourceVolume() {
        val am = audioManager ?: return
        val saved = savedMusicVolume
        savedMusicVolume = -1
        if (saved < 0) return
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0)
            VoxoraLog.i("Playback", "restore STREAM_MUSIC → $saved")
        } catch (e: Exception) {
            Log.w(TAG, "restoreSourceVolume: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "VoxoraPlayback"
    }
}
