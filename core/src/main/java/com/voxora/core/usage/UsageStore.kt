package com.voxora.core.usage

/**
 * Where the observed-usage ledger is kept between sessions.
 *
 * An interface rather than a direct DataStore dependency so the recorder's behaviour —
 * accumulate, debounce, persist, survive a failed write — can be tested on a plain JVM with an
 * in-memory implementation, and so the storage backend can change without touching the logic.
 */
interface UsageStore {
    suspend fun load(): GeminiUsageLedger
    suspend fun save(ledger: GeminiUsageLedger)
}

/**
 * A store that keeps the ledger only in memory.
 *
 * Used by tests, and as the honest fallback when no persistent store is available: usage history
 * is a convenience, so losing it is strictly better than failing to record anything or crashing.
 */
class InMemoryUsageStore(initial: GeminiUsageLedger = GeminiUsageLedger()) : UsageStore {
    var current: GeminiUsageLedger = initial
        private set

    /** Number of [save] calls, so debouncing can be asserted. */
    var saves: Int = 0
        private set

    override suspend fun load(): GeminiUsageLedger = current

    override suspend fun save(ledger: GeminiUsageLedger) {
        current = ledger
        saves++
    }
}
