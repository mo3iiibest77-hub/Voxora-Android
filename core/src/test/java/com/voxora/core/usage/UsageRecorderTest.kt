package com.voxora.core.usage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for recording observed requests.
 *
 * The load-bearing rules are that usage reported mid-turn is only committed by a *successful*
 * turn, that batching keeps a long narration from writing to disk once per unit, and that a broken
 * store never interrupts narration.
 */
class UsageRecorderTest {

    private val now = 1_760_000_000_000L

    private fun usage(total: Int) = GeminiUsageMetadata(promptTokens = 1, totalTokens = total)

    @Test
    fun usageReportedDuringATurnIsCommittedWhenTheTurnSucceeds() = runBlocking {
        val recorder = UsageRecorder(InMemoryUsageStore(), clock = { now })

        recorder.noteReportedUsage(usage(40))
        recorder.recordSuccess()

        val day = recorder.ledger.value.day(now)
        assertEquals(1, day?.successes)
        assertEquals(40L, day?.totalTokens)
        assertEquals(1, day?.tokensReported)
    }

    @Test
    fun usageFromAFailedTurnIsDiscardedRatherThanCounted() = runBlocking {
        val recorder = UsageRecorder(InMemoryUsageStore(), clock = { now })

        recorder.noteReportedUsage(usage(40))
        recorder.recordFailure(UsageFailureCategory.NETWORK)

        val day = recorder.ledger.value.day(now)
        assertEquals(1, day?.failures)
        assertEquals("a turn that produced no audio did not produce usable output", 0L, day?.totalTokens)
        assertEquals(0, day?.tokensReported)
    }

    @Test
    fun usageIsNotCarriedOverFromOneRequestToTheNext() = runBlocking {
        val recorder = UsageRecorder(InMemoryUsageStore(), clock = { now })

        recorder.noteReportedUsage(usage(40))
        recorder.recordSuccess()
        // Second request reports nothing at all.
        recorder.recordSuccess()

        assertEquals(40L, recorder.ledger.value.day(now)?.totalTokens)
        assertEquals(2, recorder.ledger.value.day(now)?.requests)
        assertEquals(1, recorder.ledger.value.day(now)?.tokensReported)
    }

    @Test
    fun aFailureCategoryIsRecordedButNoMessageIs() = runBlocking {
        val recorder = UsageRecorder(InMemoryUsageStore(), clock = { now })

        recorder.recordFailure(UsageFailureCategory.QUOTA)

        assertEquals(UsageFailureCategory.QUOTA, recorder.ledger.value.lastError)
    }

    @Test
    fun theLedgerIsLoadedFromTheStoreOnFirstUse() = runBlocking {
        val stored = GeminiUsageLedger().apply { recordSuccess(now, usage(7)) }
        val recorder = UsageRecorder(InMemoryUsageStore(stored), clock = { now })

        recorder.ensureLoaded()

        assertEquals(1, recorder.ledger.value.totalRequests())
        assertEquals(7L, recorder.ledger.value.day(now)?.totalTokens)
    }

    @Test
    fun recordingWithoutAnExplicitLoadStillPicksUpStoredHistory() = runBlocking {
        val stored = GeminiUsageLedger().apply { recordSuccess(now, usage(7)) }
        val recorder = UsageRecorder(InMemoryUsageStore(stored), clock = { now })

        recorder.recordSuccess()

        assertEquals("stored history must not be dropped", 2, recorder.ledger.value.totalRequests())
    }

    @Test
    fun writesAreBatchedSoALongNarrationDoesNotWriteOncePerUnit() = runBlocking {
        val store = InMemoryUsageStore()
        val recorder = UsageRecorder(store, clock = { now })

        repeat(UsageRecorder.PERSIST_EVERY - 1) { recorder.recordSuccess() }
        assertEquals("no write until the batch is full", 0, store.saves)

        recorder.recordSuccess()
        assertEquals(1, store.saves)

        repeat(UsageRecorder.PERSIST_EVERY) { recorder.recordSuccess() }
        assertEquals(2, store.saves)
        // The in-memory view is exact even between writes.
        assertEquals(UsageRecorder.PERSIST_EVERY * 2, recorder.ledger.value.totalRequests())
    }

    @Test
    fun flushForcesAWrite() = runBlocking {
        val store = InMemoryUsageStore()
        val recorder = UsageRecorder(store, clock = { now })

        recorder.recordSuccess()
        assertEquals(0, store.saves)

        recorder.flush()

        assertEquals(1, store.saves)
        assertEquals(1, store.current.totalRequests())
    }

    @Test
    fun aStoreThatFailsDoesNotInterruptTheCaller() = runBlocking {
        val failures = mutableListOf<String>()
        val recorder = UsageRecorder(
            store = object : UsageStore {
                override suspend fun load(): GeminiUsageLedger = throw IllegalStateException("load failed")
                override suspend fun save(ledger: GeminiUsageLedger) = throw IllegalStateException("save failed")
            },
            clock = { now },
            onStoreFailure = { failures.add(it) },
        )

        recorder.recordSuccess()
        recorder.flush()

        assertEquals("the in-memory record still happened", 1, recorder.ledger.value.totalRequests())
        assertTrue("the store failure is reported, not swallowed", failures.isNotEmpty())
    }

    @Test
    fun theFlowEmitsANewSnapshotOnEveryRecord() = runBlocking {
        val recorder = UsageRecorder(InMemoryUsageStore(), clock = { now })

        val first = recorder.ledger.value
        recorder.recordSuccess()

        assertNotNull(recorder.ledger.value)
        assertTrue("collectors must see a new value", recorder.ledger.value !== first)
        assertEquals(1, recorder.ledger.value.totalRequests())
        assertEquals("the earlier snapshot is unchanged", 0, first.totalRequests())
    }

    @Test
    fun clearEmptiesBothMemoryAndDisk() = runBlocking {
        val store = InMemoryUsageStore()
        val recorder = UsageRecorder(store, clock = { now })
        recorder.recordSuccess()

        recorder.clear()

        assertEquals(0, recorder.ledger.value.totalRequests())
        assertEquals(0, store.current.totalRequests())
    }

    @Test
    fun twoRecordersDoNotShareState() = runBlocking {
        val a = UsageRecorder(InMemoryUsageStore(), clock = { now })
        val b = UsageRecorder(InMemoryUsageStore(), clock = { now })

        a.recordSuccess()
        a.recordSuccess()

        assertEquals(2, a.ledger.value.totalRequests())
        assertEquals(0, b.ledger.value.totalRequests())
    }
}
