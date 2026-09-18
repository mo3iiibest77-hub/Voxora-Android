package com.voxora.core.usage

import org.json.JSONObject

/**
 * Token usage the Gemini server reported for a single request.
 *
 * This is **only** ever built from a `usageMetadata` object the server actually sent. The
 * Live API reference states that server messages *may* carry a `usageMetadata` field, so
 * absence is normal and must be represented as "not reported" — never as zero. Every
 * field here is therefore nullable, and [fromMessage] returns `null` when the server
 * reported nothing recognisable, so a caller can never mistake "no data" for "no usage".
 *
 * Field naming differs between the two Live backends, which is why both spellings are
 * read for the response count:
 * - Gemini Live reports `responseTokenCount`;
 * - Vertex Live reports `candidatesTokenCount`.
 *
 * Both are accepted so the same parser works against either backend without a flag. The
 * remaining names (`promptTokenCount`, `totalTokenCount`, `cachedContentTokenCount`,
 * `thoughtsTokenCount`) are shared.
 *
 * Pure JVM (no `android.*`), so the parsing contract is unit-testable. `org.json` is
 * provided by the Android platform at runtime and by the real `org.json` artifact in
 * `:core` tests.
 */
data class GeminiUsageMetadata(
    /** Tokens the request itself consumed. */
    val promptTokens: Int? = null,
    /** Tokens the model produced. `responseTokenCount` or `candidatesTokenCount`. */
    val responseTokens: Int? = null,
    /** Server-reported total. Only ever the server's own number, never a sum we invent. */
    val totalTokens: Int? = null,
    /** Tokens served from the server-side cache, when reported. */
    val cachedTokens: Int? = null,
    /** Reasoning tokens, when reported. */
    val thoughtsTokens: Int? = null,
) {
    /** True when at least one field was actually reported. */
    val hasAny: Boolean
        get() = promptTokens != null || responseTokens != null ||
            totalTokens != null || cachedTokens != null || thoughtsTokens != null

    companion object {
        private val RESPONSE_KEYS = listOf("responseTokenCount", "candidatesTokenCount")

        /**
         * Reads `usageMetadata` from a top-level server message.
         *
         * Returns `null` when the message carries no `usageMetadata`, which is the common
         * case, and also when it carries one with no recognised field.
         */
        fun fromMessage(message: JSONObject?): GeminiUsageMetadata? =
            fromUsage(message?.optJSONObject("usageMetadata") ?: message?.optJSONObject("usage_metadata"))

        /** Reads an already-extracted `usageMetadata` object. */
        fun fromUsage(usage: JSONObject?): GeminiUsageMetadata? {
            if (usage == null) return null
            val metadata = GeminiUsageMetadata(
                promptTokens = usage.intOrNull("promptTokenCount"),
                responseTokens = RESPONSE_KEYS.firstNotNullOfOrNull { usage.intOrNull(it) },
                totalTokens = usage.intOrNull("totalTokenCount"),
                cachedTokens = usage.intOrNull("cachedContentTokenCount"),
                thoughtsTokens = usage.intOrNull("thoughtsTokenCount"),
            )
            return metadata.takeIf { it.hasAny }
        }

        /**
         * A field counts as reported only when the key is present, non-null and numeric.
         *
         * `optInt` alone cannot express this: it returns `0` both for "the server said
         * zero" and for "the server said nothing", and those must not be conflated.
         */
        private fun JSONObject.intOrNull(key: String): Int? {
            if (!has(key) || isNull(key)) return null
            val value = opt(key)
            return when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        }
    }
}
