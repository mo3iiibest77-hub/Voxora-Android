package com.voxora.app.dub

import android.content.Context
import android.media.projection.MediaProjection
import com.voxora.app.util.VoxoraLog

/**
 * DISABLED in v0.6.2.
 * VirtualDisplay + fullscreen overlay caused recursive frame-in-frame and froze UI.
 * Stub only — do not call start() from production paths.
 */
class DelayedScreenOverlay(
    @Suppress("UNUSED_PARAMETER") context: Context,
    @Suppress("UNUSED_PARAMETER") lagMs: Long = DEFAULT_LAG_MS,
) {
    fun start(@Suppress("UNUSED_PARAMETER") projection: MediaProjection) {
        VoxoraLog.w("Lipsync", "DISABLED — recursive overlay removed in v0.6.2")
    }

    fun stop() {}

    companion object {
        const val DEFAULT_LAG_MS = 2200L
    }
}
