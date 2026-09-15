package com.voxora.app.dub

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.voxora.app.R

/**
 * Compact always-on-top bubble while dubbing is live.
 * Requires SYSTEM_ALERT_WINDOW (user grants in system settings).
 */
class FloatingBubbleService : Service() {
    private var windowManager: WindowManager? = null
    private var bubbleView: FrameLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            removeBubble()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        showBubble()
        return START_STICKY
    }

    private fun showBubble() {
        if (bubbleView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xE6121212.toInt())
        }

        val label = TextView(this).apply {
            text = getString(R.string.float_live_label)
            setTextColor(0xFFE6B422.toInt())
            textSize = 13f
            setPadding(0, 0, (16 * density).toInt(), 0)
        }
        val stop = Button(this).apply {
            text = getString(R.string.action_stop)
            textSize = 12f
            setOnClickListener {
                DubService.stop(this@FloatingBubbleService)
                removeBubble()
                stopSelf()
            }
        }
        row.addView(label)
        row.addView(stop)

        val root = FrameLayout(this).apply { addView(row) }
        bubbleView = root

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (12 * density).toInt()
            y = (80 * density).toInt()
        }
        try {
            windowManager?.addView(root, params)
        } catch (_: Exception) {
            bubbleView = null
            stopSelf()
        }
    }

    private fun removeBubble() {
        try {
            bubbleView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        bubbleView = null
    }

    override fun onDestroy() {
        removeBubble()
        super.onDestroy()
    }

    companion object {
        const val ACTION_HIDE = "com.voxora.app.HIDE_BUBBLE"

        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= 23) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun show(context: Context) {
            if (!canDrawOverlays(context)) return
            context.startService(Intent(context, FloatingBubbleService::class.java))
        }

        fun hide(context: Context) {
            context.startService(
                Intent(context, FloatingBubbleService::class.java).setAction(ACTION_HIDE),
            )
        }
    }
}
