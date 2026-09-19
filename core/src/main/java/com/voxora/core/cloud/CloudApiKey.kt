package com.voxora.core.cloud

import org.json.JSONObject

/**
 * Metadata for one API key in a Google Cloud project.
 *
 * Read through the official API Keys API. **`keys.list` and `keys.get` do not return the key
 * secret** — Google deliberately omits it, and the only documented way to read it is the separate
 * `keys.getKeyString` operation, which needs an extra permission. This type therefore carries
 * metadata only, and nothing in the UI may imply that the app can show every key's value.
 *
 * The key id is the last segment of the resource name, which is what the API is keyed by. The full
 * resource name is kept because `getKeyString` and any later call need it.
 */
data class CloudApiKey(
    /** Full resource name: `projects/{project}/locations/global/keys/{keyId}`. */
    val resourceName: String,
    /** The key id — the last path segment. */
    val keyId: String,
    /** Human label, or the key id when Google did not provide one. */
    val displayName: String,
    /** True when the key carries API or application restrictions. */
    val restricted: Boolean,
) {
    val label: String get() = displayName.ifBlank { keyId }

    companion object {
        /**
         * Reads one key from an API Keys `Key` object.
         *
         * A key with no usable resource name is dropped rather than kept with a blank identity:
         * it could not be selected or addressed afterwards.
         */
        fun from(json: JSONObject): CloudApiKey? {
            val name = json.optString("name").trim()
            if (name.isEmpty()) return null
            val keyId = name.substringAfterLast('/').ifBlank { name }
            val display = json.optString("displayName").trim().takeIf { it.isNotEmpty() } ?: keyId
            val restrictions = json.optJSONObject("restrictions")
            val restricted = restrictions != null && restrictions.length() > 0
            return CloudApiKey(
                resourceName = name,
                keyId = keyId,
                displayName = display,
                restricted = restricted,
            )
        }
    }
}
