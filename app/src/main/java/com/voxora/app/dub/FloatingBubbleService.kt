package com.voxora.app.dub

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.voxora.app.MainActivity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * Circular Live bubble — drag works on entire widget including red Stop.
 * Single tap Live area toggles Stop; tap Stop (no drag) stops dubbing.
 * Double-tap opens app. Bigger gold V in "LiVe".
 */
class FloatingBubbleService : Service() {
    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var menuOpen = false
    private var density = 1f
    private var waveView: WaveView? = null
    private var stopView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastTapMs = 0L

    private val amplitudePoll = object : Runnable {
        override fun run() {
            waveView?.level = DubService.audioLevel.value
            waveView?.invalidate()
            if (rootView != null) mainHandler.postDelayed(this, 50L)
        }
    }

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
        val size = (68 * density).toInt()
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
            x = resources.displayMetrics.widthPixels - size - (10 * density).toInt()
            y = (150 * density).toInt()
        }
        params = lp
        attachDrag(root, lp)

        try {
            windowManager?.addView(root, lp)
            mainHandler.post(amplitudePoll)
        } catch (_: Exception) {
            rootView = null
            stopSelf()
        }
    }

    private fun rebuildContent(open: Boolean) {
        val root = rootView ?: return
        root.removeAllViews()
        menuOpen = open
        stopView = null
        val d = density
        val disc = (68 * d).toInt()

        val liveDisc = buildLiveDisc(disc)
        if (!open) {
            root.addView(liveDisc)
            return
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // No OnClickListener — parent touch handles drag + tap-to-stop
        val stop = TextView(this).apply {
            text = "■"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFFFF6B6B.toInt())
            layoutParams = LinearLayout.LayoutParams(disc - 8, disc - 8).apply {
                marginEnd = (10 * d).toInt()
            }
            background = oval(0xF0121212.toInt(), 0xFFFF6B6B.toInt())
            isClickable = false
            isFocusable = false
        }
        stopView = stop
        row.addView(stop)
        row.addView(liveDisc)
        root.addView(row)
    }

    private fun buildLiveDisc(disc: Int): FrameLayout {
        val wrap = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(disc, disc)
            background = oval(0xF0121212.toInt(), 0xFFE6B422.toInt())
        }
        val wave = WaveView(this).apply {
            layoutParams = FrameLayout.LayoutParams(disc, disc)
        }
        waveView = wave
        wrap.addView(wave)

        val label = TextView(this).apply {
            text = liveSpannable()
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, (16 * density).toInt(), 0, 0)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        wrap.addView(label)
        return wrap
    }

    /** ● LiVe — pip green, Li green, V large gold bold, e green */
    private fun liveSpannable(): SpannableString {
        val raw = "● LiVe"
        val ss = SpannableString(raw)
        val green = 0xFF3DDC97.toInt()
        val gold = 0xFFE6B422.toInt()
        ss.setSpan(ForegroundColorSpan(green), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(ForegroundColorSpan(green), 2, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) // Li
        ss.setSpan(ForegroundColorSpan(gold), 4, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) // V
        ss.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 4, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(AbsoluteSizeSpan((18 * density).toInt(), true), 4, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(ForegroundColorSpan(green), 5, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) // e
        return ss
    }

    private fun oval(fill: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke((2.5f * density).toInt().coerceAtLeast(2), stroke)
        }
    }

    private fun openApp() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                },
            )
        } catch (_: Exception) {
        }
    }

    private fun isTouchOnStop(rawX: Float, rawY: Float): Boolean {
        val stop = stopView ?: return false
        val loc = IntArray(2)
        stop.getLocationOnScreen(loc)
        val l = loc[0].toFloat()
        val t = loc[1].toFloat()
        return rawX >= l && rawX <= l + stop.width && rawY >= t && rawY <= t + stop.height
    }

    private fun attachDrag(view: View, lp: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var downOnStop = false

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    downOnStop = menuOpen && isTouchOnStop(event.rawX, event.rawY)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    // Always drag — including finger on red Stop
                    lp.x = (startX + dx).coerceIn(0, resources.displayMetrics.widthPixels - 48)
                    lp.y = (startY + dy).coerceIn(0, resources.displayMetrics.heightPixels - 48)
                    try {
                        windowManager?.updateViewLayout(v, lp)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (downOnStop && menuOpen) {
                            DubService.stop(this)
                            removeBubble()
                            stopSelf()
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastTapMs < 320) {
                                lastTapMs = 0
                                openApp()
                            } else {
                                lastTapMs = now
                                mainHandler.postDelayed({
                                    if (lastTapMs == now) {
                                        rebuildContent(!menuOpen)
                                        updateLayout()
                                    }
                                }, 280)
                            }
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
        mainHandler.removeCallbacks(amplitudePoll)
        try {
            rootView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        rootView = null
        params = null
        waveView = null
        stopView = null
    }

    override fun onDestroy() {
        removeBubble()
        super.onDestroy()
    }

    private class WaveView(context: Context) : View(context) {
        var level: Float = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6B422")
            style = Paint.Style.FILL
            alpha = 90
        }
        private var phase = 0f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return
            val bars = 7
            val gap = w * 0.04f
            val barW = (w - gap * (bars + 1)) / bars
            phase += 0.35f
            val base = max(0.08f, level)
            for (i in 0 until bars) {
                val n = ((sin(phase + i * 0.7f) + 1f) / 2f)
                val amp = (0.25f + 0.75f * base * (0.4f + 0.6f * n)).coerceIn(0.12f, 0.92f)
                val bh = h * amp * 0.55f
                val left = gap + i * (barW + gap)
                val top = (h - bh) / 2f
                canvas.drawRoundRect(left, top, left + barW, top + bh, barW / 2f, barW / 2f, paint)
            }
        }
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
