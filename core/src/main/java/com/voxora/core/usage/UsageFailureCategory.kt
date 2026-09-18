package com.voxora.core.usage

/**
 * Short, non-identifying categories for a failed request.
 *
 * A raw exception message is deliberately **not** stored: an HTTP client can include the request
 * URL in a message, and the Reader's WebSocket URL carries the API key. Only the category below
 * ever reaches the ledger, so nothing sensitive can be persisted through the usage path.
 *
 * Ordering matters. Audio and session failures are tested **before** the generic network words,
 * because real Reader messages such as "Audio output is unavailable" contain "unavailable" and
 * would otherwise be recorded as a connectivity problem — which would misdirect the user.
 *
 * Pure JVM so the mapping is unit-testable.
 */
object UsageFailureCategory {
    const val NETWORK = "network"
    const val QUOTA = "quota"
    const val UNAUTHORIZED = "unauthorized"
    const val TIMEOUT = "timeout"
    const val AUDIO = "audio"
    const val SESSION = "session"

    /** A missing key or an unusable mode: nothing was sent, so nothing can be blamed on the network. */
    const val CONFIGURATION = "configuration"
    const val UNKNOWN = "unknown"

    fun classify(exceptionTypeName: String?, message: String?): String {
        val type = exceptionTypeName.orEmpty().substringAfterLast('.').lowercase()
        val text = message.orEmpty().lowercase()
        return when {
            "timeout" in type || "timeout" in text || "timed out" in text -> TIMEOUT
            "quota" in text || "resource_exhausted" in text || "rate limit" in text -> QUOTA
            "permission" in text || "unauthorized" in text || "api_key_invalid" in text -> UNAUTHORIZED
            "audio" in text -> AUDIO
            "spool" in type || "capacity" in type || "session" in type || "session" in text -> SESSION
            "connect" in text || "network" in text || "unavailable" in text || "socket" in text -> NETWORK
            else -> UNKNOWN
        }
    }
}
