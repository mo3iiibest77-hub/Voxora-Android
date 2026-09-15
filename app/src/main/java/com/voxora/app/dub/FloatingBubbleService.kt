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
 * Tiny always-on-top control while dubbing.
 * - Compact pill (~1/3 previous size)
 * - Drag anywhere on screen
 * - Tap to expand (Stop) / long-press edge to minimize to side tab
 * - Tap minimized tab to restore
 */
class FloatingBubbleService : Service() {
    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = true
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
        rebuildContent(expanded = true)

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
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
            x = (resources.displayMetrics.widthPixels - (72 * density).toInt())
            y = (120 * density).toInt()
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

    private fun rebuildContent(expanded: Boolean) {
        val root = rootView ?: return
        root.removeAllViews()
        this.expanded = expanded
        val d = density

        if (!expanded) {
            // Minimal edge tab — tap to expand
            val tab = TextView(this).apply {
                text = "V"
                textSize = 11f
                setTextColor(0xFFE6B422.toInt())
                setPadding((8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt())
                background = pillBg(0xCC121212.toInt(), (16 * d))
                setOnClickListener { rebuildContent(true); updateLayout() }
            }
            root.addView(tab)
            return
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt())
            background = pillBg(0xE6121212.toInt(), (18 * d))
            elevation = 6 * d
        }

        val live = TextView(this).apply {
            text = getString(R.string.float_live_label)
            setTextColor(0xFFE6B422.toInt())
            textSize = 11f
            setPadding(0, 0, (8 * d).toInt(), 0)
        }

        val stop = TextView(this).apply {
            text = getString(R.string.action_stop)
            setTextColor(0xFFFF6B6B.toInt())
            textSize = 11f
            setPadding((6 * d).toInt(), (2 * d).toInt(), (6 * d).toInt(), (2 * d).toInt())
            background = pillBg(0x33FF6B6B, (12 * d))
            setOnClickListener {
                DubService.stop(this@FloatingBubbleService)
                removeBubble()
                stopSelf()
            }
        }

        val hide = TextView(this).apply {
            text = "–"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 12f
            setPadding((8 * d).toInt(), 0, 0, 0)
            setOnClickListener {
                // Snap to nearest edge as mini tab
                val p = params ?: return@setOnClickListener
                val screenW = resources.displayMetrics.widthPixels
                p.x = if (p.x + (root.width / 2) < screenW / 2) 0 else screenW - (28 * d).toInt()
                rebuildContent(false)
                updateLayout()
            }
        }

        row.addView(live)
        row.addView(stop)
        row.addView(hide)
        root.addView(row)
    }

    private fun pillBg(color: Int, radiusPx: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx
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
                    if (abs(dx) > 4 || abs(dy) > 4) moved = true
                    lp.x = (startX + dx).coerceIn(0, resources.displayMetrics.widthPixels - v.width.coerceAtLeast(1))
                    lp.y = (startY + dy).coerceIn(0, resources.displayMetrics.heightPixels - v.height.coerceAtLeast(1))
                    try {
                        windowManager?.updateViewLayout(v, lp)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && expanded) {
                        // treat as click on empty area — ignore
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
