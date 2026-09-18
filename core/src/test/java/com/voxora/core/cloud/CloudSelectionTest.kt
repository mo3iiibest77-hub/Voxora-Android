package com.voxora.core.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the account → project → key relationship.
 *
 * The defect this exists to prevent: after switching from account A to account B, the app kept
 * showing account A's selected project and key as though they belonged to B. A stale relationship
 * is not merely unlikely here — [CloudSelection.withAccount] makes it unrepresentable.
 */
class CloudSelectionTest {

    private val projectA = CloudProject(
        projectId = "alpha-123",
        projectNumber = "111",
        displayName = "Alpha",
        lifecycleState = "ACTIVE",
    )
    private val projectB = CloudProject(
        projectId = "beta-456",
        projectNumber = "222",
        displayName = "Beta",
        lifecycleState = "ACTIVE",
    )
    private val keyA = CloudApiKey(
        resourceName = "projects/alpha-123/locations/global/keys/key-1",
        keyId = "key-1",
        displayName = "Reader key",
        restricted = true,
    )
    private val keyA2 = CloudApiKey(
        resourceName = "projects/alpha-123/locations/global/keys/key-2",
        keyId = "key-2",
        displayName = "Dub key",
        restricted = false,
    )

    private fun selectionFor(
        account: String,
        project: CloudProject,
        keys: List<CloudApiKey> = listOf(keyA, keyA2),
        key: CloudApiKey = keyA,
    ): CloudSelection = CloudSelection(accountEmail = account)
        .withProjects(listOf(project))
        .selectProject(project)
        .withKeys(keys)
        .selectKey(key)

    // ---- account switching ----------------------------------------------------------

    @Test
    fun switchingAccountDropsThePreviousProjectsAndKeys() {
        val before = selectionFor("a@example.com", projectA)
        assertTrue(before.keyLinkedToAccount)

        val after = before.withAccount("b@example.com")

        assertEquals("b@example.com", after.accountEmail)
        assertTrue(after.projects.isEmpty())
        assertNull(after.selectedProject)
        assertTrue(after.keys.isEmpty())
        assertNull(after.selectedKey)
        assertFalse(after.keyLinkedToAccount)
    }

    /**
     * The load-bearing test: account B must never be shown account A's project, even if a caller
     * forgets to clear anything itself. The switch is the only input needed.
     */
    @Test
    fun accountBCanNeverSeeAccountAsProject() {
        val after = selectionFor("a@example.com", projectA).withAccount("b@example.com")
        assertNull(after.selectedProject)
        assertNull(after.selectedKey)
        assertEquals(KeyMode.MANUAL, after.keyMode)
    }

    @Test
    fun reauthorizingTheSameAccountKeepsTheSelection() {
        val before = selectionFor("a@example.com", projectA)
        val after = before.withAccount("a@example.com")
        assertEquals(before, after)
    }

    @Test
    fun signingOutClearsEverything() {
        val after = selectionFor("a@example.com", projectA).signedOut()
        assertEquals(CloudSelection(), after)
    }

    // ---- project list refresh -------------------------------------------------------

    @Test
    fun aProjectThatDisappearedFromTheListIsDropped() {
        val after = selectionFor("a@example.com", projectA).withProjects(listOf(projectB))
        assertNull(after.selectedProject)
        assertTrue(after.keys.isEmpty())
        assertNull(after.selectedKey)
    }

    @Test
    fun aProjectStillPresentSurvivesARefresh() {
        val before = selectionFor("a@example.com", projectA)
        val after = before.withProjects(listOf(projectA, projectB))
        assertEquals(projectA, after.selectedProject)
        assertEquals(listOf(keyA, keyA2), after.keys)
        assertEquals(keyA, after.selectedKey)
    }

    @Test
    fun anEmptyProjectListDropsTheSelection() {
        val after = selectionFor("a@example.com", projectA).withProjects(emptyList())
        assertNull(after.selectedProject)
        assertNull(after.selectedKey)
    }

    // ---- project / key selection ----------------------------------------------------

    @Test
    fun selectingAnotherProjectClearsKeysBecauseKeysAreProjectScoped() {
        val after = selectionFor("a@example.com", projectA).selectProject(projectB)
        assertEquals(projectB, after.selectedProject)
        assertTrue(after.keys.isEmpty())
        assertNull(after.selectedKey)
        assertEquals(KeyMode.MANUAL, after.keyMode)
    }

    @Test
    fun aKeyThatDisappearedFromTheListIsDropped() {
        val after = selectionFor("a@example.com", projectA).withKeys(listOf(keyA2))
        assertNull(after.selectedKey)
        assertEquals(KeyMode.MANUAL, after.keyMode)
    }

    @Test
    fun selectingADiscoveredKeyMarksTheGoogleCloudMode() {
        val after = selectionFor("a@example.com", projectA).withKeys(listOf(keyA, keyA2))
        val selected = after.selectKey(keyA2)
        assertEquals(keyA2, selected.selectedKey)
        assertEquals(KeyMode.GOOGLE_CLOUD, selected.keyMode)
        assertTrue(selected.keyLinkedToAccount)
    }

    /** A key that was never listed cannot be made active, so the UI cannot invent one. */
    @Test
    fun anUnknownKeyCannotBeSelected() {
        val before = selectionFor("a@example.com", projectA)
        val after = before.selectKey(
            CloudApiKey(
                resourceName = "projects/alpha-123/locations/global/keys/ghost",
                keyId = "ghost",
                displayName = "Ghost",
                restricted = false,
            ),
        )
        assertEquals(before, after)
    }

    // ---- manual fallback ------------------------------------------------------------

    @Test
    fun manualModeIsAvailableAndNeverClaimsALink() {
        val after = selectionFor("a@example.com", projectA).useManualKey()
        assertEquals(KeyMode.MANUAL, after.keyMode)
        assertNull(after.selectedKey)
        assertFalse(after.keyLinkedToAccount)
        // The discovery state is preserved, so switching back is one tap.
        assertEquals(projectA, after.selectedProject)
        assertEquals(listOf(keyA, keyA2), after.keys)
    }

    @Test
    fun aFreshSelectionStartsInManualModeWithNoIdentity() {
        val fresh = CloudSelection()
        assertNull(fresh.accountEmail)
        assertTrue(fresh.isManual)
        assertFalse(fresh.keyLinkedToAccount)
    }

    /**
     * The link is only ever asserted when all three parts are present and the key came from
     * discovery. Signing in alone must not make a manually pasted key look linked.
     */
    @Test
    fun signingInAloneDoesNotLinkAManualKey() {
        val manualWithAccount = CloudSelection(accountEmail = "a@example.com")
            .withProjects(listOf(projectA))
            .selectProject(projectA)
        assertFalse(manualWithAccount.keyLinkedToAccount)
    }
}
