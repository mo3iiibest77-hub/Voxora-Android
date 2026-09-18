package com.voxora.app.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.voxora.app.R
import com.voxora.app.util.VoxoraLog
import com.voxora.core.prefs.UserPrefs
import kotlinx.coroutines.flow.first

/**
 * Google Sign-In via Credential Manager.
 *
 * ## What this is, and what it is not
 * This is a **local identity layer**: it obtains a Google ID token credential, keeps the
 * account's email and display name so the app can greet the user, and can clear the credential
 * state on sign-out. It is **not** a backend account system, and it does not link a Google
 * account to the configured Gemini API key — an API key carries no owner information this app
 * can verify. Nothing in the UI may imply that relationship.
 *
 * ## Configuration
 * A Web client ID must be supplied in [R.string.default_web_client_id] from Google Cloud
 * Console. Until it is, [configured] is false and [signIn] returns
 * [AuthFailure.CONFIGURATION_MISSING] without touching the credential provider — so an
 * unconfigured build reports the real problem instead of a misleading failure, and guest use
 * with an API key keeps working.
 *
 * ## Secrets
 * The ID token itself is never read into a variable, never persisted and never logged. Only the
 * email and display name reach [UserPrefs]. Log lines carry the failure classification and the
 * exception's class name, never a credential or a token.
 */
class GoogleAuthHelper(
    private val context: Context,
    private val prefs: UserPrefs,
) {
    private val credentialManager = CredentialManager.create(context)

    /**
     * Whether this build has a usable Web client ID.
     *
     * The shipped placeholder counts as absent, so a build that was never configured behaves
     * exactly like one with no value at all.
     */
    val configured: Boolean
        get() {
            val value = context.getString(R.string.default_web_client_id).trim()
            return value.isNotEmpty() && !value.startsWith(PLACEHOLDER_PREFIX)
        }

    /** Restores the last known account state from local storage. */
    suspend fun currentState(): AuthUiState {
        if (!prefs.signedIn.first()) return AuthUiState.SignedOut
        val email = prefs.userEmail.first()
        // A signed-in flag with no email is not a usable account; treat it as signed out
        // rather than rendering a blank identity card.
        if (email.isBlank()) return AuthUiState.SignedOut
        return AuthUiState.SignedIn(email = email, displayName = prefs.displayName.first())
    }

    /**
     * Launches Google sign-in.
     *
     * Returns [AuthUiState.SignedIn] on success or [AuthUiState.Failed] with a classified
     * reason. It never throws: a credential failure must not take down the Settings screen.
     */
    suspend fun signIn(): AuthUiState {
        if (!configured) {
            VoxoraLog.w(TAG, "Sign-in unavailable: no Web client ID configured in this build")
            return AuthUiState.Failed(AuthFailure.CONFIGURATION_MISSING)
        }
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(context.getString(R.string.default_web_client_id).trim())
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val credential = credentialManager.getCredential(context, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                val email = google.id
                if (email.isBlank()) {
                    VoxoraLog.w(TAG, "Sign-in returned a credential with no account identifier")
                    return AuthUiState.Failed(AuthFailure.UNSUPPORTED_CREDENTIAL)
                }
                val name = google.displayName.orEmpty().ifBlank { email.substringBefore("@") }
                prefs.setAccount(email = email, name = name, signedIn = true)
                VoxoraLog.i(TAG, "Sign-in succeeded")
                AuthUiState.SignedIn(email = email, displayName = name)
            } else {
                VoxoraLog.w(TAG, "Sign-in returned an unsupported credential type")
                AuthUiState.Failed(AuthFailure.UNSUPPORTED_CREDENTIAL)
            }
        } catch (e: Exception) {
            val reason = AuthFailureClassifier.classify(e.javaClass.name, e.message)
            // The classification and the exception class only — never the message verbatim,
            // which a provider could populate with credential material.
            VoxoraLog.w(TAG, "Sign-in failed: ${reason.name} (${e.javaClass.simpleName})")
            AuthUiState.Failed(reason)
        }
    }

    /**
     * Signs out: clears the provider's credential state, then the local account record.
     *
     * The local record is cleared **even when** the provider call fails, because leaving the app
     * showing a signed-in account the user asked to remove would be worse than a failed cleanup.
     * Returns [AuthUiState.SignedOut] unconditionally, so the UI can never be left in a stale
     * signed-in state.
     */
    suspend fun signOut(): AuthUiState {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            VoxoraLog.w(TAG, "Credential state clear failed: ${e.javaClass.simpleName}")
        }
        prefs.clearAccount()
        VoxoraLog.i(TAG, "Signed out")
        return AuthUiState.SignedOut
    }

    private companion object {
        const val TAG = "VoxoraAuth"
        const val PLACEHOLDER_PREFIX = "REPLACE_"
    }
}
