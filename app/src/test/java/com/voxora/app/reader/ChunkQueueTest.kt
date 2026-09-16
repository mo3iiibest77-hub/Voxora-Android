package com.voxora.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkQueueTest {
    @Test
    fun defaultChunksKeepWordBoundariesAndOrder() {
        for (count in listOf(499, 500, 501, 1000, 1001)) {
            val words = (1..count).map { "word$it" }
            val chunks = ChunkQueue.split(words.joinToString(" "))
            assertEquals((count + 499) / 500, chunks.size)
            assertEquals(words, chunks.flatMap { it.split(' ') })
            assertTrue(chunks.all { it.split(' ').size <= 500 })
            assertTrue(chunks.dropLast(1).all { it.split(' ').size == 500 })
        }
    }

    @Test
    fun customChunksNormalizeUnicodeWhitespaceWithoutChangingWords() {
        val text = " \tAlpha\r\nβήτα\u00A0فارسی\u2003中文\u202F\uD801\uDC00\u0085last\u3000 "
        assertEquals(
            listOf("Alpha βήτα", "فارسی 中文", "\uD801\uDC00 last"),
            ChunkQueue.split(text, 2)
        )
        assertEquals(listOf("a", "b", "c"), ChunkQueue.split("a b c", 1))
    }

    @Test
    fun emptyAndWhitespaceInputProduceNoChunksOrSegments() {
        for (text in listOf("", " \t\r\n\u00A0\u2007\u202F\u0085\u3000")) {
            assertTrue(ChunkQueue.split(text).isEmpty())
            assertTrue(ChunkQueue.speechSegments(text, 5).isEmpty())
        }
        val queue = ChunkQueue(emptyList())
        queue.advance()
        queue.reset()
        assertEquals(0, queue.size)
        assertEquals(0, queue.index)
        assertNull(queue.current)
    }

    @Test
    fun queueAdvancesInOrderAndStaysExhaustedUntilReset() {
        val source = mutableListOf("first", "second", "third")
        val queue = ChunkQueue(source)
        source.clear()
        assertEquals(3, queue.size)
        for ((index, text) in listOf("first", "second", "third").withIndex()) {
            assertEquals(index, queue.index)
            assertEquals(text, queue.current)
            queue.advance()
        }
        repeat(4) { queue.advance() }
        assertEquals(3, queue.index)
        assertNull(queue.current)
        queue.reset()
        assertEquals(0, queue.index)
        assertEquals("first", queue.current)
        queue.advance()
        queue.reset()
        assertEquals("first", queue.current)
    }

    @Test
    fun speechPrefersWhitespaceAndSplitsLongWords() {
        assertEquals(listOf("alpha", "beta", "gamma"), ChunkQueue.speechSegments("alpha beta gamma", 8))
        assertEquals(listOf("abc", "def", "ghi", "j"), ChunkQueue.speechSegments("abcdefghij", 3))
        assertEquals(listOf("a", "b", "c"), ChunkQueue.speechSegments("a\u00A0b\u0085c", 1))
        assertEquals(listOf("abc"), ChunkQueue.speechSegments("abc", Int.MAX_VALUE))
        assertEquals(listOf("abc"), ChunkQueue.speechSegments("abc", 3))
    }

    @Test
    fun speechBoundsPreserveContentAndSurrogatePairs() {
        val character = "\uD801\uDC00"
        val text = "  ab${character}cd${character}ef\u00A0one\u2003two\n${character.repeat(5)}! "
        val expected = text.filterNot { it.isWhitespace() || it == '\u0085' }
        for (limit in 2..text.length + 1) {
            val segments = ChunkQueue.speechSegments(text, limit)
            assertEquals(expected, segments.joinToString("").filterNot { it.isWhitespace() || it == '\u0085' })
            for (segment in segments) {
                assertTrue(segment.isNotEmpty())
                assertTrue(segment.length <= limit)
                assertFalse(segment.first().isLowSurrogate())
                assertFalse(segment.last().isHighSurrogate())
                for (index in segment.indices) {
                    if (segment[index].isHighSurrogate()) {
                        assertTrue(index + 1 < segment.length && segment[index + 1].isLowSurrogate())
                    }
                    if (segment[index].isLowSurrogate()) {
                        assertTrue(index > 0 && segment[index - 1].isHighSurrogate())
                    }
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun splitRejectsZeroWordLimit() {
        ChunkQueue.split("text", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun splitRejectsNegativeWordLimit() {
        ChunkQueue.split("", -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun speechRejectsZeroLength() {
        ChunkQueue.speechSegments("text", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun speechRejectsNegativeLength() {
        ChunkQueue.speechSegments("", -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun speechRejectsLimitTooSmallForSurrogatePair() {
        ChunkQueue.speechSegments("\uD801\uDC00", 1)
    }
}
