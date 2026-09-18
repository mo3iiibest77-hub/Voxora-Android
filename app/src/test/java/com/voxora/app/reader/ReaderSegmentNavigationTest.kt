package com.voxora.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the Reader's **segment** navigation, and for its separation from chunk
 * navigation.
 *
 * The reported defect this pins: the horizontal swipe moved the whole chunk, so a document
 * with 129 chunks and 8 narration units per chunk jumped `Chunk 1 → Chunk 2` on a swipe
 * instead of `Segment 1 → Segment 2 → … → Segment 8` inside the current chunk.
 *
 * The two concepts are independent by construction here:
 *
 * - [ReaderPager.swipeStep] is the *only* rule the gesture uses, and it is bounded by the
 *   current chunk's unit count. There is no argument that makes it produce a step outside
 *   that range, so a swipe can never leave the chunk — including at the last unit, where it
 *   returns null and the page stays exactly where it is.
 * - chunk navigation is the explicit Previous/Next controls, which call the controller's
 *   `jumpToChunk`; nothing in this model performs it.
 *
 * `ReaderPagerTest` covers the shared bounded-step and direction rules; this file covers
 * what a *swipe* is allowed to mean.
 */
class ReaderSegmentNavigationTest {

    private val threshold = 56f

    /** The segment a swipe lands on, or null when it does nothing. */
    private fun landed(current: Int, deltaX: Float, rtl: Boolean, segmentTotal: Int): Int? =
        ReaderPager.swipeStep(current, deltaX, rtl, threshold, segmentTotal)?.target

    // ---- one segment per swipe ------------------------------------------------------

    @Test
    fun aSwipeMovesExactlyOneSegment() {
        assertEquals(3, landed(current = 2, deltaX = -120f, rtl = false, segmentTotal = 8))
        assertEquals(1, landed(current = 2, deltaX = 120f, rtl = false, segmentTotal = 8))
    }

    @Test
    fun aSwipeReportsTheDirectionItMovedIn() {
        assertEquals(PageTurn.NEXT, ReaderPager.swipeStep(2, -120f, rtl = false, threshold, 8)?.turn)
        assertEquals(PageTurn.PREVIOUS, ReaderPager.swipeStep(2, 120f, rtl = false, threshold, 8)?.turn)
        assertEquals(PageTurn.NEXT, ReaderPager.swipeStep(2, 120f, rtl = true, threshold, 8)?.turn)
    }

    @Test
    fun aShortSwipeIsNotASegmentStep() {
        assertNull(ReaderPager.swipeStep(2, -20f, rtl = false, threshold, 8))
        assertNull(ReaderPager.swipeStep(2, 20f, rtl = true, threshold, 8))
        // A zero or negative threshold is not a threshold, so it can never turn a page.
        assertNull(ReaderPager.swipeStep(2, -500f, rtl = false, 0f, 8))
    }

    @Test
    fun swipingAtTheFirstSegmentDoesNothing() {
        assertNull(ReaderPager.swipeStep(0, 120f, rtl = false, threshold, 8))
    }

    /**
     * The load-bearing test. At the last unit of a chunk a forwards swipe must do nothing:
     * it must not land on unit 8 (out of range) and it must not roll into the next chunk.
     */
    @Test
    fun swipingAtTheLastSegmentDoesNotRollIntoTheNextChunk() {
        assertNull(ReaderPager.swipeStep(7, -120f, rtl = false, threshold, 8))
        assertNull(ReaderPager.swipeStep(7, 120f, rtl = true, threshold, 8))
    }

    /**
     * A swipe is bounded by the chunk, never by the document. A chunk of three units inside
     * a 129-chunk document still stops after the third unit.
     */
    @Test
    fun swipingIsBoundedByTheChunkNotByTheDocument() {
        assertNull(ReaderPager.swipeStep(2, -120f, rtl = false, threshold, 3))
        assertNull(ReaderPager.swipeStep(0, 120f, rtl = false, threshold, 3))
    }

