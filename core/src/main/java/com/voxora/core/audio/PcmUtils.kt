package com.voxora.core.audio

import android.util.Base64
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PcmUtils {
    fun downsampleTo16k(samples: FloatArray, fromRate: Int): FloatArray {
        if (fromRate == 16_000) return samples
        val ratio = fromRate.toDouble() / 16_000.0
        val outLen = max(1, (samples.size / ratio).roundToInt())
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val src = i * ratio
            val i0 = src.toInt().coerceIn(0, samples.lastIndex)
            val i1 = min(i0 + 1, samples.lastIndex)
            val frac = (src - i0).toFloat()
            out[i] = samples[i0] * (1 - frac) + samples[i1] * frac
        }
        return out
    }

    fun floatTo16BitPcm(samples: FloatArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = max(-1f, min(1f, samples[i]))
            val v = if (s < 0) (s * 0x8000).toInt() else (s * 0x7fff).toInt()
            out[i * 2] = (v and 0xff).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xff).toByte()
        }
        return out
    }

    fun pcm16ToFloat(bytes: ByteArray): FloatArray {
        val usable = bytes.size - (bytes.size % 2)
        val out = FloatArray(usable / 2)
        var j = 0
        for (i in 0 until usable step 2) {
            val lo = bytes[i].toInt() and 0xff
            val hi = bytes[i + 1].toInt()
            val s = (hi shl 8) or lo
            val sample = s.toShort().toInt()
            out[j++] = sample / if (sample < 0) 32768f else 32767f
        }
        return out
    }

    fun toBase64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun fromBase64(b64: String): ByteArray =
        Base64.decode(b64, Base64.DEFAULT)
}
