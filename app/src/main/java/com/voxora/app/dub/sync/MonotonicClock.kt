package com.voxora.app.dub.sync

/**
 * The only clock the Live Dub synchronization layer is allowed to read.
 *
 * Synchronization math must never use wall-clock time: `System.currentTimeMillis()` jumps
 * when the user or the network changes the device clock, which would show up as a huge
 * phantom drift and trigger a correction for no reason. The production implementation is
 * `SystemClock.elapsedRealtimeNanos()`, which keeps counting while the device sleeps and
 * cannot be moved. Tests inject a plain counter.
 *
 * It is a `fun interface` so the production site can be a method reference and a test can be
 * a lambda — no mocking framework and no Android dependency anywhere in this package.
 */
fun interface MonotonicClock {
    fun nowNanos(): Long
}
