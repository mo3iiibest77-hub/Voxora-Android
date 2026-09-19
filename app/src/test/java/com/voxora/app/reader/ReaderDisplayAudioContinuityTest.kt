package com.voxora.app.reader

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for display-language updates never disturbing the audio pipeline.
 *
 * The requirement: switching the visible language at a chunk boundary must not insert a
 * pause between Chunk N and Chunk N+1. That is a structural property of the production
 * code, not a timing measurement:
 *
 * - the display layer is *derived*. [ReaderController.publish] reads the language cache
 *   and the canonical [ChunkQueue] and writes a state object; it never appends PCM,
 *   never advances a cursor and never completes a unit.
 * - the audio layer never reads that state. The producer writes PCM and the transcript
 *   into a [ReaderSpool]; the consumer reads bytes out of the spool snapshot. Neither
 *   consults the display cache, so nothing the UI does can gate them.
 * - because the producer renders whole chunks ahead of playback, the chunk that is
 *   promoted next already has its selected-language text and its audio committed *before*
 *   the turn. The turn is therefore a pure state read, which is what removes the gap.
 *
 * These tests drive the real [ReaderSpool] and the real [ReaderDisplayText] and assert
 * those ownership and ordering properties. They deliberately contain no sleeps: a timing
 * assertion here would measure the test machine, not the contract.
 */
class ReaderDisplayAudioContinuityTest {

    /** A spool whose units are all committed and finished, i.e. fully produced. */
    private class Audio(dir: File, unitBytes: List<ByteArray>) : Closeable {
        val spool = ReaderSpool(dir, 0)
        val unitCount = unitBytes.size

        init {
            unitBytes.forEachIndexed { index, bytes ->
                spool.append(bytes)
                spool.endUnit(index, "transcript-$index")
            }
            spool.finish()
        }

        override fun close() = spool.close()
    }

    /**
     * Stand-in for the derived display layer: the same cache, the same mapping and the
     * same republish gate the controller uses.
     */
    private class Display(chunks: List<String>, var language: String) {
        val cache = ReaderDisplayText()
        val queue = ChunkQueue(chunks)
        var published = ReaderState()
        var records = 0
            private set

        init {
            publish()
        }

        fun publish(phase: ReaderPhase = ReaderPhase.SPEAKING) {
            val units = queue.segments(queue.index)
            published = ReaderState(
                phase = phase,
                chunk = if (queue.size == 0) 0 else queue.index + 1,
                total = queue.size,
                segmentTotal = units.size,
                text = queue.current.orEmpty(),
                segments = cache.readingText(language, queue.index, units),
                pendingSegments = cache.pending(language, queue.index, units.size),
            )
        }

        /** Mirrors the record-and-maybe-republish step inside ReaderController.produce(). */
        fun record(language: String, chunk: Int, segment: Int, text: String) {
            if (!cache.record(language, chunk, segment, text)) return
            records++
            if (cache.shouldRepublish(language, chunk, this.language, queue.index)) publish()
        }

        fun select(selected: String) {
            if (selected == language) return
            language = selected
            publish(published.phase)
        }
    }

    /**
     * Mirrors the read loop of `ReaderController.consume()`: walk the units in order and
     * pull every committed byte of each one, never looking at display state.
     */
    private fun consume(spool: ReaderSpool, units: Int, beforeRead: (Int) -> Unit = {}): ByteArray {
        val out = ByteArrayOutputStream()
        var cursor = 0L
        for (unit in 0 until units) {
            val end = spool.state.value.ends.first { it.index == unit }
            while (cursor < end.bytes) {
                beforeRead(unit)
                val bytes = spool.read(cursor, end.bytes)
                out.write(bytes)
                cursor += bytes.size
            }
        }
        return out.toByteArray()
    }

    private fun tempDir(): File = Files.createTempDirectory("voxora-continuity").toFile()

    private fun pcm(seed: Int, size: Int = 16): ByteArray =
        ByteArray(size) { ((seed + it) % 251).toByte() }

    private fun paragraph(tag: String): String = (1..60).joinToString(" ") { "$tag$it" }

    private fun chunk(tag: String): String =
        listOf(paragraph("${tag}a"), paragraph("${tag}b"), paragraph("${tag}c")).joinToString("\n\n")

    // ---- the two layers do not touch each other -------------------------------------

    @Test
    fun recordingDisplayTextDoesNotTouchTheSpool() {
        val audio = Audio(tempDir(), listOf(pcm(1), pcm(2), pcm(3)))
        val display = Display(listOf(chunk("one")), "en")
        val before = audio.spool.state.value

        display.record("en", 0, 0, "rendered unit one")

        assertEquals(before, audio.spool.state.value)
        audio.close()
    }

