package com.voxora.app.reader

/**
 * The Reader's bounded chunk look-ahead, as a pure rule.
 *
 * ## What it decides
 *
 * While chunk N is playing, chunk N+1 is prepared locally so the promotion at the end of N costs
 * nothing. The window is deliberately **one** chunk wide:
 *
 * - each prepared chunk holds a spool that may grow to `ReaderSpool.MAX_BYTES` (~28 MB of PCM),
 *   plus its own Gemini session, so a wider window multiplies both memory and concurrent API
 *   requests for a benefit the reader never sees — the next chunk is always ready long before the
 *   current one ends;
 * - the whole point of the cache is that a chunk which has already been paid for is replayed
 *   rather than re-synthesised, so there is nothing to gain from generating further ahead.
 *
 * ## Why it is separate from the controller
 *
 * The controller is Android-bound and cannot be unit-tested on a plain JVM. Keeping the "which
 * chunk is worth preparing next" decision here means the bound, the end-of-book behaviour and the
 * deduplication rule are all testable, and the controller only has to ask for an index.
 *
 * Pure JVM (no `android.*`).
 */
internal object ReaderPrefetchWindow {

    /** How many chunks ahead of the one playing may be prepared. */
    const val LOOK_AHEAD = 1

    /**
     * The chunk indices worth preparing ahead of [current], in order.
     *
     * Empty at the end of the book, for an empty document, and for a non-positive look-ahead, so a
     * caller never receives an index that does not exist. An index that is already being prepared
     * is not returned: preparing it twice would open a second session for one artifact.
     */
    fun ahead(
        current: Int,
        chunkCount: Int,
        prepared: Set<Int> = emptySet(),
        lookAhead: Int = LOOK_AHEAD,
    ): List<Int> {
        if (chunkCount <= 0 || lookAhead <= 0) return emptyList()
        val start = current + 1
        if (start < 0 || start >= chunkCount) return emptyList()
        val end = minOf(chunkCount, start + lookAhead)
        return (start until end).filterNot { it in prepared }
    }
}
