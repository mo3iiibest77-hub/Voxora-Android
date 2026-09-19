package com.voxora.core.usage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Records what Voxora observed, and keeps the ledger on disk.
 *
 * ## One request, one record
 * A request reports its token usage *during* the turn but only succeeds or fails at the end, so
 * usage is held as [noteReportedUsage] until [recordSuccess] commits it. A failed request
 * discards any usage it reported: a turn that produced no audio did not produce usable output,
 * and counting its tokens as if it had would overstate usage.
 *
 * ## Write cost
 * Persisting on every narrated unit would be one small disk write per ~80 words. [PERSIST_EVERY]
 * batches them so a long narration performs a handful of writes instead of hundreds, while the
 * in-memory flow stays exact for the UI. [flush] forces a write when the caller knows the run has
 * ended.
 *
 * ## Failure of the store is not failure of the app
 * A store that throws is logged and ignored. Usage history is a convenience; it must never
 * interrupt narration or block Settings.
 *
 * Pure JVM apart from the [UsageStore] it is handed, so the ordering, batching and discard rules
 * are all unit-testable.
 */
class UsageRecorder(
    private val store: UsageStore = InMemoryUsageStore(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val onStoreFailure: (String) -> Unit = {},
) {
    private val mutex = Mutex()
    private val mutableLedger = MutableStateFlow(GeminiUsageLedger())
    private var loaded = false
    private var writesSincePersist = 0

    @Volatile
    private var pendingUsage: GeminiUsageMetadata? = null

    /** The ledger as the UI should render it. Exact, including unpersisted records. */
    val ledger: StateFlow<GeminiUsageLedger> = mutableLedger.asStateFlow()

    /** Loads the persisted ledger once. Safe to call repeatedly and from several callers. */
    suspend fun ensureLoaded() = mutex.withLock {
        if (loaded) return@withLock
        loaded = true
        try {
            mutableLedger.value = store.load()
        } catch (_: Exception) {
            onStoreFailure("usage-ledger-load")
        }
    }

    /**
     * Holds the usage the server reported for the request currently in flight.
     *
     * Not thread-confined: [GeminiReaderSession] reports from its own IO worker while the caller
     * awaits the turn, so this is a plain volatile hand-off consumed by [recordSuccess].
     */
    fun noteReportedUsage(usage: GeminiUsageMetadata) {
        pendingUsage = usage
    }

    /** Commits one successful request, attaching whatever usage it reported. */
    suspend fun recordSuccess() {
        val usage = pendingUsage
        pendingUsage = null
        mutate { ledger -> ledger.also { it.recordSuccess(clock(), usage) } }
    }

    /** Commits one failed request under [category]. Any usage it reported is discarded. */
    suspend fun recordFailure(category: String?) {
        pendingUsage = null
        mutate { ledger -> ledger.also { it.recordFailure(clock(), category) } }
    }

    /** Persists immediately, regardless of the batching counter. */
    suspend fun flush() = mutex.withLock {
        writesSincePersist = 0
        persistLocked()
    }

    /** Clears the ledger in memory and on disk. */
    suspend fun clear() = mutex.withLock {
        mutableLedger.value = GeminiUsageLedger()
        pendingUsage = null
        writesSincePersist = 0
        persistLocked()
    }

    private suspend fun mutate(update: (GeminiUsageLedger) -> Unit) = mutex.withLock {
        if (!loaded) {
            loaded = true
            try {
                mutableLedger.value = store.load()
            } catch (_: Exception) {
                onStoreFailure("usage-ledger-load")
            }
        }
        update(mutableLedger.value)
        // The ledger mutates in place, so publish an independent snapshot for collectors.
        mutableLedger.value = mutableLedger.value.snapshot()
        writesSincePersist++
        if (writesSincePersist >= PERSIST_EVERY) {
            writesSincePersist = 0
            persistLocked()
        }
    }

    private suspend fun persistLocked() {
        try {
            store.save(mutableLedger.value)
        } catch (_: Exception) {
            onStoreFailure("usage-ledger-save")
        }
    }

    companion object {
        /** Records between disk writes. Keeps a long narration from writing once per unit. */
        const val PERSIST_EVERY = 10
    }
}
