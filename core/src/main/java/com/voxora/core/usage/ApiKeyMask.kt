package com.voxora.core.usage

/**
 * Display-safe representation of a Gemini API key.
 *
 * The full key is a secret: it must never be rendered in the UI, written to a log, put in
 * a crash report or included in an error message. Every surface that needs to *show* that
 * a key is configured goes through here instead of printing the value.
 *
 * The mask deliberately reveals only the first and last four characters, and always uses
 * the same bullet run rather than one proportional to the key length, so the mask does not
 * leak the key's length either. A key too short to mask safely is replaced entirely.
 */
object ApiKeyMask {

    private const val PREFIX = 4
    private const val SUFFIX = 4
    private const val BULLETS = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"

    /**
     * Below this length, revealing 8 characters would expose most of the key, so the whole
     * value is masked instead.
     */
    private const val MIN_REVEALABLE = 12

    /** Google AI Studio keys begin with this prefix; used only to recognise obvious junk. */
    private const val EXPECTED_PREFIX = "AIza"

    /**
     * The placeholder shipped in `strings.xml` so the build works before the owner
     * configures Google Cloud. Treated as "not configured" rather than as a key.
     */
    private const val PLACEHOLDER = "REPLACE_WITH_GOOGLE_WEB_CLIENT_ID"

    /**
     * The mask for [key], or an empty string when no usable key is configured.
     *
     * Returning empty rather than masking the placeholder means no caller can accidentally render
     * `REPL••••••••••_ID` in the UI as though a key were configured.
     */
    fun mask(key: String?): String {
        if (!isConfigured(key)) return ""
        val trimmed = key!!.trim()
        if (trimmed.length < MIN_REVEALABLE) return BULLETS
        return trimmed.take(PREFIX) + BULLETS + trimmed.takeLast(SUFFIX)
    }

    /**
     * Whether a usable key is present.
     *
     * A blank value, the shipped placeholder, or anything else that is obviously not a
     * pasted key counts as not configured, so the UI can say "no key" instead of probing
     * something that cannot succeed.
     */
    fun isConfigured(key: String?): Boolean {
        val trimmed = key?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        if (trimmed.equals(PLACEHOLDER, ignoreCase = true)) return false
        if (trimmed.startsWith("REPLACE_", ignoreCase = true)) return false
        return true
    }

    /**
     * A short, non-identifying description of the key's *shape*, for diagnostics.
     *
     * Returns nothing derived from the key's contents beyond whether it starts with the
     * documented AI Studio prefix, because that is useful when a user has pasted an OAuth
     * token or a service-account JSON fragment by mistake.
     */
    fun describeShape(key: String?): String? {
        val trimmed = key?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith(EXPECTED_PREFIX)) "ai-studio" else "unrecognised"
    }
}
