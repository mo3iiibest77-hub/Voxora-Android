package com.voxora.app.dub.sync

import android.service.notification.NotificationListenerService

/**
 * An intentionally empty notification listener.
 *
 * Android grants `MediaSessionManager.getActiveSessions()` only to a notification listener or a
 * system app, so this class exists solely to make Voxora eligible for that read once the user
 * grants notification access in system settings. It never reads, stores, forwards or logs a
 * notification — it overrides nothing and holds no state.
 *
 * It is declared in `AndroidManifest.xml` with
 * `android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"`, which means only
 * the system can bind it. The Live Dub screen explains the grant and links to the settings page;
 * it is never requested silently, and Live Dub works without it (as audio-only).
 */
class VoxoraNotificationListenerService : NotificationListenerService()
