package com.voxora.app.reader

import kotlin.math.abs

/**
 * Deterministic reading-order reconstruction for PDF glyph fragments.
 *
 * `PDFTextStripper` emits glyphs in content-stream order by default, which is not
 * reading order for a large class of PDFs (the stream often paints the page in an
 * arbitrary sequence). Reconstructing order from geometry fixes that.
 *
 * The rules are intentionally simple and total, and they never invent text:
 * 1. Fragments are grouped into **lines** by vertical proximity (half the taller
 *    fragment's height); lines are ordered top-to-bottom.
 * 2. A **column gutter** is an x-range that a *minority* of lines cross, is wide
 *    enough to be a real column separation, and has substantial content on both
 *    sides. Measuring crossing by line (not by raw fragment) is what makes the
 *    detector robust: a single full-width running header or footer no longer
 *    erases the gutter for the whole page.
 * 3. The page is read as a stack of regions. A line that crosses a gutter
 *    (a full-width element such as a running header, a section rule of text or a
 *    page footer) is emitted on its own, in place; the runs of ordinary lines
 *    between those are read column by column, left to right, each column
 *    top-to-bottom.
 *
 * This is pure geometry: no PDFBox or `android.*` types, so the policy stays
 * unit-testable on the JVM.
 *
 * Known limits, stated honestly: it cannot reconstruct arbitrary layouts. Tables,
 * sidebars, marginalia, rotated text, overlapping columns and full-width elements
 * that sit in the *middle* of a multi-column region may still come out imperfectly,
 * and text that was never in the content stream (scanned pages) cannot be recovered.
 * A page with fewer than [MIN_LINES_FOR_COLUMNS] lines is always read top-to-bottom,
 * because there is no column structure to infer.
 *
 * One measured limitation deserves naming: because fragments arrive per glyph run,
 * a gap that is *inside* a line but large and repeated across most lines (a
 * tab-aligned label, a table-of-contents leader, a widely letter-spaced heading) can
 * be mistaken for a column gutter. That layout is genuinely ambiguous from geometry
 * alone, and it was already mis-ordered before this rework; it is not worth guessing
 * a threshold for, since tuning one against a synthetic page would trade a real fix
 * for a speculative one.
 */
internal object PdfReadingOrder {

    /** A glyph fragment in page space; [y] grows downwards (PDFBox `getYDirAdj`). */
    data class Fragment(val x: Float, val y: Float, val width: Float, val height: Float)

    /** A gutter must be at least this many median glyph advances wide. */
    private const val GUTTER_ADVANCE_FACTOR = 2.5f

    /** ...and never narrower than this, so tiny fonts do not split on a space. */
    private const val GUTTER_MIN_POINTS = 6f

    /** A gutter may be crossed by at most one line in this many (headers/footers). */
    private const val CROSSING_SHARE = 3

    /** Each side of a gutter must hold at least one in this many fragments. */
    private const val SIDE_SHARE = 20

    /** Below this many lines a page has no column structure worth inferring. */
    private const val MIN_LINES_FOR_COLUMNS = 3

    /**
     * Returns a stable permutation of [fragments] indices in reading order.
     * Fragments that compare equal keep their original relative order.
     */
    fun order(fragments: List<Fragment>): IntArray {
        if (fragments.isEmpty()) return IntArray(0)

        val lines = lines(fragments.indices.toList(), fragments)
        if (lines.size < MIN_LINES_FOR_COLUMNS) return flatten(lines, fragments).toIntArray()
        val gutters = gutters(lines, fragments)
        if (gutters.isEmpty()) return flatten(lines, fragments).toIntArray()

        val ordered = ArrayList<Int>(fragments.size)
        val region = ArrayList<List<Int>>()
        for (line in lines) {
            if (line.any { crosses(it, gutters, fragments) }) {
                ordered += flush(region, gutters, fragments)
                region.clear()
                ordered += horizontal(line, fragments)
            } else {
                region.add(line)
            }
        }
        ordered += flush(region, gutters, fragments)
        return ordered.toIntArray()
    }

