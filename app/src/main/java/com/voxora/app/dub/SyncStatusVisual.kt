package com.voxora.app.dub

import com.voxora.app.dub.sync.SyncState

/**
 * What the Live Dub synchronization indicator should communicate.
 *
 * The sync state says what the *engine* is doing; this says what the *user* needs to see, and it
 * is the only place that decides it — no composable picks a colour inline. Same shape as
 * `ReaderStatusVisual` and `UsageStatusVisual`.
 *
 * - [ACTIVE] — the source and the dub are at a stable offset. The only state that earns green.
 * - [WARNING] — measuring, or briefly holding the source to catch up. Transient, in progress.
 * - [NEUTRAL] — nothing to claim: not running, running without control, or synchronization
 *   withdrawn. **An unavailable sync is not a failure** — it is an honest limitation of the
 *   device or of the source app, so it must never be dressed as an error.
 *
 * Pure JVM (no `android.*`) so the mapping is unit-testable.
 */
internal enum class SyncTone { ACTIVE, WARNING, NEUTRAL }

internal object SyncStatusVisual {

    fun tone(state: SyncState): SyncTone = when (state) {
        SyncState.SYNCED -> SyncTone.ACTIVE
        SyncState.WARMING_UP, SyncState.CORRECTING -> SyncTone.WARNING
        SyncState.IDLE, SyncState.AUDIO_ONLY, SyncState.UNAVAILABLE -> SyncTone.NEUTRAL
    }

    /**
     * Whether the honest "external video cannot be delayed" note belongs on screen.
     *
     * Only once the source and the dub are actually being kept together: showing it before then
     * would explain a behaviour the user has not seen yet.
     */
    fun showsVideoNote(state: SyncState): Boolean = state == SyncState.SYNCED

    /**
     * Whether the media-control opt-in belongs on screen.
     *
     * Only while a session is live and the grant is missing — offering it when Live Dub is idle
     * would ask for a sensitive permission before the user has asked for the feature.
     */
    fun showsPermissionPrompt(live: Boolean, granted: Boolean): Boolean = live && !granted
}
