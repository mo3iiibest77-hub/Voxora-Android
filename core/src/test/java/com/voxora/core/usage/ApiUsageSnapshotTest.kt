package com.voxora.core.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the dashboard figures.
 *
 * The central rule under test: a number is shown only when it was observed. Everything else must
 * be an explicit "not available" with a reason, because rendering `0` where the truth is "unknown"
 * is a fabricated claim. Project quota and billing must always be unavailable — an API key cannot
 * read them — and the account/key relationship must never be asserted.
 */
class ApiUsageSnapshotTest {

    private val now = java.time.Instant.parse("2026-09-18T12:00:00Z").toEpochMilli()
    private val key = "AIzaSyD1234567890ABCD"

    private fun ledgerWith(requests: Int, usage: GeminiUsageMetadata? = null): GeminiUsageLedger {
        val ledger = GeminiUsageLedger()
        repeat(requests) { ledger.recordSuccess(now, usage) }
        return ledger
    }

    @Test
    fun withNoKeyEverythingIsNotConfiguredRatherThanZero() {
        val snapshot = ApiUsageSnapshot.assemble(
            apiKey = null,
            accountEmail = null,
            probe = null,
            ledger = GeminiUsageLedger(),
            nowMillis = now,
        )

        assertFalse(snapshot.keyConfigured)
        assertEquals("", snapshot.maskedKey)
        assertEquals(UsageUnavailable.NOT_CONFIGURED, snapshot.requestsToday.reasonOrNull)
        assertEquals(UsageUnavailable.NOT_CONFIGURED, snapshot.requestsThisMonth.reasonOrNull)
        assertEquals(UsageUnavailable.NOT_CONFIGURED, snapshot.totalTokens.reasonOrNull)
        assertNull(snapshot.requestsToday.knownValue)
    }

    @Test
    fun aConfiguredKeyWithNoActivityReportsUnknownRatherThanZero() {
        val snapshot = ApiUsageSnapshot.assemble(key, null, null, GeminiUsageLedger(), now)

        assertTrue(snapshot.keyConfigured)
        assertEquals("0 would be a claim we cannot make", UsageUnavailable.UNKNOWN, snapshot.requestsToday.reasonOrNull)
        assertEquals(UsageUnavailable.UNKNOWN, snapshot.requestsThisMonth.reasonOrNull)
    }

    @Test
    fun observedRequestsAreShownExactly() {
        val snapshot = ApiUsageSnapshot.assemble(key, null, null, ledgerWith(3), now)

        assertEquals(3L, snapshot.requestsToday.knownValue)
        assertEquals(3L, snapshot.requestsThisMonth.knownValue)
    }

    @Test
    fun tokensAreNotOfferedWhenTheServerNeverReportedThem() {
        val snapshot = ApiUsageSnapshot.assemble(key, null, null, ledgerWith(4), now)

        assertEquals(4L, snapshot.requestsThisMonth.knownValue)
        assertEquals(UsageUnavailable.NOT_OFFERED, snapshot.totalTokens.reasonOrNull)
        assertEquals(UsageUnavailable.NOT_OFFERED, snapshot.inputTokens.reasonOrNull)
        assertEquals(UsageUnavailable.NOT_OFFERED, snapshot.outputTokens.reasonOrNull)
    }

    @Test
    fun tokensAreShownWhenTheyWereReportedAndCoverageIsStated() {
        val ledger = GeminiUsageLedger()
        ledger.recordSuccess(now, GeminiUsageMetadata(promptTokens = 10, responseTokens = 20, totalTokens = 30))
        ledger.recordSuccess(now, null)

        val snapshot = ApiUsageSnapshot.assemble(key, null, null, ledger, now)

        assertEquals(30L, snapshot.totalTokens.knownValue)
        assertEquals(10L, snapshot.inputTokens.knownValue)
        assertEquals(20L, snapshot.outputTokens.knownValue)
        assertEquals("1 of 2 requests reported tokens", 1L, snapshot.tokenCoverage.knownValue)
        assertEquals(2L, snapshot.requestsThisMonth.knownValue)
    }

