package com.voxora.core.cloud

/**
 * The Google Cloud API hosts.
 *
 * Injectable purely so the HTTP behaviour — status mapping, header placement, URL shape — is
 * testable against a local server without reaching Google. Production always uses the real hosts.
 */
data class CloudEndpoints(
    val resourceManagerBase: String = "https://cloudresourcemanager.googleapis.com",
    val apiKeysBase: String = "https://apikeys.googleapis.com",
    val monitoringBase: String = "https://monitoring.googleapis.com",
    val quotasBase: String = "https://cloudquotas.googleapis.com",
    val userInfoBase: String = "https://openidconnect.googleapis.com",
) {
    companion object {
        val PRODUCTION = CloudEndpoints()
    }
}
