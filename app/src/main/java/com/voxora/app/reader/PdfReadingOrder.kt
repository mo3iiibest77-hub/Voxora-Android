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
 * 1. A **column gutter** is an x-range that no fragment at any height crosses.
 *    Single-column text has no interior gutter, so it falls through to rule 2 and
 *    is ordered top-to-bottom, matching the previous behaviour.
 * 2. Inside a column, fragments are grouped into lines by vertical proximity
 *    (half the taller fragment's height), then ordered left-to-right.
 * 3. Columns are emitted left-to-right, each read top-to-bottom.
 *
 * This is pure geometry: no PDFBox or `android.*` types, so the policy stays
 * unit-testable on the JVM. It cannot reconstruct arbitrary layouts — a page with
 * a full-width header or footer spanning the gutter is treated as single-column,
 * which is the safe fallback — and it does not attempt to place figures, tables
 * or sidebars relative to body text.
 */
internal object PdfReadingOrder {

    /** A glyph fragment in page space; [y] grows downwards (PDFBox `getYDirAdj`). */
    data class Fragment(val x: Float, val y: Float, val width: Float, val height: Float)

    /** A gutter must be at least this many median glyph advances wide. */
    private const val GUTTER_ADVANCE_FACTOR = 2.5f

    /** ...and never narrower than this, so tiny fonts do not split on a space. */
    private const val GUTTER_MIN_POINTS = 6f

    /**
     * Returns a stable permutation of [fragments] indices in reading order.
     * Fragments that compare equal keep their original relative order.
     */
    fun order(fragments: List<Fragment>): IntArray {
        if (fragments.isEmpty()) return IntArray(0)

        val gutters = columnGutters(fragments)
        val ordered = ArrayList<Int>(fragments.size)
        for (band in 0..gutters.size) {
            val indices = fragments.indices.filter { bandOf(fragments[it].x, gutters) == band }
            ordered += orderBand(fragments, indices)
        }
        return ordered.toIntArray()
    }

    /**
     * Centre positions of vertical gutters, left to right. A gutter exists where
     * no fragment's horizontal extent covers an x-range, so it can only appear
     * between real columns rather than between words on a line.
     */
    private fun columnGutters(fragments: List<Fragment>): List<Float> {
        val threshold = maxOf(GUTTER_ADVANCE_FACTOR * medianWidth(fragments), GUTTER_MIN_POINTS)
        val byLeft = fragments.indices.sortedBy { fragments[it].x }
        val gutters = ArrayList<Float>()
        var coveredUntil = Float.NEGATIVE_INFINITY
        for (index in byLeft) {
            val fragment = fragments[index]
            val start = fragment.x
            val end = fragment.x + fragment.width
            if (coveredUntil != Float.NEGATIVE_INFINITY && start - coveredUntil >= threshold) {
                gutters.add((coveredUntil + start) / 2f)
            }
            coveredUntil = maxOf(coveredUntil, end)
        }
        return gutters
    }

    private fun bandOf(x: Float, gutters: List<Float>): Int = gutters.count { x >= it }

    private fun medianWidth(fragments: List<Fragment>): Float {
        val widths = fragments.map { it.width }.filter { it > 0.01f }.sorted()
        return if (widths.isEmpty()) 1f else widths[widths.size / 2]
    }

    private fun orderBand(fragments: List<Fragment>, indices: List<Int>): List<Int> {
        if (indices.isEmpty()) return emptyList()
        val byVertical = indices.sortedWith(compareBy<Int> { fragments[it].y }.thenBy { it })

        val lines = ArrayList<MutableList<Int>>()
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
            .sortedWith(compareBy { fragments[it.first()].y })
            .flatMap { line -> line.sortedWith(compareBy<Int> { fragments[it].x }.thenBy { it }) }
    }
}
