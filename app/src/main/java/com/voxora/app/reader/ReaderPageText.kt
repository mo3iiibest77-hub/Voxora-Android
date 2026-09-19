package com.voxora.app.reader

/**
 * Whether a narration unit of the current page is still waiting for its selected-language
 * rendering.
 *
 * The reading text is the Gemini transcript of each unit, so for a unit Gemini has not
 * reached yet there is no selected-language text to show. What the Reader must never do
 * is *present the document's own language as if it were the selected one* while the chunk
 * is being narrated — that is the "the audio is English but the page is still Persian"
 * complaint.
 *
 * So a unit with no rendering is shown as an explicit, temporary state rather than as
 * settled text, but only while the chunk is actually being narrated. Outside narration —
 * browsing, paused, stopped — the page falls back to the extracted source, because a
 * reader who has not pressed Play still needs to be able to read their document.
 *
 * Pure JVM (no `android.*`) so the rule is unit-testable.
 */
internal object ReaderPageText {

    /** True when [index] has no selected-language rendering and is being narrated now. */
    fun isPreparing(phase: ReaderPhase, pending: Set<Int>, index: Int): Boolean =
        ReaderGates.isNarrating(phase) && index in pending

    /**
     * True when the whole page is still waiting: narration has started but not one unit
     * of this chunk has a rendering yet. The page then says so once, in the header,
     * instead of repeating the temporary state on every unit.
     */
    fun isPreparingWholePage(phase: ReaderPhase, pending: Set<Int>, unitCount: Int): Boolean =
        ReaderGates.isNarrating(phase) && unitCount > 0 && pending.size == unitCount
}
