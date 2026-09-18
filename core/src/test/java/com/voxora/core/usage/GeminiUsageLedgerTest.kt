package com.voxora.core.usage

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the observed-usage ledger.
 *
 * These are Voxora's own counts of requests it made, so the tests pin three things that keep the
 * dashboard honest: token totals only ever come from numbers the server reported, retention is
 * bounded, and a damaged payload degrades to an empty ledger instead of throwing.
 */
class GeminiUsageLedgerTest {

    private val day18 = Instant.parse("2026-09-18T10:00:00Z").toEpochMilli()
    private val day17 = Instant.parse("2026-09-17T10:00:00Z").toEpochMilli()
    private val nextMonth = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()

    private fun usage(prompt: Int? = null, response: Int? = null, total: Int? = null) =
        GeminiUsageMetadata(promptTokens = prompt, responseTokens = response, totalTokens = total)

    @Test
    fun aSuccessfulRequestIsCountedWithTheTokensItReported() {
        val ledger = GeminiUsageLedger()

        ledger.recordSuccess(day18, usage(prompt = 10, response = 25, total = 35))

        val day = ledger.day(day18)
        assertEquals(1, day?.requests)
        assertEquals(1, day?.successes)
        assertEquals(0, day?.failures)
        assertEquals(10L, day?.promptTokens)
        assertEquals(25L, day?.responseTokens)
        assertEquals(35L, day?.totalTokens)
        assertEquals(1, day?.tokensReported)
    }

    @Test
    fun aRequestThatReportedNoUsageStillCountsAsARequest() {
        val ledger = GeminiUsageLedger()

        ledger.recordSuccess(day18, null)

        val day = ledger.day(day18)
        assertEquals(1, day?.requests)
        assertEquals("no tokens were reported, so none are invented", 0L, day?.totalTokens)
        assertEquals(0, day?.tokensReported)
    }

    @Test
    fun aPartialUsageObjectOnlyAddsTheFieldsItCarried() {
        val ledger = GeminiUsageLedger()

        ledger.recordSuccess(day18, usage(total = 35))

        val day = ledger.day(day18)
        assertEquals(0L, day?.promptTokens)
        assertEquals(35L, day?.totalTokens)
        assertEquals(1, day?.tokensReported)
    }

    @Test
    fun aFailureIsCountedAndItsCategoryRemembered() {
        val ledger = GeminiUsageLedger()

        ledger.recordFailure(day18, UsageFailureCategory.NETWORK)

        val day = ledger.day(day18)
        assertEquals(1, day?.requests)
        assertEquals(0, day?.successes)
        assertEquals(1, day?.failures)
        assertEquals(UsageFailureCategory.NETWORK, ledger.lastError)
        assertEquals(day18, ledger.lastFailure)
    }

    @Test
    fun activityIsGroupedByUtcDay() {
        val ledger = GeminiUsageLedger()

        ledger.recordSuccess(day18)
        ledger.recordSuccess(day18)
        ledger.recordSuccess(day17)

        assertEquals(2, ledger.day(day18)?.requests)
        assertEquals(1, ledger.day(day17)?.requests)
        assertEquals(2, ledger.recentDays().size)
        assertEquals("2026-09-18", ledger.recentDays().first().day)
    }

    @Test
    fun theMonthViewSumsOnlyThatMonth() {
        val ledger = GeminiUsageLedger()
        ledger.recordSuccess(day18, usage(total = 10))
        ledger.recordSuccess(day17, usage(total = 5))
        ledger.recordSuccess(nextMonth, usage(total = 999))

        val month = ledger.month(day18)

        assertEquals("2026-09", month.month)
        assertEquals(2, month.requests)
        assertEquals(15L, month.totalTokens)
        assertEquals(2, month.tokensReported)
    }

    @Test
    fun theLastSuccessAndLastFailureAreTrackedIndependently() {
        val ledger = GeminiUsageLedger()

        ledger.recordSuccess(day17, usage(total = 1))
        ledger.recordFailure(day18, UsageFailureCategory.TIMEOUT)

        assertEquals(day17, ledger.lastSuccess)
        assertEquals(day18, ledger.lastFailure)
        assertEquals(UsageFailureCategory.TIMEOUT, ledger.lastError)
    }

    @Test
    fun retentionIsBoundedSoTheLedgerCannotGrowForever() {
        val ledger = GeminiUsageLedger()
        val base = Instant.parse("2026-01-01T00:00:00Z")

        repeat(GeminiUsageLedger.MAX_DAYS + 30) { offset ->
            ledger.recordSuccess(base.plus(offset.toLong(), ChronoUnit.DAYS).toEpochMilli())
        }

        assertEquals(GeminiUsageLedger.MAX_DAYS, ledger.recentDays().size)
        // 2026-01-01 plus (MAX_DAYS + 30 - 1) days.
        assertEquals("the newest day is kept", "2026-04-30", ledger.recentDays().first().day)
        assertEquals("the oldest days are dropped", "2026-01-31", ledger.recentDays().last().day)
    }

