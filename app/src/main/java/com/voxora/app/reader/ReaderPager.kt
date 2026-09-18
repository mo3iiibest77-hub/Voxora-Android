package com.voxora.app.reader

import kotlin.math.abs

/** A page turn the user asked for. */
internal enum class PageTurn { NEXT, PREVIOUS }

/**
 * The Reader's page model: one chunk on screen, turned like a page.
 *
 * The document is never rendered as one long list. Exactly one chunk is the reading
 * surface — [visible] is the whole set of chunks the UI may compose — and turning a page
 * only ever moves one step and only through the controller's existing `jumpToChunk`, so
 * there is no second navigation state machine and no unbounded work for a 200-chunk PDF.
 *
 * **Direction is logical, not physical.** In a left-to-right layout the next page sits to
 * the right, so dragging the page leftwards advances. In a right-to-left layout (Persian,
 * Arabic) the next page sits to the left, so the same gesture is mirrored. [turnFor]
 * performs that mapping from the raw drag delta, and [enterOffset] gives the side the
 * incoming page slides in from, so a Persian reader can never get reversed chunk order.
 *
 * Pure JVM (no `android.*`) so navigation and RTL behaviour are unit-testable.
 */
internal object ReaderPager {

    /**
     * The chunk a turn would land on, or null when the turn would leave the document.
     *
     * Returning null rather than clamping is deliberate: a page turn that cannot happen
     * must do nothing at all, so the page never appears to move and then snap back.
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
     * The chunk the page surface renders, or null when there is nothing to show.
     *
     * This is the entire set of chunks the UI is allowed to compose for the document.
     */
    fun visible(current: Int, total: Int): Int? = current.takeIf { it in 0 until total }

    /**
     * Maps a horizontal drag to the page turn it means, or null when the drag is too
     * small to count as a turn.
     */
    fun turnFor(deltaX: Float, rtl: Boolean, threshold: Float): PageTurn? {
        if (threshold <= 0f) return null
        if (abs(deltaX) < threshold) return null
        val forwards = if (rtl) deltaX > 0f else deltaX < 0f
        return if (forwards) PageTurn.NEXT else PageTurn.PREVIOUS
    }

    /**
     * Unit direction the incoming page slides in from, in the current layout direction:
     * `1f` means it enters from the trailing edge, `-1f` from the leading edge.
     */
    fun enterOffset(forward: Boolean, rtl: Boolean): Float {
        val leftToRight = if (forward) 1f else -1f
        return if (rtl) -leftToRight else leftToRight
    }
}
