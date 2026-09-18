package com.voxora.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxora.app.R
import com.voxora.app.auth.CloudEntryPoint
import com.voxora.app.auth.CloudRepository
import com.voxora.app.ui.theme.VoxoraColors
import com.voxora.app.ui.theme.VoxoraTheme
import com.voxora.core.cloud.CloudApiKey
import com.voxora.core.cloud.CloudAuthFailure
import com.voxora.core.cloud.CloudAuthState
import com.voxora.core.cloud.CloudLoadState
import com.voxora.core.cloud.CloudProject
import com.voxora.core.cloud.CloudSelection
import dagger.hilt.android.EntryPointAccessors

/**
 * The Google account → Gemini project → key hierarchy, in the product's own language.
 *
 * The model a reader sees is **sign in with Google → Google account → Gemini / Google AI Studio
 * access → the key**. This is not a Cloud Console: the project level is named for what it actually
 * is to the user — the project a Gemini key belongs to — and the screen links out to AI Studio for
 * the two jobs it cannot do itself (creating a key and reading usage/limits), rather than
 * pretending those live here.
 *
 * This replaced the ID-token sign-in that used to sit here: an ID token identifies the user but
 * authorises no read, so it could never discover a project or a key. Each level of the hierarchy is
 * shown explicitly, and the active key states whether it came from discovery or was pasted by hand —
 * the app never implies a link it cannot verify.
 *
 * The pickers expand inline rather than opening a dialog: a nested list inside the card keeps the
 * hierarchy visible, needs no extra surface, and lays out correctly under RTL.
 */
@Composable
fun CloudAccountCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = rememberCloudRepository(context)
    val auth by repository.auth.collectAsStateWithLifecycle()
    val selection by repository.selection.collectAsStateWithLifecycle()
    val projectLoad by repository.projectLoad.collectAsStateWithLifecycle()
    val keyLoad by repository.keyLoad.collectAsStateWithLifecycle()
    var choosingProject by remember { mutableStateOf(false) }
    var choosingKey by remember { mutableStateOf(false) }

    // After the consent screen returns, ask again: the documented flow collects the access token
    // from a second call once the user has granted the scopes.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        context.findActivity()?.let { activity -> repository.requestAuthorization(activity) {} }
    }

    fun authorize() {
        val activity = context.findActivity() ?: return
        repository.requestAuthorization(activity) { pending ->
            consentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
        }
    }

    CloudAccountContent(
        auth = auth,
        selection = selection,
        projectLoad = projectLoad,
        keyLoad = keyLoad,
        choosingProject = choosingProject,
        choosingKey = choosingKey,
        onAuthorize = ::authorize,
        onSignOut = repository::signOut,
        onToggleProjects = {
            choosingProject = !choosingProject
            if (choosingProject && projectLoad != CloudLoadState.LOADING) repository.refreshProjects()
        },
        onRefreshProjects = repository::refreshProjects,
        onSelectProject = {
            repository.selectProject(it)
            choosingProject = false
        },
        onToggleKeys = {
            choosingKey = !choosingKey
            if (choosingKey && keyLoad != CloudLoadState.LOADING) repository.refreshKeys()
        },
        onRefreshKeys = repository::refreshKeys,
        onSelectKey = {
            repository.selectKey(it)
            choosingKey = false
        },
        onUseManualKey = {
            repository.useManualKey()
            choosingKey = false
        },
        modifier = modifier,
    )
}

