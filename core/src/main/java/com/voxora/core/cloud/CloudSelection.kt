package com.voxora.core.cloud

/**
 * Which key Voxora is currently using.
 *
 * These are genuinely different products and the UI must say which one is active:
 * - [GOOGLE_CLOUD] means the key was discovered through the signed-in account's project and is
 *   tied to that account/project relationship;
 * - [MANUAL] means the user pasted a key by hand. Nothing verifies that it belongs to the
 *   signed-in account, so the UI must not imply that it does.
 */
enum class KeyMode { GOOGLE_CLOUD, MANUAL }

/**
 * The account → project → key relationship, and the rules that keep it honest.
 *
 * This is the whole reason the Settings account section exists, so it is modelled explicitly
 * rather than as loose fields. Its defining property is that **stale state is unrepresentable
 * after an account switch**: [withAccount] clears the project, the key list and the selected key
 * whenever the account actually changes, so account B can never be shown account A's project.
 *
 * Every transition is a pure function returning a new value, so the invalidation rules are
 * unit-testable without Android, network or a real Google account.
 */
data class CloudSelection(
    val accountEmail: String? = null,
    val projects: List<CloudProject> = emptyList(),
    val selectedProject: CloudProject? = null,
    val keys: List<CloudApiKey> = emptyList(),
    val selectedKey: CloudApiKey? = null,
    val keyMode: KeyMode = KeyMode.MANUAL,
) {
    /**
     * Switches to [email].
     *
     * When the account is unchanged this is a no-op, so a re-authorization for the same account
     * does not throw away a project the user already picked. When it changes, **everything the
     * previous account owned is dropped**: its projects, its selection, its keys and its active
     * key. Returning to manual mode is deliberate — the discovered key belonged to the old
     * account and must not silently stay active for the new one.
     */
    fun withAccount(email: String?): CloudSelection {
        if (email == accountEmail) return this
        return CloudSelection(accountEmail = email)
    }

    /** Replaces the discovered project list, dropping a selection that is no longer accessible. */
    fun withProjects(projects: List<CloudProject>): CloudSelection {
        val stillThere = selectedProject?.let { selected -> projects.any { it.projectId == selected.projectId } }
        return if (stillThere == true) {
            copy(projects = projects)
        } else {
            // The selected project is not in the new list, so neither it nor any key discovered
            // under it can still be trusted.
            copy(
                projects = projects,
                selectedProject = null,
                keys = emptyList(),
                selectedKey = null,
                keyMode = KeyMode.MANUAL,
            )
        }
    }

    /** Selects a project from the current list. Keys are project-scoped, so they are cleared. */
    fun selectProject(project: CloudProject?): CloudSelection = copy(
        selectedProject = project,
        keys = emptyList(),
        selectedKey = null,
        keyMode = KeyMode.MANUAL,
    )

    /** Replaces the discovered key list, dropping a selection that is no longer present. */
    fun withKeys(keys: List<CloudApiKey>): CloudSelection {
        val stillThere = selectedKey?.let { selected -> keys.any { it.keyId == selected.keyId } }
        return if (stillThere == true) {
            copy(keys = keys)
        } else {
            copy(keys = keys, selectedKey = null, keyMode = KeyMode.MANUAL)
        }
    }

    /** Makes a discovered key the active one. Only valid for a key that is in [keys]. */
    fun selectKey(key: CloudApiKey): CloudSelection {
        val known = keys.any { it.keyId == key.keyId }
        return if (known) copy(selectedKey = key, keyMode = KeyMode.GOOGLE_CLOUD) else this
    }

    /** Falls back to the manually pasted key without discarding the discovery state. */
    fun useManualKey(): CloudSelection = copy(selectedKey = null, keyMode = KeyMode.MANUAL)

    /** Sign-out: nothing about the account or its resources may survive. */
    fun signedOut(): CloudSelection = CloudSelection()

    /** True when the active key is the manually pasted one, so the UI can label the mode honestly. */
    val isManual: Boolean get() = keyMode == KeyMode.MANUAL

    /**
     * True only when the active key was discovered through the current account's selected project.
     *
     * This is the single property the UI should use to decide whether it may imply that the key
     * belongs to the signed-in account. It can never be true for a manual key, which is what keeps
     * the app from claiming a relationship it cannot verify.
     */
    val keyLinkedToAccount: Boolean
        get() = keyMode == KeyMode.GOOGLE_CLOUD &&
            selectedKey != null &&
            selectedProject != null &&
            accountEmail != null
}
