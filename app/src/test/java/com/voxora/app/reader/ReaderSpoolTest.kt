package com.voxora.app.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderSpoolTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun streamsBeforeEofAndPreservesUnitBoundaries() {
        ReaderSpool(temporary.root, 2).use { spool ->
            spool.append(byteArrayOf(1, 2, 3, 4))
            assertFalse(spool.state.value.complete)
            assertArrayEquals(byteArrayOf(1, 2), spool.read(0, 2))
            spool.endUnit(2, "first")
            spool.append(byteArrayOf(5, 6))
            spool.endUnit(3, "second")
            spool.finish()
            assertArrayEquals(byteArrayOf(3, 4, 5, 6), spool.read(2, 6))
            assertEquals(listOf(4L, 6L), spool.state.value.ends.map { it.bytes })
            assertEquals(listOf(2, 3), spool.state.value.ends.map { it.index })
            assertTrue(spool.state.value.complete)
        }
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun partialFailureKeepsCommittedBytesButRejectsAppendAndEof() {
        ReaderSpool(temporary.root, 0).use { spool ->
            spool.append(byteArrayOf(1, 2))
            spool.fail(0, IllegalStateException("connection lost"))
            assertTrue(spool.state.value.failure!!.partial)
            assertArrayEquals(byteArrayOf(1, 2), spool.read(0, 2))
            assertThrows(IllegalStateException::class.java) { spool.append(byteArrayOf(3, 4)) }
            assertThrows(IllegalStateException::class.java) { spool.finish() }
            assertEquals(2L, spool.state.value.committed)
        }
    }

    @Test
    fun completedUnitsPreventPrefetchReplacementEvenBeforeConsumption() {
        ReaderSpool(temporary.root, 0).use { spool ->
            assertFalse(spool.state.value.canRetryPrefetch(0))
            spool.append(byteArrayOf(1, 2))
            spool.endUnit(0, "completed unit")
            spool.append(byteArrayOf(3, 4))
            spool.fail(1, IllegalStateException("connection lost"))
            assertFalse(spool.state.value.canRetryPrefetch(0))
            assertFalse(spool.state.value.canRetryPrefetch(2))
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), spool.read(0, 4))
        }
        ReaderSpool(temporary.root, 0).use { replacement ->
            assertEquals(0L, replacement.state.value.committed)
            assertTrue(replacement.state.value.ends.isEmpty())
            replacement.append(byteArrayOf(5, 6))
            replacement.endUnit(0, "fresh turn")
            replacement.finish()
            assertArrayEquals(byteArrayOf(5, 6), replacement.read(0, 2))
        }
    }

    @Test
    fun onlyEmptyPrefetchIsRetryableBeforeConsumption() {
        for (partial in listOf(false, true)) {
            ReaderSpool(temporary.root, 0).use { spool ->
                if (partial) spool.append(byteArrayOf(1, 2))
                spool.fail(0, IllegalStateException("connection lost"))
                assertEquals(partial, spool.state.value.failure!!.partial)
                assertEquals(!partial, spool.state.value.canRetryPrefetch(0))
                assertFalse(spool.state.value.canRetryPrefetch(2))
                if (partial) assertArrayEquals(byteArrayOf(1, 2), spool.read(0, 2))
            }
        }
    }

    @Test
    fun readsAreBoundedAndPreserveEveryByteAcrossUnitBoundaries() {
        ReaderSpool(temporary.root, 0).use { spool ->
            val first = ByteArray(24_002) { (it % 251).toByte() }
            val second = ByteArray(10_000) { (it % 127).toByte() }
            spool.append(first)
            spool.endUnit(0, "first")
            spool.append(second)
            spool.endUnit(1, "second")
            spool.finish()
            val expected = first + second
            var cursor = 0L
            for (end in spool.state.value.ends) {
                while (cursor < end.bytes) {
                    val bytes = spool.read(cursor, end.bytes)
                    assertTrue(bytes.size in 2..9_600 && bytes.size % 2 == 0)
                    assertTrue(cursor + bytes.size <= end.bytes)
                    assertArrayEquals(expected.copyOfRange(cursor.toInt(), cursor.toInt() + bytes.size), bytes)
                    cursor += bytes.size
                }
            }
            assertEquals(expected.size.toLong(), cursor)
            assertEquals(expected.size.toLong(), spool.state.value.committed)
        }
    }

    @Test
    fun invalidRangesAndOddPcmLeaveStorageUnchanged() {
        ReaderSpool(temporary.root, 0).use { spool ->
            assertThrows(IllegalArgumentException::class.java) { spool.append(byteArrayOf(1)) }
            spool.append(byteArrayOf(1, 2, 3, 4))
            for ((position, limit) in listOf(-2L to 2L, 1L to 4L, 0L to 3L, 4L to 6L, 2L to 2L, Long.MAX_VALUE - 1 to 0L)) {
                assertThrows(IllegalArgumentException::class.java) { spool.read(position, limit) }
            }
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), spool.read(0, Long.MAX_VALUE - 1))
            assertEquals(4L, spool.state.value.committed)
        }
    }

    @Test
    fun currentAndNextSpoolsAreIndependentlyBoundedAndClosingCurrentPreservesNext() {
        val current = ReaderSpool(temporary.root, 0)
        try {
            ReaderSpool(temporary.root, 0).use { next ->
                val block = ByteArray(9_600) { (it % 251).toByte() }
                repeat((ReaderSpool.MAX_BYTES / block.size).toInt()) {
                    current.append(block)
                    next.append(block)
                }
                assertEquals(2, temporary.root.listFiles()!!.size)
                assertEquals(2 * ReaderSpool.MAX_BYTES, temporary.root.listFiles()!!.sumOf { it.length() })
                val before = next.state.value
                assertThrows(ReaderSpool.CapacityException::class.java) { next.append(byteArrayOf(1, 2)) }
                assertEquals(before, next.state.value)
                current.close()
                assertEquals(ReaderSpool.MAX_BYTES, temporary.root.listFiles()!!.single().length())
                assertArrayEquals(block, next.read(0, ReaderSpool.MAX_BYTES))
                assertArrayEquals(block, next.read(ReaderSpool.MAX_BYTES - block.size, ReaderSpool.MAX_BYTES))
                next.endUnit(0, "complete")
                next.finish()
            }
        } finally {
            current.close()
        }
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun capacityIsBoundedWithoutCommittingOverflow() {
        ReaderSpool(temporary.root, 0).use { spool ->
            val block = ByteArray(9_600)
            repeat((ReaderSpool.MAX_BYTES / block.size).toInt()) { spool.append(block) }
            assertThrows(ReaderSpool.CapacityException::class.java) { spool.append(byteArrayOf(1, 2)) }
            assertEquals(ReaderSpool.MAX_BYTES, spool.state.value.committed)
            assertEquals(ReaderSpool.MAX_BYTES, temporary.root.listFiles()!!.single().length())
            spool.fail(0, ReaderSpool.CapacityException())
            assertFalse(spool.state.value.canRetryPrefetch(0))
        }
    }

    @Test
    fun concurrentProducerAndConsumerPreserveOrderWithoutDroppingAudio() = runBlocking {
        withTimeout(10_000) {
            ReaderSpool(temporary.root, 0).use { spool ->
                coroutineScope {
                    val blocks = List(200) { index -> ByteArray(962) { (index + it).toByte() } }
                    val consumedFirst = CompletableDeferred<Unit>()
                    val producer = async(Dispatchers.IO) {
                        blocks.forEachIndexed { index, bytes ->
                            spool.append(bytes)
                            if (index == 0) consumedFirst.await()
                            if (index % 10 == 9) spool.endUnit(index / 10, "unit${index / 10}")
                        }
                        spool.finish()
                    }
                    val consumer = async(Dispatchers.IO) {
                        val expected = blocks.reduce { accumulated, bytes -> accumulated + bytes }
                        var cursor = 0L
                        while (true) {
                            val snapshot = spool.state.value
                            if (cursor < snapshot.committed) {
                                val bytes = spool.read(cursor, snapshot.committed)
                                assertArrayEquals(expected.copyOfRange(cursor.toInt(), cursor.toInt() + bytes.size), bytes)
                                cursor += bytes.size
                                consumedFirst.complete(Unit)
                            } else if (snapshot.complete) {
                                break
                            } else {
                                delay(1)
                            }
                        }
                        assertEquals(expected.size.toLong(), cursor)
                    }
                    producer.await()
                    consumer.await()
                    assertEquals(20, spool.state.value.ends.size)
                }
            }
        }
    }

    @Test
    fun closeIsIdempotentAndRejectsLateCallbacks() {
        val spool = ReaderSpool(temporary.root, 0)
        spool.close()
        spool.close()
        assertThrows(IllegalStateException::class.java) { spool.append(byteArrayOf(1, 2)) }
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun unitMarkersCannotSkipOrFinishWithoutAudio() {
        ReaderSpool(temporary.root, 2).use { spool ->
            assertThrows(IllegalStateException::class.java) { spool.endUnit(2, "empty") }
            spool.append(byteArrayOf(1, 2))
            assertThrows(IllegalStateException::class.java) { spool.endUnit(3, "skipped") }
            spool.endUnit(2, "valid")
            assertThrows(IllegalStateException::class.java) { spool.endUnit(2, "duplicate") }
        }
    }

    // ---- handing a produced chunk over, and reading one back ---------------------------------

    @Test
    fun detachHandsTheFileOverAndLeavesItInPlace() {
        val spool = ReaderSpool(temporary.root, 0)
        spool.append(byteArrayOf(1, 2, 3, 4))
        spool.endUnit(0, "unit")
        spool.finish()

        val file = spool.detach()
        assertTrue(file!!.isFile)
        // The spool is closed by the detach, so a later close must not delete what the caller holds.
        spool.close()
        assertTrue(file.isFile)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), file.readBytes())
    }

    @Test
    fun detachRefusesASpoolThatDoesNotOwnItsFile() {
        val spool = ReaderSpool(temporary.root, 0)
        spool.append(byteArrayOf(1, 2))
        spool.endUnit(0, "unit")
        spool.finish()
        val file = spool.detach()!!

        ReaderSpool.fromCache(file, 2L, listOf(ReaderSpool.UnitEnd(0, 2L, "unit"))).use { restored ->
            // The file belongs to the cache, so a restored spool can never hand it over or delete it.
            assertNull(restored.detach())
        }
        assertTrue(file.isFile)
    }

    @Test
    fun aRestoredSpoolIsCompleteAndReadsFromItsFirstUnit() {
        val spool = ReaderSpool(temporary.root, 0)
        spool.append(byteArrayOf(1, 2, 3, 4))
        spool.endUnit(0, "first")
        spool.append(byteArrayOf(5, 6))
        spool.endUnit(1, "second")
        spool.finish()
        val file = spool.detach()!!

        ReaderSpool.fromCache(file, 6L, spool.state.value.ends).use { restored ->
            assertEquals(0, restored.firstUnit)
            assertTrue(restored.state.value.complete)
            assertEquals(6L, restored.state.value.committed)
            assertEquals(listOf("first", "second"), restored.state.value.ends.map { it.transcript })
            // The consumer reads it exactly like a chunk that was just produced.
            assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), restored.read(0, 6))
            assertFalse(restored.state.value.canRetryPrefetch(0))
        }
        // Closing a restored spool leaves the cache entry for the next Stop → Continue.
        assertTrue(file.isFile)
    }

    @Test
    fun aRestoredSpoolCannotBeAppendedToOrFailed() {
        val spool = ReaderSpool(temporary.root, 0)
        spool.append(byteArrayOf(1, 2))
        spool.endUnit(0, "unit")
        spool.finish()
        val file = spool.detach()!!

        ReaderSpool.fromCache(file, 2L, listOf(ReaderSpool.UnitEnd(0, 2L, "unit"))).use { restored ->
            assertThrows(IllegalStateException::class.java) { restored.append(byteArrayOf(3, 4)) }
            assertThrows(IllegalStateException::class.java) { restored.fail(0, IllegalStateException("late")) }
        }
    }

    @Test
    fun fromCacheRefusesBoundariesThatDoNotCoverTheFile() {
        val file = temporary.newFile("audio.pcm").apply { writeBytes(ByteArray(6)) }
        assertThrows(IllegalArgumentException::class.java) { ReaderSpool.fromCache(file, 0L, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            ReaderSpool.fromCache(file, 6L, listOf(ReaderSpool.UnitEnd(0, 4L, "short")))
        }
    }
}
