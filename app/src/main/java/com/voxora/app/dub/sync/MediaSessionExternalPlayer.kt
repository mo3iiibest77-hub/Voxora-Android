package com.voxora.app.dub.sync

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import com.voxora.app.util.VoxoraLog

/**
 * The Android implementation of [ExternalPlayer], built on `MediaSessionManager`.
 *
 * ## Why this needs a permission, and why that is not hidden
 *
 * `MediaSessionManager.getActiveSessions()` is the only supported way to discover and steer
 * *another* app's playback, and Android only grants it to a `NotificationListenerService` (or to
 * a system app holding `MEDIA_CONTENT_CONTROL`). There is no narrower capability. So Voxora
 * declares [VoxoraNotificationListenerService] — an empty listener — and the Live Dub screen
 * explains the grant and offers a button to the system settings page. Nothing is requested
 * silently, and the feature is simply unavailable until the user chooses it: every method here
 * degrades to [NoExternalPlayer] behaviour rather than failing.
 *
 * ## Limits, stated honestly
 *
 * - A session may exist but refuse transport control. `pause()` is then a no-op, which the
 *   synchronizer tolerates (it never assumes the pause succeeded — it re-reads the state).
 * - The video *frames* cannot be delayed or frozen; only the player's own transport can be
 *   steered. Correcting drift is therefore all this can do for video. See `AGENTS.md`.
 * - Resolution is throttled: `getActiveSessions` is a binder call and Live Dub ticks four times
 *   a second, so it must not be called on every tick.
 */
class MediaSessionExternalPlayer(context: Context) : ExternalPlayer {
    private val appContext = context.applicationContext
    private val listenerComponent =
        ComponentName(appContext, VoxoraNotificationListenerService::class.java)

    private var controller: MediaController? = null
    private var lastResolveNanos = Long.MIN_VALUE

    override fun isControllable(): Boolean {
        resolveIfStale()
        return controller != null
    }

    override fun isPlaying(): Boolean {
        resolveIfStale()
        return controller?.playbackState?.state == PlaybackState.STATE_PLAYING
    }

    override fun pause() = transport { it.pause() }

    override fun play() = transport { it.play() }

    override fun release() {
        controller = null
        lastResolveNanos = Long.MIN_VALUE
    }

    private fun resolveIfStale() {
        val now = SystemClock.elapsedRealtimeNanos()
        if (controller != null && now - lastResolveNanos < RESOLVE_INTERVAL_NANOS) return
        lastResolveNanos = now

        if (!hasMediaControlAccess()) {
            controller = null
            return
        }
        val manager = appContext.getSystemService(MediaSessionManager::class.java)
        if (manager == null) {
            controller = null
            return
        }
        val sessions = try {
            manager.getActiveSessions(listenerComponent)
        } catch (e: SecurityException) {
            VoxoraLog.w(TAG, "media sessions denied: ${e.message}")
            controller = null
            return
        } catch (e: Exception) {
            VoxoraLog.w(TAG, "media sessions failed: ${e.message}")
            controller = null
            return
        }
        val own = appContext.packageName
        val other = sessions.filter { it.packageName != own }
        // Prefer something that is actually playing; fall back to any other session so that a
        // source *we* paused is still found (and can be resumed) on the next resolution.
        controller = other.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: other.firstOrNull()
    }

    private fun hasMediaControlAccess(): Boolean = MediaControlAccess.isGranted(appContext)

    private inline fun transport(block: (MediaController.TransportControls) -> Unit) {
        val active = controller ?: return
        try {
            block(active.transportControls)
        } catch (e: Exception) {
            VoxoraLog.w(TAG, "transport failed: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "VoxoraSync"
        const val RESOLVE_INTERVAL_NANOS = 3_000_000_000L
    }
}
