package com.voxora.app.auth

import com.voxora.core.cloud.CloudAuthFailure

/**
 * Maps a Google authorization failure onto [CloudAuthFailure].
 *
 * The Play Services exceptions cannot be loaded on a plain JVM, so classification goes by type
 * name, message and the Google status code — which keeps it unit-testable and keeps this logic in
 * exactly one place. The status codes are the documented `CommonStatusCodes` values the
 * authorization API returns.
 */
object CloudAuthFailureClassifier {

    /** `CommonStatusCodes.SIGN_IN_CANCELLED` — the user dismissed the consent screen. */
    private const val STATUS_CANCELLED = 12501

    /** `CommonStatusCodes.SIGN_IN_FAILED`. */
    private const val STATUS_SIGN_IN_FAILED = 12500

    /** `CommonStatusCodes.SIGN_IN_REQUIRED` — no account is available to authorize. */
    private const val STATUS_SIGN_IN_REQUIRED = 4

    /** `CommonStatusCodes.NETWORK_ERROR`. */
    private const val STATUS_NETWORK_ERROR = 7

    /** `CommonStatusCodes.CANCELED`, the older spelling. */
    private const val STATUS_CANCELED_LEGACY = 16

    /**
     * The status codes that mean Play Services itself is unusable, not the request.
     *
     * `SERVICE_MISSING`, `SERVICE_VERSION_UPDATE_REQUIRED`, `SERVICE_DISABLED` and
     * `SERVICE_INVALID`. Retrying cannot fix any of them, which is why they are reported as
     * [CloudAuthFailure.PROVIDER_UNAVAILABLE] rather than as a generic failure.
     */
    private val PROVIDER_STATUS_CODES = setOf(1, 2, 3, 9)

    fun classify(
        exceptionTypeName: String?,
        message: String?,
        statusCode: Int?,
    ): CloudAuthFailure {
        val type = exceptionTypeName.orEmpty().substringAfterLast('.').lowercase()
        val text = message.orEmpty().lowercase()
        return when {
            statusCode == STATUS_CANCELLED || statusCode == STATUS_CANCELED_LEGACY -> CloudAuthFailure.CANCELLED
            "cancel" in text || "cancel" in type -> CloudAuthFailure.CANCELLED
            statusCode == STATUS_NETWORK_ERROR -> CloudAuthFailure.NETWORK
            "network" in text || "timeout" in text || "timed out" in text || "unavailable" in text ->
                CloudAuthFailure.NETWORK
            statusCode == STATUS_SIGN_IN_REQUIRED -> CloudAuthFailure.NO_ACCOUNT
            "no credential" in text || "noaccount" in type || "no account" in text -> CloudAuthFailure.NO_ACCOUNT
            statusCode in PROVIDER_STATUS_CODES -> CloudAuthFailure.PROVIDER_UNAVAILABLE
            "resolvable" in type || "providerconfiguration" in type || "unsupported" in type ->
                CloudAuthFailure.PROVIDER_UNAVAILABLE
            statusCode == STATUS_SIGN_IN_FAILED -> CloudAuthFailure.UNKNOWN
            // A refusal is not a failure: the account is fine, it just declined the scopes.
            "permission" in text || "denied" in text || "access_denied" in text ->
                CloudAuthFailure.PERMISSION_DENIED
            "invalid" in text -> CloudAuthFailure.UNSUPPORTED
            else -> CloudAuthFailure.UNKNOWN
        }
    }
}
