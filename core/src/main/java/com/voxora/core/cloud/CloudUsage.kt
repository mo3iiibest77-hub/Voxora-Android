package com.voxora.core.cloud

import com.voxora.core.usage.UsageMetric
import com.voxora.core.usage.UsageUnavailable

/**
 * Where a usage figure came from.
 *
 * This distinction is the point of the whole dashboard: Voxora's own counts and Google's
 * project-level report are different measurements and must never be presented as one number.
 * A local count cannot answer "what is my project quota doing", and Google's report cannot say
 * what this device did.
 */
enum class CloudUsageSource {
    /** Counted by Voxora from the requests it made on this device. */
    LOCAL_OBSERVED,

    /** Reported by Google for the selected Cloud project. */
    GOOGLE_PROJECT,
}

/**
 * Project-level usage for the selected Google Cloud project.
 *
 * Every field is a [UsageMetric] so a figure is present **only when Google actually reported it**.
 * A missing field carries the reason, never a zero: the whole point of this type is that an
 * unavailable number is never rendered as `0`.
 *
 * What Google does and does not expose, stated honestly:
 * - **requests** — Cloud Monitoring publishes `serviceruntime.googleapis.com/api/request_count`
 *   per service, so a project request count is genuinely readable;
 * - **tokens** — there is no per-project token metric on the Cloud Monitoring API, so both token
 *   figures are [UsageUnavailable.NOT_OFFERED]. Voxora will not estimate them;
 * - **quota limit** — the Cloud Quotas API exposes per-service quota info; where it answers, the
 *   limit is shown, otherwise it is unavailable;
 * - **quota remaining** — Google does not expose remaining quota as a simple read, so it is
 *   [UsageUnavailable.NOT_OFFERED] rather than a computed guess;
 * - **billing** — cost is not exposed by a simple read API; it needs a billing export to BigQuery.
 *   It is therefore [UsageUnavailable.NOT_OFFERED], never an estimate.
 */
data class CloudProjectUsage(
    val projectId: String,
    val source: CloudUsageSource,
    val requests: UsageMetric,
    val inputTokens: UsageMetric,
    val outputTokens: UsageMetric,
    val quotaLimit: UsageMetric,
    val quotaRemaining: UsageMetric,
    val billing: UsageMetric,
) {
    companion object {

        /** Every figure needs authorization: used before any Cloud grant is held. */
        fun authRequired(projectId: String): CloudProjectUsage = unavailable(
            projectId = projectId,
            reason = UsageUnavailable.AUTH_REQUIRED,
        )

        /** Every figure carries [reason]. Used for refusals, network failures and unreadable answers. */
        fun unavailable(projectId: String, reason: UsageUnavailable): CloudProjectUsage =
            CloudProjectUsage(
                projectId = projectId,
                source = CloudUsageSource.GOOGLE_PROJECT,
                requests = UsageMetric.missing(reason),
                inputTokens = UsageMetric.missing(reason),
                outputTokens = UsageMetric.missing(reason),
                quotaLimit = UsageMetric.missing(reason),
                quotaRemaining = UsageMetric.missing(reason),
                billing = UsageMetric.missing(reason),
            )

        /**
         * A project whose request count was actually read.
         *
         * Tokens and billing stay [UsageUnavailable.NOT_OFFERED] because no official API in this
         * path exposes them; that is a property of Google's APIs, not of this request. [quotaLimit]
         * is passed separately because the Quotas API is a second, independent call.
         */
        fun observed(
            projectId: String,
            requests: Long,
            quotaLimit: Long?,
        ): CloudProjectUsage = CloudProjectUsage(
            projectId = projectId,
            source = CloudUsageSource.GOOGLE_PROJECT,
            requests = UsageMetric.known(requests),
            inputTokens = UsageMetric.missing(UsageUnavailable.NOT_OFFERED),
            outputTokens = UsageMetric.missing(UsageUnavailable.NOT_OFFERED),
            quotaLimit = if (quotaLimit == null) {
                UsageMetric.missing(UsageUnavailable.NOT_OFFERED)
            } else {
                UsageMetric.known(quotaLimit)
            },
            quotaRemaining = UsageMetric.missing(UsageUnavailable.NOT_OFFERED),
            billing = UsageMetric.missing(UsageUnavailable.NOT_OFFERED),
        )
    }
}
