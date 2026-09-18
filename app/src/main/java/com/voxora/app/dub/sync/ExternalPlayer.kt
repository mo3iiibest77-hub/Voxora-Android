package com.voxora.app.dub.sync

/**
 * The external media player the Live Dub synchronization layer is allowed to steer.
 *
 * This is the seam that keeps the synchronizer testable without Android: the adaptive
 * controller only ever talks to this interface, and the Android implementation
 * ([MediaSessionExternalPlayer]) is the only file that knows about `MediaSessionManager`.
 *
 * Implementations must be *safe by construction*: none of these calls may throw, and a call on
 * an unavailable player must be a no-op rather than a crash. Live Dub must never take the
 * system UI down because a media session went away.
 */
interface ExternalPlayer {
    /**
     * True when the source can actually be paused and resumed right now.
     *
     * False when the user has not granted media-control access, when no other app is playing,
     * or when the playing app exposes no transport controls. The synchronizer treats false as
     * "audio-only": the dub still plays, no correction is attempted, and nothing is paused.
     */
    fun isControllable(): Boolean

    /**
     * Best-effort play state of the source.
     *
     * Used to avoid fighting the user: if the controller did not pause the source and it is
     * already paused, that was the user's choice and must not be overridden.
     */
    fun isPlaying(): Boolean

    /** Pause the source. No-op when not controllable. */
    fun pause()

    /** Resume the source. No-op when not controllable. */
    fun play()

    /** Drop any held controller. Never stops the external app. */
    fun release()
}

/**
 * The player used when the feature is unavailable — a device without media-control access, or
 * a build where the user has not opted in.
 *
 * It is an explicit object rather than `null` so the controller's fallback path is exercised by
 * the same code in tests and in production, and so "there is no player" can never be confused
 * with "the player has not been resolved yet".
 */
object NoExternalPlayer : ExternalPlayer {
    override fun isControllable(): Boolean = false
    override fun isPlaying(): Boolean = false
    override fun pause() = Unit
    override fun play() = Unit
    override fun release() = Unit
}
