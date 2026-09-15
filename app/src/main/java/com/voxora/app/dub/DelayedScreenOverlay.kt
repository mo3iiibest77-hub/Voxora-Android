package com.voxora.app.dub

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import com.voxora.app.util.VoxoraLog
import java.util.ArrayDeque

/**
 * Experimental lipsync: capture screen at low res, show frames delayed by [lagMs]
 * so picture lines up with Gemini audio (~2–3s late).
 *
 * Real YouTube surface stays underneath; we cover it with delayed frames.
 * If capture fails or is too heavy, call stop() and picture stays real-time.
 */
class DelayedScreenOverlay(
    private val context: Context,
    private val lagMs: Long = DEFAULT_LAG_MS,
) {
    private var windowManager: WindowManager? = null
    private var imageView: ImageView? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val frameQueue = ArrayDeque<TimedFrame>()
    private val lock = Any()
    private var running = false
    private var width = 360
    private var height = 640

    fun start(projection: MediaProjection) {
        stop()
        running = true
        val metrics = context.resources.displayMetrics
        // Low-res to limit memory (~360p)
        val scale = 0.33f
        width = (metrics.widthPixels * scale).toInt().coerceAtLeast(240).coerceAtMost(480)
        height = (metrics.heightPixels * scale).toInt().coerceAtLeast(400).coerceAtMost(860)

        thread = HandlerThread("voxora-lipsync").also { it.start() }
        handler = Handler(thread!!.looper)

        try {
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            imageReader?.setOnImageAvailableListener({ reader ->
                onImage(reader)
            }, handler)

            virtualDisplay = projection.createVirtualDisplay(
                "voxora-delay",
                width,
                height,
                metrics.densityDpi / 3,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                handler,
            )

            handler?.post(displayRunnable)
            showOverlay(metrics)
            VoxoraLog.i("Lipsync", "started lagMs=$lagMs size=${width}x$height")
        } catch (e: Exception) {
            VoxoraLog.e("Lipsync", "start failed: ${e.message}", e)
            stop()
        }
    }

    private fun showOverlay(metrics: DisplayMetrics) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        val iv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            setBackgroundColor(0xFF000000.toInt())
        }
        imageView = iv
        try {
            wm.addView(iv, lp)
        } catch (e: Exception) {
            VoxoraLog.e("Lipsync", "overlay add failed: ${e.message}")
            imageView = null
        }
    }

    private fun onImage(reader: ImageReader) {
        if (!running) return
        val image = try {
            reader.acquireLatestImage()
        } catch (_: Exception) {
            null
        } ?: return
        try {
            val plane = image.planes[0]
            val buf = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val bitmap = android.graphics.Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                android.graphics.Bitmap.Config.ARGB_8888,
            )
            bitmap.copyPixelsFromBuffer(buf)
            val cropped = if (bitmap.width != width) {
                android.graphics.Bitmap.createBitmap(bitmap, 0, 0, width, height).also {
                    if (it != bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }
            val now = SystemClock.elapsedRealtime()
            synchronized(lock) {
                // Cap queue length (~lag + headroom at ~12 fps)
                while (frameQueue.size > 40) {
                    frameQueue.removeFirst().bitmap.recycle()
                }
                frameQueue.addLast(TimedFrame(now, cropped))
            }
        } catch (e: Exception) {
            VoxoraLog.w("Lipsync", "frame error: ${e.message}")
        } finally {
            try {
                image.close()
            } catch (_: Exception) {
            }
        }
    }

    private val displayRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.elapsedRealtime()
            var show: android.graphics.Bitmap? = null
            synchronized(lock) {
                while (frameQueue.isNotEmpty()) {
                    val f = frameQueue.first()
                    if (now - f.t >= lagMs) {
                        frameQueue.removeFirst()
                        show?.recycle()
                        show = f.bitmap
                    } else {
                        break
                    }
                }
            }
            if (show != null) {
                val bmp = show
                imageView?.post {
                    try {
                        imageView?.setImageBitmap(bmp)
                    } catch (_: Exception) {
                    }
                }
            }
            handler?.postDelayed(this, 50L)
        }
    }

    fun stop() {
        running = false
        try {
            handler?.removeCallbacksAndMessages(null)
        } catch (_: Exception) {
        }
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null
        synchronized(lock) {
            while (frameQueue.isNotEmpty()) {
                frameQueue.removeFirst().bitmap.recycle()
            }
        }
        try {
            imageView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        imageView = null
        windowManager = null
        try {
            thread?.quitSafely()
        } catch (_: Exception) {
        }
        thread = null
        handler = null
        VoxoraLog.i("Lipsync", "stopped")
    }

    private data class TimedFrame(val t: Long, val bitmap: android.graphics.Bitmap)

    companion object {
        const val DEFAULT_LAG_MS = 2200L
    }
}