    /**
     * No gesture and no repetition can ever produce an index outside the current chunk, so
     * rapid repeated swipes cannot corrupt the position.
     */
    @Test
    fun noSwipeCanEverProduceAnIndexOutsideTheChunk() {
        val segmentTotal = 8
        val deltas = listOf(-1000f, -120f, -56f, -55f, 0f, 55f, 56f, 120f, 1000f)
        for (rtl in listOf(false, true)) {
            for (current in 0 until segmentTotal) {
                for (delta in deltas) {
                    val step = ReaderPager.swipeStep(current, delta, rtl, threshold, segmentTotal)
                    val target = step?.target
                    assertTrue(
                        "swipe from $current by $delta (rtl=$rtl) produced $target",
                        target == null || target in 0 until segmentTotal,
                    )
                    if (target != null) {
                        // Exactly one step, never a jump.
                        assertEquals(1, kotlin.math.abs(target - current))
                    }
                }
            }
        }
    }

    @Test
    fun walkingTheSwipeForwardsVisitsEverySegmentOnceAndStops() {
        val visited = mutableListOf(0)
        var index = 0
        while (true) {
            val next = landed(index, -120f, rtl = false, segmentTotal = 5) ?: break
            index = next
            visited.add(index)
        }
        assertEquals(listOf(0, 1, 2, 3, 4), visited)
    }

    @Test
    fun walkingTheSwipeBackwardsVisitsEverySegmentOnceAndStops() {
        val visited = mutableListOf(4)
        var index = 4
        while (true) {
            val previous = landed(index, 120f, rtl = false, segmentTotal = 5) ?: break
            index = previous
            visited.add(index)
        }
        assertEquals(listOf(4, 3, 2, 1, 0), visited)
    }

    // ---- direction under RTL --------------------------------------------------------

    @Test
    fun aPersianReaderNeverGetsReversedSegmentOrder() {
        val fromEnglish = landed(current = 2, deltaX = -120f, rtl = false, segmentTotal = 8)
        val fromPersian = landed(current = 2, deltaX = 120f, rtl = true, segmentTotal = 8)
        assertEquals(3, fromEnglish)
        assertEquals(fromEnglish, fromPersian)

        val backEnglish = landed(current = 2, deltaX = 120f, rtl = false, segmentTotal = 8)
        val backPersian = landed(current = 2, deltaX = -120f, rtl = true, segmentTotal = 8)
        assertEquals(1, backEnglish)
        assertEquals(backEnglish, backPersian)
    }

    /**
     * The unit being left must exit towards the side the finger was already moving, which is
     * the opposite of the side the incoming unit enters from. The composable derives the
     * exit direction that way rather than carrying a second table of its own.
     */
    @Test
    fun theLeavingSegmentExitsOppositeTheEnteringSide() {
        // LTR forwards: the incoming unit comes from the right, so the leaving one goes left.
        assertEquals(1f, ReaderPager.enterOffset(forward = true, rtl = false))
        // RTL forwards: mirrored, the incoming unit comes from the left.
        assertEquals(-1f, ReaderPager.enterOffset(forward = true, rtl = true))
        // Backwards is the mirror of forwards in each layout.
        assertEquals(-1f, ReaderPager.enterOffset(forward = false, rtl = false))
        assertEquals(1f, ReaderPager.enterOffset(forward = false, rtl = true))

        // A forwards swipe drags left in LTR and right in RTL, so the exit side (the
        // negation of the entry side) has the same sign as the finger movement.
        assertEquals(-1f, -ReaderPager.enterOffset(true, rtl = false))
        assertEquals(1f, -ReaderPager.enterOffset(true, rtl = true))
    }

    // ---- safe index derivation ------------------------------------------------------

    @Test
    fun theOneBasedSegmentPositionMapsToASafeZeroBasedIndex() {
        assertEquals(0, ReaderPager.segmentIndex(segment = 1, segmentTotal = 8))
        assertEquals(7, ReaderPager.segmentIndex(segment = 8, segmentTotal = 8))
    }

    @Test
    fun anOutOfRangeSegmentPositionHasNoIndex() {
        assertNull(ReaderPager.segmentIndex(segment = 9, segmentTotal = 8))
        assertNull(ReaderPager.segmentIndex(segment = 0, segmentTotal = 8))
        assertNull(ReaderPager.segmentIndex(segment = -1, segmentTotal = 8))
        assertNull(ReaderPager.segmentIndex(segment = 1, segmentTotal = 0))
    }
}
