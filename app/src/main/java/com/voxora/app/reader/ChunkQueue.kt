package com.voxora.app.reader

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

    companion object {
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
            val segments = mutableListOf<String>()
            var start = 0
            while (start < text.length) {
                while (start < text.length && isWhitespace(text[start])) start++
                if (start == text.length) break
                var end = start + minOf(maxLength, text.length - start)
                if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) {
                    end--
                }
                require(end > start) { "Speech segment length is too small for a Unicode character." }
                if (end < text.length && !isWhitespace(text[end])) {
                    var boundary = end
                    while (boundary > start && !isWhitespace(text[boundary - 1])) boundary--
                    if (boundary > start) end = boundary
                }
                var trimmedEnd = end
                while (trimmedEnd > start && isWhitespace(text[trimmedEnd - 1])) trimmedEnd--
                segments.add(text.substring(start, trimmedEnd))
                start = end
            }
            return segments
        }

        private fun isWhitespace(char: Char): Boolean = char.isWhitespace() || char == '\u0085'
    }
}
