package com.voxora.core.cloud

import com.voxora.core.usage.UsageMetric
import com.voxora.core.usage.UsageUnavailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for project-level usage.
 *
 * The rule under test: a figure is present only when Google actually reported it. An unavailable
 * figure carries a reason and is never rendered as `0`, because a zero is a claim and "we could
 * not read this" is the truth. Local observed usage and Google project usage are separate sources
 * and are never merged.
 */
class CloudUsageTest {

    // ---- nothing is invented ---------------------------------------------------------

    @Test
    fun beforeAuthorizationEveryFigureNeedsAuthorization() {
        val usage = CloudProjectUsage.authRequired("alpha-123")
        val metrics = listOf(
            usage.requests,
            usage.inputTokens,
            usage.outputTokens,
            usage.quotaLimit,
            usage.quotaRemaining,
            usage.billing,
        )
        for (metric in metrics) {
            assertEquals(UsageUnavailable.AUTH_REQUIRED, metric.reasonOrNull)
            assertNull(metric.knownValue)
        }
    }

    @Test
    fun aRefusalPropagatesToEveryFigureWithoutInventingAZero() {
        val usage = CloudProjectUsage.unavailable("alpha-123", UsageUnavailable.PERMISSION_DENIED)
        val metrics = listOf(
            usage.requests,
            usage.inputTokens,
            usage.outputTokens,
            usage.quotaLimit,
            usage.quotaRemaining,
            usage.billing,
        )
        for (metric in metrics) {
            assertEquals(UsageUnavailable.PERMISSION_DENIED, metric.reasonOrNull)
            assertNull(metric.knownValue)
        }
    }

    /**
     * The load-bearing test: an unavailable metric is never equal to a zero. This is exactly the
     * substitution the brief forbids.
     */
    @Test
    fun unavailableIsNeverTheSameAsZero() {
        val missing = UsageMetric.missing(UsageUnavailable.NOT_OFFERED)
        val zero = UsageMetric.known(0L)
        assertNotEquals(missing, zero)
        assertNull(missing.knownValue)
        assertEquals(0L, zero.knownValue)
    }

    // ---- an observed request count ---------------------------------------------------

    @Test
    fun anObservedCountIsReportedAndTheRestStaysHonest() {
        val usage = CloudProjectUsage.observed(projectId = "alpha-123", requests = 4210L, quotaLimit = 2000L)
        assertEquals(4210L, usage.requests.knownValue)
        assertEquals(2000L, usage.quotaLimit.knownValue)
        // Google exposes no per-project token metric and no simple billing read, so these are
        // absent by the API's nature — not zero, and not an estimate.
        assertEquals(UsageUnavailable.NOT_OFFERED, usage.inputTokens.reasonOrNull)
        assertEquals(UsageUnavailable.NOT_OFFERED, usage.outputTokens.reasonOrNull)
        assertEquals(UsageUnavailable.NOT_OFFERED, usage.quotaRemaining.reasonOrNull)
        assertEquals(UsageUnavailable.NOT_OFFERED, usage.billing.reasonOrNull)
    }

    @Test
    fun anAbsentQuotaIsNotOfferedRatherThanZero() {
        val usage = CloudProjectUsage.observed("alpha-123", requests = 1L, quotaLimit = null)
        assertEquals(UsageUnavailable.NOT_OFFERED, usage.quotaLimit.reasonOrNull)
        assertNull(usage.quotaLimit.knownValue)
    }

    @Test
    fun anExplicitZeroRequestCountIsARealZero() {
        val usage = CloudProjectUsage.observed("alpha-123", requests = 0L, quotaLimit = null)
        assertEquals(0L, usage.requests.knownValue)
        assertNull(usage.requests.reasonOrNull)
    }

    // ---- source separation -----------------------------------------------------------

    @Test
    fun projectUsageIsAlwaysSourcedFromGoogleNotFromLocalObservation() {
        assertEquals(CloudUsageSource.GOOGLE_PROJECT, CloudProjectUsage.observed("p", 1L, null).source)
        assertEquals(CloudUsageSource.GOOGLE_PROJECT, CloudProjectUsage.authRequired("p").source)
        assertEquals(CloudUsageSource.GOOGLE_PROJECT, CloudProjectUsage.unavailable("p", UsageUnavailable.UNKNOWN).source)
    }

    @Test
    fun theTwoUsageSourcesAreDistinct() {
        assertNotEquals(CloudUsageSource.LOCAL_OBSERVED, CloudUsageSource.GOOGLE_PROJECT)
        assertEquals(2, CloudUsageSource.entries.size)
    }

    @Test
    fun everyUnavailableReasonIsRepresentableAndDistinct() {
        val reasons = UsageUnavailable.entries
        assertEquals(reasons.size, reasons.toSet().size)
        assertTrue(UsageUnavailable.AUTH_REQUIRED in reasons)
        assertTrue(UsageUnavailable.NOT_OFFERED in reasons)
        assertTrue(UsageUnavailable.PERMISSION_DENIED in reasons)
    }
}