    @Test
    fun switchingLanguageLeavesTheSpoolAndTheQueueUntouched() {
        val audio = Audio(tempDir(), listOf(pcm(1), pcm(2), pcm(3)))
        val display = Display(listOf(chunk("one"), chunk("two")), "en")
        val spoolBefore = audio.spool.state.value
        val queueBefore = display.queue.index

        display.select("fa")
        display.select("en")

        assertEquals(spoolBefore, audio.spool.state.value)
        assertEquals(queueBefore, display.queue.index)
        assertEquals(2, display.queue.size)
        audio.close()
    }

    @Test
    fun consumingAChunkNeverRendersAnythingForIt() {
        val audio = Audio(tempDir(), listOf(pcm(1), pcm(2), pcm(3)))
        val display = Display(listOf(chunk("one")), "en")

        consume(audio.spool, audio.unitCount)

        // The audio path does not drive the display path.
        assertEquals(0, display.records)
        assertEquals(0, display.cache.count("en"))
        audio.close()
    }

    // ---- a display refresh cannot change the audio bytes ----------------------------

    @Test
    fun publishingBetweenUnitsDoesNotChangeTheBytesTheConsumerWrites() {
        val units = listOf(pcm(1), pcm(2), pcm(3))

        val plain = Audio(tempDir(), units)
        val baseline = consume(plain.spool, units.size)

        val refreshed = Audio(tempDir(), units)
        val display = Display(listOf(chunk("one")), "en")
        val interleaved = consume(refreshed.spool, units.size) { unit ->
            // The controller publishes from the playback progress callback, i.e. in the
            // middle of the byte stream. That must be invisible to the stream.
            display.record("en", 0, unit, "rendered-$unit")
            display.select(if (unit % 2 == 0) "fa" else "en")
        }

        assertArrayEquals(baseline, interleaved)
        assertEquals(units.flatMap { it.toList() }, baseline.toList())
        plain.close()
        refreshed.close()
    }

    @Test
    fun theConsumerReadsEveryUnitOnceAndInOrderWhateverTheDisplayDoes() {
        val audio = Audio(tempDir(), listOf(pcm(1), pcm(2), pcm(3)))
        val display = Display(listOf(chunk("one")), "en")
        val order = mutableListOf<Int>()

        consume(audio.spool, audio.unitCount) { unit ->
            order.add(unit)
            display.select(if (unit == 1) "fa" else "en")
        }

        assertEquals(listOf(0, 1, 2), order.distinct())
        assertEquals(order.sorted(), order)
        audio.close()
    }

    // ---- the promoted chunk is already prepared -------------------------------------

    @Test
    fun theChunkPromotedNextIsAlreadyRenderedBeforeTheTurn() {
        val chunks = listOf(chunk("one"), chunk("two"))
        val display = Display(chunks, "en")
        val nextAudio = Audio(tempDir(), listOf(pcm(1), pcm(2), pcm(3)))

        // Production runs ahead: chunk 2's units are rendered and its audio is committed
        // while chunk 1 is still the page.
        val second = display.queue.segments(1)
        second.indices.forEach { display.record("en", 1, it, "en2-$it") }
        val recordsAtTurn = display.records
        val spoolAtTurn = nextAudio.spool.state.value
        assertTrue(spoolAtTurn.complete)

        // The consumer finishes chunk 1 and promotes the prefetched chunk 2.
        display.queue.jumpTo(1)
        display.publish(ReaderPhase.NEXT)

        assertEquals(2, display.published.chunk)
        assertEquals(second.indices.map { "en2-$it" }, display.published.segments)
        assertTrue(display.published.pendingSegments.isEmpty())
        // The turn rendered nothing new and did not touch the audio it is about to play.
        assertEquals(recordsAtTurn, display.records)
        assertEquals(spoolAtTurn, nextAudio.spool.state.value)
        nextAudio.close()
    }

    @Test
    fun aChunkBoundaryAddsNoWorkToTheAudioPath() {
        val display = Display(listOf(chunk("one"), chunk("two")), "en")
        val first = Audio(tempDir(), listOf(pcm(1), pcm(2), pcm(3)))
        val second = Audio(tempDir(), listOf(pcm(4), pcm(5), pcm(6)))

        // Drain chunk 1, then turn the page: the only calls on the boundary are the queue
        // move and the publish. Neither can suspend on audio.
        val drained = consume(first.spool, first.unitCount)
        display.queue.jumpTo(1)
        display.publish(ReaderPhase.NEXT)

        val nextUp = consume(second.spool, second.unitCount)

        assertEquals(48, drained.size)
        assertEquals(48, nextUp.size)
        assertEquals(2, display.published.chunk)
        first.close()
        second.close()
    }

    @Test
    fun displayStateIsNeverWrittenBackIntoTheDocument() {
        val source = chunk("one")
        val display = Display(listOf(source), "en")
        val units = display.queue.segments(0)

        units.indices.forEach { display.record("en", 0, it, "en-$it") }
        display.select("fa")

        assertEquals(source, display.queue.current)
        assertEquals(units, display.queue.segments(0))
        assertTrue(display.queue.segments(0).none { it.startsWith("en-") })
    }
}
