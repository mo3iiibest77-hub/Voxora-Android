package com.voxora.app.reader

import java.text.BreakIterator
import java.util.Locale
import java.util.regex.Pattern

class ChunkQueue(chunks: List<String>) {
    private val chunks = chunks.toList()

    private var position = 0

    val size: Int get() = chunks.size
    val index: Int get() = position
    val current: String? get() = chunks.getOrNull(position)

    fun advance() {
        if (position < size) position++
    }

    fun reset() {
        position = 0
    }

    fun jumpTo(index: Int) {
        position = index.coerceIn(0, maxOf(0, size - 1))
    }

    private val segmentCache = mutableMapOf<Int, List<String>>()

    fun segments(index: Int): List<String> {
        val text = chunks.getOrNull(index) ?: return emptyList()
        return segmentCache.getOrPut(index) { readableUnits(text) }
    }

    companion object {
        fun normalize(text: String): String = text
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace(Regex("[\\r\\u0085\\u2028]"), "\n")
            .replace(Regex("[\\u2029\\f]"), "\n\n")
            .replace(Regex("\\u00AD[\\p{Zs}\\t]*\\n(?![\\p{Zs}\\t]*\\n)[\\p{Zs}\\t]*"), "")
            .replace("\u00AD", "")
            .split(Regex("\\n(?:[\\p{Zs}\\t]*\\n)+"))
            .map { it.replace(Regex("[\\p{Z}\\s\\u0085]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")

        fun readableUnits(text: String): List<String> {
            val units = mutableListOf<String>()
            val pending = StringBuilder()
            var words = 0
            fun flush() {
                if (pending.isNotEmpty()) units.add(pending.toString())
                pending.setLength(0)
                words = 0
            }
            for (paragraph in normalize(text).split("\n\n").filter { it.isNotEmpty() }) {
                if (words >= 40 || pending.length >= 500) flush()
                val iterator = BreakIterator.getSentenceInstance(Locale.ROOT).apply { setText(paragraph) }
                var start = iterator.first()
                var end = iterator.next()
                var separator = if (pending.isEmpty()) "" else "\n\n"
                while (end != BreakIterator.DONE) {
                    for (piece in wordBoundedPieces(paragraph.substring(start, end), 100)
                        .flatMap { boundedSegments(it, 1_400, allowOversizedGrapheme = true) }) {
                        val count = wordCount(piece)
                        if (pending.isNotEmpty() &&
                            (words + count > 100 || pending.length + separator.length + piece.length > 1_400)
                        ) flush()
                        if (pending.isNotEmpty()) pending.append(separator)
                        pending.append(piece)
                        words += count
                        if (words >= 80 || pending.length >= 1_000) flush()
                        separator = " "
                    }
                    start = end
                    end = iterator.next()
                }
            }
            flush()
            return units
        }

        fun documentChunks(text: String): List<String> = wordBoundedPieces(normalize(text), 500)

        private fun wordBoundedPieces(text: String, maxWords: Int): List<String> {
            val iterator = BreakIterator.getWordInstance(Locale.ROOT).apply { setText(text) }
            val graphemes = graphemePattern.matcher(text)
            var graphemeEnd = 0
            val result = mutableListOf<String>()
            var start = 0
            var last = iterator.first()
            var words = 0
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                if (text.substring(last, end).codePoints().anyMatch { Character.isLetterOrDigit(it) }) {
                    while (graphemeEnd < last && graphemes.find()) graphemeEnd = graphemes.end()
                    if (words >= maxWords && last == graphemeEnd) {
                        text.substring(start, last).trim().takeIf { it.isNotEmpty() }?.let(result::add)
                        start = last
                        words = 0
                    }
                    words++
                }
                last = end
                end = iterator.next()
            }
            text.substring(start).trim().takeIf { it.isNotEmpty() }?.let(result::add)
            return result
        }

        private fun wordCount(text: String): Int {
            val iterator = BreakIterator.getWordInstance(Locale.ROOT).apply { setText(text) }
            var count = 0
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                if (text.substring(start, end).codePoints().anyMatch { Character.isLetterOrDigit(it) }) count++
                start = end
                end = iterator.next()
            }
            return count
        }

        fun split(text: String, wordsPerChunk: Int = 500): List<String> {
            require(wordsPerChunk > 0) { "Words per chunk must be positive." }
            val chunks = mutableListOf<String>()
            val chunk = StringBuilder()
            var words = 0
            var position = 0
            while (position < text.length) {
                if (isWhitespace(text[position])) {
                    position++
                    continue
                }
                val start = position
                while (position < text.length && !isWhitespace(text[position])) position++
                if (chunk.isNotEmpty()) chunk.append(' ')
                chunk.append(text, start, position)
                words++
                if (words == wordsPerChunk) {
                    chunks.add(chunk.toString())
                    chunk.setLength(0)
                    words = 0
                }
            }
            if (chunk.isNotEmpty()) chunks.add(chunk.toString())
            return chunks
        }

        fun speechSegments(text: String, maxLength: Int): List<String> {
            require(maxLength > 0) { "Speech segment length must be positive." }
            return boundedSegments(text, maxLength, allowOversizedGrapheme = false)
        }

        private val graphemePattern = Pattern.compile("\\X")

        private fun boundedSegments(text: String, maxLength: Int, allowOversizedGrapheme: Boolean): List<String> {
            val segments = mutableListOf<String>()
            val graphemes = graphemePattern.matcher(text)
            var start = 0
            while (start < text.length) {
                while (start < text.length && isWhitespace(text[start])) start++
                if (start == text.length) break
                val limit = start + minOf(maxLength, text.length - start)
                var end = start
                var whitespace = start
                var cursor = start
                while (cursor < text.length && graphemes.find(cursor)) {
                    val next = graphemes.end()
                    if (next > limit) {
                        if (end == start) {
                            require(allowOversizedGrapheme) { "Speech segment length is too small for a Unicode grapheme." }
                            end = next
                        }
                        break
                    }
                    end = next
                    if (isWhitespace(text[cursor])) whitespace = cursor
                    cursor = next
                    if (end == limit) break
                }
                if (end < text.length && !isWhitespace(text[end]) && whitespace > start) end = whitespace
                segments.add(text.substring(start, end).trim())
                start = end
            }
            return segments
        }

        private fun isWhitespace(char: Char): Boolean = char.isWhitespace() || char == '\u0085'
    }
}
