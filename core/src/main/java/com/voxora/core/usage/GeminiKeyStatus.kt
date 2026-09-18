package com.voxora.core.usage

import org.json.JSONObject

/**
 * How the configured Gemini API key currently behaves.
 *
 * These are deliberately *behavioural* states, not guesses about the key's owner. An API
 * key cannot report which Google account or project it belongs to, so nothing here claims
 * that relationship — see [ApiUsageSnapshot] for the same rule applied to the dashboard.
 */
enum class GeminiKeyStatus {
    /** The key authenticated and the service answered. */
    CONNECTED,

    /** The key is malformed, revoked or not an API key at all. */
    INVALID_KEY,

    /** The key is understood but not permitted for this API (blocked, or wrong project). */
    UNAUTHORIZED,

    /** The key is valid but has exhausted its rate or daily quota right now. */
    QUOTA_LIMITED,

    /** The request never reached Google. */
    NETWORK_UNAVAILABLE,

    /** Google answered, but with a server-side failure. Retrying later is reasonable. */
    SERVICE_ERROR,

    /** No usable key is stored, so no request was attempted. */
    CONFIGURATION_INCOMPLETE,

    /** The response could not be classified from what was returned. */
    UNKNOWN,
    ;

    /** Only [CONNECTED] means the key is usable right now. */
    val isHealthy: Boolean get() = this == CONNECTED
}

/**
 * A transport-level answer from the official `models.list` endpoint.
 *
 * Kept separate from the classifier so the classification rules can be unit-tested against
 * every status code without touching the network.
 */
data class GeminiProbeResponse(
    /** HTTP status, or null when the request never completed. */
    val statusCode: Int? = null,
    /** Response body, when one was read. Never logged or persisted. */
    val body: String? = null,
    /** Why the request did not complete, when it did not. */
    val transportFailure: TransportFailure? = null,
) {
    enum class TransportFailure { NETWORK, TIMEOUT, OTHER }

    val isHttp: Boolean get() = statusCode != null && transportFailure == null

    companion object {
        fun http(statusCode: Int, body: String? = null) = GeminiProbeResponse(statusCode = statusCode, body = body)
        fun network() = GeminiProbeResponse(transportFailure = TransportFailure.NETWORK)
        fun timeout() = GeminiProbeResponse(transportFailure = TransportFailure.TIMEOUT)
        fun failed() = GeminiProbeResponse(transportFailure = TransportFailure.OTHER)
    }
}

/**
 * The outcome of a real key check.
 *
 * [modelCount] is populated only when the service actually listed models, so "0 models" is
 * never confused with "we did not get that far". [detail] is a short, sanitised reason that
 * is safe to show a user: it never contains the key, the request URL, or a raw response body.
 */
data class GeminiKeyProbeResult(
    val status: GeminiKeyStatus,
    val modelCount: Int? = null,
    val detail: String? = null,
)

/**
 * Maps a transport answer onto a [GeminiKeyStatus].
 *
 * Google reports the authoritative reason in the body's `error.status` field
 * (`INVALID_ARGUMENT`, `UNAUTHENTICATED`, `PERMISSION_DENIED`, `RESOURCE_EXHAUSTED`,
 * `UNAVAILABLE`, …). That reason is preferred when present, because the HTTP code alone is
 * ambiguous — Google returns `400` for both a malformed request and an invalid key, and
 * `403` for both a blocked API and a permission problem. The HTTP code is the fallback.
 *
 * Pure JVM and free of I/O so every branch is directly testable.
 */
object GeminiKeyClassifier {

    fun classify(response: GeminiProbeResponse): GeminiKeyStatus {
        response.transportFailure?.let { failure ->
            return when (failure) {
                GeminiProbeResponse.TransportFailure.NETWORK,
                GeminiProbeResponse.TransportFailure.TIMEOUT,
                -> GeminiKeyStatus.NETWORK_UNAVAILABLE
                GeminiProbeResponse.TransportFailure.OTHER -> GeminiKeyStatus.UNKNOWN
            }
        }
        val code = response.statusCode ?: return GeminiKeyStatus.UNKNOWN
        if (code in 200..299) return GeminiKeyStatus.CONNECTED
        return reasonFrom(response.body) ?: fromHttpCode(code)
    }

    /**
     * Reads `error.status` (or, failing that, `error.message`) from a Google API error
     * body. Returns null when the body is absent, not JSON, or not a recognisable reason,
     * so an unparseable body never produces a confident wrong answer.
     */
    private fun reasonFrom(body: String?): GeminiKeyStatus? {
        if (body.isNullOrBlank()) return null
        val error = try {
            JSONObject(body).optJSONObject("error") ?: return null
        } catch (_: Exception) {
            return null
        }
        val reason = error.optString("status").ifBlank { error.optString("message") }.uppercase()
        if (reason.isBlank()) return null
        return when {
            reason.contains("API_KEY_INVALID") || reason.contains("API KEY NOT VALID") -> GeminiKeyStatus.INVALID_KEY
            reason.contains("API_KEY_SERVICE_BLOCKED") || reason.contains("SERVICE_DISABLED") -> GeminiKeyStatus.UNAUTHORIZED
            reason.contains("PERMISSION_DENIED") || reason.contains("UNAUTHENTICATED") -> GeminiKeyStatus.UNAUTHORIZED
            reason.contains("RESOURCE_EXHAUSTED") || reason.contains("QUOTA") || reason.contains("RATE_LIMIT") ->
                GeminiKeyStatus.QUOTA_LIMITED
            reason.contains("UNAVAILABLE") || reason.contains("INTERNAL") -> GeminiKeyStatus.SERVICE_ERROR
            reason.contains("INVALID_ARGUMENT") -> GeminiKeyStatus.INVALID_KEY
            else -> null
        }
    }

    private fun fromHttpCode(code: Int): GeminiKeyStatus = when (code) {
        400 -> GeminiKeyStatus.INVALID_KEY
        401, 403 -> GeminiKeyStatus.UNAUTHORIZED
        404 -> GeminiKeyStatus.UNAUTHORIZED
        429 -> GeminiKeyStatus.QUOTA_LIMITED
        in 500..599 -> GeminiKeyStatus.SERVICE_ERROR
        else -> GeminiKeyStatus.UNKNOWN
    }

    /**
     * Counts the models in a successful `models.list` body.
     *
     * Returns null when the body is absent or unparseable — that is "partial data", not
     * zero models, and the caller must render it as unknown rather than as 0. A paged
     * response is also treated as unknown, because the first page is not the total.
     */
    fun modelCount(body: String?): Int? {
        if (body.isNullOrBlank()) return null
        return try {
            val json = JSONObject(body)
            if (json.optString("nextPageToken").isNotBlank()) return null
            json.optJSONArray("models")?.length()
        } catch (_: Exception) {
            null
        }
    }
}
