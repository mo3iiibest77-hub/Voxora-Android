package com.voxora.core.cloud

import org.json.JSONObject

/**
 * A Google Cloud project the authorized account can actually see.
 *
 * Discovered through the Resource Manager API (`projects.list`), which returns only projects the
 * caller has some access to. Nothing here is invented: a project that is not in the response is
 * not in the list, and Voxora never assumes the AI Studio project is the only one.
 */
data class CloudProject(
    /** The project id, e.g. `my-project-123456`. Stable, and what every other API is keyed by. */
    val projectId: String,
    /** The project number, shown as secondary identity because it is unambiguous. */
    val projectNumber: String?,
    /** The human display name, or the id when Google did not provide one. */
    val displayName: String,
    /** Lifecycle state as Google reports it, e.g. `ACTIVE`. Null when absent. */
    val lifecycleState: String?,
) {
    /** What the UI shows as the title: never blank. */
    val label: String get() = displayName.ifBlank { projectId }

    companion object {
        /**
         * Reads one project from a Resource Manager `Project` object.
         *
         * Returns null when there is no usable identity — a project with no `projectId` cannot be
         * addressed by any later API call, so keeping it would only produce a broken selection.
         */
        fun from(json: JSONObject): CloudProject? {
            val id = json.optString("projectId").trim()
            if (id.isEmpty()) return null
            val number = json.optString("projectNumber").trim().takeIf { it.isNotEmpty() }
            val name = json.optString("name").trim().takeIf { it.isNotEmpty() }
            val state = json.optString("lifecycleState").trim().takeIf { it.isNotEmpty() }
            return CloudProject(
                projectId = id,
                projectNumber = number,
                displayName = name ?: id,
                lifecycleState = state,
            )
        }
    }
}
