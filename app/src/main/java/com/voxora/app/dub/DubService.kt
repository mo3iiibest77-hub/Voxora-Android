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

private fun Intent.mediaProjectionData(): Intent? {
    return if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(DubService.EXTRA_DATA, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(DubService.EXTRA_DATA)
    }
}

class DubService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gemini = GeminiLiveSession()
    private val capture = SystemAudioCapture { pcm -> gemini.sendPcm16k(pcm) }
    private val playback = DubPlayback()
    private var projection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        scope.launch {
            gemini.status.collect { st ->
                _status.value = when (st) {
                    is GeminiStatus.Idle -> DubUiStatus.Idle
                    is GeminiStatus.Connecting -> DubUiStatus.Connecting
                    is GeminiStatus.Ready -> DubUiStatus.Live
                    is GeminiStatus.Reconnecting -> DubUiStatus.Connecting
                    is GeminiStatus.Error -> DubUiStatus.Error(mapError(st.message))
                }
                updateNotification()
                syncBubble()
            }
        }
        scope.launch {
            gemini.audioOut.collect { samples ->
                playback.writeFloats(samples)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAll()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.mediaProjectionData()
                if (data == null) {
                    _status.value = DubUiStatus.Error(getString(R.string.error_projection_missing))
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundTyped()
                scope.launch { beginSession(resultCode, data) }
            }
        }
        return START_STICKY
    }

    private suspend fun beginSession(resultCode: Int, data: Intent) {
        try {
            val prefs = UserPrefs(applicationContext)
            val apiKey = prefs.apiKey.first()
            val lang = prefs.targetLanguage.first()
            if (apiKey.isBlank()) {
                _status.value = DubUiStatus.Error(getString(R.string.error_no_api_key))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val proj = mpm.getMediaProjection(resultCode, data)
                ?: throw IllegalStateException(getString(R.string.error_projection_null))
            projection = proj
            proj.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopAll()
                    stopSelf()
                }
            }, null)

            playback.start()
            gemini.connect(apiKey, lang)
            capture.start(proj, scope)
            _status.value = DubUiStatus.Connecting
            updateNotification()
            syncBubble()
        } catch (e: Exception) {
            Log.e(TAG, "beginSession", e)
            _status.value = DubUiStatus.Error(mapError(e.message ?: getString(R.string.error_start_failed)))
            stopAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun mapError(raw: String): String {
        val m = raw.lowercase()
        return when {
            m.contains("401") || m.contains("api key") || m.contains("invalid") ->
                getString(R.string.error_api_invalid)
            m.contains("429") || m.contains("quota") || m.contains("resource exhausted") ->
                getString(R.string.error_quota)
            m.contains("network") || m.contains("unable to resolve") || m.contains("failed to connect") ->
                getString(R.string.error_network)
            m.contains("permission") || m.contains("security") ->
                getString(R.string.error_permission)
            else -> raw
        }
    }

    private fun stopAll() {
        capture.stop()
        gemini.stop()
        playback.stop()
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        FloatingBubbleService.hide(this)
        _status.value = DubUiStatus.Idle
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
