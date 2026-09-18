package com.voxora.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the PDF ordering defect.
 *
 * PDFBox hands `PDFTextStripper` glyphs in content-stream order. PDFs are free to
 * paint a page in any sequence, so text could be read back scrambled ("Third /
 * Second / First" for a page whose visible order is "First / Second / Third").
 * [PdfReadingOrder] rebuilds the order from geometry; these tests pin that
 * behaviour, including the multi-column case that a naive positional sort gets
 * wrong, and the full-width running header/footer case that used to erase the
 * column gutter for the whole page.
 *
 * Coordinates follow PDFBox: `y` grows downwards, so the top of the page has the
 * smallest `y` and reading order is increasing `y`.
 */
class PdfReadingOrderTest {

    private data class Word(
        val text: String,
        val x: Float,
        val y: Float,
        val height: Float = 12f,
    ) {
        val width: Float get() = text.length * 6f
    }

    private fun order(words: List<Word>): List<String> {
        val fragments = words.map { PdfReadingOrder.Fragment(it.x, it.y, it.width, it.height) }
        return PdfReadingOrder.order(fragments).map { words[it].text }
    }

    private fun Word.fragment() = PdfReadingOrder.Fragment(x, y, width, height)

    @Test
    fun glyphsEmittedOutOfReadingOrderAreRebuiltIntoReadingOrder() {
        // The content stream paints the page bottom line first.
        val words = listOf(
            Word("Third", 72f, 140f),
            Word("Second", 72f, 120f),
            Word("First", 72f, 100f),
        )
        assertEquals(listOf("First", "Second", "Third"), order(words))
    }

    @Test
    fun aPageAlreadyInReadingOrderIsLeftAlone() {
        val words = listOf(
            Word("First", 72f, 100f),
            Word("Second", 72f, 120f),
            Word("Third", 72f, 140f),
        )
        assertEquals(listOf("First", "Second", "Third"), order(words))
    }

    @Test
    fun withinLineFragmentsAreOrderedLeftToRight() {
        val words = listOf(
            Word("beta", 200f, 100f),
            Word("alpha", 72f, 100f),
            Word("gamma", 320f, 100f),
        )
        assertEquals(listOf("alpha", "beta", "gamma"), order(words))
    }

    @Test
    fun baselineJitterDoesNotSplitOneVisualLine() {
        // Same line, sub-pixel baseline differences and no vertical order to speak of.
        val words = listOf(
            Word("one", 72f, 100.4f),
            Word("two", 160f, 99.6f),
            Word("three", 248f, 100.1f),
        )
        assertEquals(listOf("one", "two", "three"), order(words))
    }

    @Test
    fun linesAreSeparatedByTheirVerticalGaps() {
        val words = listOf(
            Word("second line", 72f, 130f),
            Word("first line", 72f, 100f),
            Word("third line", 72f, 160f),
        )
        assertEquals(listOf("first line", "second line", "third line"), order(words))
    }

    @Test
    fun rowMajorMultiColumnPagesAreReadColumnByColumn() {
        // Stream order is row-major: Left one, Right one, Left two, ...
        val words = listOf(
            Word("Left one", 72f, 100f), Word("Right one", 320f, 100f),
            Word("Left two", 72f, 120f), Word("Right two", 320f, 120f),
            Word("Left three", 72f, 140f), Word("Right three", 320f, 140f),
        )
        assertEquals(
            listOf("Left one", "Left two", "Left three", "Right one", "Right two", "Right three"),
            order(words),
        )
    }

    @Test
    fun columnMajorMultiColumnPagesAreNotRowInterleaved() {
        // This is the ordering the old extractor already produced for column-major
        // streams; a positional sort would have destroyed it, so guard it here.
        val words = listOf(
            Word("Left one", 72f, 100f),
            Word("Left two", 72f, 120f),
            Word("Left three", 72f, 140f),
            Word("Right one", 320f, 100f),
            Word("Right two", 320f, 120f),
            Word("Right three", 320f, 140f),
        )
        assertEquals(
            listOf("Left one", "Left two", "Left three", "Right one", "Right two", "Right three"),
            order(words),
        )
    }

    @Test
    fun aHeaderNarrowerThanTheGutterIsReadFirstAndTheColumnsStayDeInterleaved() {
        // A header that sits above the body and does not reach across the gutter
        // is simply the first line; it must not turn the page into one column.
        val words = listOf(
            Word("Left one", 72f, 100f), Word("Right one", 320f, 100f),
            Word("Left two", 72f, 120f), Word("Right two", 320f, 120f),
            Word("Wide header", 72f, 60f),
        )
        assertEquals(
            listOf("Wide header", "Left one", "Left two", "Right one", "Right two"),
            order(words),
        )
    }

    @Test
    fun aHeaderThatCrossesTheGutterDoesNotEraseTheColumnStructure() {
        // Regression: a full-width running header used to cover the gutter for the
        // whole page, so the columns were emitted row-interleaved
        // ("Left one Right one Left two Right two ...") — scrambled text before it
        // ever reached chunking or Gemini. The header is now read on its own and
        // the columns below it are still read one after the other.
        val words = listOf(
            Word("THE RUNNING HEADER OF THE BOOK", 72f, 60f),
            Word("Left one", 72f, 100f), Word("Right one", 320f, 100f),
            Word("Left two", 72f, 120f), Word("Right two", 320f, 120f),
        )
        assertEquals(
            listOf(
                "THE RUNNING HEADER OF THE BOOK",
                "Left one", "Left two", "Right one", "Right two",
            ),
            order(words),
        )
    }

    @Test
    fun aFullWidthFooterIsReadAfterBothColumns() {
        val words = listOf(
            Word("Left one", 72f, 100f), Word("Right one", 320f, 100f),
            Word("Left two", 72f, 120f), Word("Right two", 320f, 120f),
            Word("COPYRIGHT NOTICE AND PAGE FORTY TWO", 72f, 200f),
        )
        assertEquals(
            listOf(
                "Left one", "Left two", "Right one", "Right two",
                "COPYRIGHT NOTICE AND PAGE FORTY TWO",
            ),
            order(words),
        )
    }

    @Test
    fun aRunningHeaderOnLaterPagesNoLongerScramblesThosePages() {
        // The reported real-world pattern: the first page of a section has no
        // running header and read correctly, while every later page carried one and
        // came back row-interleaved. Both shapes must now read column by column.
        val body = listOf(
            Word("Left one", 72f, 100f), Word("Right one", 320f, 100f),
            Word("Left two", 72f, 120f), Word("Right two", 320f, 120f),
            Word("Left three", 72f, 140f), Word("Right three", 320f, 140f),
        )
        val bodyOrder = listOf(
            "Left one", "Left two", "Left three",
            "Right one", "Right two", "Right three",
        )

        // Page without a running header.
        assertEquals(bodyOrder, order(body))

        // The same page plus a running header spanning the gutter.
        val withHeader = body + Word("THE RUNNING HEADER OF THE BOOK", 72f, 60f)
        assertEquals(listOf("THE RUNNING HEADER OF THE BOOK") + bodyOrder, order(withHeader))
    }

    @Test
    fun aPageWithTooFewLinesHasNoColumnStructureToInfer() {
        // A column gutter is a property of many lines. With only two lines there is
        // nothing to infer, so the page is read top to bottom.
        val words = listOf(
            Word("alpha", 72f, 100f), Word("beta", 320f, 100f),
            Word("gamma", 72f, 120f), Word("delta", 320f, 120f),
        )
        assertEquals(listOf("alpha", "beta", "gamma", "delta"), order(words))
    }

    @Test
    fun aWordGapInsideOneLineIsNotMistakenForAColumnGutter() {
        // A justified line with an unusually wide space must stay one line.
        val words = listOf(
            Word("alpha", 72f, 100f),
            Word("beta", 220f, 100f),
            Word("gamma", 300f, 100f),
        )
        assertEquals(listOf("alpha", "beta", "gamma"), order(words))
    }

    @Test
    fun emptyInputProducesNoOrdering() {
        assertEquals(0, PdfReadingOrder.order(emptyList()).size)
    }

    @Test
    fun singleFragmentIsReturnedAsIs() {
        val words = listOf(Word("only", 72f, 100f))
        assertEquals(listOf("only"), order(words))
    }

    @Test
    fun orderingIsAPermutationOfEveryInputFragment() {
        val words = listOf(
            Word("Third", 72f, 140f),
            Word("Right", 320f, 120f),
            Word("Second", 72f, 120f),
            Word("First", 72f, 100f),
            Word("Right two", 320f, 100f),
        )
        val fragments = words.map { it.fragment() }
        val indices = PdfReadingOrder.order(fragments)
        assertEquals(fragments.size, indices.size)
        assertEquals(fragments.indices.toSet(), indices.toSet())
    }

    @Test
    fun orderingIsDeterministicForIdenticalGeometry() {
        val words = listOf(
            Word("b", 72f, 100f),
            Word("a", 72f, 100f),
            Word("c", 72f, 100f),
        )
        // Equal geometry keeps the original relative order (stable sort).
        assertEquals(listOf("b", "a", "c"), order(words))
        assertTrue(order(words) == order(words))
    }
}
