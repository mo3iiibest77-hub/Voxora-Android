package com.voxora.app.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkQueueTest {
    @Test
    fun extractionNormalizationPreservesContentAndVisibleHyphens() {
        assertEquals(
            "Heading co operate cooperate well- known فارسی 中文",
            ChunkQueue.normalize("Heading\nco\u00AD operate co\u00AD\noperate well-\nknown\u00A0فارسی\u0085中文"),
        )
    }

    @Test
    fun normalizationKeepsParagraphsButUnwrapsVisualLines() {
        val text = "\uFEFFHeading\r\n\r\nFirst\rline\u2028continues.\n \t\nSecond\u2029Third\u000CFourth"
        val expected = "Heading\n\nFirst line continues.\n\nSecond\n\nThird\n\nFourth"
        assertEquals(expected, ChunkQueue.normalize(text))
        assertEquals(expected, ChunkQueue.normalize(expected))
        assertEquals("cooperate\n\nnext", ChunkQueue.normalize("co\u00AD\r\noperate\u00AD\n\nnext"))
        assertEquals("می\u200Cروم cafe\u0301", ChunkQueue.normalize("می\u200Cروم cafe\u0301"))
    }

    @Test
    fun documentChunksUseFiveHundredWordsWithoutOrphaningFinalPunctuation() {
        for (count in listOf(499, 500, 501, 1000, 1001)) {
            val words = (1..count).map { "word$it" }
            val chunks = ChunkQueue.documentChunks(words.joinToString(" ") + ".")
            assertEquals((count + 499) / 500, chunks.size)
            assertEquals(words.joinToString(" ") + ".", chunks.joinToString(" "))
            assertTrue(chunks.dropLast(1).all { it.split(' ').size == 500 })
            assertTrue(chunks.last().endsWith('.'))
        }
        val longWords = List(500) { "a".repeat(20) }.joinToString(" ")
        assertEquals(listOf(longWords), ChunkQueue.documentChunks(longWords))
    }

    @Test
    fun paragraphsRemainNavigableWithoutTurningVisualLinesIntoUnits() {
        val paragraph = (1..60).joinToString("\n") { "word$it" } + "."
        val text = "Heading\n\n$paragraph\n\n$paragraph"
        val chunks = ChunkQueue.documentChunks(text)
        assertEquals(1, chunks.size)
        val queue = ChunkQueue(chunks)
        val segments = queue.segments(0)
        assertEquals(2, segments.size)
        assertEquals("Heading\n\n" + paragraph.replace('\n', ' '), segments.first())
        assertEquals(paragraph.replace('\n', ' '), segments.last())
        assertEquals(segments, queue.segments(0))
        assertTrue(queue.segments(-1).isEmpty())
        assertTrue(queue.segments(1).isEmpty())
    }

    @Test
    fun sentenceGroupsPreserveTextAndStayWithinNarrationBounds() {
        val sentence = List(20) { if (it == 0) "Word" else "word" }.joinToString(" ") + "."
        val text = List(25) { sentence }.joinToString("\n")
        val units = ChunkQueue.readableUnits(text)
        assertEquals(7, units.size)
        assertEquals(ChunkQueue.normalize(text), units.joinToString(" "))
        assertTrue(units.dropLast(1).all { it.split(' ').size == 80 })
        assertTrue(units.all { it.length <= 1_400 })
        val lowercase = ChunkQueue.readableUnits(text.lowercase(java.util.Locale.ROOT))
        assertEquals(ChunkQueue.normalize(text.lowercase(java.util.Locale.ROOT)), lowercase.joinToString(" "))
        assertTrue(lowercase.all { it.split(' ').size in 1..100 })
        assertTrue(ChunkQueue.readableUnits("\u0085\u00A0\n\n").isEmpty())
    }

    @Test
    fun longTokensAndUnspacedScriptsAreBoundedWithoutLosingContent() {
        for (text in listOf("a".repeat(10_000), "中文日本語".repeat(600), "ภาษาไทย".repeat(600))) {
            val units = ChunkQueue.documentChunks(text).flatMap(ChunkQueue::readableUnits)
            assertEquals(text, units.joinToString("").filterNot { it.isWhitespace() })
            assertTrue(units.all { it.isNotEmpty() && it.length <= 1_400 })
        }
    }

    @Test
    fun graphemeClustersAreNeverSplitBySpeechOrNarrationLimits() {
        val clusters = listOf(
            "e\u0301",
            "\uD83D\uDC69\uD83C\uDFFD\u200D\uD83D\uDCBB",
            "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66",
            "\uD83C\uDDEE\uD83C\uDDF7",
        )
        for (cluster in clusters) {
            val text = cluster.repeat(301)
            val speech = ChunkQueue.speechSegments(text, cluster.length * 3)
            assertEquals(text, speech.joinToString(""))
            assertTrue(speech.all { it.length % cluster.length == 0 })
            val units = ChunkQueue.readableUnits(text)
            assertEquals(text, units.joinToString(""))
            assertTrue(units.all { it.length % cluster.length == 0 })
        }
        val oversized = "a" + "\u0301".repeat(1_500)
        assertEquals(listOf(oversized), ChunkQueue.readableUnits(oversized))
    }

    @Test(expected = IllegalArgumentException::class)
    fun speechRejectsLimitTooSmallForCombiningCluster() {
        ChunkQueue.speechSegments("e\u0301", 1)
    }

    @Test
    fun readerLanguageValidationUsesCatalogAndEnglishMigration() {
        val catalog = com.voxora.core.gemini.ReaderLanguages
        for (invalid in listOf(null, "", "original", "invalid")) {
            assertEquals("en", catalog.normalize(invalid))
        }
        assertEquals("no", catalog.normalize("nb"))
        assertEquals("pt-BR", catalog.normalize("PT-br"))
        assertEquals(catalog.all.size, catalog.all.map { it.code }.toSet().size)
        for (language in catalog.all) {
            assertTrue(catalog.isValid(language.code))
            assertEquals(language, catalog.language(language.code))
            assertTrue(language.displayName(java.util.Locale.ENGLISH).isNotBlank())
        }
    }

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
