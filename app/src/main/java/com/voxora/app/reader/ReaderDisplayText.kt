package com.voxora.app.reader

/**
 * Selected-language reading text for the Reader.
 *
 * The canonical source is the extracted document held by [ChunkQueue]; that text is
 * never mutated. This cache holds the selected-language rendering of individual
 * narration units, which the Reader derives from the Gemini transcript of that unit —
 * the Reader Gemini path is the only translation mechanism in the product, so no
 * second backend, translation API or device text-to-speech is involved.
 *
 * Every entry is keyed by **language** as well as chunk and segment, so a language
 * change can never serve text produced for a different language and two languages can
 * never share an entry. Units that have not been narrated in the selected language yet
 * have no entry; the Reader then falls back to the extracted source for that unit.
 *
 * Pure JVM on purpose: no `android.*` APIs, so the language contract stays unit-testable
 * without Robolectric.
 */
internal class ReaderDisplayText {

    private val byLanguage = mutableMapOf<String, MutableMap<Int, MutableMap<Int, String>>>()

    /**
     * Records the selected-language text of one narration unit.
     *
     * Blank text is rejected rather than stored, so an empty transcript can never
     * overwrite a usable rendering or hide the extracted source behind an empty string.
     *
     * @return true when the text was stored.
     */
    fun record(language: String, chunk: Int, segment: Int, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        byLanguage.getOrPut(language) { mutableMapOf() }
            .getOrPut(chunk) { mutableMapOf() }[segment] = trimmed
        return true
    }

    /** Selected-language text of one unit, or null when it has not been rendered yet. */
    fun text(language: String, chunk: Int, segment: Int): String? =
        byLanguage[language]?.get(chunk)?.get(segment)

    /** Units of [chunk] already rendered in [language], as segment index -> text. */
    fun chunk(language: String, chunk: Int): Map<Int, String> =
        byLanguage[language]?.get(chunk)?.toMap().orEmpty()

    /**
     * Reading text of [units] (the extracted source of one chunk) in [language].
     *
     * Each unit shows its rendering for [language] when one exists, and the extracted
     * source for that unit otherwise, so the list always has the same length, order and
     * boundaries as the canonical chunk and never mixes two languages within a rendered
     * unit. This is the exact mapping the Reader publishes to the UI.
     */
    fun readingText(language: String, chunk: Int, units: List<String>): List<String> =
        units.mapIndexed { index, source -> text(language, chunk, index) ?: source }

    /**
     * Whether a rendering just recorded for [chunk] in [language] is part of what the
     * Reader is showing right now, and therefore has to be republished immediately.
     *
     * Production runs ahead of playback: a producer renders whole chunks before they are
     * audible, and the reader may switch language mid-run. Republishing on every record
     * would pull the UI onto a prefetched chunk or onto a language the reader has left.
     * Requiring both to match keeps the visible state owned by the current position.
     *
     * Pure JVM so the refresh rule is unit-testable without Android.
     */
    fun shouldRepublish(
        language: String,
        chunk: Int,
        displayedLanguage: String,
        displayedChunk: Int,
    ): Boolean = language == displayedLanguage && chunk == displayedChunk

    /** Languages that currently hold at least one rendering. */
    fun languages(): Set<String> = byLanguage.keys.toSet()

    /** Total number of rendered units stored for [language]. */
    fun count(language: String): Int =
        byLanguage[language]?.values?.sumOf { it.size } ?: 0

    /** Drops every rendering. Called when a new document replaces the queue. */
    fun clear() = byLanguage.clear()
}