@Composable
private fun CloudAccountContent(
    auth: CloudAuthState,
    selection: CloudSelection,
    projectLoad: CloudLoadState,
    keyLoad: CloudLoadState,
    choosingProject: Boolean,
    choosingKey: Boolean,
    onAuthorize: () -> Unit,
    onSignOut: () -> Unit,
    onToggleProjects: () -> Unit,
    onRefreshProjects: () -> Unit,
    onSelectProject: (CloudProject) -> Unit,
    onToggleKeys: () -> Unit,
    onRefreshKeys: () -> Unit,
    onSelectKey: (CloudApiKey) -> Unit,
    onUseManualKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        CloudCard {
            CloudRowHeader(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.settings_cloud_account),
                subtitle = cloudAccountSubtitle(auth),
            )
            when (auth) {
                is CloudAuthState.Authorized -> {
                    Text(
                        text = auth.accountEmail ?: stringResource(R.string.settings_cloud_authorized),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onAuthorize,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_cloud_change_account))
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_sign_out))
                        }
                    }
                }
                is CloudAuthState.NotConfigured -> {
                    CloudNote(
                        text = stringResource(R.string.settings_cloud_not_configured),
                        tone = CloudNoteTone.NEUTRAL,
                    )
                }
                is CloudAuthState.Authorizing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_cloud_authorizing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is CloudAuthState.Failed -> {
                    CloudNote(text = cloudFailureMessage(auth.reason), tone = CloudNoteTone.ERROR)
                    AuthorizeButton(onAuthorize)
                }
                is CloudAuthState.SignedOut -> AuthorizeButton(onAuthorize)
            }
        }

        if (auth is CloudAuthState.Authorized) {
            CloudCard {
                CloudRowHeader(
                    icon = Icons.Filled.Cloud,
                    title = stringResource(R.string.settings_project_section),
                    subtitle = selection.selectedProject?.label
                        ?: stringResource(R.string.settings_project_none),
                )
                selection.selectedProject?.projectNumber?.let { number ->
                    CloudNote(text = stringResource(R.string.settings_project_number, number))
                }
                CloudNote(text = stringResource(R.string.settings_project_help))
                CloudStateNote(projectLoad)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onRefreshProjects,
                        enabled = projectLoad != CloudLoadState.LOADING,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_project_change))
                    }
                }
                if (choosingProject) {
                    selection.projects.forEach { project ->
                        ProjectOption(
                            project = project,
                            selected = project.projectId == selection.selectedProject?.projectId,
                            onSelect = { onSelectProject(project) },
                        )
                    }
                }

                CloudRowHeader(
                    icon = Icons.Filled.Key,
                    title = stringResource(R.string.settings_key_section),
                    subtitle = selection.selectedKey?.label
                        ?: stringResource(R.string.settings_key_none),
                )
                if (selection.isManual) {
                    CloudNote(text = stringResource(R.string.settings_key_manual_note))
                } else {
                    CloudNote(text = stringResource(R.string.settings_key_secret_note))
                }
                // The documented rule, stated where the key is chosen: limits belong to the
                // project, so a second key cannot buy a second quota. Never call this a per-key
                // limit — that is the exact misunderstanding this note exists to prevent.
                CloudNote(text = stringResource(R.string.settings_key_rate_limit_note))
                CloudStateNote(
                    state = keyLoad,
                    loading = R.string.settings_key_loading,
                    empty = R.string.settings_key_empty,
                    denied = R.string.settings_key_denied,
                    network = R.string.settings_key_network,
                    unauthorized = R.string.settings_key_unauthorized,
                    failed = R.string.settings_key_failed,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onToggleKeys,
                        enabled = keyLoad != CloudLoadState.LOADING,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(stringResource(R.string.settings_key_change))
                    }
                    if (!selection.isManual) {
                        OutlinedButton(
                            onClick = onUseManualKey,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(stringResource(R.string.settings_key_use_manual))
                        }
                    }
                }
                if (choosingKey) {
                    selection.keys.forEach { key ->
                        KeyOption(
                            key = key,
                            selected = key.keyId == selection.selectedKey?.keyId,
                            onSelect = { onSelectKey(key) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorizeButton(onAuthorize: () -> Unit) {
    Button(
        onClick = onAuthorize,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(
            text = stringResource(R.string.settings_sign_in),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * States the reason a list is not shown, distinctly for projects and for keys.
 *
 * The resource ids are parameters rather than a `key` flag so the two call sites read the strings
 * that belong to them; an empty project list and an empty key list are different sentences.
 */
@Composable
private fun CloudStateNote(
    state: CloudLoadState,
    loading: Int = R.string.settings_project_loading,
    empty: Int = R.string.settings_project_empty,
    denied: Int = R.string.settings_project_denied,
    network: Int = R.string.settings_project_network,
    unauthorized: Int = R.string.settings_project_unauthorized,
    failed: Int = R.string.settings_project_failed,
) {
    val text = when (state) {
        CloudLoadState.LOADING -> stringResource(loading)
        CloudLoadState.EMPTY -> stringResource(empty)
        CloudLoadState.PERMISSION_DENIED -> stringResource(denied)
        CloudLoadState.NETWORK_UNAVAILABLE -> stringResource(network)
        CloudLoadState.NOT_AUTHORIZED -> stringResource(unauthorized)
        CloudLoadState.FAILED -> stringResource(failed)
        CloudLoadState.IDLE, CloudLoadState.LOADED -> return
    }
    CloudNote(
        text = text,
        tone = if (state == CloudLoadState.FAILED) CloudNoteTone.ERROR else CloudNoteTone.NEUTRAL,
    )
}

@Composable
private fun ProjectOption(
    project: CloudProject,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = project.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The id is a technical identifier: left as a selectable LTR run, never reordered.
                Text(
                    text = project.projectId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.reader_mode_selected),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun KeyOption(
    key: CloudApiKey,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = key.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (key.restricted) {
                    Text(
                        text = stringResource(R.string.settings_key_restricted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.reader_mode_selected),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ---- shared card pieces ------------------------------------------------------------

@Composable
private fun CloudCard(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun CloudRowHeader(
    icon: ImageVector,
    title: String,
    subtitle: String?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private enum class CloudNoteTone { NEUTRAL, ERROR }

@Composable
private fun CloudNote(text: String, tone: CloudNoteTone = CloudNoteTone.NEUTRAL) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (tone == CloudNoteTone.ERROR) {
            VoxoraColors.danger
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun cloudAccountSubtitle(auth: CloudAuthState): String? = when (auth) {
    is CloudAuthState.Authorized -> auth.accountEmail ?: stringResource(R.string.settings_cloud_authorized)
    is CloudAuthState.Authorizing -> stringResource(R.string.settings_cloud_authorizing)
    // The body below already explains the configuration gap; a subtitle would only repeat it.
    is CloudAuthState.NotConfigured -> null
    is CloudAuthState.SignedOut -> stringResource(R.string.settings_sign_in)
    is CloudAuthState.Failed -> cloudFailureMessage(auth.reason)
}

@Composable
private fun cloudFailureMessage(reason: CloudAuthFailure): String = when (reason) {
    CloudAuthFailure.CONFIGURATION_MISSING -> stringResource(R.string.settings_cloud_not_configured)
    CloudAuthFailure.CANCELLED -> stringResource(R.string.settings_cloud_failure_cancelled)
    CloudAuthFailure.PERMISSION_DENIED -> stringResource(R.string.settings_cloud_failure_denied)
    CloudAuthFailure.EXPIRED -> stringResource(R.string.settings_cloud_failure_expired)
    CloudAuthFailure.NO_ACCOUNT,
    CloudAuthFailure.PROVIDER_UNAVAILABLE,
    CloudAuthFailure.NETWORK,
    CloudAuthFailure.UNSUPPORTED,
    CloudAuthFailure.UNKNOWN,
    -> stringResource(R.string.settings_cloud_failure_generic)
}

/** The Activity that hosts this composition, for the consent intent and the sign-in call. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The one [CloudRepository] instance.
 *
 * Shared by Settings and the API usage screen: a per-screen instance would give each its own
 * in-memory grant and its own selection, so the two screens would disagree about the account.
 */
@Composable
internal fun rememberCloudRepository(context: Context): CloudRepository = remember(context) {
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        CloudEntryPoint::class.java,
    ).cloudRepository()
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CloudAccountSignedOutPreview(modifier: Modifier = Modifier) {
    VoxoraTheme {
        Surface(modifier = modifier) {
            CloudAccountContent(
                auth = CloudAuthState.SignedOut,
                selection = CloudSelection(),
                projectLoad = CloudLoadState.IDLE,
                keyLoad = CloudLoadState.IDLE,
                choosingProject = false,
                choosingKey = false,
                onAuthorize = {},
                onSignOut = {},
                onToggleProjects = {},
                onRefreshProjects = {},
                onSelectProject = {},
                onToggleKeys = {},
                onRefreshKeys = {},
                onSelectKey = {},
                onUseManualKey = {},
            )
        }
    }
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CloudAccountAuthorizedPreview(modifier: Modifier = Modifier) {
    VoxoraTheme {
        Surface(modifier = modifier) {
            CloudAccountContent(
                auth = CloudAuthState.Authorized("owner@example.com", null),
                selection = CloudSelection(accountEmail = "owner@example.com")
                    .withProjects(
                        listOf(
                            CloudProject("alpha-123", "111", "Alpha project", "ACTIVE"),
                            CloudProject("beta-456", "222", "Beta project", "ACTIVE"),
                        ),
                    )
                    .selectProject(CloudProject("alpha-123", "111", "Alpha project", "ACTIVE"))
                    .withKeys(
                        listOf(
                            CloudApiKey(
                                resourceName = "projects/alpha-123/locations/global/keys/key-1",
                                keyId = "key-1",
                                displayName = "Reader key",
                                restricted = true,
                            ),
                        ),
                    )
                    .selectKey(
                        CloudApiKey(
                            resourceName = "projects/alpha-123/locations/global/keys/key-1",
                            keyId = "key-1",
                            displayName = "Reader key",
                            restricted = true,
                        ),
                    ),
                projectLoad = CloudLoadState.LOADED,
                keyLoad = CloudLoadState.LOADED,
                choosingProject = true,
                choosingKey = false,
                onAuthorize = {},
                onSignOut = {},
                onToggleProjects = {},
                onRefreshProjects = {},
                onSelectProject = {},
                onToggleKeys = {},
                onRefreshKeys = {},
                onSelectKey = {},
                onUseManualKey = {},
            )
        }
    }
}
