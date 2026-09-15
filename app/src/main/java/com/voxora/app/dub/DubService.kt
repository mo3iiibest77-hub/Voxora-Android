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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voxora.app.MainActivity
import com.voxora.app.R
import com.voxora.app.util.StatusToast
import com.voxora.app.util.VoxoraLog
import com.voxora.core.gemini.GeminiLiveSession
import com.voxora.core.gemini.GeminiStatus
import com.voxora.core.prefs.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val gemini = GeminiLiveSession()
    private val capture = SystemAudioCapture { pcm ->
        var sum = 0.0
        for (s in pcm) sum += s * s
        val rms = if (pcm.isNotEmpty()) sqrt(sum / pcm.size).toFloat() else 0f
        val level = (rms * 4f).coerceIn(0f, 1f)
        val prev = _audioLevel.value
        _audioLevel.value = prev * 0.55f + level * 0.45f
        gemini.sendPcm16k(pcm)
    }
    private lateinit var playback: DubPlayback
    private var projection: MediaProjection? = null
    private var lastToastKey: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        VoxoraLog.i("DubService", "onCreate")
        playback = DubPlayback(applicationContext)
        createChannel()

        scope.launch {
            gemini.status.collect { st ->
                val mapped = when (st) {
                    is GeminiStatus.Idle -> {
                        if (_status.value is DubUiStatus.Error) _status.value
                        else DubUiStatus.Idle
                    }
                    is GeminiStatus.Connecting -> DubUiStatus.Connecting
                    is GeminiStatus.Ready -> DubUiStatus.Live
                    is GeminiStatus.Reconnecting -> DubUiStatus.Connecting
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

        scope.launch {
            gemini.audioOut.collect { samples ->
                audioScope.launch {
                    playback.writeFloats(samples)
                }
            }
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
            _status.value = DubUiStatus.Connecting
            postUiUpdate()
            VoxoraLog.i("DubService", "beginSession done → Connecting")
        } catch (e: Exception) {
            VoxoraLog.e("DubService", "beginSession FAILED", e)
            Log.e(TAG, "beginSession", e)
            setErrorAndStop(mapError(e.message ?: getString(R.string.error_start_failed)))
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
        capture.stop()
        gemini.stop()
        try { playback.stop() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        _audioLevel.value = 0f
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
        audioScope.cancel()
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

        private val _status = MutableStateFlow<DubUiStatus>(DubUiStatus.Idle)
        val status: StateFlow<DubUiStatus> = _status.asStateFlow()

        private val _audioLevel = MutableStateFlow(0f)
        val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

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
