package com.voxora.app.reader

import com.voxora.core.gemini.ReaderNarrationModes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The persisted chunk cache.
 *
 * The point of this cache is that a chunk the reader already paid for is replayed instead of
 * re-synthesised — and the danger is that the wrong chunk is replayed. So the tests are about two
 * things: **what makes a stored chunk reusable** (every part of the key, and a consistent unit
 * layout) and **that the cache cannot grow without limit**.
 */
class ReaderChunkCacheTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var root: File
    private lateinit var spools: File
    private var now = 1_000L

    private fun cache(
        keepPerBook: Int = ReaderChunkCache.KEEP_PER_BOOK,
        maxBytes: Long = ReaderChunkCache.MAX_BYTES,
    ): ReaderChunkCache {
        root = temporary.newFolder("chunks")
        spools = temporary.newFolder("spools")
        return ReaderChunkCache(root, clock = { now++ }, keepPerBook = keepPerBook, maxBytes = maxBytes)
    }

    /** A spool as the producer would have left it: every unit's audio present, in order. */
    private fun produced(units: List<String>, bytesPerUnit: Int = 4): ReaderSpool {
        val spool = ReaderSpool(spools, 0)
        units.forEachIndexed { index, text ->
            spool.append(ByteArray(bytesPerUnit) { index.toByte() })
            spool.endUnit(index, text)
        }
        spool.finish()
        return spool
    }

    private fun key(
        chunk: Int = 0,
        bookId: String = "book-1",
        units: List<String> = listOf("one", "two"),
        chunkCount: Int = 10,
        language: String = "fa",
        mode: String = ReaderNarrationModes.DEFAULT,
        voice: String = "voice-a",
        model: String = "models/reader",
    ) = ReaderChunkCache.Key(
        bookId = bookId,
        chunk = chunk,
        chunkCount = chunkCount,
        unitCount = units.size,
        unitHash = ReaderChunkCache.unitHash(units),
        language = language,
        mode = mode,
        voice = voice,
        model = model,
    )

    private fun store(cache: ReaderChunkCache, key: ReaderChunkCache.Key, units: List<String>) {
        assertTrue("the chunk should have been stored", cache.store(key, produced(units)))
    }

    private fun metaOf(cache: ReaderChunkCache, key: ReaderChunkCache.Key): File =
        File(cache.load(key)!!.audio.parentFile, "meta.json")

    // ---- what is reusable -------------------------------------------------------------------

    @Test
    fun aStoredChunkIsReturnedWithItsAudioAndEveryTranscript() {
        val cache = cache()
        val units = listOf("first unit", "second unit", "third unit")
        store(cache, key(units = units), units)

        val entry = cache.load(key(units = units))
        assertNotNull(entry)
        assertEquals(12L, entry!!.committed)
        assertEquals(listOf(0, 1, 2), entry.ends.map { it.index })
        assertEquals(units, entry.ends.map { it.transcript })
        assertEquals(12L, entry.audio.length())
        assertTrue(entry.audio.isFile)
    }

    @Test
    fun aStoredChunkIsNeverServedUnderADifferentKey() {
        val cache = cache()
        val units = listOf("first unit", "second unit")
        store(cache, key(units = units), units)

        // Each of these is a different chunk of work, so none of them may be served the stored audio.
        val different = listOf(
            key(units = units).copy(bookId = "book-2"),
            key(units = units).copy(chunk = 1),
            key(units = units).copy(chunkCount = 11),
            key(units = units).copy(unitCount = 3),
            key(units = units).copy(unitHash = ReaderChunkCache.unitHash(listOf("different"))),
            key(units = units).copy(language = "en"),
            key(units = units).copy(mode = ReaderNarrationModes.FLUENT),
            key(units = units).copy(voice = "voice-b"),
            key(units = units).copy(model = "models/other"),
        )
        for (other in different) {
            assertNull(other.toString(), cache.load(other))
            // A miss for another key must not disturb the entry that is stored.
            assertNotNull(other.toString(), cache.load(key(units = units)))
        }
    }

    @Test
    fun reExtractingDifferentWordsAtTheSameIndexIsADifferentChunk() {
        val cache = cache()
        val before = listOf("the old extraction of this chunk")
        val after = listOf("the new extraction of this chunk")
        store(cache, key(units = before), before)

        // Same book, same index — different words. Playing the stored audio would narrate text the
        // reader can no longer see, so the fingerprint must separate them.
        assertNull(cache.load(key(units = after)))
    }

    @Test
    fun resumingAtAPersistedChunkReturnsThatChunksOwnUnitsInOrder() {
        val cache = cache()
        val chunk71 = listOf("chunk seventy-one, unit one", "chunk seventy-one, unit two")
        val chunk72 = listOf("chunk seventy-two, unit one", "chunk seventy-two, unit two")
        store(cache, key(chunk = 71, units = chunk71), chunk71)
        store(cache, key(chunk = 72, units = chunk72), chunk72)

        // The saved position names a logical chunk, and the entry answers with that chunk's own
        // units, in order — so resume is exact rather than an offset into another chunk's audio.
        val resumed = cache.load(key(chunk = 72, units = chunk72))
        assertNotNull(resumed)
        assertEquals(listOf(0, 1), resumed!!.ends.map { it.index })
        assertEquals(chunk72, resumed.ends.map { it.transcript })
        assertEquals(8L, resumed.committed)
        assertEquals(
            chunk71,
            cache.load(key(chunk = 71, units = chunk71))!!.ends.map { it.transcript },
        )
    }

    @Test
    fun anEntryWhoseRecordedKeyNoLongerMatchesIsDropped() {
        val cache = cache()
        val units = listOf("first unit", "second unit")
        store(cache, key(units = units), units)

        // Defence in depth: the directory name is derived from the key, so this can only happen if
        // the record was written by something else. It must still be refused rather than trusted.
        val meta = metaOf(cache, key(units = units))
        meta.writeText(meta.readText().replace("\"voice\":\"voice-a\"", "\"voice\":\"voice-b\""))
        assertNull(cache.load(key(units = units)))
        assertFalse(meta.isFile)
    }

    @Test
    fun anEntryWhoseAudioNoLongerMatchesItsRecordedLengthIsDropped() {
        val cache = cache()
        val units = listOf("first unit", "second unit")
        store(cache, key(units = units), units)

        // A truncated file would play a partial unit as if it were whole.
        cache.load(key(units = units))!!.audio.writeBytes(ByteArray(6))
        assertNull(cache.load(key(units = units)))
    }

    @Test
    fun anEntryWhoseUnitBoundariesDoNotCoverTheFileIsDropped() {
        val cache = cache()
        val units = listOf("first unit", "second unit")
        store(cache, key(units = units), units)
        val meta = metaOf(cache, key(units = units))
        val original = meta.readText()

        // A unit index out of order: the boundaries no longer describe this chunk.
        meta.writeText(original.replace("\"index\":1", "\"index\":2"))
        assertNull(cache.load(key(units = units)))

        store(cache, key(units = units), units)
        // The last unit no longer ends at the end of the file: audio would be lost or mislabelled.
        meta.writeText(meta.readText().replace("\"bytes\":8", "\"bytes\":6"))
        assertNull(cache.load(key(units = units)))
    }

    @Test
    fun anEntryWithABlankTranscriptIsDropped() {
        val cache = cache()
        val units = listOf("first unit", "second unit")
        store(cache, key(units = units), units)

        // The transcript is the reading text: a blank one would put an empty card on the page.
        val meta = metaOf(cache, key(units = units))
        meta.writeText(meta.readText().replace("\"transcript\":\"first unit\"", "\"transcript\":\"\""))
        assertNull(cache.load(key(units = units)))
    }

    @Test
    fun anUnreadableEntryIsAMissRatherThanAFailure() {
        val cache = cache()
        val units = listOf("first unit", "second unit")
        store(cache, key(units = units), units)
        val meta = metaOf(cache, key(units = units))

        meta.writeText("not json at all")
        assertNull(cache.load(key(units = units)))
        // And the unusable entry is gone, so the next run does not re-read it.
        assertFalse(meta.isFile)
    }

    // ---- what is not worth storing ----------------------------------------------------------

    @Test
    fun aChunkThatWasNotProducedInFullIsNotStored() {
        val cache = cache()
        val units = listOf("first unit", "second unit")
        val spool = ReaderSpool(spools, 0)
        spool.append(ByteArray(4))
        spool.endUnit(0, "first unit")
        // The producer was cancelled before the second unit: this is half a chunk, and storing it
        // would let a later Continue play it as if it were complete.
        try {
            assertFalse(cache.store(key(units = units), spool))
        } finally {
            spool.close()
        }
        assertNull(cache.load(key(units = units)))
    }

    @Test
    fun aChunkWithNoAudioIsNotStored() {
        val cache = cache()
        val spool = ReaderSpool(spools, 0)
        try {
            assertFalse(cache.store(key(units = emptyList()), spool))
        } finally {
            spool.close()
        }
    }

    @Test
    fun aChunkAlreadyRestoredFromTheCacheIsNotStoredAgain() {
        val cache = cache()
        val units = listOf("first unit", "second unit")
        store(cache, key(units = units), units)
        val entry = cache.load(key(units = units))!!

        // The spool reads the cache's own file, so it has nothing to hand over and must not delete it.
        ReaderSpool.fromCache(entry.audio, entry.committed, entry.ends).use { restored ->
            assertFalse(cache.store(key(units = units), restored))
        }
        assertNotNull(cache.load(key(units = units)))
        assertTrue(entry.audio.isFile)
    }

    // ---- bounds -----------------------------------------------------------------------------

    @Test
    fun onlyTheMostRecentlyUsedChunksOfABookAreKept() {
        val cache = cache(keepPerBook = 2)
        val units = listOf("first unit", "second unit")
        for (chunk in 0..2) store(cache, key(chunk = chunk, units = units), units)

        assertNull(cache.load(key(chunk = 0, units = units)))
        assertNotNull(cache.load(key(chunk = 1, units = units)))
        assertNotNull(cache.load(key(chunk = 2, units = units)))
    }

    @Test
    fun readingAChunkMakesItTheMostRecentlyUsed() {
        val cache = cache(keepPerBook = 2)
        val units = listOf("first unit", "second unit")
        store(cache, key(chunk = 0, units = units), units)
        store(cache, key(chunk = 1, units = units), units)
        // Reading chunk 0 makes it newer than chunk 1, so chunk 1 is the one to go.
        assertNotNull(cache.load(key(chunk = 0, units = units)))
        store(cache, key(chunk = 2, units = units), units)

        assertNotNull(cache.load(key(chunk = 0, units = units)))
        assertNull(cache.load(key(chunk = 1, units = units)))
    }

    @Test
    fun oneBookNeverEvictsAnotherBooksChunks() {
        val cache = cache(keepPerBook = 1)
        val units = listOf("first unit", "second unit")
        store(cache, key(chunk = 0, bookId = "book-a", units = units), units)
        store(cache, key(chunk = 0, bookId = "book-b", units = units), units)
        store(cache, key(chunk = 1, bookId = "book-b", units = units), units)

        assertNotNull(cache.load(key(chunk = 0, bookId = "book-a", units = units)))
        assertNull(cache.load(key(chunk = 0, bookId = "book-b", units = units)))
    }

    @Test
    fun theCacheStaysWithinItsByteBudget() {
        // Four bytes per chunk, a twelve-byte budget: the fourth store must push the oldest out.
        val cache = cache(keepPerBook = 8, maxBytes = 12)
        val units = listOf("first unit")
        for (chunk in 0..3) store(cache, key(chunk = chunk, units = units), units)

        assertNull(cache.load(key(chunk = 0, units = units)))
        assertNotNull(cache.load(key(chunk = 3, units = units)))
        val remaining = root.walkTopDown().filter { it.isFile && it.name == "audio.pcm" }.toList()
        assertEquals(3, remaining.size)
        assertEquals(12L, remaining.sumOf { it.length() })
    }

    @Test
    fun theChunkJustStoredIsNeverTheOneEvicted() {
        // A budget smaller than a single chunk: the entry that was just paid for must survive.
        val cache = cache(keepPerBook = 1, maxBytes = 1)
        val units = listOf("first unit", "second unit")
        store(cache, key(chunk = 0, units = units), units)
        assertNotNull(cache.load(key(chunk = 0, units = units)))
    }

    @Test
    fun aStagingDirectoryLeftBehindByAnInterruptedStoreIsCleared() {
        val cache = cache()
        val abandoned = File(root, "staging-999").apply { mkdirs() }
        assertTrue(abandoned.isDirectory)
        val units = listOf("first unit", "second unit")
        store(cache, key(units = units), units)
        assertFalse(abandoned.exists())
    }

    // ---- removal ----------------------------------------------------------------------------

    @Test
    fun removingABookDropsItsChunksAndLeavesEveryOtherBookAlone() {
        val cache = cache()
        val units = listOf("first unit", "second unit")
        store(cache, key(chunk = 0, bookId = "book-a", units = units), units)
        store(cache, key(chunk = 1, bookId = "book-a", units = units), units)
        store(cache, key(chunk = 0, bookId = "book-b", units = units), units)

        cache.remove("book-a")

        assertNull(cache.load(key(chunk = 0, bookId = "book-a", units = units)))
        assertNull(cache.load(key(chunk = 1, bookId = "book-a", units = units)))
        assertNotNull(cache.load(key(chunk = 0, bookId = "book-b", units = units)))
    }

    @Test
    fun removingAnUnknownBookIsHarmless() {
        val cache = cache()
        val units = listOf("first unit", "second unit")
        store(cache, key(units = units), units)
        cache.remove("never-stored")
        assertNotNull(cache.load(key(units = units)))
    }

    @Test
    fun clearingDropsEveryBook() {
        val cache = cache()
        val units = listOf("first unit", "second unit")
        store(cache, key(bookId = "book-a", units = units), units)
        store(cache, key(bookId = "book-b", units = units), units)
        cache.clear()
        assertNull(cache.load(key(bookId = "book-a", units = units)))
        assertNull(cache.load(key(bookId = "book-b", units = units)))
    }

    // ---- the fingerprint --------------------------------------------------------------------

    @Test
    fun theUnitFingerprintIsStableAndSeparatesDifferentText() {
        val units = listOf("first unit", "second unit")
        assertEquals(
            ReaderChunkCache.unitHash(units),
            ReaderChunkCache.unitHash(listOf("first unit", "second unit")),
        )
        // Running two units together must not hash like the two units themselves.
        assertFalse(ReaderChunkCache.unitHash(listOf("ab", "c")) == ReaderChunkCache.unitHash(listOf("a", "bc")))
        assertFalse(ReaderChunkCache.unitHash(units) == ReaderChunkCache.unitHash(units.reversed()))
        assertFalse(ReaderChunkCache.unitHash(emptyList()) == ReaderChunkCache.unitHash(listOf("")))
    }
}
