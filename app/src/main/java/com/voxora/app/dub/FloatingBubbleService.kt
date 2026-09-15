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
 * Always-circular brand bubble (Telegram-voice style):
 * - Default: round disc + V logo + green live pip
 * - Drag anywhere
 * - Tap → small circular Stop action
 * - Snap to edge stays circular
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
        rebuildContent(menuOpen = false)

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val size = (56 * density).toInt()
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

    private fun rebuildContent(menuOpen: Boolean) {
        val root = rootView ?: return
        root.removeAllViews()
        this.menuOpen = menuOpen
        val d = density
        val size = (56 * d).toInt()

        val circle = ImageView(this).apply {
            setImageResource(R.drawable.ic_voxora_bubble)
            layoutParams = FrameLayout.LayoutParams(size, size)
            scaleType = ImageView.ScaleType.FIT_XY
            contentDescription = getString(R.string.app_name)
            elevation = 10 * d
        }

        if (!menuOpen) {
            root.addView(circle)
            return
        }

        // Expanded: still circular cluster — logo + stop disc
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val stopSize = (48 * d).toInt()
        val stop = TextView(this).apply {
            text = "■"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFFFF6B6B.toInt())
            layoutParams = LinearLayout.LayoutParams(stopSize, stopSize).apply {
                marginEnd = (8 * d).toInt()
            }
            background = circleBg(0xF01A1A1A.toInt(), 0xFFFF6B6B.toInt())
            setOnClickListener {
                DubService.stop(this@FloatingBubbleService)
                removeBubble()
                stopSelf()
            }
        }

        row.addView(stop)
        row.addView(circle)
        root.addView(row)

        // Tap logo again to collapse
        circle.setOnClickListener {
            rebuildContent(false)
            updateLayout()
        }
    }

    private fun circleBg(fill: Int, stroke: Int): GradientDrawable {
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
                    } else {
                        // snap slightly to edge if close
                        val screenW = resources.displayMetrics.widthPixels
                        if (lp.x < 24) lp.x = 0
                        if (lp.x > screenW - 80) lp.x = screenW - (56 * density).toInt()
                        try {
                            windowManager?.updateViewLayout(v, lp)
                        } catch (_: Exception) {
                        }
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
