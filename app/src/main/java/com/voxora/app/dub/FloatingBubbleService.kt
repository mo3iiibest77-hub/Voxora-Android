package com.voxora.app.dub

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.voxora.app.R
import kotlin.math.abs

/**
 * Simple circular floating control (Telegram-style disc):
 * - Always circular dark disc + gold "Live" + green pip
 * - Drag anywhere
 * - Tap → show circular Stop
 * - No wave logo / no rectangle pill as default
 */
class FloatingBubbleService : Service() {
    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var menuOpen = false
    private var density = 1f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                removeBubble()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (!canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        showBubble()
        return START_STICKY
    }

    private fun showBubble() {
        if (rootView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        density = resources.displayMetrics.density

        val root = FrameLayout(this)
        rootView = root
        rebuildContent(false)

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val size = (52 * density).toInt()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - (12 * density).toInt()
            y = (160 * density).toInt()
        }
        params = lp
        attachDrag(root, lp)

        try {
            windowManager?.addView(root, lp)
        } catch (_: Exception) {
            rootView = null
            stopSelf()
        }
    }

    private fun rebuildContent(open: Boolean) {
        val root = rootView ?: return
        root.removeAllViews()
        menuOpen = open
        val d = density
        val disc = (52 * d).toInt()

        if (!open) {
            val live = TextView(this).apply {
                text = getString(R.string.float_live_label)
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(0xFFE6B422.toInt())
                layoutParams = FrameLayout.LayoutParams(disc, disc)
                background = oval(0xF0121212.toInt(), 0xFFE6B422.toInt())
                // green live pip via compound? simple: prefix green dot in text
                text = "●  " + getString(R.string.float_live_label)
                setTextColor(0xFFE6B422.toInt())
            }
            // color the first char green is hard on TextView; use two layers
            root.addView(live)
            return
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val stop = TextView(this).apply {
            text = "■"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFFF6B6B.toInt())
            layoutParams = LinearLayout.LayoutParams(disc - 4, disc - 4).apply {
                marginEnd = (8 * d).toInt()
            }
            background = oval(0xF0121212.toInt(), 0xFFFF6B6B.toInt())
            setOnClickListener {
                DubService.stop(this@FloatingBubbleService)
                removeBubble()
                stopSelf()
            }
        }
        val live = TextView(this).apply {
            text = "●  " + getString(R.string.float_live_label)
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFFE6B422.toInt())
            layoutParams = LinearLayout.LayoutParams(disc, disc)
            background = oval(0xF0121212.toInt(), 0xFFE6B422.toInt())
            setOnClickListener {
                rebuildContent(false)
                updateLayout()
            }
        }
        row.addView(stop)
        row.addView(live)
        root.addView(row)
    }

    private fun oval(fill: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke((2 * density).toInt(), stroke)
        }
    }

    private fun attachDrag(view: View, lp: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > 6 || abs(dy) > 6) moved = true
                    lp.x = (startX + dx).coerceIn(0, resources.displayMetrics.widthPixels - 40)
                    lp.y = (startY + dy).coerceIn(0, resources.displayMetrics.heightPixels - 40)
                    try {
                        windowManager?.updateViewLayout(v, lp)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        rebuildContent(!menuOpen)
                        updateLayout()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateLayout() {
        val v = rootView ?: return
        val p = params ?: return
        try {
            windowManager?.updateViewLayout(v, p)
        } catch (_: Exception) {
        }
    }

    private fun removeBubble() {
        try {
            rootView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        rootView = null
        params = null
    }

    override fun onDestroy() {
        removeBubble()
        super.onDestroy()
    }

    companion object {
        const val ACTION_HIDE = "com.voxora.app.HIDE_BUBBLE"

        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(context) else true
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
