package com.voxora.core.reader

/**
 * Where an imported Reader document came from.
 *
 * [id] is the persisted value. It is a separate field from the enum name so a rename can never
 * silently invalidate a stored library.
 */
enum class ReaderSourceType(val id: String) {
    PDF("pdf"),
    TXT("txt"),
    ;

    companion object {
        fun normalize(id: String?): ReaderSourceType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: TXT

        fun fromFileName(name: String): ReaderSourceType =
            if (name.substringAfterLast('.', "").equals("pdf", ignoreCase = true)) PDF else TXT
    }
}

/**
 * The **persisted** reading state of a book.
 *
 * This is deliberately coarse. It records what is still true after the process has died, which is
 * why there is no `PLAYING`: a killed process is not playing anything, and writing "playing" to
 * disk would be a claim the app cannot honour on the next launch. The live transport state is
 * `ReaderPhase` (CONNECTING / SPEAKING / PAUSED / …) and stays in memory where it belongs.
 *
 * - [NOT_STARTED] — imported, never narrated.
 * - [IN_PROGRESS] — has been narrated at least once; [ReaderBook.currentChunk] is where to resume.
 * - [COMPLETED] — narration reached the end of the document.
 */
enum class ReaderBookState(val id: String) {
    NOT_STARTED("not_started"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),

    /**
     * The record outlived the copy of its document.
     *
     * This is a real, recoverable state rather than a reason to delete the book: the reader's
     * progress and cached Book Intelligence are still valuable, and the file may reappear (a
     * restored backup, a storage permission). It is persisted so the library stops offering a
     * Continue action that cannot work, and so the Reader does not re-attempt extraction on every
     * open.
     */
    UNAVAILABLE("unavailable"),
    ;

    companion object {
        fun normalize(id: String?): ReaderBookState =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NOT_STARTED
    }
}

/**
 * One imported Reader book/document, as it is persisted.
 *
 * ## What is stored, and what is deliberately not
 *
 * The record holds **metadata and position**, never the document's text. The document itself is
 * copied into app-controlled storage at import time and referenced by [localPath], so reopening a
 * book re-extracts it from a file Voxora owns rather than from a provider `Uri` that may no longer
 * resolve. That is also why chunk indices stay valid across restarts: extraction is deterministic,
 * so the same file always produces the same chunks.
 *
 * The narration voice and the output language are **not** here. They are global Reader preferences
 * (`UserPrefs`), because they describe how the product sounds rather than what the book is; copying
 * them into every record would create two sources of truth for one decision.
 *
 * ## Progress
 *
 * [currentChunk] is a zero-based index into the extracted chunks — the persisted resume point, and
 * the only position the Reader promises. PCM offsets are deliberately not persisted: they are not
 * stable across re-extraction, and a chunk is the granularity the user actually perceives.
 */
