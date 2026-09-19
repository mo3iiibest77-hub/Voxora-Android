package com.voxora.app.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.voxora.app.MainActivity
import com.voxora.app.R
import com.voxora.app.util.VoxoraLog
import com.voxora.core.prefs.UserPrefs
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

@AndroidEntryPoint
class ReaderService : Service() {
    @Inject lateinit var controller: ReaderController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var mediaSession: MediaSession
    private var playbackJob: Job? = null
    @Volatile private var generation = 0L
    private var latestStartId = 0
    private var destroyed = false

    /** The bubble's own preference, and the phase it is gated on. */
    private val prefs by lazy { UserPrefs(this) }
    @Volatile private var bubbleEnabled = true
    @Volatile private var currentPhase: ReaderPhase? = null

    /** Set once `startForeground` has run, so the collector cannot post before it. */
    @Volatile private var foregroundPosted = false

    /** The mode a resume should use; a transport control carries no mode of its own. */
    @Volatile private var lastMode = "faithful"

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
                // Hardware and lock-screen transport controls. Pause keeps the session and the
                // service alive so Resume is available; skip moves one segment and is clamped by
                // the controller, so it can never leave the current chunk.
                override fun onPlay() = resumePlayback(lastMode)
                override fun onPause() = pausePlayback()
                override fun onStop() = stopPlayback()
                override fun onSkipToNext() = stepSegment(1)
                override fun onSkipToPrevious() = stepSegment(-1)
            })
        }
        scope.launch {
            controller.state.collect { state ->
                withContext(Dispatchers.Main.immediate) {
                    if (!destroyed) {
                        currentPhase = state.phase
                        updateMediaState(state.phase)
                        updateNotification(state.phase)
                        syncBubble()
                    }
                }
            }
        }
        // The top-bar toggle writes this preference; the bubble follows it rather than owning a
        // second copy of the decision.
        scope.launch {
            prefs.readerBubble.collect { enabled ->
                bubbleEnabled = enabled
                withContext(Dispatchers.Main.immediate) {
                    if (!destroyed) syncBubble()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        startForeground(
            NOTIFICATION_ID,
            buildNotification(currentPhase),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        foregroundPosted = true
        mediaSession.isActive = true
        when (intent?.action) {
            ACTION_PLAY -> resumePlayback(intent.getStringExtra(EXTRA_MODE) ?: lastMode)
            ACTION_PAUSE -> pausePlayback()
            // Navigation is synchronous, so a service woken by a stale notification can be
            // recognised as such right after the (no-op) step and torn down.
            ACTION_NEXT -> {
                stepSegment(1)
                teardownIfTerminal()
            }
            ACTION_PREV -> {
                stepSegment(-1)
                teardownIfTerminal()
            }
            ACTION_STOP -> stopPlayback()
            // Started without a command: do not leave a foreground service running for nothing.
            else -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun resumePlayback(mode: String) {
        lastMode = mode
        runCommand { controller.play(mode) }
    }

    /**
     * Pauses without tearing the service down.
     *
     * Routed through [runCommand] so it also cancels a running play command. Its cleanup leaves the
     * service, the session and the notification alive because [ReaderPhase.PAUSED] is not terminal —
     * that is what makes Resume possible from the notification.
     */
    private fun pausePlayback() {
        runCommand { controller.pause() }
    }

    private fun stopPlayback() {
        runCommand { controller.stop() }
    }

    /**
     * Moves exactly one segment, bounded by the current chunk.
     *
     * Deliberately **not** routed through [runCommand]: that would cancel the running play command
     * and end background narration. `ReaderController`'s play loop already re-runs its pipeline
     * when the navigation revision changes, so a synchronous jump is all that is needed.
     */
    private fun stepSegment(delta: Int) {
        val state = controller.state.value
        val current = ReaderPager.segmentIndex(state.segment, state.segmentTotal) ?: return
        // jumpToSegment clamps to the chunk's units, so a step at either end is a truthful no-op
        // rather than a move into the neighbouring chunk.
        controller.jumpToSegment(current + delta)
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
                    command()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VoxoraLog.e(TAG, "Reader service command failed", e)
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (!destroyed && run == generation) {
                        playbackJob = null
                        // Only a terminal phase ends the service. A pause leaves it alive so the
                        // notification can offer Resume; stopping it here would remove the very
                        // control the user needs next.
                        if (controller.state.value.phase.isTerminal()) teardown()
                    }
                }
            }
        }
    }

    private fun teardown() {
        if (destroyed) return
        mediaSession.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(latestStartId)
    }

    /** Ends a service that was woken by a transport control with nothing left to control. */
    private fun teardownIfTerminal() {
        if (controller.state.value.phase.isTerminal()) teardown()
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
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (phase.isActivePlayback()) 1f else 0f)
                .build(),
        )
    }

    /**
     * Shows the bubble only while the Reader is actually narrating and the preference allows it.
     *
     * Mirrors `DubService.syncBubble`: the bubble is a function of the phase, so it appears when
     * narration starts and disappears when it stops, and it is never left on screen after the
     * service is gone.
     */
    private fun syncBubble() {
        if (bubbleEnabled && currentPhase?.isActivePlayback() == true) {
            ReaderBubbleService.show(this)
        } else {
            ReaderBubbleService.hide(this)
        }
    }

    /** Repaints the transport controls whenever the phase changes. */
    private fun updateNotification(phase: ReaderPhase) {
        if (!foregroundPosted || destroyed) return
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(phase))
    }

    /**
     * Builds the transport notification.
     *
     * The body states the phase and never the document: a lock screen must not leak what the
     * reader is listening to. The actions are previous / pause-or-resume / next / stop; the first
     * three are the compact view, and stop stays in the expanded view.
     */
    private fun buildNotification(phase: ReaderPhase?): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            flags,
        )
        fun control(requestCode: Int, action: String): PendingIntent =
            PendingIntent.getForegroundService(
                this,
                requestCode,
                Intent(this, ReaderService::class.java).setAction(action),
                flags,
            )
        val playing = phase?.isActivePlayback() == true
        val body = when (phase) {
            ReaderPhase.SPEAKING -> getString(R.string.reader_notif_body_playing)
            ReaderPhase.PAUSED -> getString(R.string.reader_notif_body_paused)
            else -> getString(R.string.reader_notif_body)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(getString(R.string.reader_title))
            .setContentText(body)
            .setContentIntent(open)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .addAction(
                Notification.Action.Builder(null, getString(R.string.reader_notif_prev), control(1, ACTION_PREV)).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(if (playing) R.string.reader_notif_pause else R.string.reader_notif_resume),
                    control(2, ACTION_PAUSE),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(null, getString(R.string.reader_notif_next), control(3, ACTION_NEXT)).build(),
            )
            .addAction(
                Notification.Action.Builder(null, getString(R.string.reader_notif_stop), control(4, ACTION_STOP)).build(),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            // Hide the body on a secure lock screen; it names no document, but keep it private.
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
    }

    override fun onDestroy() {
        destroyed = true
        ReaderBubbleService.hide(this)
        scope.cancel()
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

    /** Phases in which the Reader is finished, so the foreground service has no reason to live. */
    private fun ReaderPhase.isTerminal(): Boolean = this in setOf(
        ReaderPhase.IDLE,
        ReaderPhase.STOPPED,
        ReaderPhase.COMPLETE,
        ReaderPhase.ERROR,
    )

    companion object {
        const val ACTION_PLAY = "com.voxora.app.reader.PLAY"
        const val ACTION_PAUSE = "com.voxora.app.reader.PAUSE"
        const val ACTION_NEXT = "com.voxora.app.reader.NEXT"
        const val ACTION_PREV = "com.voxora.app.reader.PREV"
        const val ACTION_STOP = "com.voxora.app.reader.STOP"
        const val EXTRA_MODE = "reader_mode"
        private const val CHANNEL_ID = "voxora_reader"
        private const val NOTIFICATION_ID = 43
        private const val TAG = "ReaderService"
        private val commandMutex = Mutex()

        /**
         * Stops narration from outside the service.
         *
         * The service is already running as a foreground service while it narrates, so delivering
         * an intent to it is allowed from the bubble, exactly as `DubService.stop` does for Live.
         */
        fun stop(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ReaderService::class.java).setAction(ACTION_STOP),
                )
            } catch (_: Exception) {
            }
        }
    }
}
