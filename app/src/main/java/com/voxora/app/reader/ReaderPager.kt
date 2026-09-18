package com.voxora.app.reader

import kotlin.math.abs

/** One bounded step in a direction, requested by a gesture or by an explicit control. */
internal enum class PageTurn { NEXT, PREVIOUS }

/** The segment a released swipe lands on, together with the direction it moved in. */
internal data class SegmentStep(val turn: PageTurn, val target: Int)

/**
 * The Reader's position model: one narration unit on screen at a time, stepped one unit.
 *
 * The Reader has **two independent navigation concepts**, and this model exists to keep
 * them from being conflated:
 *
 * - **chunk navigation** is the explicit Previous/Next controls. It moves the document
 *   position by one chunk and is the only thing allowed to cross a chunk boundary.
 * - **segment navigation** is the horizontal swipe. It moves by exactly one narration
 *   unit *inside the current chunk*, so a document with 129 chunks and 8 units each is
 *   swiped unit by unit, never chunk by chunk.
 *
 * [swipeStep] is the whole swipe rule and it can only ever produce a **segment** step:
 * it is bounded by the current chunk's unit count, so no gesture can carry the reader into
 * another chunk. [target] is the bounded single-step rule both concepts share; it returns
 * `null` at either end instead of wrapping or clamping, because a step that cannot happen
 * must do nothing at all rather than move and snap back.
 *
 * **Direction is logical, not physical.** In a left-to-right layout the next unit sits to
 * the right, so dragging leftwards advances. In a right-to-left layout (Persian, Arabic)
 * the next unit sits to the left, so the same gesture is mirrored. [turnFor] performs that
 * mapping and [enterOffset] gives the side the incoming unit slides in from, so a Persian
 * reader can never get reversed order.
 *
 * Pure JVM (no `android.*`) so navigation, the swipe rule and RTL behaviour are all
 * unit-testable.
 */
internal object ReaderPager {

    /**
     * The index one step would land on, or null when the step would leave [0, total).
     *
     * Shared by chunk and segment navigation; the caller decides which range it is bounded
     * by. Returning null rather than clamping is deliberate — see the type comment.
     */
    fun target(current: Int, turn: PageTurn, total: Int): Int? {
        if (total <= 0) return null
        val candidate = when (turn) {
            PageTurn.NEXT -> current + 1
            PageTurn.PREVIOUS -> current - 1
        }
        return candidate.takeIf { it in 0 until total }
    }

    /**
     * What a released horizontal swipe does: the segment step it lands on, or null when the
     * drag was too short or the reader is already at the first/last unit of this chunk.
     *
     * Bounded by [segmentTotal] on purpose. A swipe at the last unit returns null and the
     * page stays put; it must never roll into the next chunk, because that is chunk
     * navigation and belongs to the explicit controls.
     */
    fun swipeStep(
        current: Int,
        deltaX: Float,
        rtl: Boolean,
        threshold: Float,
        segmentTotal: Int,
    ): SegmentStep? {
        val turn = turnFor(deltaX, rtl, threshold) ?: return null
        val target = target(current, turn, segmentTotal) ?: return null
        return SegmentStep(turn, target)
    }

    /**
     * Zero-based index of the unit currently on the page, or null when [segment] (the
     * one-based value carried by [ReaderState]) is outside [segmentTotal].
     *
     * Deriving the index here instead of in the composable is what keeps an out-of-range
     * state from being turned into an invalid list access.
     */
    fun segmentIndex(segment: Int, segmentTotal: Int): Int? =
        (segment - 1).takeIf { it in 0 until segmentTotal }

    /**
     * Maps a horizontal drag to the step it means, or null when the drag is too small to
     * count as a step.
     */
    fun turnFor(deltaX: Float, rtl: Boolean, threshold: Float): PageTurn? {
        if (threshold <= 0f) return null
        if (abs(deltaX) < threshold) return null
        val forwards = if (rtl) deltaX > 0f else deltaX < 0f
        return if (forwards) PageTurn.NEXT else PageTurn.PREVIOUS
    }

    /**
     * Unit direction the incoming unit slides in from, in the current layout direction:
     * `1f` means it enters from the trailing edge, `-1f` from the leading edge.
     */
    fun enterOffset(forward: Boolean, rtl: Boolean): Float {
        val leftToRight = if (forward) 1f else -1f
        return if (rtl) -leftToRight else leftToRight
    }
}
