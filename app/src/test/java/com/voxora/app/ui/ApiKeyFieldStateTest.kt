package com.voxora.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the Settings API-key control.
 *
 * The reported defect this pins: after saving a key, reopening Settings put the real key back into
 * the text field, so the secret was displayed on every visit. The fix is structural rather than a
 * display rule — [ApiKeyFieldState] has no field that can hold the stored key, so the screen has
 * nothing to prefill with even if someone later forgets the rule.
 *
 * The tests below assert that property directly: the resting state is always empty, a save clears
 * the draft, and the only string the state can hold is what the user is typing right now.
 */
class ApiKeyFieldStateTest {

    // ---- the secret has no path into UI state --------------------------------------------

    /**
     * The load-bearing case. A configured install must be representable without a key anywhere in
     * the state, so reopening Settings shows "configured" and an empty field.
     */
    @Test
    fun aConfiguredInstallCarriesNoKeyText() {
        val state = ApiKeyFieldState.of(configured = true)

        assertTrue(state.showsConfigured)
        assertFalse(state.showsEntry)
        assertEquals("", state.draft)
        assertFalse(state.canSaveDraft)
    }

    @Test
    fun theRestingStateNeverPrefillsTheField() {
        for (configured in listOf(true, false)) {
            assertEquals("", ApiKeyFieldState.of(configured).draft)
            assertFalse(ApiKeyFieldState.of(configured).replacing)
        }
    }

    /** Saving closes the editor and drops the draft, so the secret is not retained in UI state. */
    @Test
    fun savingClearsTheDraftAndReturnsToTheConfiguredRestingState() {
        val typed = ApiKeyFieldState.edit(ApiKeyFieldState.of(configured = false), "AIza-secret")
        assertTrue(typed.canSaveDraft)

        val after = ApiKeyFieldState.saved()

        assertEquals("", after.draft)
        assertEquals(ApiKeyFieldState.of(configured = true), after)
        assertTrue(after.showsConfigured)
        assertFalse(after.showsEntry)
    }

    /** Removing returns to the empty entry field, not to a field holding the removed value. */
    @Test
    fun removingReturnsToAnEmptyEntryField() {
        val after = ApiKeyFieldState.removed()

        assertFalse(after.configured)
        assertTrue(after.showsEntry)
        assertFalse(after.showsConfigured)
        assertEquals("", after.draft)
    }

    // ---- replace / cancel ----------------------------------------------------------------

    @Test
    fun replacingOpensAnEmptyFieldOverAConfiguredKey() {
        val replacing = ApiKeyFieldState.beginReplace(ApiKeyFieldState.of(configured = true))

        assertTrue(replacing.replacing)
        assertTrue(replacing.showsEntry)
        assertFalse(replacing.showsConfigured)
        assertEquals("", replacing.draft)
    }

    /** A draft from an abandoned edit must never reappear when the field is reopened. */
    @Test
    fun reopeningTheFieldDropsAPreviousDraft() {
        val abandoned = ApiKeyFieldState.edit(
            ApiKeyFieldState.beginReplace(ApiKeyFieldState.of(configured = true)),
            "half-typed",
        )
        assertEquals("half-typed", abandoned.draft)

        val reopened = ApiKeyFieldState.beginReplace(ApiKeyFieldState.of(configured = true))
        assertEquals("", reopened.draft)
    }

    @Test
    fun cancellingClosesTheFieldAndDropsTheDraft() {
        val editing = ApiKeyFieldState.edit(
            ApiKeyFieldState.beginReplace(ApiKeyFieldState.of(configured = true)),
            "half-typed",
        )

        val cancelled = ApiKeyFieldState.cancel(editing)

        assertFalse(cancelled.replacing)
        assertEquals("", cancelled.draft)
        assertTrue(cancelled.showsConfigured)
    }

    @Test
    fun cancellingOnAnUnconfiguredInstallReturnsToTheEmptyField() {
        val cancelled = ApiKeyFieldState.cancel(ApiKeyFieldState.of(configured = false))

        assertTrue(cancelled.showsEntry)
        assertEquals("", cancelled.draft)
    }

    // ---- what can be saved ---------------------------------------------------------------

    @Test
    fun aBlankDraftIsNeverSaveable() {
        val empty = ApiKeyFieldState.of(configured = false)
        assertFalse(empty.canSaveDraft)
        assertFalse(ApiKeyFieldState.edit(empty, "").canSaveDraft)
        assertFalse(ApiKeyFieldState.edit(empty, "   ").canSaveDraft)
        assertFalse(ApiKeyFieldState.edit(empty, "\n\t").canSaveDraft)
    }

    @Test
    fun aTypedDraftIsSaveableOnlyWhileTheFieldIsOpen() {
        val typed = ApiKeyFieldState.edit(ApiKeyFieldState.of(configured = false), "AIza")
        assertTrue(typed.canSaveDraft)

        // Once saved, the field is closed, so the draft is neither shown nor saveable again.
        assertFalse(ApiKeyFieldState.saved().canSaveDraft)
    }

    @Test
    fun editingHoldsOnlyWhatWasTyped() {
        val state = ApiKeyFieldState.edit(
            ApiKeyFieldState.beginReplace(ApiKeyFieldState.of(configured = true)),
            "new-key",
        )

        assertEquals("new-key", state.draft)
        // The draft is the user's input, not a recovered stored value: the configured fact is
        // unchanged, so saving can flip back to the configured state without ever holding the key.
        assertTrue(state.configured)
        assertTrue(state.replacing)
        assertTrue(state.showsEntry)
    }
}
