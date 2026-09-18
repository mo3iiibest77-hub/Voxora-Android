package com.voxora.app.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import com.voxora.core.cloud.CloudApiKey
import com.voxora.core.cloud.CloudAuthFailure
import com.voxora.core.cloud.CloudAuthState
import com.voxora.core.cloud.CloudLoadState
import com.voxora.core.cloud.CloudProject
import com.voxora.core.cloud.CloudProjectUsage
import com.voxora.core.cloud.CloudResult
import com.voxora.core.cloud.CloudSelection
import com.voxora.core.cloud.GoogleCloudDirectory
import com.voxora.core.cloud.GoogleCloudHttpDirectory
import com.voxora.core.usage.UsageUnavailable
import com.voxora.app.util.VoxoraLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the Google Cloud side of Settings: the account grant, the discovered projects and keys,
 * and the selected project's usage.
 *
 * ## What is deliberately not here
 * The **access token lives in this object's memory and nowhere else**. It is never written to
 * DataStore, never logged and never placed in [CloudAuthState], so no UI state can leak it. It is
 * cleared on sign-out, on account switch, and whenever Google answers `401`.
 *
 * ## Why account switching is safe
 * [CloudSelection] owns the invalidation rules, so a new account cannot inherit the previous
 * account's project or key. This class only has to call `withAccount`; it never re-implements the
 * clearing, which is what keeps the rule in one tested place.
 */
