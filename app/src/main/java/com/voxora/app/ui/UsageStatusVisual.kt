package com.voxora.app.ui

import com.voxora.core.usage.GeminiKeyStatus
import com.voxora.core.usage.UsageUnavailable

/**
 * Which semantic colour a usage figure or status deserves.
 *
 * Kept separate from the composable and free of `android.*` so the mapping is unit-testable, and
 * so no composable has to decide a colour inline. The tones map onto the existing semantic roles:
 * [OK] to `VoxoraColors.success`, [WARNING] to `VoxoraColors.warning`, [ERROR] to
 * `VoxoraColors.danger`, [NEUTRAL] to `colorScheme.outline`.
 *
 * The distinction that matters most here is that **"we cannot show this" is usually not an error**.
 * An unconfigured key, a quota source that needs credentials, and a figure the API does not expose
 * are all expected states, not faults, so they are neutral rather than red. Only a genuinely
 * refused or broken thing is [ERROR].
 */
internal enum class UsageTone { OK, WARNING, ERROR, NEUTRAL }

internal object UsageStatusVisual {

    /** Tone for the connection indicator, before and after a check. */
    fun tone(status: GeminiKeyStatus?): UsageTone = when (status) {
        null -> UsageTone.NEUTRAL
        GeminiKeyStatus.CONNECTED -> UsageTone.OK
        GeminiKeyStatus.QUOTA_LIMITED -> UsageTone.WARNING
        GeminiKeyStatus.NETWORK_UNAVAILABLE -> UsageTone.WARNING
        GeminiKeyStatus.CONFIGURATION_INCOMPLETE -> UsageTone.NEUTRAL
        GeminiKeyStatus.INVALID_KEY -> UsageTone.ERROR
        GeminiKeyStatus.UNAUTHORIZED -> UsageTone.ERROR
        GeminiKeyStatus.SERVICE_ERROR -> UsageTone.ERROR
        GeminiKeyStatus.UNKNOWN -> UsageTone.ERROR
    }

    /**
     * Tone for a figure that could not be shown.
     *
     * A missing key, a source needing credentials, and an unsupported figure are all *expected*
     * gaps and stay neutral; only a refusal or a network problem is worth colouring.
     */
    fun tone(reason: UsageUnavailable): UsageTone = when (reason) {
        UsageUnavailable.NOT_CONFIGURED,
        UsageUnavailable.AUTH_REQUIRED,
        UsageUnavailable.NOT_OFFERED,
        UsageUnavailable.UNSUPPORTED,
        UsageUnavailable.UNKNOWN,
        -> UsageTone.NEUTRAL
        UsageUnavailable.PERMISSION_DENIED -> UsageTone.ERROR
        UsageUnavailable.NETWORK_ERROR -> UsageTone.WARNING
    }

    /** Whether the connection indicator should show the slow active breath. */
    fun pulses(status: GeminiKeyStatus?): Boolean = status == GeminiKeyStatus.CONNECTED
}