data class ReaderBook(
    /** Stable local identity. Generated once at import and never reused. */
    val id: String,
    /** Absolute path of the copy Voxora owns, inside app storage. */
    val localPath: String,
    /** Display title: the discovered title when one was matched, otherwise the file name. */
    val title: String,
    val sourceType: ReaderSourceType,
    val chunkCount: Int,
    /** Zero-based index of the chunk to resume at. */
    val currentChunk: Int,
    val state: ReaderBookState,
    /** When the document was imported, in wall-clock epoch millis. */
    val importedAt: Long,
    /** When the reader was last in this book, in wall-clock epoch millis. */
    val lastReadAt: Long,
    /** Cached bibliographic data, or null when none has been retrieved. */
    val metadata: BookMetadata?,
    /** Where the metadata lookup stands, so the UI never has to guess. */
    val lookup: MetadataLookupState,
    /** The signals identification was attempted with. Kept so a refresh can reuse them offline. */
    val signals: BookSignals?,
    /**
     * AI-generated overviews of this book, at most one per Reader output language.
     *
     * Cached with the record so the Book Intelligence section reads in the reader's language while
     * offline, and so switching languages back and forth never regenerates what was already paid
     * for. Empty until a generation has succeeded; a generation that fails leaves it untouched.
     */
    val overviews: List<BookIntelOverview> = emptyList(),
) {
    /** True once narration has reached the end of the document. */
    val isCompleted: Boolean get() = state == ReaderBookState.COMPLETED

    /** True when the record's document copy is gone, so the book cannot be opened. */
    val isUnavailable: Boolean get() = state == ReaderBookState.UNAVAILABLE

    /**
     * Reading progress in `0f..1f`, derived from the persisted chunk.
     *
     * `currentChunk` is zero-based and names the chunk the reader is *on*, so a 200-chunk book
     * sitting on chunk index 72 (the 73rd chunk) reports 73/200 — the position the UI shows as
     * "Chunk 73 of 200". A completed book reports exactly `1f` rather than whatever the last index
     * happened to be.
     */
    val progressFraction: Float
        get() = when {
            isCompleted -> 1f
            chunkCount <= 0 -> 0f
            else -> ((currentChunk + 1).toFloat() / chunkCount).coerceIn(0f, 1f)
        }

    /** One-based chunk number for display, or 0 when there is nothing to show. */
    val displayChunk: Int get() = if (chunkCount <= 0) 0 else currentChunk.coerceIn(0, chunkCount - 1) + 1

    /** True when there is a position worth resuming from. */
    val hasResumePoint: Boolean get() = state != ReaderBookState.NOT_STARTED && !isUnavailable && chunkCount > 0

    /**
     * Moves the persisted position.
     *
     * A move on a completed book is ignored: completion is a fact about the document, and a stale
     * in-flight save must not silently un-complete it. Every other move records [IN_PROGRESS] and
     * stamps [lastReadAt], because "the reader was here" is exactly what recency means.
     */
    fun withPosition(chunk: Int, atMillis: Long): ReaderBook {
        if (isCompleted) return this
        val bounded = chunk.coerceIn(0, maxOf(0, chunkCount - 1))
        return copy(
            currentChunk = bounded,
            state = ReaderBookState.IN_PROGRESS,
            lastReadAt = maxOf(lastReadAt, atMillis),
        )
    }

    /** Records that the reader was in this book, without claiming a position. */
    fun touched(atMillis: Long): ReaderBook = copy(lastReadAt = maxOf(lastReadAt, atMillis))

    /**
     * Restarts a finished book.
     *
     * A completed record refuses ordinary position moves — completion is a fact about the document.
     * Hearing the book again is a different act, and it is the one case that clears completion,
     * because the reader has deliberately gone back to the beginning.
     */
    fun reopened(atMillis: Long): ReaderBook = copy(
        currentChunk = 0,
        state = ReaderBookState.IN_PROGRESS,
        lastReadAt = maxOf(lastReadAt, atMillis),
    )

    /** Records that narration reached the end of the document. */
    fun completed(atMillis: Long): ReaderBook = copy(
        state = ReaderBookState.COMPLETED,
        lastReadAt = maxOf(lastReadAt, atMillis),
    )

    /** Attaches cached bibliographic data, and the title it identified when it has one. */
    fun withMetadata(metadata: BookMetadata): ReaderBook = copy(
        metadata = metadata,
        lookup = MetadataLookupState.FOUND,
        // A matched title is a better display title than a file name, but only when it is real.
        title = metadata.title.trim().ifEmpty { title },
    )

    /** Records a lookup that produced no confident match, or could not run at all. */
    fun withLookup(state: MetadataLookupState): ReaderBook = copy(lookup = state)

    /** The cached AI-generated overview for [language], or null when none has been generated. */
    fun overviewFor(language: String): BookIntelOverview? =
        overviews.firstOrNull { it.language.equals(language, ignoreCase = true) }

    /**
     * Stores [overview], replacing any existing text for the same language.
     *
     * The cache is pruned so a reader who tries many languages cannot grow one record without
     * bound; see [BookIntelOverviewPrompt.prune].
     */
    fun withOverview(overview: BookIntelOverview): ReaderBook {
        val others = overviews.filterNot { it.language.equals(overview.language, ignoreCase = true) }
        return copy(overviews = BookIntelOverviewPrompt.prune(others + overview))
    }

    /**
     * Records that the document copy could not be found.
     *
     * Deliberately keeps everything else — the saved position, the cached Book Intelligence, the
     * title. Deleting the record would throw away the reader's progress over a file that may come
     * back, and it would make the failure indistinguishable from a book that was never imported.
     */
    fun unavailable(): ReaderBook =
        if (isUnavailable) this else copy(state = ReaderBookState.UNAVAILABLE)
}
