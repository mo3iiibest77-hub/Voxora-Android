package com.voxora.core.usage

/**
 * Why a number is not shown.
 *
 * The dashboard must be able to say *why* something is missing instead of rendering a zero.
 * A zero is a claim; "not available" is the truth, and the two must never be confused.
 */
enum class UsageUnavailable {
    /** No usable key is stored, so nothing could be measured. */
    NOT_CONFIGURED,

    /** Reading this needs authenticated access to the owning Google Cloud project. */
    AUTH_REQUIRED,

    /** Access was refused. */
    PERMISSION_DENIED,

    /** The request never reached Google. */
    NETWORK_ERROR,

    /** The API does not expose this at all. */
    NOT_OFFERED,

    /** Google answered but not in a shape we can read. */
    UNSUPPORTED,

    UNKNOWN,
}

/** One dashboard figure: either a real observed number, or an explicit reason there isn't one. */
sealed interface UsageMetric {
    data class Known(val value: Long) : UsageMetric
    data class Missing(val reason: UsageUnavailable) : UsageMetric

    val knownValue: Long? get() = (this as? Known)?.value
    val reasonOrNull: UsageUnavailable? get() = (this as? Missing)?.reason

    companion object {
        fun of(value: Long?): UsageMetric = if (value == null) Missing(UsageUnavailable.UNKNOWN) else Known(value)
        fun known(value: Long) = Known(value)
        fun missing(reason: UsageUnavailable) = Missing(reason)
    }
}

/**
 * Everything the API usage dashboard shows, assembled from what is actually known.
 *
 * ## The rule this type exists to enforce
 * Gemini quota, billing and project identity belong to the **Google Cloud project**, not to an
 * API key. An API key cannot be used to read them — that needs OAuth credentials for the owning
 * project. So:
 *
 * - the masked key, the connection status and the observed request/token counts are real,
 *   because Voxora either holds the key or made the request itself;
 * - the Cloud project, its quota and its billing are reported as
 *   [UsageUnavailable.AUTH_REQUIRED], because this app genuinely cannot read them;
 * - [accountLinkedToKey] is `false`, because nothing in this app can verify that a signed-in
 *   Google account owns the configured key. The UI must not imply that relationship.
 *
 * Nothing here is estimated. [assemble] only ever copies numbers that were observed.
 */
