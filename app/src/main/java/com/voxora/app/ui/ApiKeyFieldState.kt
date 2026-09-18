package com.voxora.app.ui

/**
 * What the Settings Gemini API-key control shows.
 *
 * ## The rule this type exists to enforce
 * The stored key is a secret and is **never an input to this type**. The screen may learn *whether*
 * a key is configured — that is all [configured] is — but it never receives the value, so it is
 * incapable of repopulating the field with the saved key. The only string this type can hold is
 * [draft], which is what the user is typing right now and is emptied whenever the field closes.
 *
 * That is deliberately stronger than "remember not to display the key": the secret has no path into
 * the UI state at all, so the defect cannot be reintroduced by a later edit to the composable.
 *
 * Pure Kotlin (no Compose, no `android.*`) so the transitions are unit-testable, in the same spirit
 * as `ReaderPager` and `UsageStatusVisual`.
 */
internal data class ApiKeyFieldState(
    /** Whether a usable key is stored. Never the key itself. */
    val configured: Boolean,
    /** Whether the user has asked to enter a new key over the stored one. */
    val replacing: Boolean,
    /** What the user has typed since the field opened. Empty whenever the field is closed. */
    val draft: String,
) {
    /** True when the secret entry field is on screen. */
    val showsEntry: Boolean get() = replacing || !configured

    /** True when the "configured" status is on screen instead of the entry field. */
    val showsConfigured: Boolean get() = configured && !replacing

    /** A blank draft is not a key, so it can never be saved. */
    val canSaveDraft: Boolean get() = showsEntry && draft.isNotBlank()

    companion object {
        /**
         * The resting state for a known configuration.
         *
         * The draft always starts empty: opening Settings must not prefill anything, which is the
         * whole point — the saved key is not available here to prefill with.
         */
        fun of(configured: Boolean): ApiKeyFieldState =
            ApiKeyFieldState(configured = configured, replacing = false, draft = "")

        /** "Replace": open an empty field. A previous draft is never carried over. */
        fun beginReplace(state: ApiKeyFieldState): ApiKeyFieldState =
            state.copy(replacing = true, draft = "")

        /** "Cancel": close the field and drop whatever was typed. */
        fun cancel(state: ApiKeyFieldState): ApiKeyFieldState =
            state.copy(replacing = false, draft = "")

        /** The user typed. Only what they typed is held, and only while the field is open. */
        fun edit(state: ApiKeyFieldState, value: String): ApiKeyFieldState =
            state.copy(draft = value)

        /**
         * A key was saved: configured, closed, and the draft cleared so the secret is not retained
         * in UI state once it is on disk.
         */
        fun saved(): ApiKeyFieldState =
            ApiKeyFieldState(configured = true, replacing = false, draft = "")

        /** A key was removed: back to the empty entry field. */
        fun removed(): ApiKeyFieldState =
            ApiKeyFieldState(configured = false, replacing = false, draft = "")
    }
}
