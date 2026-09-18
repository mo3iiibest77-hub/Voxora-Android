package com.voxora.core.cloud

/**
 * Whether this build carries a usable Google OAuth client ID.
 *
 * The app ships a placeholder in `strings.xml` so the build works before the owner configures a
 * Google client. Treating that placeholder as *configured* would send an authorization request that
 * cannot succeed and then report the failure as the user's fault, so the placeholder — and anything
 * else that is obviously not a client ID — counts as absent.
 *
 * Kept free of `android.*` so the rule is unit-testable; `GoogleCloudAuthorizer` is the only caller
 * and is the one that reads the string resource.
 *
 * This deliberately checks **presence, not validity**. Whether a client ID is well-formed or belongs
 * to the right project is for Google to answer; guessing here would only replace an honest
 * "not configured" with a misleading "authorization failed".
 */
object CloudOAuthConfig {

    /** The prefix of the placeholder shipped in `res/values/strings.xml`. */
    const val PLACEHOLDER_PREFIX = "REPLACE_"

    /** Whether [clientId] looks like a real client ID rather than the shipped placeholder. */
    fun isConfigured(clientId: String?): Boolean {
        val value = clientId?.trim().orEmpty()
        if (value.isEmpty()) return false
        if (value.startsWith(PLACEHOLDER_PREFIX, ignoreCase = true)) return false
        return true
    }
}
