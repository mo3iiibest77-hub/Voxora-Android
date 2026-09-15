package com.voxora.app.dub

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.voxora.app.util.VoxoraLog
import com.voxora.core.GeminiLiveConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Plays Gemini PCM on a **separate** path from STREAM_MUSIC so we can duck
 * YouTube/Instagram/etc. without also quieting the dub.
 *
 * Strategy (closest to Chrome extension tab ducking on stock Android):
 * 1) AudioTrack uses USAGE_ASSISTANT + CONTENT_TYPE_SPEECH → not STREAM_MUSIC
 * 2) Request AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK so well-behaved apps soft-duck
 * 3) Lower STREAM_MUSIC only (source apps); assistant volume stays independent
 * 4) ALLOW_CAPTURE_BY_NONE so our output is not re-captured into Gemini
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
            // Separate from media stream — critical for independent volume
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
            .build()

        requestFocus(attrs)
        lowerSourceMusicOnly()

        val minBuf = AudioTrack.getMinBufferSize(
            GeminiLiveConfig.OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        // Larger buffer → fewer underruns / less choppy dub on weak networks
        val bufSize = (minBuf * 8).coerceAtLeast(GeminiLiveConfig.OUTPUT_SAMPLE_RATE * 2)
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
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        // Full assistant path volume (independent of STREAM_MUSIC)
        try {
            track?.setVolume(1.0f)
        } catch (_: Exception) {
        }
        track?.play()
        playing.set(true)
        VoxoraLog.i("Playback", "AudioTrack ASSISTANT playing buf=$bufSize")
    }

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
                VoxoraLog.w("Playback", "write error $written")
                break
            }
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
        restoreSourceMusic()
    }

    private fun requestFocus(attrs: AudioAttributes) {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener { change ->
                        VoxoraLog.i("Playback", "focusChange=$change")
                    }
                    .build()
                focusRequest = req
                val r = am.requestAudioFocus(req)
                VoxoraLog.i("Playback", "requestAudioFocus result=$r")
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
            VoxoraLog.w("Playback", "focus failed: ${e.message}")
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

    /** Duck only STREAM_MUSIC (YouTube, Instagram players, etc.). Dub stays on ASSISTANT. */
    private fun lowerSourceMusicOnly() {
        val am = audioManager ?: return
        try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (cur <= 0 || max <= 0) return
            savedMusicVolume = cur
            // ~4% of current media volume for source apps only
            val target = (cur * 4 / 100).coerceAtLeast(1).coerceAtMost(cur - 1)
            if (target < cur) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                VoxoraLog.i("Playback", "duck STREAM_MUSIC only $cur → $target (max=$max); dub=ASSISTANT full")
            }
        } catch (e: Exception) {
            Log.w(TAG, "lowerSourceMusicOnly: ${e.message}")
            VoxoraLog.w("Playback", "duck failed: ${e.message}")
            savedMusicVolume = -1
        }
    }

    private fun restoreSourceMusic() {
        val am = audioManager ?: return
        val saved = savedMusicVolume
        savedMusicVolume = -1
        if (saved < 0) return
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0)
            VoxoraLog.i("Playback", "restore STREAM_MUSIC → $saved")
        } catch (e: Exception) {
            Log.w(TAG, "restoreSourceMusic: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "VoxoraPlayback"
    }
}