    @Test
    fun theLedgerSurvivesAJsonRoundTrip() {
        val ledger = GeminiUsageLedger()
        ledger.recordSuccess(day18, usage(prompt = 3, response = 4, total = 7))
        ledger.recordFailure(day17, UsageFailureCategory.QUOTA)

        val restored = GeminiUsageLedger.fromJson(ledger.toJson())

        assertEquals(ledger.day(day18), restored.day(day18))
        assertEquals(ledger.day(day17), restored.day(day17))
        assertEquals(ledger.lastSuccess, restored.lastSuccess)
        assertEquals(ledger.lastFailure, restored.lastFailure)
        assertEquals(ledger.lastError, restored.lastError)
        assertEquals(ledger.totalRequests(), restored.totalRequests())
    }

    @Test
    fun nothingStoredMeansAnEmptyLedgerNotAnError() {
        assertEquals(0, GeminiUsageLedger.fromJson(null).totalRequests())
        assertEquals(0, GeminiUsageLedger.fromJson("").totalRequests())
        assertEquals(0, GeminiUsageLedger.fromJson("   ").totalRequests())
    }

    @Test
    fun aDamagedPayloadDegradesToEmptyRatherThanThrowing() {
        assertEquals(0, GeminiUsageLedger.fromJson("{not json").totalRequests())
        assertEquals(0, GeminiUsageLedger.fromJson("[]").totalRequests())
        assertEquals(0, GeminiUsageLedger.fromJson("{}").totalRequests())
    }

    @Test
    fun aFutureFormatIsDiscardedRatherThanMisread() {
        val future = """{"version":99,"days":[{"day":"2026-09-18","requests":5}]}"""
        assertEquals(0, GeminiUsageLedger.fromJson(future).totalRequests())
    }

    @Test
    fun oneBadRowDoesNotDiscardTheGoodOnes() {
        val payload = """
            {"version":1,"days":[
              {"day":"2026-09-18","requests":2,"successes":2},
              {"day":"not-a-date","requests":9},
              {"day":"2026-09-17","requests":1,"failures":1}
            ]}
        """.trimIndent()

        val ledger = GeminiUsageLedger.fromJson(payload)

        assertEquals(2, ledger.day(day18)?.requests)
        assertEquals(1, ledger.day(day17)?.requests)
        assertEquals(3, ledger.totalRequests())
    }

    @Test
    fun negativeStoredCountsAreClampedRatherThanTrusted() {
        val payload = """{"version":1,"days":[{"day":"2026-09-18","requests":-5,"totalTokens":-100}]}"""
        val ledger = GeminiUsageLedger.fromJson(payload)
        assertEquals(0, ledger.day(day18)?.requests)
        assertEquals(0L, ledger.day(day18)?.totalTokens)
    }

    @Test
    fun clearEmptiesEverything() {
        val ledger = GeminiUsageLedger()
        ledger.recordSuccess(day18, usage(total = 5))
        ledger.recordFailure(day18, UsageFailureCategory.AUDIO)

        ledger.clear()

        assertEquals(0, ledger.totalRequests())
        assertNull(ledger.day(day18))
        assertNull(ledger.lastSuccess)
        assertNull(ledger.lastFailure)
        assertNull(ledger.lastError)
    }

    @Test
    fun aSnapshotIsIndependentOfLaterRecords() {
        val ledger = GeminiUsageLedger()
        ledger.recordSuccess(day18)

        val snapshot = ledger.snapshot()
        ledger.recordSuccess(day18)

        assertEquals(1, snapshot.day(day18)?.requests)
        assertEquals(2, ledger.day(day18)?.requests)
    }

    @Test
    fun theStoredPayloadCarriesNoSecrets() {
        val ledger = GeminiUsageLedger()
        ledger.recordSuccess(day18, usage(total = 5))

        val json = org.json.JSONObject(ledger.toJson())
        val topLevel = json.keys().asSequence().toSet()

        assertEquals(setOf("version", "lastSuccessAt", "lastFailureAt", "lastErrorCategory", "days"), topLevel)
        val row = json.getJSONArray("days").getJSONObject(0).keys().asSequence().toSet()
        assertEquals(
            setOf("day", "requests", "successes", "failures", "promptTokens", "responseTokens", "totalTokens", "tokensReported"),
            row,
        )
    }
}
