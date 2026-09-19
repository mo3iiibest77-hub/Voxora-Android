package com.voxora.app.reader

import com.voxora.core.gemini.ReaderNarrationModes

/**
 * The Reader's display caches, one per narration mode.
 *
 * The reading text answers one question: *what does this segment say, in the selected language
 * and the selected narration style?* Faithful and Fluent are different rewrites of the same
 * source — the instruction Gemini receives differs, so the transcript it returns differs — and a
 * rendering produced for one is not a rendering for the other. Showing a Faithful rendering while
 * Fluent is selected would present text the reader did not ask for, so the two must never share
 * storage.
 *
 * The mode is the **cache's identity, not a key inside it**. A cache can only ever be asked for
 * its own mode's text, so "the other mode's text is on screen" is unrepresentable rather than
 * merely unlikely — the same reasoning [ReaderDisplayText] applies to language.
 *
 * An unknown mode normalizes to [ReaderNarrationModes.DEFAULT], exactly as the narration
 * instruction does, so a stray value cannot create a third cache that no run would ever fill.
 *
 * Pure JVM (no `android.*`) so the scoping rule is unit-testable without Robolectric.
 */
internal class ReaderDisplayModes {

    private val byMode: Map<String, ReaderDisplayText> =
        ReaderNarrationModes.all.associateWith { ReaderDisplayText() }

    /**
     * The cache for [mode], normalized.
     *
     * Never null: every mode in the contract has a cache, and an unrecognised value resolves to
     * [ReaderNarrationModes.DEFAULT] rather than throwing.
     */
    fun forMode(mode: String): ReaderDisplayText = byMode.getValue(ReaderNarrationModes.normalize(mode))

    /** Modes that own a cache, in the contract's order. */
    fun modes(): List<String> = byMode.keys.toList()

    /** Drops every rendering in every mode. Called when a new document replaces the queue. */
    fun clear() = byMode.values.forEach { it.clear() }
}