data class ApiUsageSnapshot(
    /** Display-safe key, e.g. `AIza••••••••••ABCD`. Empty when no key is stored. */
    val maskedKey: String,
    val keyConfigured: Boolean,
    /** Signed-in Google email from local state, or null. */
    val accountEmail: String?,
    /** Always false today: the key/account relationship is unverifiable from an API key. */
    val accountLinkedToKey: Boolean,
    /** Result of the last explicit key check, or null when it has not been run. */
    val connection: GeminiKeyStatus?,
    /** Models the key could see, when the check listed them. */
    val modelsAvailable: UsageMetric,
    /** Observed requests Voxora made today (UTC). */
    val requestsToday: UsageMetric,
    /** Observed requests Voxora made this UTC month. */
    val requestsThisMonth: UsageMetric,
    val inputTokens: UsageMetric,
    val outputTokens: UsageMetric,
    val totalTokens: UsageMetric,
    /** How many requests actually carried usage metadata, out of [requestsThisMonth]. */
    val tokenCoverage: UsageMetric,
    val lastSuccessAtMillis: Long?,
    val lastFailureAtMillis: Long?,
    val lastErrorCategory: String?,
    /** Cloud project quota — never readable from an API key. */
    val projectQuota: UsageMetric,
    /** Cloud billing spend — never readable from an API key, and never estimated. */
    val billing: UsageMetric,
) {
    companion object {
        /**
         * Assembles the dashboard from local state plus the last probe result.
         *
         * [ledger] is Voxora's own record of requests it made, so those figures are marked
         * known only when at least one request was actually recorded — otherwise they are
         * [UsageUnavailable.NOT_CONFIGURED] or [UsageUnavailable.UNKNOWN], never `0`.
         */
        fun assemble(
            apiKey: String?,
            accountEmail: String?,
            probe: GeminiKeyProbeResult?,
            ledger: GeminiUsageLedger?,
            nowMillis: Long,
        ): ApiUsageSnapshot {
            val configured = ApiKeyMask.isConfigured(apiKey)
            val today = ledger?.day(nowMillis)
            val month = ledger?.month(nowMillis)
            val observed = (month?.requests ?: 0) > 0
            val tokensReported = month?.tokensReported ?: 0

            return ApiUsageSnapshot(
                maskedKey = ApiKeyMask.mask(apiKey),
                keyConfigured = configured,
                accountEmail = accountEmail?.takeIf { it.isNotBlank() },
                accountLinkedToKey = false,
                connection = probe?.status,
                modelsAvailable = when {
                    probe == null -> UsageMetric.missing(UsageUnavailable.UNKNOWN)
                    probe.modelCount != null -> UsageMetric.known(probe.modelCount.toLong())
                    probe.status == GeminiKeyStatus.CONNECTED -> UsageMetric.missing(UsageUnavailable.UNSUPPORTED)
                    else -> UsageMetric.missing(probe.status.toUnavailable())
                },
                requestsToday = when {
                    today != null -> UsageMetric.known(today.requests.toLong())
                    !configured -> UsageMetric.missing(UsageUnavailable.NOT_CONFIGURED)
                    else -> UsageMetric.missing(UsageUnavailable.UNKNOWN)
                },
                requestsThisMonth = when {
                    observed -> UsageMetric.known(month!!.requests.toLong())
                    !configured -> UsageMetric.missing(UsageUnavailable.NOT_CONFIGURED)
                    else -> UsageMetric.missing(UsageUnavailable.UNKNOWN)
                },
                inputTokens = tokenMetric(month?.promptTokens, tokensReported, observed, configured),
                outputTokens = tokenMetric(month?.responseTokens, tokensReported, observed, configured),
                totalTokens = tokenMetric(month?.totalTokens, tokensReported, observed, configured),
                tokenCoverage = when {
                    !observed -> UsageMetric.missing(
                        if (configured) UsageUnavailable.UNKNOWN else UsageUnavailable.NOT_CONFIGURED,
                    )
                    else -> UsageMetric.known(tokensReported.toLong())
                },
                lastSuccessAtMillis = ledger?.lastSuccess,
                lastFailureAtMillis = ledger?.lastFailure,
                lastErrorCategory = ledger?.lastError,
                // An API key cannot read Cloud project quota or billing. These need OAuth
                // credentials for the owning project, which this app does not hold.
                projectQuota = UsageMetric.missing(UsageUnavailable.AUTH_REQUIRED),
                billing = UsageMetric.missing(UsageUnavailable.AUTH_REQUIRED),
            )
        }

        /**
         * Token totals are shown only when the server actually reported tokens.
         *
         * The Live API only *may* return `usageMetadata`, so "we made requests but none of them
         * reported tokens" is a real outcome. In that case the total is not zero — it is
         * unknown, and saying so is the honest answer.
         */
        private fun tokenMetric(
            reportedTotal: Long?,
            tokensReported: Int,
            observed: Boolean,
            configured: Boolean,
        ): UsageMetric = when {
            !observed -> UsageMetric.missing(if (configured) UsageUnavailable.UNKNOWN else UsageUnavailable.NOT_CONFIGURED)
            tokensReported == 0 -> UsageMetric.missing(UsageUnavailable.NOT_OFFERED)
            else -> UsageMetric.known(reportedTotal ?: 0L)
        }

        private fun GeminiKeyStatus.toUnavailable(): UsageUnavailable = when (this) {
            GeminiKeyStatus.CONFIGURATION_INCOMPLETE -> UsageUnavailable.NOT_CONFIGURED
            GeminiKeyStatus.NETWORK_UNAVAILABLE -> UsageUnavailable.NETWORK_ERROR
            GeminiKeyStatus.UNAUTHORIZED -> UsageUnavailable.PERMISSION_DENIED
            GeminiKeyStatus.CONNECTED -> UsageUnavailable.UNKNOWN
            GeminiKeyStatus.INVALID_KEY,
            GeminiKeyStatus.QUOTA_LIMITED,
            GeminiKeyStatus.SERVICE_ERROR,
            GeminiKeyStatus.UNKNOWN,
            -> UsageUnavailable.UNKNOWN
        }
    }
}
