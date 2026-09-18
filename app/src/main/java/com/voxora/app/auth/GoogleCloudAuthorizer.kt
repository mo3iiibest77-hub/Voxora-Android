package com.voxora.app.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.voxora.app.R
import com.voxora.app.util.VoxoraLog
import com.voxora.core.cloud.CloudAuthFailure
import com.voxora.core.cloud.CloudScopes

/**
 * What one authorization attempt produced.
 *
 * [NeedsConsent] is not a failure: it carries the `PendingIntent` the caller must launch so the
 * user can grant the Cloud scopes. After that activity returns, the caller re-runs
 * [GoogleCloudAuthorizer.requestAuthorization] to collect the access token, which is the
 * documented two-step flow.
 */
sealed interface CloudAuthorizationOutcome {

    /** The user must approve the requested scopes. Launch [pendingIntent] and then ask again. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : CloudAuthorizationOutcome

    /** Scopes granted. [accessToken] is short-lived and must never be logged or persisted. */
    data class Granted(val accessToken: String, val accountEmail: String?) : CloudAuthorizationOutcome

    /** The attempt failed, with a classified reason. */
    data class Denied(val reason: CloudAuthFailure) : CloudAuthorizationOutcome
}

/**
 * Requests an OAuth **access token** for Google Cloud, through Google Identity Services.
 *
 * ## Why this is not Google Sign-In
 * `GoogleAuthHelper` obtains an ID token, which identifies the account and authorises nothing.
 * Reading projects, keys and usage needs a Cloud-scoped access token, so this class asks the
 * Authorization API for exactly [CloudScopes.ALL] and nothing more. The two are deliberately
 * separate, and neither is a substitute for the other.
 *
 * ## Configuration
 * A Web client ID must exist in [R.string.default_web_client_id]. Until it does, [configured] is
 * false and [requestAuthorization] reports [CloudAuthFailure.CONFIGURATION_MISSING] **without
 * touching the authorization provider**, so an unconfigured build says the real thing rather than
 * a misleading failure, and manual API-key use keeps working.
 *
 * ## Credentials
 * The access token is handed to the caller and never stored here, never persisted and never
 * logged. Log lines carry only the classification and the exception's class name — never a
 * message, which a provider could populate with credential material.
 */
class GoogleCloudAuthorizer(private val context: Context) {

    /** Whether this build has a usable OAuth client ID. The shipped placeholder counts as absent. */
    val configured: Boolean
        get() {
            val value = context.getString(R.string.default_web_client_id).trim()
            return value.isNotEmpty() && !value.startsWith(PLACEHOLDER_PREFIX)
        }

    /**
     * Starts an authorization attempt.
     *
     * Never throws: a provider failure must not take down the Settings screen. [onOutcome] runs on
     * the main thread, which is where the caller launches any consent intent from.
     */
    fun requestAuthorization(activity: Activity, onOutcome: (CloudAuthorizationOutcome) -> Unit) {
        if (!configured) {
            VoxoraLog.w(TAG, "Cloud authorization unavailable: no OAuth client configured in this build")
            onOutcome(CloudAuthorizationOutcome.Denied(CloudAuthFailure.CONFIGURATION_MISSING))
            return
        }
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(CloudScopes.ALL)
            .build()
        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result -> onOutcome(interpret(result)) }
            .addOnFailureListener { error -> onOutcome(CloudAuthorizationOutcome.Denied(classify(error))) }
    }

    private fun interpret(result: AuthorizationResult): CloudAuthorizationOutcome {
        if (result.hasResolution()) {
            val pending = result.pendingIntent
            return if (pending == null) {
                VoxoraLog.w(TAG, "Authorization requested consent but returned no intent")
                CloudAuthorizationOutcome.Denied(CloudAuthFailure.UNKNOWN)
            } else {
                CloudAuthorizationOutcome.NeedsConsent(pending)
            }
        }
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            // The scopes were not granted. That is a refusal, not a failure.
            VoxoraLog.w(TAG, "Authorization returned no access token")
            return CloudAuthorizationOutcome.Denied(CloudAuthFailure.PERMISSION_DENIED)
        }
        // The account name is the email address. Only the email leaves this method — never the token.
        return CloudAuthorizationOutcome.Granted(accessToken = token, accountEmail = result.account?.name)
    }

    private fun classify(error: Exception): CloudAuthFailure {
        val status = (error as? ApiException)?.statusCode
        val reason = CloudAuthFailureClassifier.classify(error.javaClass.name, error.message, status)
        // Classification and exception class only — never the message verbatim.
        VoxoraLog.w(TAG, "Cloud authorization failed: ${reason.name} (${error.javaClass.simpleName})")
        return reason
    }

    private companion object {
        const val TAG = "VoxoraCloud"
        const val PLACEHOLDER_PREFIX = "REPLACE_"
    }
}