@Singleton
class CloudRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authorizer = GoogleCloudAuthorizer(context)
    private val directory: GoogleCloudDirectory = GoogleCloudHttpDirectory()

    private val mutableAuth = MutableStateFlow<CloudAuthState>(
        if (authorizer.configured) CloudAuthState.SignedOut else CloudAuthState.NotConfigured,
    )
    val auth = mutableAuth.asStateFlow()

    private val mutableSelection = MutableStateFlow(CloudSelection())
    val selection = mutableSelection.asStateFlow()

    private val mutableProjectLoad = MutableStateFlow(CloudLoadState.IDLE)
    val projectLoad = mutableProjectLoad.asStateFlow()

    private val mutableKeyLoad = MutableStateFlow(CloudLoadState.IDLE)
    val keyLoad = mutableKeyLoad.asStateFlow()

    private val mutableUsage = MutableStateFlow<CloudProjectUsage?>(null)
    val usage = mutableUsage.asStateFlow()

    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()

    /** In memory only, for the lifetime of the process. Never persisted. */
    @Volatile private var accessToken: String? = null

    val configured: Boolean get() = authorizer.configured

    /**
     * Starts (or restarts) Cloud authorization.
     *
     * [onNeedsConsent] is called with the consent intent when the user has to approve the scopes;
     * the caller launches it and then calls [requestAuthorization] again, which is the documented
     * two-step flow. Switching accounts uses the same entry point — the account picker is part of
     * the consent UI.
     */
    fun requestAuthorization(activity: Activity, onNeedsConsent: (PendingIntent) -> Unit) {
        mutableAuth.value = CloudAuthState.Authorizing
        authorizer.requestAuthorization(activity) { outcome ->
            when (outcome) {
                is CloudAuthorizationOutcome.NeedsConsent -> {
                    // Still authorizing from the user's point of view; the consent screen is open.
                    mutableAuth.value = CloudAuthState.Authorizing
                    onNeedsConsent(outcome.pendingIntent)
                }
                is CloudAuthorizationOutcome.Granted -> onGranted(outcome)
                is CloudAuthorizationOutcome.Denied -> {
                    accessToken = null
                    mutableAuth.value = CloudAuthState.Failed(outcome.reason)
                }
            }
        }
    }

    private fun onGranted(outcome: CloudAuthorizationOutcome.Granted) {
        accessToken = outcome.accessToken
        val email = outcome.accountEmail
        // The account may have changed, so the selection's invalidation rule runs before anything
        // is loaded: the new account starts with no project and no key.
        mutableSelection.value = mutableSelection.value.withAccount(email)
        mutableAuth.value = CloudAuthState.Authorized(accountEmail = email, expiresAtMillis = null)
        VoxoraLog.i(TAG, "Cloud authorization granted")
        refreshProjects()
    }

    /** Re-reads the accessible projects for the current account. */
    fun refreshProjects() {
        val token = accessToken ?: run {
            mutableProjectLoad.value = CloudLoadState.NOT_AUTHORIZED
            return
        }
        mutableBusy.value = true
        scope.launch {
            when (val result = directory.listProjects(token)) {
                is CloudResult.Success -> {
                    mutableSelection.value = mutableSelection.value.withProjects(result.value)
                    mutableProjectLoad.value = CloudLoadState.of(result)
                }
                CloudResult.NotAuthorized -> {
                    onGrantLost()
                    mutableProjectLoad.value = CloudLoadState.NOT_AUTHORIZED
                }
                else -> mutableProjectLoad.value = CloudLoadState.of(result)
            }
            mutableBusy.value = false
        }
    }

    /** Selects a project and immediately loads its key metadata. */
    fun selectProject(project: CloudProject?) {
        mutableSelection.value = mutableSelection.value.selectProject(project)
        mutableUsage.value = null
        if (project != null) refreshKeys() else mutableKeyLoad.value = CloudLoadState.IDLE
    }

    /** Re-reads the key **metadata** for the selected project. */
    fun refreshKeys() {
        val token = accessToken ?: run {
            mutableKeyLoad.value = CloudLoadState.NOT_AUTHORIZED
            return
        }
        val project = mutableSelection.value.selectedProject ?: run {
            mutableKeyLoad.value = CloudLoadState.IDLE
            return
        }
        mutableBusy.value = true
        scope.launch {
            when (val result = directory.listKeys(token, project.projectId)) {
                is CloudResult.Success -> {
                    mutableSelection.value = mutableSelection.value.withKeys(result.value)
                    mutableKeyLoad.value = CloudLoadState.of(result)
                }
                CloudResult.NotAuthorized -> {
                    onGrantLost()
                    mutableKeyLoad.value = CloudLoadState.NOT_AUTHORIZED
                }
                else -> mutableKeyLoad.value = CloudLoadState.of(result)
            }
            mutableBusy.value = false
        }
    }

    /** Makes a discovered key the active one. */
    fun selectKey(key: CloudApiKey) {
        mutableSelection.value = mutableSelection.value.selectKey(key)
    }

    /** Falls back to the manually pasted key without discarding discovery state. */
    fun useManualKey() {
        mutableSelection.value = mutableSelection.value.useManualKey()
    }

    /** Re-reads the selected project's usage from Google. */
    fun refreshUsage() {
        val token = accessToken ?: run {
            mutableUsage.value = mutableSelection.value.selectedProject
                ?.let { CloudProjectUsage.authRequired(it.projectId) }
            return
        }
        val project = mutableSelection.value.selectedProject ?: return
        mutableBusy.value = true
        scope.launch {
            val result = directory.projectUsage(token, project.projectId)
            mutableUsage.value = when (result) {
                is CloudResult.Success -> result.value
                CloudResult.NotAuthorized -> {
                    onGrantLost()
                    CloudProjectUsage.authRequired(project.projectId)
                }
                CloudResult.PermissionDenied ->
                    CloudProjectUsage.unavailable(project.projectId, UsageUnavailable.PERMISSION_DENIED)
                CloudResult.NetworkUnavailable ->
                    CloudProjectUsage.unavailable(project.projectId, UsageUnavailable.NETWORK_ERROR)
                is CloudResult.Failed ->
                    CloudProjectUsage.unavailable(project.projectId, UsageUnavailable.UNSUPPORTED)
            }
            mutableBusy.value = false
        }
    }

    /**
     * Signs out of Google Cloud: drops the in-memory token and every discovered resource.
     *
     * The selection is reset wholesale rather than trimmed, because nothing about the previous
     * account may survive. Manual API-key mode is unaffected — the key is not a Google credential
     * and stays where the user put it.
     */
    fun signOut() {
        accessToken = null
        mutableSelection.value = mutableSelection.value.signedOut()
        mutableAuth.value = if (authorizer.configured) CloudAuthState.SignedOut else CloudAuthState.NotConfigured
        mutableProjectLoad.value = CloudLoadState.IDLE
        mutableKeyLoad.value = CloudLoadState.IDLE
        mutableUsage.value = null
        VoxoraLog.i(TAG, "Cloud account signed out")
    }

    /** A `401` means the grant is gone, so the app must ask for authorization again. */
    private fun onGrantLost() {
        accessToken = null
        mutableSelection.value = mutableSelection.value.signedOut()
        mutableAuth.value = CloudAuthState.Failed(CloudAuthFailure.EXPIRED)
        VoxoraLog.w(TAG, "Cloud grant rejected; re-authorization required")
    }

    private companion object {
        const val TAG = "VoxoraCloud"
    }
}
