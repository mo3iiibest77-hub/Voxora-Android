package com.voxora.app.dub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voxora.app.MainActivity
import com.voxora.app.R
import com.voxora.app.dub.sync.ChunkAction
import com.voxora.app.dub.sync.DubEvent
import com.voxora.app.dub.sync.DubSyncController
import com.voxora.app.dub.sync.ExternalPlayer
import com.voxora.app.dub.sync.LatencyTimeline
import com.voxora.app.dub.sync.MediaSessionExternalPlayer
import com.voxora.app.dub.sync.MonotonicClock
import com.voxora.app.dub.sync.PlaybackTimeline
import com.voxora.app.dub.sync.SyncDecision
import com.voxora.app.dub.sync.SyncState
import com.voxora.app.util.StatusToast
import com.voxora.app.util.VoxoraLog
import com.voxora.core.GeminiLiveConfig
import com.voxora.core.gemini.GeminiLiveSession
import com.voxora.core.gemini.GeminiStatus
import com.voxora.core.prefs.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

private fun Intent.mediaProjectionData(): Intent? {
    return if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(DubService.EXTRA_DATA, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(DubService.EXTRA_DATA)
    }
}

class DubService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val gemini = GeminiLiveSession()

    // ---- Synchronization layer. Pure logic lives in `dub/sync`; this class only feeds it. ----
    private val clock = MonotonicClock { SystemClock.elapsedRealtimeNanos() }

    // These are initialised in onCreate, not as field initialisers: a Service's fields are built
    // before attachBaseContext, so `applicationContext` is not available yet and constructing
    // anything context-bound here would crash on a null base context.
    private lateinit var timeline: LatencyTimeline
    private lateinit var playbackTimeline: PlaybackTimeline
    private lateinit var externalPlayer: ExternalPlayer
    private lateinit var sync: DubSyncController

    /** Source audio content captured, in nanoseconds. Advanced only while the source is audible. */
    private val sourceContentNanos = AtomicLong(0L)

    /** Chunks the ordered consumer actually received, for accounting Gemini's drops. */
    private val collectedDubChunks = AtomicLong(0L)

    /** Guards the one-shot "first audio actually left the speaker" instrumentation mark. */
    private val actualPlaybackMarked = AtomicBoolean(false)

    private val capture = SystemAudioCapture { pcm ->
        var sum = 0.0
        for (s in pcm) sum += s * s
        val rms = if (pcm.isNotEmpty()) sqrt(sum / pcm.size).toFloat() else 0f
        val level = (rms * 4f).coerceIn(0f, 1f)
        val prev = _audioLevel.value
        _audioLevel.value = prev * 0.55f + level * 0.45f
        timeline.mark(DubEvent.SOURCE_CHUNK)
        // The source content clock advances only while the source is actually audible and not
        // held by a correction. Counting silence would make a paused source look like it was
        // still moving, and the synchronizer would try to "catch up" forever.
        if (rms >= SOURCE_SILENCE_RMS && !sync.isHoldingSource) {
            sourceContentNanos.addAndGet(
                pcm.size * 1_000_000_000L / GeminiLiveConfig.INPUT_SAMPLE_RATE,
            )
        }
        timeline.mark(DubEvent.PCM_SENT)
        gemini.sendPcm16k(pcm)
    }
    private lateinit var playback: DubPlayback
    private var projection: MediaProjection? = null
    private var lastToastKey: String = ""
    private var syncJob: Job? = null
    private var lastSyncState = SyncState.IDLE

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        VoxoraLog.i("DubService", "onCreate")
        playback = DubPlayback(applicationContext)
        timeline = LatencyTimeline(clock, log = { VoxoraLog.d("DubSync", it) })
        playbackTimeline = PlaybackTimeline(log = { VoxoraLog.i("DubPlayback", it) })
        externalPlayer = MediaSessionExternalPlayer(applicationContext)
        sync = DubSyncController(externalPlayer, log = { VoxoraLog.i("DubSync", it) })
        createChannel()

        gemini.onFirstAudio = { timeline.mark(DubEvent.GEMINI_FIRST_AUDIO) }

        scope.launch {
            gemini.status.collect { st ->
                val mapped = when (st) {
                    is GeminiStatus.Idle -> {
                        if (_status.value is DubUiStatus.Error) _status.value
                        else DubUiStatus.Idle
                    }
                    is GeminiStatus.Connecting -> DubUiStatus.Connecting
                    is GeminiStatus.Ready -> DubUiStatus.Live
                    is GeminiStatus.Reconnecting -> {
                        // The audio that was in flight is gone, and the new connection may have a
                        // different round trip. Rebase both timelines rather than carrying stale
                        // counters into a session that no longer matches them.
                        playbackTimeline.reset(clock.nowNanos(), playback.playedNanos())
                        sync.onGeminiReconnect(clock.nowNanos())
                        DubUiStatus.Connecting
                    }
                    is GeminiStatus.Error -> DubUiStatus.Error(mapError(st.message))
                }
                _status.value = mapped
                if (mapped !is DubUiStatus.Live && mapped !is DubUiStatus.Connecting) {
                    _audioLevel.value = 0f
                }
                toastForGemini(st)
                postUiUpdate()
            }
        }

        // One ordered consumer, deliberately. The previous implementation launched a new
        // coroutine per emission, so two chunks could be written to the AudioTrack out of order
        // under load — audible as stutter and as the synchronizer's clocks disagreeing.
        // `writeFloats` blocks on WRITE_BLOCKING, which is the backpressure that keeps the
        // Gemini buffer from being overrun.
        //
        // The consumer is also where the playback timeline is enforced. Because it receives
        // oldest-first, refusing the chunk it is holding is exactly the reference behaviour of
        // discarding the oldest queued audio: the content the user hears jumps forward instead of
        // the dub falling further behind.
        scope.launch(Dispatchers.IO) {
            gemini.audioOut.collect { samples ->
                val now = clock.nowNanos()
                val durationNanos =
                    samples.size * 1_000_000_000L / GeminiLiveConfig.OUTPUT_SAMPLE_RATE
                timeline.mark(DubEvent.DUB_CHUNK, now)
                val action = playbackTimeline.onChunkArrived(
                    now,
                    durationNanos,
                    playback.playedNanos(),
                )
                if (action == ChunkAction.PLAY) {
                    playback.writeFloats(samples)
                    timeline.mark(DubEvent.AUDIO_WRITE)
                    timeline.mark(DubEvent.SCHEDULED_PLAYBACK)
                } else {
                    timeline.mark(DubEvent.CHUNK_DROPPED)
                }
                collectedDubChunks.incrementAndGet()
                val dropped = gemini.emittedAudioChunks - collectedDubChunks.get()
                if (dropped > 0) timeline.recordDroppedChunks(dropped)
                markActualPlayback()
            }
        }
    }

    /**
     * Marks, once per session, the monotonic time the platform associates with audio actually
     * leaving the speaker.
     *
     * Deliberately not derived from the write timestamp: the whole point of the measurement is
     * to separate "handed to the output" from "heard", and only the device can answer the second.
     */
    private fun markActualPlayback() {
        if (actualPlaybackMarked.get()) return
        val playedAt = playback.playbackTimestampNanos() ?: return
        if (actualPlaybackMarked.compareAndSet(false, true)) {
            timeline.mark(DubEvent.ACTUAL_PLAYBACK, playedAt)
        }
    }

    private fun toastForGemini(st: GeminiStatus) {
        val key = when (st) {
            is GeminiStatus.Connecting -> "connecting"
            is GeminiStatus.Ready -> "ready"
            is GeminiStatus.Reconnecting -> "reconnect"
            is GeminiStatus.Error -> "err:${st.message}"
            is GeminiStatus.Idle -> "idle"
        }
        if (key == lastToastKey) return
        lastToastKey = key
        val msg = when (st) {
            is GeminiStatus.Connecting -> getString(R.string.toast_connecting)
            is GeminiStatus.Ready -> getString(R.string.toast_gemini_ready)
            is GeminiStatus.Reconnecting -> getString(R.string.toast_reconnecting)
            is GeminiStatus.Error -> mapError(st.message)
            is GeminiStatus.Idle -> return
        }
        StatusToast.show(applicationContext, msg)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAll()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                VoxoraLog.i("DubService", "ACTION_START")
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.mediaProjectionData()
                if (data == null) {
                    VoxoraLog.e("DubService", "projection data null")
                    setErrorAndStop(getString(R.string.error_projection_missing))
                    return START_NOT_STICKY
                }
                startForegroundTyped()
                scope.launch { beginSession(resultCode, data) }
            }
        }
        return START_STICKY
    }

    private suspend fun beginSession(resultCode: Int, data: Intent) {
        VoxoraLog.i("DubService", "beginSession resultCode=$resultCode")
        try {
            val prefs = UserPrefs(applicationContext)
            val apiKey = prefs.apiKey.first()
            val lang = prefs.targetLanguage.first()
            VoxoraLog.i("DubService", "prefs ok lang=$lang keyLen=${apiKey.length}")
            if (apiKey.isBlank()) {
                VoxoraLog.e("DubService", "apiKey blank")
                setErrorAndStop(getString(R.string.error_no_api_key))
                return
            }
            val mpm = getSystemService(MediaProjectionManager::class.java)
            VoxoraLog.i("DubService", "getting MediaProjection...")
            val proj = mpm.getMediaProjection(resultCode, data)
                ?: throw IllegalStateException(getString(R.string.error_projection_null))
            projection = proj
            VoxoraLog.i("DubService", "MediaProjection obtained")
            withContext(Dispatchers.Main) {
                proj.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        VoxoraLog.w("DubService", "MediaProjection.onStop")
                        scope.launch {
                            stopAll()
                            stopSelf()
                        }
                    }
                }, null)
                VoxoraLog.i("DubService", "starting playback on Main...")
                playback.start()
            }
            VoxoraLog.i("DubService", "playback started, connecting Gemini...")
            lastToastKey = ""
            StatusToast.show(applicationContext, getString(R.string.toast_connecting))
            gemini.connect(apiKey, lang)
            VoxoraLog.i("DubService", "gemini.connect called, starting capture...")
            capture.start(proj, scope, applicationInfo.uid)
            VoxoraLog.i("DubService", "capture started")
            startSyncLoop()
            _status.value = DubUiStatus.Connecting
            postUiUpdate()
            VoxoraLog.i("DubService", "beginSession done → Connecting")
        } catch (e: Exception) {
            VoxoraLog.e("DubService", "beginSession FAILED", e)
            Log.e(TAG, "beginSession", e)
            setErrorAndStop(mapError(e.message ?: getString(R.string.error_start_failed)))
        }
    }

    /**
     * Drives the adaptive synchronizer and the rate-limited latency log.
     *
     * A quarter-second tick is far below the drift tolerance, so the controller sees the drift
     * long before it matters, and it is cheap enough to run for hours.
     */
    private fun startSyncLoop() {
        syncJob?.cancel()
        timeline.reset()
        actualPlaybackMarked.set(false)
        sourceContentNanos.set(0L)
        collectedDubChunks.set(0L)
        lastSyncState = SyncState.IDLE
        val startedAt = clock.nowNanos()
        playbackTimeline.start(startedAt, playback.playedNanos())
        sync.start(startedAt)
        _syncState.value = SyncState.WARMING_UP
        syncJob = scope.launch {
            while (isActive) {
                delay(SYNC_TICK_MS)
                val now = clock.nowNanos()
                // The dub's content clock is the *playhead*, not the write cursor. Audio sitting
                // in the output buffer has been written but not heard, and counting it as
                // progress is exactly how a pipeline convinces itself it is in sync when it is
                // not.
                val played = playback.playedNanos()
                playbackTimeline.onPlayed(played)
                val decision = sync.tick(now, sourceContentNanos.get(), played)
                when (decision) {
                    SyncDecision.PAUSE_SOURCE -> timeline.mark(DubEvent.SOURCE_PAUSE, now)
                    SyncDecision.RESUME_SOURCE -> timeline.mark(DubEvent.SOURCE_RESUME, now)
                    else -> Unit
                }
                _syncState.value = sync.state
                val latencyMs = sync.baselineLatencyNanos / 1_000_000
                val stateChanged = sync.state != lastSyncState
                lastSyncState = sync.state
                timeline.maybeLog(
                    now,
                    context = "state=${sync.state} latency=${latencyMs}ms " +
                        "backlog=${playbackTimeline.backlogNanos / 1_000_000}ms " +
                        "tolerance=${playbackTimeline.toleranceNanos / 1_000_000}ms " +
                        "drops=${playbackTimeline.dropCount} " +
                        "underruns=${playbackTimeline.underrunCount}",
                    force = stateChanged,
                )
            }
        }
    }

    private fun setErrorAndStop(message: String) {
        VoxoraLog.e("DubService", "setErrorAndStop: $message")
        _status.value = DubUiStatus.Error(message)
        StatusToast.show(applicationContext, message)
        scope.launch { postUiUpdate() }
        stopAll()
        stopForegroundCompat()
        stopSelf()
    }

    private fun mapError(raw: String): String {
        val m = raw.lowercase()
        return when {
            m.contains("401") || m.contains("api key") || m.contains("invalid") || m.contains("permission denied") ->
                getString(R.string.error_api_invalid)
            m.contains("429") || m.contains("quota") || m.contains("resource exhausted") ->
                getString(R.string.error_quota)
            m.contains("network") || m.contains("unable to resolve") || m.contains("failed to connect") ||
                m.contains("timeout") || m.contains("unreachable") ->
                getString(R.string.error_network)
            m.contains("permission") || m.contains("security") ->
                getString(R.string.error_permission)
            m.contains("handler") || m.contains("looper") ->
                getString(R.string.error_start_failed)
            else -> raw
        }
    }

    private fun stopAll() {
        syncJob?.cancel()
        syncJob = null
        // Order matters: release the source first (a correction may be holding it paused), then
        // stop playback, which restores the source volume to exactly what the user had.
        // Guarded because stopAll also runs from onDestroy even if onCreate failed early.
        if (::sync.isInitialized) sync.stop()
        if (::playbackTimeline.isInitialized) playbackTimeline.stop()
        if (::externalPlayer.isInitialized) externalPlayer.release()
        capture.stop()
        gemini.stop()
        try { playback.stop() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        _audioLevel.value = 0f
        _syncState.value = SyncState.IDLE
        FloatingBubbleService.hide(this)
        if (_status.value !is DubUiStatus.Error) {
            _status.value = DubUiStatus.Idle
        }
        stopForegroundCompat()
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
            else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
    }

    private suspend fun postUiUpdate() {
        withContext(Dispatchers.Main) {
            updateNotification()
            syncBubble()
        }
    }

    private fun syncBubble() {
        when (_status.value) {
            is DubUiStatus.Live, is DubUiStatus.Connecting -> FloatingBubbleService.show(this)
            else -> FloatingBubbleService.hide(this)
        }
    }

    override fun onDestroy() {
        stopAll()
        scope.cancel()
        instance = null
        super.onDestroy()
    }

    private fun startForegroundTyped() {
        val n = buildNotification(
            title = getString(R.string.notif_title_connecting),
            body = getString(R.string.notif_body_connecting),
        )
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification() {
        val (title, body) = when (val s = _status.value) {
            is DubUiStatus.Live ->
                getString(R.string.notif_title_live) to getString(R.string.notif_body_live)
            is DubUiStatus.Connecting ->
                getString(R.string.notif_title_connecting) to getString(R.string.notif_body_connecting)
            is DubUiStatus.Error ->
                getString(R.string.notif_title_error) to s.message
            else ->
                getString(R.string.app_name) to getString(R.string.status_idle)
        }
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(title, body))
    }

    private fun buildNotification(title: String, body: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, DubService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentIntent(open)
            .addAction(0, getString(R.string.action_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    companion object {
        private const val TAG = "VoxoraDub"
        private const val CHANNEL_ID = "voxora_live"
        private const val NOTIF_ID = 42
        const val ACTION_START = "com.voxora.app.START_DUB"
        const val ACTION_STOP = "com.voxora.app.STOP_DUB"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"

        /** How often the synchronizer is allowed to look at the clocks. */
        private const val SYNC_TICK_MS = 250L

        /** Below this RMS a captured chunk is treated as silence, not as source progress. */
        private const val SOURCE_SILENCE_RMS = 0.004f

        private val _status = MutableStateFlow<DubUiStatus>(DubUiStatus.Idle)
        val status: StateFlow<DubUiStatus> = _status.asStateFlow()

        private val _audioLevel = MutableStateFlow(0f)
        val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

        private val _syncState = MutableStateFlow(SyncState.IDLE)
        val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

        @Volatile
        var instance: DubService? = null
            private set

        fun start(context: Context, resultCode: Int, data: Intent) {
            val i = Intent(context, DubService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, DubService::class.java).setAction(ACTION_STOP))
        }

        fun postError(message: String) {
            _status.value = DubUiStatus.Error(message)
        }

        fun clearError() {
            if (_status.value is DubUiStatus.Error) {
                _status.value = DubUiStatus.Idle
            }
        }
    }
}

sealed class DubUiStatus {
    data object Idle : DubUiStatus()
    data object Connecting : DubUiStatus()
    data object Live : DubUiStatus()
    data class Error(val message: String) : DubUiStatus()
}