    /** Groups [indices] into visual lines, top to bottom. */
    private fun lines(indices: List<Int>, fragments: List<Fragment>): List<List<Int>> {
        if (indices.isEmpty()) return emptyList()
        val byVertical = indices.sortedWith(compareBy<Int> { fragments[it].y }.thenBy { it })

        val lines = ArrayList<List<Int>>()
        var current = ArrayList<Int>()
        var lineY = 0f
        var lineHeight = 0f
        for (index in byVertical) {
            val fragment = fragments[index]
            when {
                current.isEmpty() -> {
                    lineY = fragment.y
                    lineHeight = fragment.height
                    current.add(index)
                }
                abs(fragment.y - lineY) < maxOf(lineHeight, fragment.height) / 2f -> {
                    lineHeight = maxOf(lineHeight, fragment.height)
                    current.add(index)
                }
                else -> {
                    lines.add(current)
                    current = ArrayList()
                    current.add(index)
                    lineY = fragment.y
                    lineHeight = fragment.height
                }
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }

    /**
     * Orders one region: columns left to right, each column top to bottom.
     *
     * The region is banded by the *page's* gutters, not by gutters re-derived from
     * the region. Re-deriving them was wrong twice over: a short region (say two
     * lines under a running header) falls below [MIN_LINES_FOR_COLUMNS] and would
     * silently collapse back to row-interleaved order, and the page gutter is the
     * authority anyway. A gutter-crossing line can never reach a region — `order`
     * emits those on their own — so every fragment here belongs to exactly one band.
     */
    private fun flush(
        region: List<List<Int>>,
        gutters: List<Float>,
        fragments: List<Fragment>,
    ): List<Int> {
        if (region.isEmpty()) return emptyList()
        if (gutters.isEmpty()) return flatten(region, fragments)
        val indices = region.flatten()
        val ordered = ArrayList<Int>(indices.size)
        for (band in 0..gutters.size) {
            val bandIndices = indices.filter { bandOf(fragments[it].x, gutters) == band }
            ordered += flatten(lines(bandIndices, fragments), fragments)
        }
        return ordered
    }

    /** Lines top to bottom, fragments left to right inside a line. */
    private fun flatten(lines: List<List<Int>>, fragments: List<Fragment>): List<Int> =
        lines.flatMap { horizontal(it, fragments) }

    private fun horizontal(line: List<Int>, fragments: List<Fragment>): List<Int> =
        line.sortedWith(compareBy<Int> { fragments[it].x }.thenBy { it })

    private fun crosses(index: Int, gutters: List<Float>, fragments: List<Fragment>): Boolean {
        val fragment = fragments[index]
        val end = fragment.x + fragment.width
        return gutters.any { it > fragment.x && it < end }
    }

    private fun bandOf(x: Float, gutters: List<Float>): Int = gutters.count { x >= it }

    /**
     * Centre positions of vertical gutters, left to right.
     *
     * A gutter is a maximal x-range that at most `lines.size / CROSSING_SHARE` lines
     * cover. Counting by line rather than by fragment is what keeps a full-width
     * running header or footer from hiding the columns underneath it.
     */
    private fun gutters(lines: List<List<Int>>, fragments: List<Fragment>): List<Float> {
        if (lines.size < MIN_LINES_FOR_COLUMNS) return emptyList()
        val all = lines.flatten()
        val minX = all.minOf { fragments[it].x }
        val maxX = all.maxOf { fragments[it].x + fragments[it].width }
        if (maxX - minX <= 0f) return emptyList()

        val threshold = maxOf(GUTTER_ADVANCE_FACTOR * medianWidth(all, fragments), GUTTER_MIN_POINTS)
        val tolerance = lines.size / CROSSING_SHARE

        // Coverage is measured per line, so overlapping fragments on one line count once.
        val deltas = sortedMapOf<Float, Int>()
        for (line in lines) {
            for ((start, end) in merge(line.map { fragments[it].x to fragments[it].x + fragments[it].width })) {
                deltas[start] = (deltas[start] ?: 0) + 1
                deltas[end] = (deltas[end] ?: 0) - 1
            }
        }

        val uncovered = ArrayList<Pair<Float, Float>>()
        var coverage = 0
        var previous = Float.NaN
        var runStart = Float.NaN
        for ((x, delta) in deltas) {
            if (!previous.isNaN()) {
                if (coverage <= tolerance) {
                    if (runStart.isNaN()) runStart = previous
                } else if (!runStart.isNaN()) {
                    uncovered.add(runStart to previous)
                    runStart = Float.NaN
                }
            }
            coverage += delta
            previous = x
        }
        if (!runStart.isNaN()) uncovered.add(runStart to previous)

        val minimumSide = maxOf(1, all.size / SIDE_SHARE)
        return uncovered
            .filter { it.second - it.first >= threshold }
            .filter { it.first > minX && it.second < maxX }
            .map { (it.first + it.second) / 2f }
            .filter { centre ->
                all.count { fragments[it].x < centre } >= minimumSide &&
                    all.count { fragments[it].x >= centre } >= minimumSide
            }
            .sorted()
    }

    private fun merge(intervals: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        if (intervals.isEmpty()) return emptyList()
        val sorted = intervals.sortedBy { it.first }
        val merged = ArrayList<Pair<Float, Float>>()
        var start = sorted[0].first
        var end = sorted[0].second
        for (index in 1 until sorted.size) {
            val (nextStart, nextEnd) = sorted[index]
            if (nextStart <= end) {
                end = maxOf(end, nextEnd)
            } else {
                merged.add(start to end)
                start = nextStart
                end = nextEnd
            }
        }
        merged.add(start to end)
        return merged
    }

    private fun medianWidth(indices: List<Int>, fragments: List<Fragment>): Float {
        val widths = indices.map { fragments[it].width }.filter { it > 0.01f }.sorted()
        return if (widths.isEmpty()) 1f else widths[widths.size / 2]
    }
}
