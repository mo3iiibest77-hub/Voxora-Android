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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.voxora.app.R
import kotlin.math.abs

/**
 * Circular brand bubble:
 * - Default: gold-ring circle with Voxora mark
 * - Drag anywhere
 * - Tap → expand Stop panel
 * - Minimize → half-circle edge tab with logo
 * - Tap tab → restore
 */
class FloatingBubbleService : Service() {
    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false
    private var density = 1f
    private var lastX = 0
    private var lastY = 0

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
        rebuildContent(expanded = false)

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val size = (52 * density).toInt()
        lastX = resources.displayMetrics.widthPixels - size - (10 * density).toInt()
        lastY = (140 * density).toInt()
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
            x = lastX
            y = lastY
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
            // Circular brand mark (or half-disk when snapped to edge)
            val circleSize = (48 * d).toInt()
            val icon = ImageView(this).apply {
                setImageResource(R.drawable.ic_voxora_bubble)
                layoutParams = FrameLayout.LayoutParams(circleSize, circleSize)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = getString(R.string.app_name)
                setOnClickListener {
                    rebuildContent(true)
                    updateLayout()
                }
            }
            root.addView(icon)
            return
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
            background = pillBg(0xF0121212.toInt(), (22 * d))
            elevation = 8 * d
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_voxora_bubble)
            layoutParams = LinearLayout.LayoutParams((28 * d).toInt(), (28 * d).toInt())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        val live = TextView(this).apply {
            text = getString(R.string.float_live_label)
            setTextColor(0xFFE6B422.toInt())
            textSize = 11f
            setPadding((6 * d).toInt(), 0, (8 * d).toInt(), 0)
        }

        val stop = TextView(this).apply {
            text = getString(R.string.action_stop)
            setTextColor(0xFFFF6B6B.toInt())
            textSize = 11f
            setPadding((8 * d).toInt(), (4 * d).toInt(), (8 * d).toInt(), (4 * d).toInt())
            background = pillBg(0x33FF6B6B, (12 * d))
            setOnClickListener {
                DubService.stop(this@FloatingBubbleService)
                removeBubble()
                stopSelf()
            }
        }

        val hide = TextView(this).apply {
            text = "·"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 16f
            setPadding((10 * d).toInt(), 0, 0, 0)
            setOnClickListener {
                snapToEdge()
                rebuildContent(false)
                updateLayout()
            }
        }

        row.addView(logo)
        row.addView(live)
        row.addView(stop)
        row.addView(hide)
        root.addView(row)
    }

    private fun snapToEdge() {
        val p = params ?: return
        val screenW = resources.displayMetrics.widthPixels
        val tab = (28 * density).toInt()
        p.x = if (p.x + 40 < screenW / 2) 0 else screenW - tab
        lastX = p.x
        lastY = p.y
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
                    if (abs(dx) > 6 || abs(dy) > 6) moved = true
                    lp.x = (startX + dx).coerceIn(0, resources.displayMetrics.widthPixels - 24)
                    lp.y = (startY + dy).coerceIn(0, resources.displayMetrics.heightPixels - 24)
                    lastX = lp.x
                    lastY = lp.y
                    try {
                        windowManager?.updateViewLayout(v, lp)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && !expanded) {
                        rebuildContent(true)
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
