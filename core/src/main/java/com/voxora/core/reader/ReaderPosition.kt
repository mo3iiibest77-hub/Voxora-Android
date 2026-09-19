package com.voxora.core.reader

/**
 * The rules that turn a persisted position into a playable one.
 *
 * ## Why this is separate from the record
 *
 * A [ReaderBook] stores a chunk index and a segment index, but it does **not** store how many
 * narration units a chunk has: unit counts come from extraction, which happens after the record is
 * read. So the record can only bound the chunk (it knows `chunkCount`) and must leave the segment
 * alone; the bound for the segment exists only in the controller, which has the extracted queue.
 *
 * Keeping that clamp here — as a pure function over two integers — is what makes it testable
 * without a device: the interesting cases are a segment written against a longer extraction, a
 * segment on a chunk that now has no units, and a legacy record that has no segment at all.
 *
 * Pure JVM, no `android.*`.
 */
object ReaderPosition {

    /**
     * The segment to actually resume at, given the persisted value and the real unit count.
     *
     * Clamped rather than rejected, for the same reason the chunk index is: a document re-imported
     * or re-extracted at a different granularity must still open, and the safe answer is the last
     * unit of the chunk the reader was in — never a crash and never a silent jump to the start of
     * the document.
     *
     * A chunk with no units at all resolves to `0`, which the controller reads as "the beginning of
     * this chunk" rather than as an error.
     */
    fun clampSegment(persisted: Int, segmentCount: Int): Int =
        if (segmentCount <= 0) 0 else persisted.coerceIn(0, segmentCount - 1)
}
