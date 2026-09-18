package com.voxora.app.dub

import android.content.Context
import android.media.projection.MediaProjection
import com.voxora.app.util.VoxoraLog

/**
 * DISABLED. Kept only to document an approach that was tried and rejected.
 *
 * Buffering low-resolution captured frames and showing them in a fullscreen overlay so the
 * picture would "wait" for the translated audio caused recursive frame-in-frame capture and
 * froze the UI. It is not a synchronization mechanism: it cannot delay another app's video, and
 * its fixed lag was never a measurement of anything. Do not reinstate it.
 *
 * Live Dub synchronizes by *measuring* the pipeline's own latency and correcting drift through
 * the source's media session — see `dub/sync/`. Nothing in production constructs this class.
 */
class DelayedScreenOverlay(
    @Suppress("UNUSED_PARAMETER") context: Context,
) {
    fun start(@Suppress("UNUSED_PARAMETER") projection: MediaProjection) {
        VoxoraLog.w("Lipsync", "DISABLED — recursive overlay removed in v0.6.2")
    }

    fun stop() = Unit
}
