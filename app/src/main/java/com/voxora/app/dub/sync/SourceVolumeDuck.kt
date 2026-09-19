package com.voxora.app.dub.sync

/**
 * The arithmetic behind ducking the source while the dub plays.
 *
 * It lives here, as a pure function, for one reason: "restore the source exactly to the level it
 * had" is a correctness property that must not depend on a device. `DubPlayback` still performs
 * the `AudioManager` calls — this only decides the numbers, so the decision is unit-tested.
 *
 * The source is never muted by this rule. If there is no headroom to duck into (the stream is
 * already at 0 or 1), the answer is `null`, which means "leave it alone" — a silent source would
 * be a worse product than an un-ducked one, and Live Dub must not be able to mute the user's
 * audio by accident.
 */
object SourceVolumeDuck {
    /** The source is reduced to roughly this share of its current level while the dub plays. */
    const val TARGET_PERCENT = 28

    /**
     * The level to duck [current] down to, or `null` when it must be left unchanged.
     *
     * @param current the stream's current index.
     * @param max the stream's maximum index.
     */
    fun duckTarget(current: Int, max: Int): Int? {
        if (current <= 1 || max <= 0 || current > max) return null
        val raw = current * TARGET_PERCENT / 100
        val target = raw.coerceIn(1, current - 1)
        return if (target < current) target else null
    }

    /**
     * The level to restore, or `null` when there is nothing saved.
     *
     * The caller stores exactly what it read before ducking and restores exactly that, so the
     * user's own volume setting survives a Live Dub session unchanged.
     */
    fun restoreTarget(saved: Int): Int? = if (saved < 0) null else saved
}
