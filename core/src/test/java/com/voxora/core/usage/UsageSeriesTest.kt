package com.voxora.core.usage

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chart series.
 *
 * The chart is the one place the dashboard draws a shape, so the rule it must not break is that the
 * shape is made only of days Voxora actually counted. A day with no recorded request is a real zero
 * for an observed count — never an interpolated or invented value — and a window with nothing
 * behind it produces no points at all rather than an empty axis.
 */
class UsageSeriesTest {

    private val now = Instant.parse("2026-09-18T12:00:00Z").toEpochMilli()

    @Test
    fun theSeriesRunsOldestFirstAndEndsOnTheCurrentDay() {
        val points = UsageSeries.daily(GeminiUsageLedger(), now, 7)

        assertEquals(7, points.size)
        assertEquals("2026-09-12", points.first().day)
        assertEquals("2026-09-18", points.last().day)
    }

    @Test
    fun aDayWithNoRecordedRequestIsAZeroRatherThanAMissingPoint() {
        val ledger = GeminiUsageLedger()
        ledger.recordSuccess(now, null)

        val points = UsageSeries.daily(ledger, now, 3)

        assertEquals(listOf(0, 0, 1), points.map { it.requests })
    }

    @Test
    fun tokensAreCarriedPerDayAndNeverInvented() {
        val ledger = GeminiUsageLedger()
        ledger.recordSuccess(now, GeminiUsageMetadata(promptTokens = 1, responseTokens = 2, totalTokens = 3))

        val points = UsageSeries.daily(ledger, now, 2)

        assertEquals(0L, points.first().totalTokens)
        assertEquals(3L, points.last().totalTokens)
    }

    @Test
    fun daysOutsideTheWindowAreNotIncluded() {
        val ledger = GeminiUsageLedger()
        ledger.recordSuccess(Instant.parse("2026-09-01T10:00:00Z").toEpochMilli())

        assertTrue(UsageSeries.daily(ledger, now, 7).all { it.requests == 0 })
    }

    @Test
    fun aWindowWithNothingBehindItProducesNoPoints() {
        assertTrue(UsageSeries.daily(GeminiUsageLedger(), now, 0).isEmpty())
        assertTrue(UsageSeries.daily(GeminiUsageLedger(), now, -1).isEmpty())
    }

    @Test
    fun aMissingLedgerIsAllZerosRatherThanACrash() {
        val points = UsageSeries.daily(null, now, 2)
        assertEquals(2, points.size)
        assertTrue(points.all { it.requests == 0 && it.totalTokens == 0L })
    }

    @Test
    fun aPointDateIsReadableAndAMalformedLabelIsRefused() {
        assertEquals("2026-09-18", UsageSeries.dateOf(UsagePoint("2026-09-18", 0, 0L)).toString())
        assertNull(UsageSeries.dateOf(UsagePoint("not-a-date", 0, 0L)))
    }
}
