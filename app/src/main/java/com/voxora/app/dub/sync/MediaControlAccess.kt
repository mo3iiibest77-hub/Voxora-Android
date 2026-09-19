package com.voxora.app.dub.sync

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * The one place that answers "may Voxora steer another app's playback?".
 *
 * Android grants `MediaSessionManager.getActiveSessions()` only to a notification listener, so
 * the answer is "the user has enabled Voxora in the notification-access list". Both the
 * [MediaSessionExternalPlayer] and the Live Dub screen ask this object, so the card the user
 * sees and the capability the synchronizer actually has can never disagree.
 *
 * Nothing here requests anything: the screen offers the system settings page and the user
 * decides. Without the grant Live Dub still works, as audio-only.
 */
object MediaControlAccess {
    /** True when Voxora is currently enabled as a notification listener. */
    fun isGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context.applicationContext)
            .contains(context.applicationContext.packageName)

    /**
     * The system screen where notification access is granted. Deliberately the platform settings
     * page rather than an in-app dialog: this is a sensitive, system-level grant and the user
     * should see it in the system's own words.
     */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
