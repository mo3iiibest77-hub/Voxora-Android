package com.voxora.app.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.voxora.app.R
import com.voxora.core.prefs.UserPrefs

/**
 * Google Sign-In via Credential Manager.
 *
 * Set a Web client ID in [R.string.default_web_client_id] (from Google Cloud Console)
 * for production. If empty, sign-in returns a clear configuration error — guest mode still works.
 */
class GoogleAuthHelper(
    private val context: Context,
    private val prefs: UserPrefs,
) {
    private val credentialManager = CredentialManager.create(context)

    data class Result(
        val ok: Boolean,
        val email: String = "",
        val name: String = "",
        val message: String = "",
    )

    suspend fun signIn(): Result {
        val webClientId = context.getString(R.string.default_web_client_id).trim()
        if (webClientId.isEmpty() || webClientId.startsWith("REPLACE_")) {
            return Result(
                ok = false,
                message = context.getString(R.string.auth_need_client_id),
            )
        }
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val response = credentialManager.getCredential(context, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                val email = google.id
                val name = google.displayName.orEmpty().ifBlank { email.substringBefore("@") }
                prefs.setAccount(email = email, name = name, signedIn = true)
                Result(ok = true, email = email, name = name)
            } else {
                Result(ok = false, message = context.getString(R.string.auth_failed))
            }
        } catch (e: GetCredentialException) {
            Log.w(TAG, "signIn failed", e)
            Result(ok = false, message = e.message ?: context.getString(R.string.auth_failed))
        } catch (e: Exception) {
            Log.w(TAG, "signIn failed", e)
            Result(ok = false, message = e.message ?: context.getString(R.string.auth_failed))
        }
    }

    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "clearCredentialState", e)
        }
        prefs.clearAccount()
    }

    companion object {
        private const val TAG = "VoxoraAuth"
    }
}