    @Test
    fun projectQuotaAndBillingAreAlwaysUnavailableToAnApiKey() {
        val snapshot = ApiUsageSnapshot.assemble(key, null, null, ledgerWith(5), now)

        assertEquals(UsageUnavailable.AUTH_REQUIRED, snapshot.projectQuota.reasonOrNull)
        assertEquals(UsageUnavailable.AUTH_REQUIRED, snapshot.billing.reasonOrNull)
        assertNull("quota must never be invented", snapshot.projectQuota.knownValue)
        assertNull("cost must never be estimated", snapshot.billing.knownValue)
    }

    @Test
    fun signingInDoesNotClaimTheKeyBelongsToThatAccount() {
        val snapshot = ApiUsageSnapshot.assemble(key, "owner@example.com", null, GeminiUsageLedger(), now)

        assertEquals("owner@example.com", snapshot.accountEmail)
        assertFalse("the key/account relationship is unverifiable", snapshot.accountLinkedToKey)
    }

    @Test
    fun connectionIsNullUntilACheckHasRun() {
        assertNull(ApiUsageSnapshot.assemble(key, null, null, GeminiUsageLedger(), now).connection)
    }

    @Test
    fun aSuccessfulProbeReportsTheModelCountItSaw() {
        val probe = GeminiKeyProbeResult(GeminiKeyStatus.CONNECTED, modelCount = 12)

        val snapshot = ApiUsageSnapshot.assemble(key, null, probe, GeminiUsageLedger(), now)

        assertEquals(GeminiKeyStatus.CONNECTED, snapshot.connection)
        assertEquals(12L, snapshot.modelsAvailable.knownValue)
    }

    @Test
    fun aConnectedProbeWithNoCountIsUnknownNotZero() {
        val probe = GeminiKeyProbeResult(GeminiKeyStatus.CONNECTED, modelCount = null)

        val snapshot = ApiUsageSnapshot.assemble(key, null, probe, GeminiUsageLedger(), now)

        assertEquals(UsageUnavailable.UNSUPPORTED, snapshot.modelsAvailable.reasonOrNull)
        assertNull(snapshot.modelsAvailable.knownValue)
    }

    @Test
    fun aFailedProbeMapsItsReasonOntoTheModelFigure() {
        assertEquals(
            UsageUnavailable.NETWORK_ERROR,
            ApiUsageSnapshot.assemble(
                key, null, GeminiKeyProbeResult(GeminiKeyStatus.NETWORK_UNAVAILABLE), GeminiUsageLedger(), now,
            ).modelsAvailable.reasonOrNull,
        )
        assertEquals(
            UsageUnavailable.PERMISSION_DENIED,
            ApiUsageSnapshot.assemble(
                key, null, GeminiKeyProbeResult(GeminiKeyStatus.UNAUTHORIZED), GeminiUsageLedger(), now,
            ).modelsAvailable.reasonOrNull,
        )
    }

    @Test
    fun theDisplayedKeyIsAlwaysMasked() {
        val snapshot = ApiUsageSnapshot.assemble(key, null, null, GeminiUsageLedger(), now)

        assertTrue(snapshot.maskedKey.startsWith("AIza"))
        assertFalse(snapshot.maskedKey == key)
        assertFalse(snapshot.maskedKey.contains("SyD1234567890"))
    }

    @Test
    fun activityTimestampsAndLastErrorPassThrough() {
        val ledger = GeminiUsageLedger()
        ledger.recordSuccess(now, null)
        ledger.recordFailure(now + 1000, UsageFailureCategory.TIMEOUT)

        val snapshot = ApiUsageSnapshot.assemble(key, null, null, ledger, now)

        assertEquals(now, snapshot.lastSuccessAtMillis)
        assertEquals(now + 1000, snapshot.lastFailureAtMillis)
        assertEquals(UsageFailureCategory.TIMEOUT, snapshot.lastErrorCategory)
    }

    @Test
    fun aBlankAccountEmailIsTreatedAsAbsent() {
        assertNull(ApiUsageSnapshot.assemble(key, "   ", null, GeminiUsageLedger(), now).accountEmail)
    }

