package com.voxora.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contract for the Reader's page model.
 *
 * The Reader presents one chunk at a time and turns pages. These tests pin the two
 * properties that make that safe on a 200-chunk PDF and correct in Persian:
 *
 * - navigation is bounded and single-step, and exactly one chunk is ever the page, so no
 *   amount of turning can render the whole document or skip past its ends;
 * - "next" and "previous" are logical, not physical. The same finger movement means
 *   opposite things in a left-to-right and a right-to-left layout, and each layout maps
 *   its own gesture to the same logical turn, so a Persian reader cannot get reversed
 *   chunk order.
 */
class ReaderPagerTest {

    @Test
    fun nextAndPreviousMoveExactlyOneChunk() {
        assertEquals(3, ReaderPager.target(current = 2, turn = PageTurn.NEXT, total = 10))
        assertEquals(1, ReaderPager.target(current = 2, turn = PageTurn.PREVIOUS, total = 10))
    }

    @Test
    fun turningPastEitherEndDoesNothing() {
        assertNull(ReaderPager.target(current = 9, turn = PageTurn.NEXT, total = 10))
        assertNull(ReaderPager.target(current = 0, turn = PageTurn.PREVIOUS, total = 10))
    }

    @Test
    fun thereIsNothingToTurnWithoutADocument() {
        assertNull(ReaderPager.target(current = 0, turn = PageTurn.NEXT, total = 0))
        assertNull(ReaderPager.target(current = 0, turn = PageTurn.PREVIOUS, total = 0))
    }

    @Test
    fun onlyTheCurrentChunkIsEverThePage() {
        assertEquals(4, ReaderPager.visible(current = 4, total = 10))
        assertNull(ReaderPager.visible(current = 10, total = 10))
        assertNull(ReaderPager.visible(current = -1, total = 10))
        assertNull(ReaderPager.visible(current = 0, total = 0))
    }

    @Test
    fun walkingForwardsVisitsEveryChunkOnceAndStops() {
        val visited = mutableListOf(0)
        var index = 0
        while (true) {
            val next = ReaderPager.target(index, PageTurn.NEXT, total = 5) ?: break
            index = next
            visited.add(index)
        }
        assertEquals(listOf(0, 1, 2, 3, 4), visited)
    }

    @Test
    fun walkingBackwardsVisitsEveryChunkOnceAndStops() {
        val visited = mutableListOf(4)
        var index = 4
        while (true) {
            val previous = ReaderPager.target(index, PageTurn.PREVIOUS, total = 5) ?: break
            index = previous
            visited.add(index)
        }
        assertEquals(listOf(4, 3, 2, 1, 0), visited)
    }

    @Test
    fun aShortDragIsNotAPageTurn() {
        assertNull(ReaderPager.turnFor(deltaX = -20f, rtl = false, threshold = 56f))
        assertNull(ReaderPager.turnFor(deltaX = 20f, rtl = true, threshold = 56f))
        assertNull(ReaderPager.turnFor(deltaX = -100f, rtl = false, threshold = 0f))
    }

    @Test
    fun leftToRightDragsMapToLogicalTurns() {
        assertEquals(PageTurn.NEXT, ReaderPager.turnFor(deltaX = -100f, rtl = false, threshold = 56f))
        assertEquals(PageTurn.PREVIOUS, ReaderPager.turnFor(deltaX = 100f, rtl = false, threshold = 56f))
    }

    @Test
    fun rightToLeftDragsAreMirrored() {
        assertEquals(PageTurn.NEXT, ReaderPager.turnFor(deltaX = 100f, rtl = true, threshold = 56f))
        assertEquals(PageTurn.PREVIOUS, ReaderPager.turnFor(deltaX = -100f, rtl = true, threshold = 56f))
    }

    @Test
    fun aPersianReaderNeverGetsReversedChunkOrder() {
        // Opposite physical gestures, same logical meaning, in their own layout.
        val fromEnglish = ReaderPager.turnFor(deltaX = -120f, rtl = false, threshold = 56f)
        val fromPersian = ReaderPager.turnFor(deltaX = 120f, rtl = true, threshold = 56f)

        assertEquals(PageTurn.NEXT, fromEnglish)
        assertEquals(fromEnglish, fromPersian)
        // And the opposite gesture still goes back, in both layouts.
        assertEquals(
            ReaderPager.turnFor(deltaX = 120f, rtl = false, threshold = 56f),
            ReaderPager.turnFor(deltaX = -120f, rtl = true, threshold = 56f),
        )
    }

    @Test
    fun theArrivingPageComesFromTheSideItWasTurnedTowards() {
        assertEquals(1f, ReaderPager.enterOffset(forward = true, rtl = false))
        assertEquals(-1f, ReaderPager.enterOffset(forward = false, rtl = false))
        assertEquals(-1f, ReaderPager.enterOffset(forward = true, rtl = true))
        assertEquals(1f, ReaderPager.enterOffset(forward = false, rtl = true))
    }
}
