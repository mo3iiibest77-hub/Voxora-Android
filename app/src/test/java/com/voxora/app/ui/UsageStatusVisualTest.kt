package com.voxora.app.ui

import com.voxora.core.usage.GeminiKeyStatus
import com.voxora.core.usage.UsageUnavailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for how the usage screen colours things.
 *
 * The distinction that matters is that **"we cannot show this" is usually not an error**. An
 * unconfigured key, a quota source that needs credentials, and a figure the API does not expose are
 * all expected states and must stay neutral rather than alarming red. Only a refusal or a broken
 * connection is worth colouring.
 */
class UsageStatusVisualTest {

    @Test
    fun aHealthyConnectionIsTheOnlyActiveState() {
        assertEquals(UsageTone.OK, UsageStatusVisual.tone(GeminiKeyStatus.CONNECTED))
        assertTrue(UsageStatusVisual.pulses(GeminiKeyStatus.CONNECTED))
    }

    @Test
    fun onlyAHealthyConnectionPulses() {
        val others = GeminiKeyStatus.entries.filter { it != GeminiKeyStatus.CONNECTED }
        assertTrue(others.none { UsageStatusVisual.pulses(it) })
        assertFalse("an unchecked key must not animate", UsageStatusVisual.pulses(null))
    }

    @Test
    fun aRefusedOrBrokenKeyIsAnError() {
        assertEquals(UsageTone.ERROR, UsageStatusVisual.tone(GeminiKeyStatus.INVALID_KEY))
        assertEquals(UsageTone.ERROR, UsageStatusVisual.tone(GeminiKeyStatus.UNAUTHORIZED))
        assertEquals(UsageTone.ERROR, UsageStatusVisual.tone(GeminiKeyStatus.SERVICE_ERROR))
        assertEquals(UsageTone.ERROR, UsageStatusVisual.tone(GeminiKeyStatus.UNKNOWN))
    }

    @Test
    fun transientProblemsWarnRatherThanFail() {
        assertEquals(UsageTone.WARNING, UsageStatusVisual.tone(GeminiKeyStatus.QUOTA_LIMITED))
        assertEquals(UsageTone.WARNING, UsageStatusVisual.tone(GeminiKeyStatus.NETWORK_UNAVAILABLE))
    }

    @Test
    fun anUnconfiguredBuildIsNeutralNotAnError() {
        assertEquals(
            "a missing key is a setup step, not a fault",
            UsageTone.NEUTRAL,
            UsageStatusVisual.tone(GeminiKeyStatus.CONFIGURATION_INCOMPLETE),
        )
    }

    @Test
    fun anUncheckedKeyIsNeutral() {
        assertEquals(UsageTone.NEUTRAL, UsageStatusVisual.tone(null))
    }

    @Test
    fun expectedGapsInTheDataStayNeutral() {
        assertEquals(UsageTone.NEUTRAL, UsageStatusVisual.tone(UsageUnavailable.NOT_CONFIGURED))
        assertEquals(UsageTone.NEUTRAL, UsageStatusVisual.tone(UsageUnavailable.AUTH_REQUIRED))
        assertEquals(UsageTone.NEUTRAL, UsageStatusVisual.tone(UsageUnavailable.NOT_OFFERED))
        assertEquals(UsageTone.NEUTRAL, UsageStatusVisual.tone(UsageUnavailable.UNSUPPORTED))
        assertEquals(UsageTone.NEUTRAL, UsageStatusVisual.tone(UsageUnavailable.UNKNOWN))
    }

    @Test
    fun aRefusalIsAnErrorAndANetworkGapWarns() {
        assertEquals(UsageTone.ERROR, UsageStatusVisual.tone(UsageUnavailable.PERMISSION_DENIED))
        assertEquals(UsageTone.WARNING, UsageStatusVisual.tone(UsageUnavailable.NETWORK_ERROR))
    }

    @Test
    fun projectQuotaAndBillingArePresentedAsExpectedGapsNotFailures() {
        // An API key cannot read them, so they are neutral: the user has done nothing wrong.
        assertEquals(UsageTone.NEUTRAL, UsageStatusVisual.tone(UsageUnavailable.AUTH_REQUIRED))
    }

    @Test
    fun everyStatusAndEveryReasonHasATone() {
        assertTrue(GeminiKeyStatus.entries.all { UsageStatusVisual.tone(it) in UsageTone.entries })
        assertTrue(UsageUnavailable.entries.all { UsageStatusVisual.tone(it) in UsageTone.entries })
    }
}
