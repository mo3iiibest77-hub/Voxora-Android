package com.voxora.app.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import com.voxora.app.MainActivity
import com.voxora.app.R
import com.voxora.app.util.VoxoraLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class ReaderPhase { IDLE, EXTRACTING, READY, CONNECTING, REWRITING, SPEAKING, NEXT, PAUSED, STOPPED, COMPLETE, ERROR }

internal data class ReaderState(
    val phase: ReaderPhase = ReaderPhase.IDLE,
    val chunk: Int = 0,
    val total: Int = 0,
    val text: String = "",
    val error: String? = null,
)

@AndroidEntryPoint
class ReaderService : Service() {
    @Inject lateinit var controller: ReaderController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var mediaSession: MediaSession
    private var playbackJob: Job? = null
    private var generation = 0L
    private var latestStartId = 0
    private var destroyed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.reader_notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.reader_notif_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        mediaSession = MediaSession(this, "VoxoraReader").apply {
            setPlaybackToLocal(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, getString(R.string.reader_title))
                    .build(),
            )
            setCallback(object : MediaSession.Callback() {
                override fun onPause() = finishPlayback(stop = false)
                override fun onStop() = finishPlayback(stop = true)
            })
        }
        scope.launch {
            controller.state.collect { state ->
                withContext(Dispatchers.Main.immediate) {
                    if (!destroyed) updateMediaState(state.phase)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        mediaSession.isActive = true
        when (intent?.action) {
            ACTION_PLAY -> startPlayback(intent.getStringExtra(EXTRA_MODE) ?: "faithful")
            ACTION_STOP -> finishPlayback(stop = true)
            else -> finishPlayback(stop = false)
        }
        return START_NOT_STICKY
    }

    private fun startPlayback(mode: String) {
        runCommand { controller.play(mode) }
    }

    private fun finishPlayback(stop: Boolean) {
        runCommand {
            if (stop) controller.stop() else controller.pause()
        }
    }

    private fun runCommand(command: suspend () -> Unit) {
        val run = ++generation
        val previous = playbackJob
        previous?.cancel()
        playbackJob = scope.launch {
            previous?.cancelAndJoin()
            try {
                commandMutex.withLock {
                    ensureActive()
                    try {
                        command()
                    } finally {
                        withContext(NonCancellable) {
                            if (run == generation && controller.state.value.phase.isActivePlayback()) {
                                controller.pause()
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VoxoraLog.e(TAG, "Reader service command failed", e)
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (!destroyed && run == generation) {
                        playbackJob = null
                        mediaSession.isActive = false
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(latestStartId)
                    }
                }
            }
        }
    }

    private fun updateMediaState(phase: ReaderPhase) {
        val state = when (phase) {
            ReaderPhase.CONNECTING, ReaderPhase.REWRITING, ReaderPhase.NEXT -> PlaybackState.STATE_BUFFERING
            ReaderPhase.SPEAKING -> PlaybackState.STATE_PLAYING
            ReaderPhase.PAUSED -> PlaybackState.STATE_PAUSED
            ReaderPhase.ERROR -> PlaybackState.STATE_ERROR
            else -> PlaybackState.STATE_STOPPED
        }
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (phase.isActivePlayback()) 1f else 0f)
                .build(),
        )
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, ReaderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(getString(R.string.reader_title))
            .setContentText(getString(R.string.reader_notif_body))
            .setContentIntent(open)
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0))
            .addAction(Notification.Action.Builder(null, getString(R.string.reader_notif_stop), stop).build())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .build()
    }

    override fun onDestroy() {
        destroyed = true
        scope.cancel()
        if (controller.state.value.phase.isActivePlayback()) controller.pause()
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    private fun ReaderPhase.isActivePlayback(): Boolean = this in setOf(
        ReaderPhase.CONNECTING,
        ReaderPhase.REWRITING,
        ReaderPhase.SPEAKING,
        ReaderPhase.NEXT,
    )

    companion object {
        const val ACTION_PLAY = "com.voxora.app.reader.PLAY"
        const val ACTION_STOP = "com.voxora.app.reader.STOP"
        const val EXTRA_MODE = "reader_mode"
        private const val CHANNEL_ID = "voxora_reader"
        private const val NOTIFICATION_ID = 43
        private const val TAG = "ReaderService"
        private val commandMutex = Mutex()
    }
}