    @Test
    fun theSuccessShareIsMeasuredFromObservedRequestsOnly() {
        val ledger = GeminiUsageLedger()
        repeat(3) { ledger.recordSuccess(now, null) }
        ledger.recordFailure(now, UsageFailureCategory.TIMEOUT)

        val snapshot = ApiUsageSnapshot.assemble(key, null, null, ledger, now)

        assertEquals(4L, snapshot.requestsThisMonth.knownValue)
        assertEquals(3L, snapshot.successesThisMonth.knownValue)
        assertEquals(1L, snapshot.failuresThisMonth.knownValue)
        assertEquals(0.75f, snapshot.successShare()!!, 0.0001f)
    }

    @Test
    fun theSuccessShareIsNullRatherThanAFakePercentageWhenThereIsNothingToDivide() {
        assertNull(
            "no requests means no percentage",
            ApiUsageSnapshot.assemble(key, null, null, GeminiUsageLedger(), now).successShare(),
        )

        val noKey = ApiUsageSnapshot.assemble(null, null, null, GeminiUsageLedger(), now)
        assertNull(noKey.successShare())
        assertEquals(UsageUnavailable.NOT_CONFIGURED, noKey.successesThisMonth.reasonOrNull)
        assertEquals(UsageUnavailable.NOT_CONFIGURED, noKey.failuresThisMonth.reasonOrNull)
    }

    @Test
    fun aFullySuccessfulMonthIsAFullShareAndAFullyFailedOneIsZero() {
        assertEquals(1f, ApiUsageSnapshot.assemble(key, null, null, ledgerWith(2), now).successShare()!!, 0.0001f)

        val failing = GeminiUsageLedger()
        repeat(2) { failing.recordFailure(now, UsageFailureCategory.NETWORK) }
        val snapshot = ApiUsageSnapshot.assemble(key, null, null, failing, now)

        assertEquals(0f, snapshot.successShare()!!, 0.0001f)
        assertEquals(2L, snapshot.failuresThisMonth.knownValue)
        assertEquals(0L, snapshot.successesThisMonth.knownValue)
    }

    @Test
    fun theShareNeverDependsOnQuotaOrBilling() {
        val ledger = GeminiUsageLedger()
        repeat(3) { ledger.recordSuccess(now, null) }

        val snapshot = ApiUsageSnapshot.assemble(key, null, null, ledger, now)

        // The one real ratio on the dashboard comes from observed requests; the figures Google
        // does not expose stay unavailable and can never feed it.
        assertEquals(1f, snapshot.successShare()!!, 0.0001f)
        assertNull(snapshot.projectQuota.knownValue)
        assertNull(snapshot.billing.knownValue)
    }

    @Test
    fun theWeekAndTheChartAreBuiltFromObservedRequestsOnly() {
        val ledger = GeminiUsageLedger()
        ledger.recordSuccess(now, GeminiUsageMetadata(totalTokens = 7))

        val snapshot = ApiUsageSnapshot.assemble(key, null, null, ledger, now)

        assertEquals(1L, snapshot.requestsThisWeek.knownValue)
        assertEquals(ApiUsageSnapshot.CHART_DAYS, snapshot.observedDaily.size)
        assertEquals("2026-09-18", snapshot.observedDaily.last().day)
        assertEquals(1, snapshot.observedDaily.last().requests)
        assertEquals("earlier days are real zeros, not invented points", 0, snapshot.observedDaily.first().requests)
    }

    @Test
    fun withNothingEverObservedTheWeekIsUnknownAndThereIsNoChart() {
        val snapshot = ApiUsageSnapshot.assemble(key, null, null, GeminiUsageLedger(), now)

        assertEquals(UsageUnavailable.UNKNOWN, snapshot.requestsThisWeek.reasonOrNull)
        assertTrue("no data means nothing to draw", snapshot.observedDaily.isEmpty())
    }

    @Test
    fun anActiveLedgerWithNoRequestsThisWeekReportsARealZeroRatherThanUnknown() {
        val ledger = GeminiUsageLedger()
        // A request three weeks ago: the ledger has observations, but none in the last seven days.
        ledger.recordSuccess(now - 21L * 24L * 60L * 60L * 1000L, null)

        val snapshot = ApiUsageSnapshot.assemble(key, null, null, ledger, now)

        assertEquals(0L, snapshot.requestsThisWeek.knownValue)
        assertTrue(snapshot.observedDaily.isNotEmpty())
        assertTrue(snapshot.observedDaily.all { it.requests == 0 })
    }
}
