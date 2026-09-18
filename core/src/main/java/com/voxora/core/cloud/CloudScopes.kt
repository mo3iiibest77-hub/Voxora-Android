package com.voxora.core.cloud

/**
 * The OAuth scopes Voxora asks for, and nothing else.
 *
 * A Google **ID token** proves who the user is; it does not authorise a single Google Cloud
 * read. Project discovery, API-key metadata and project usage each need an OAuth **access
 * token** carrying a Cloud scope, which is a genuinely different credential. This object is the
 * one place the scope set is written down so the authorizer cannot drift from the APIs it calls.
 *
 * Both scopes are read-only. Voxora never creates, edits or deletes a project or key, so it has
 * no business asking for write access.
 */
object CloudScopes {

    /**
     * Read access to Google Cloud resources.
     *
     * Covers `cloudresourcemanager.googleapis.com` `projects.list` (project discovery),
     * `apikeys.googleapis.com` `projects.locations.keys.list` / `.get` (key **metadata**), and
     * the read side of Service Usage. Without it none of the discovery in this package works.
     */
    const val CLOUD_PLATFORM_READ_ONLY = "https://www.googleapis.com/auth/cloud-platform.read-only"

    /**
     * Read access to Cloud Monitoring time series.
     *
     * Used for the one usage figure Google actually exposes per project: API request counts via
     * `serviceruntime.googleapis.com/api/request_count`. It grants no write access to monitoring.
     */
    const val MONITORING_READ = "https://www.googleapis.com/auth/monitoring.read"

    /**
     * The signed-in account's email address.
     *
     * Needed to show *which* Google account is connected, which is the top level of the account →
     * project → key hierarchy. It is read through the OpenID Connect userinfo endpoint. Nothing
     * else about the user is requested, and `AuthorizationResult` does not expose an account.
     */
    const val USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email"

    /** Everything the authorizer requests, in a stable order. */
    val ALL: List<String> = listOf(CLOUD_PLATFORM_READ_ONLY, MONITORING_READ, USERINFO_EMAIL)
}
