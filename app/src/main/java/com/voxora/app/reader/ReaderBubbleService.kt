package com.voxora.app.reader

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
import com.voxora.core.prefs.UserPrefs
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// Voxora brand palette (mirrors ui/theme/Theme.kt and the Live bubble). Flat colours only —
// the Reader disc deliberately uses no gradient.
private val BRAND_GOLD = Color.parseColor("#D4AF37")
private val BRAND_READER_GREEN = Color.parseColor("#3DDC84")
private val BRAND_ERROR = Color.parseColor("#E85D5D")
private val BRAND_BUBBLE_FILL = 0xF21C1C1F.toInt()

/**
 * Circular Reader bubble for background narration.
 *
 * This is the Reader's counterpart to the Live bubble and deliberately a **separate service in
 * `reader/`**: the Live bubble imports `DubService`, and the reader package must never depend on
 * `dub/`, so the two cannot share a class. The behaviour is the same where it matters — drag the
 * whole widget, tap to reveal the stop disc, tap stop to end narration, double-tap to open the app,
 * an oval in the brand palette, and a teardown that never leaks a window — and it adds one Reader
 * specific rule: stopping from the bubble also clears the bubble preference, so a dismissed bubble
 * stays dismissed until the Reader's top-bar toggle turns it back on.
 *
 * The disc carries the flat brand label `● READER`: the leading dot and the letters are the reader
 * green, the `R` is a larger bold gold. The wave behind it is a solid gold fill rather than the
 * Live bubble's gradient.
 */
@AndroidEntryPoint
class ReaderBubbleService : Service() {
    @Inject lateinit var controller: ReaderController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var menuOpen = false
    private var density = 1f
    private var waveView: ReaderWaveView? = null
    private var stopView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastTapMs = 0L

    /** Drives the wave height; the Reader has no audio-level tap, so it tracks the phase. */
    @Volatile private var level = 0.15f

    private val levelPoll = object : Runnable {
        override fun run() {
            waveView?.level = level
            waveView?.invalidate()
            if (rootView != null) mainHandler.postDelayed(this, 50L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            controller.state.collect { state ->
                level = when (state.phase) {
                    ReaderPhase.SPEAKING -> 0.75f
                    ReaderPhase.CONNECTING, ReaderPhase.REWRITING, ReaderPhase.NEXT -> 0.35f
                    else -> 0.15f
                }
            }
        }
    }

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
            // Sits below the Live bubble's default spot so the two never open on top of each other.
            x = resources.displayMetrics.widthPixels - size - (10 * density).toInt()
            y = (232 * density).toInt()
        }
        params = lp
        attachDrag(root, lp)

        try {
            windowManager?.addView(root, lp)
            mainHandler.post(levelPoll)
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

        val readerDisc = buildReaderDisc(disc)
        if (!open) {
            root.addView(readerDisc)
            return
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // No OnClickListener — the parent touch listener handles drag and tap-to-stop.
        val stop = TextView(this).apply {
            text = "■"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(BRAND_ERROR)
            layoutParams = LinearLayout.LayoutParams(disc - 8, disc - 8).apply {
                marginEnd = (10 * d).toInt()
            }
            background = oval(BRAND_BUBBLE_FILL, BRAND_ERROR)
            isClickable = false
            isFocusable = false
        }
        stopView = stop
        row.addView(stop)
        row.addView(readerDisc)
        root.addView(row)
    }

    private fun buildReaderDisc(disc: Int): FrameLayout {
        val wrap = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(disc, disc)
            background = oval(BRAND_BUBBLE_FILL, BRAND_GOLD)
        }
        val wave = ReaderWaveView(this).apply {
            layoutParams = FrameLayout.LayoutParams(disc, disc)
        }
        waveView = wave
        wrap.addView(wave)

        val label = TextView(this).apply {
            text = readerSpannable()
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

    /** ● READER — dot green, R large bold gold, EADER green. */
    private fun readerSpannable(): SpannableString {
        val raw = "● READER"
        val ss = SpannableString(raw)
        val green = BRAND_READER_GREEN
        val gold = BRAND_GOLD
        ss.setSpan(ForegroundColorSpan(green), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(ForegroundColorSpan(gold), 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) // R
        ss.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(AbsoluteSizeSpan((18 * density).toInt(), true), 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ss.setSpan(ForegroundColorSpan(green), 3, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) // EADER
        return ss
    }

    private fun oval(fill: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke((1.5f * density).toInt().coerceAtLeast(2), stroke)
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

    /**
     * Ends narration and hides the bubble for good.
     *
     * The preference is cleared here on purpose: a stop from the bubble is the user dismissing it,
     * and the plan is explicit that a dismissal persists. Turning it back on is the Reader top-bar
     * toggle, which writes the same preference.
     *
     * The write is awaited **before** `stopSelf()`: `onDestroy` cancels [scope], so stopping first
     * would cancel the coroutine mid-write and the dismissal would silently not stick.
     */
    private fun stopAndDismiss() {
        ReaderService.stop(this)
        scope.launch {
            try {
                UserPrefs(applicationContext).setReaderBubble(false)
            } catch (_: Exception) {
            }
            removeBubble()
            stopSelf()
        }
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
                    // Always drag, including a finger on the stop disc.
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
                            stopAndDismiss()
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
        mainHandler.removeCallbacks(levelPoll)
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
        scope.cancel()
        super.onDestroy()
    }

    /**
     * A flat brand wave — the Live bubble's wave, minus the gradient.
     *
     * The Reader has no audio-level stream to follow, so [level] is set from the narration phase
     * instead; the bars still animate on their own so an idle bubble does not look frozen.
     */
    private class ReaderWaveView(context: Context) : View(context) {
        var level: Float = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = BRAND_GOLD
            alpha = 200
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
        const val ACTION_HIDE = "com.voxora.app.reader.HIDE_BUBBLE"

        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(context) else true
        }

        fun show(context: Context) {
            if (!canDrawOverlays(context)) return
            try {
                context.startService(Intent(context, ReaderBubbleService::class.java))
            } catch (_: Exception) {
            }
        }

        fun hide(context: Context) {
            try {
                context.startService(
                    Intent(context, ReaderBubbleService::class.java).setAction(ACTION_HIDE),
                )
            } catch (_: Exception) {
            }
        }
    }
}
