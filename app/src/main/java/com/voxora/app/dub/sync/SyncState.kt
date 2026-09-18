package com.voxora.app.dub.sync

/**
 * What the synchronization layer is doing, in the words the Live Dub screen needs.
 *
 * This is deliberately a small, closed set: the UI shows one line, not a diagnostics panel.
 * The technical detail (measured latency, drift, drops) belongs in the Logs screen.
 */
enum class SyncState {
    /** No session running. */
    IDLE,

    /**
     * Measuring the pipeline's own latency. The offset between the source content clock and
     * the dubbed content clock is still settling, so no correction may be attempted yet.
     */
    WARMING_UP,

    /** The source and the dub are running at a stable relative offset. */
    SYNCED,

    /** The source has been paused so the dub can consume its backlog and catch up. */
    CORRECTING,

    /**
     * The dub is playing but the source cannot be controlled, so drift cannot be corrected.
     * This is the honest normal state on a device where the user has not granted the
     * media-control permission, or where the playing app exposes no controllable session.
     */
    AUDIO_ONLY,

    /**
     * Synchronization was attempted and had to be abandoned: the dub stalled, or a controllable
     * source disappeared. The dub keeps playing; only the sync claim is withdrawn.
     */
    UNAVAILABLE,
}

/**
 * The action the controller took on a tick.
 *
 * The controller performs the pause/resume on the [ExternalPlayer] itself, so this is an
 * observation, not an instruction — the service uses it to log a state transition once and to
 * keep the notification honest. Returning an action rather than a boolean keeps "we did
 * nothing" distinguishable from "we resumed", which is what the tests assert on.
 */
enum class SyncDecision {
    NONE,
    PAUSE_SOURCE,
    RESUME_SOURCE,
    UNAVAILABLE,
}
