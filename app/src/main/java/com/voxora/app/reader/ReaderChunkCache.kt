package com.voxora.app.reader

import com.voxora.app.util.VoxoraLog
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * The persisted cache of whole narration chunks: the audio, and the transcript of every unit in it.
 *
 * ## Why it exists
 *
 * A chunk costs a Gemini round trip to produce, and the reader's most common action is to stop and
 * start again. Without this, every Continue re-synthesised the chunk the reader was already
 * listening to — the same words, the same voice, paid for twice. With it, Continue plays from disk.
 *
 * ## What is stored, and what makes a stored chunk reusable
 *
 * One directory per chunk, holding `audio.pcm` and a `meta.json` that records the key the chunk was
 * produced under. A stored chunk is reused only when **every** part of that key still matches:
 *
 * - the book, the chunk index, the chunk count and the number of units — so a position can never
 *   resolve to a different chunk of a re-imported document;
 * - a hash of the units' own text, so a re-extraction that produced different words is a different
 *   chunk even at the same index;
 * - the output language, the narration mode and the voice — the three choices that decide what the
 *   audio actually says and sounds like;
 * - the model, so audio from a model the app no longer uses is not replayed as if it were current.
 *
 * The unit boundaries are validated too, not just trusted: every unit must be present in order, its
 * end must move forward, and the last one must land exactly at the end of the file. A cache entry
 * that fails any of this is **deleted and treated as a miss** — never partially restored, because a
 * partially restored chunk would play one unit's audio under another unit's text.
 *
 * ## Bounded and deterministic
 *
 * Two entries per book, and a byte budget across all books; the least recently used entries go first.
 * The entry just written is never the one evicted. Nothing here grows without limit, and nothing here
 * is timing-dependent.
 *
 * ## Not on the audio path
 *
 * [load] is called while a chunk is being prepared, [store] once a chunk has finished — never from a
 * PCM callback. A store hands over the spool's existing file with a rename rather than copying it, so
 * a ~10 MB chunk costs a directory entry, not a disk write that would stall the next chunk.
 *
 * Pure JVM (no `android.*`) so the reuse and eviction rules are unit-testable.
 */
internal class ReaderChunkCache(
    private val root: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
    /** How many chunks of one book are worth keeping. */
    private val keepPerBook: Int = KEEP_PER_BOOK,
    /** The total size the cache may occupy. */
    private val maxBytes: Long = MAX_BYTES,
) {

    /**
     * Everything that makes a stored chunk the chunk the reader is asking for.
     *
     * A key is compared field by field; there is no "close enough".
     */
    data class Key(
        val bookId: String,
        val chunk: Int,
        val chunkCount: Int,
        val unitCount: Int,
        val unitHash: String,
        val language: String,
        val mode: String,
        val voice: String,
        val model: String,
    )

    /** A validated entry: the audio file, its exact length, and the boundary of every unit. */
    data class Entry(
        val audio: File,
        val committed: Long,
        val ends: List<ReaderSpool.UnitEnd>,
    )

    /**
     * The stored chunk for [key], or null when there is none — or when what is stored no longer
     * matches, in which case the unusable entry is deleted rather than left to be re-checked.
     */
    fun load(key: Key): Entry? {
        val dir = entryDir(key)
        if (!dir.isDirectory) return null
        val stored = readMeta(dir) ?: return discard(dir)
        if (!stored.matches(key)) return discard(dir)
        val audio = File(dir, AUDIO)
        if (!audio.isFile || audio.length() != stored.committed) return discard(dir)
        if (!boundariesAreSound(stored.ends, key.unitCount, stored.committed)) return discard(dir)
        touch(dir, stored)
        return Entry(audio = audio, committed = stored.committed, ends = stored.ends)
    }

    /**
     * Persists [spool] under [key], taking ownership of its file.
     *
     * Refuses anything that is not a whole chunk: a spool whose producer was cancelled mid-unit has
     * no boundary for its last unit, and storing it would let a later Continue play a truncated chunk
     * as if it were complete. A spool restored from the cache is refused too — [ReaderSpool.detach]
     * returns null for it, because the entry it reads is already stored.
     *
     * @return true when the entry was published.
     */
    fun store(key: Key, spool: ReaderSpool): Boolean {
        val snapshot = spool.state.value
        if (snapshot.committed <= 0L) return false
        if (snapshot.ends.size != key.unitCount) return false
        if (snapshot.ends.lastOrNull()?.bytes != snapshot.committed) return false
        val audio = spool.detach() ?: return false
        val staging = File(root, "$STAGING_PREFIX${clock()}-${System.nanoTime()}")
        var published = false
        try {
            if (staging.mkdirs() && audio.renameTo(File(staging, AUDIO))) {
                writeMeta(staging, key, snapshot)
                val target = entryDir(key)
                target.parentFile?.mkdirs()
                // Replace in place: the entry is either the old chunk or the new one, never a mix of
                // the two. A concurrent reader that arrives in the gap sees a miss and re-synthesises,
                // which is a slower answer, not a wrong one.
                if ((!target.exists() || target.deleteRecursively()) && staging.renameTo(target)) {
                    evict(target)
                    published = true
                }
            }
        } catch (e: Exception) {
            VoxoraLog.w(TAG, "Chunk cache store failed: ${e.javaClass.simpleName}")
        } finally {
            if (!published) {
                staging.deleteRecursively()
                // The detached file is no longer reachable by its old name, so it is removed here
                // rather than left as an orphan.
                if (audio.exists() && !audio.delete()) VoxoraLog.w(TAG, "Chunk cache staging left behind")
            }
        }
        return published
    }

    /**
     * Drops every cached chunk of one book.
     *
     * Called when the book itself is removed: an entry keyed by a book that no longer exists could
     * never be read again, so keeping it would only be dead weight on the device.
     */
    fun remove(bookId: String) {
        deleteTree(File(root, bookHash(bookId)))
    }

    /** Drops every cached chunk, for every book. */
    fun clear() {
        deleteTree(root)
    }

    // ---- layout -----------------------------------------------------------------------------

    private fun entryDir(key: Key): File = File(File(root, bookHash(key.bookId)), entryHash(key))

    private fun bookHash(bookId: String): String = digest("book\u0000$bookId").take(16)

    private fun entryHash(key: Key): String = digest(
        listOf(
            key.chunk,
            key.chunkCount,
            key.unitCount,
            key.unitHash,
            key.language,
            key.mode,
            key.voice,
            key.model,
        ).joinToString("\u0000"),
    ).take(32)

    // ---- metadata ---------------------------------------------------------------------------

    private class Stored(
        val bookId: String,
        val chunk: Int,
        val chunkCount: Int,
        val unitCount: Int,
        val unitHash: String,
        val language: String,
        val mode: String,
        val voice: String,
        val model: String,
        val committed: Long,
        val ends: List<ReaderSpool.UnitEnd>,
        val lastUsedAt: Long,
    ) {
        fun matches(key: Key): Boolean =
            bookId == key.bookId &&
                chunk == key.chunk &&
                chunkCount == key.chunkCount &&
                unitCount == key.unitCount &&
                unitHash == key.unitHash &&
                language == key.language &&
                mode == key.mode &&
                voice == key.voice &&
                model == key.model
    }

    private fun readMeta(dir: File): Stored? {
        val file = File(dir, META)
        if (!file.isFile) return null
        return try {
            val json = JSONObject(file.readText())
            val endsJson = json.optJSONArray(ENDS) ?: return null
            val ends = ArrayList<ReaderSpool.UnitEnd>(endsJson.length())
            for (i in 0 until endsJson.length()) {
                val end = endsJson.optJSONObject(i) ?: return null
                val transcript = end.optString(TRANSCRIPT, "")
                if (transcript.isBlank()) return null
                ends.add(
                    ReaderSpool.UnitEnd(
                        index = end.optInt(INDEX, -1),
                        bytes = end.optLong(BYTES, -1L),
                        transcript = transcript,
                    ),
                )
            }
            Stored(
                bookId = json.optString(BOOK_ID, ""),
                chunk = json.optInt(CHUNK, -1),
                chunkCount = json.optInt(CHUNK_COUNT, -1),
                unitCount = json.optInt(UNIT_COUNT, -1),
                unitHash = json.optString(UNIT_HASH, ""),
                language = json.optString(LANGUAGE, ""),
                mode = json.optString(MODE, ""),
                voice = json.optString(VOICE, ""),
                model = json.optString(MODEL, ""),
                committed = json.optLong(COMMITTED, -1L),
                ends = ends,
                lastUsedAt = json.optLong(LAST_USED_AT, 0L),
            )
        } catch (e: Exception) {
            VoxoraLog.w(TAG, "Chunk cache entry unreadable: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun writeMeta(dir: File, key: Key, snapshot: ReaderSpool.Snapshot) {
        val ends = JSONArray()
        for (end in snapshot.ends) {
            ends.put(
                JSONObject()
                    .put(INDEX, end.index)
                    .put(BYTES, end.bytes)
                    .put(TRANSCRIPT, end.transcript),
            )
        }
        val json = JSONObject()
            .put(BOOK_ID, key.bookId)
            .put(CHUNK, key.chunk)
            .put(CHUNK_COUNT, key.chunkCount)
            .put(UNIT_COUNT, key.unitCount)
            .put(UNIT_HASH, key.unitHash)
            .put(LANGUAGE, key.language)
            .put(MODE, key.mode)
            .put(VOICE, key.voice)
            .put(MODEL, key.model)
            .put(COMMITTED, snapshot.committed)
            .put(ENDS, ends)
            .put(LAST_USED_AT, clock())
        File(dir, META).writeText(json.toString())
    }

    /** Records that an entry was just used, so eviction keeps the chunks the reader is actually on. */
    private fun touch(dir: File, stored: Stored) {
        try {
            val file = File(dir, META)
            val json = JSONObject(file.readText()).put(LAST_USED_AT, clock())
            file.writeText(json.toString())
        } catch (e: Exception) {
            // A failed touch only costs eviction accuracy; the entry itself is still valid.
            VoxoraLog.w(TAG, "Chunk cache touch failed: ${e.javaClass.simpleName}")
        }
    }

    /**
     * True when [ends] describes the whole chunk: every unit present once, in order, each ending
     * further into the file than the last, and the last ending exactly at [committed].
     */
    private fun boundariesAreSound(
        ends: List<ReaderSpool.UnitEnd>,
        unitCount: Int,
        committed: Long,
    ): Boolean {
        if (ends.size != unitCount) return false
        var previous = 0L
        ends.forEachIndexed { position, end ->
            if (end.index != position) return false
            if (end.bytes <= previous || end.bytes > committed) return false
            if (end.transcript.isBlank()) return false
            previous = end.bytes
        }
        return ends.last().bytes == committed
    }

    // ---- eviction ---------------------------------------------------------------------------

    private class Found(val dir: File, val bookHash: String, val bytes: Long, val lastUsedAt: Long)

    /**
     * Keeps the cache inside its two limits: [keepPerBook] entries per book, and [maxBytes] in total
     * across all books. [protectedDir] is the entry just written, which is never evicted — otherwise
     * a chunk larger than the budget would be deleted the instant it was stored.
     */
    private fun evict(protectedDir: File) {
        // A store that died mid-way can leave a staging directory behind; the next store is the
        // natural place to clear it.
        root.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(STAGING_PREFIX) }
            ?.forEach { deleteTree(it) }

        val entries = scan()
        if (entries.isEmpty()) return
        val keep = mutableSetOf<File>()
        entries.groupBy { it.bookHash }.values.forEach { forBook ->
            forBook.sortedByDescending { it.lastUsedAt }.take(keepPerBook).forEach { keep.add(it.dir) }
        }
        keep.add(protectedDir)

        var total = entries.filter { it.dir in keep }.sumOf { it.bytes }
        val candidates = entries.filter { it.dir in keep && it.dir != protectedDir }.sortedBy { it.lastUsedAt }
        for (entry in candidates) {
            if (total <= maxBytes) break
            keep.remove(entry.dir)
            total -= entry.bytes
        }
        entries.filter { it.dir !in keep }.forEach { deleteTree(it.dir) }
    }

    private fun scan(): List<Found> {
        val found = mutableListOf<Found>()
        for (bookDir in root.listFiles().orEmpty()) {
            if (!bookDir.isDirectory || bookDir.name.startsWith(STAGING_PREFIX)) continue
            for (entryDir in bookDir.listFiles().orEmpty()) {
                if (!entryDir.isDirectory) continue
                val stored = readMeta(entryDir) ?: continue
                val audio = File(entryDir, AUDIO)
                if (!audio.isFile) continue
                found.add(Found(entryDir, bookDir.name, audio.length(), stored.lastUsedAt))
            }
        }
        return found
    }

    private fun discard(dir: File): Entry? {
        deleteTree(dir)
        return null
    }

    private fun deleteTree(dir: File) {
        try {
            if (dir.exists() && !dir.deleteRecursively()) {
                VoxoraLog.w(TAG, "Chunk cache deletion failed")
            }
        } catch (e: Exception) {
            VoxoraLog.w(TAG, "Chunk cache deletion failed: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        /** The directory under `cacheDir` that holds every entry. */
        const val DIRECTORY = "reader-chunks"

        /** How many chunks of one book are worth keeping: the one being read, and the next one. */
        const val KEEP_PER_BOOK = 2

        /** The total size the cache may occupy. Well under a typical device cache allowance. */
        const val MAX_BYTES = 64L * 1024L * 1024L

        private const val AUDIO = "audio.pcm"
        private const val META = "meta.json"
        private const val STAGING_PREFIX = "staging-"
        private const val TAG = "ReaderChunkCache"

        private const val BOOK_ID = "bookId"
        private const val CHUNK = "chunk"
        private const val CHUNK_COUNT = "chunkCount"
        private const val UNIT_COUNT = "unitCount"
        private const val UNIT_HASH = "unitHash"
        private const val LANGUAGE = "language"
        private const val MODE = "mode"
        private const val VOICE = "voice"
        private const val MODEL = "model"
        private const val COMMITTED = "committed"
        private const val ENDS = "ends"
        private const val INDEX = "index"
        private const val BYTES = "bytes"
        private const val TRANSCRIPT = "transcript"
        private const val LAST_USED_AT = "lastUsedAt"

        /**
         * A stable fingerprint of a chunk's source text.
         *
         * Length-prefixed so no two different lists of units can hash the same way by running their
         * text together. It is what makes a re-extraction that produced different words a different
         * chunk, even at the same index.
         */
        fun unitHash(units: List<String>): String = try {
            val digest = MessageDigest.getInstance("SHA-256")
            for (unit in units) {
                val bytes = unit.toByteArray(Charsets.UTF_8)
                digest.update(
                    byteArrayOf(
                        (bytes.size ushr 24).toByte(),
                        (bytes.size ushr 16).toByte(),
                        (bytes.size ushr 8).toByte(),
                        bytes.size.toByte(),
                    ),
                )
                digest.update(bytes)
            }
            hex(digest.digest())
        } catch (e: Exception) {
            // SHA-256 is present on every Android platform. This fallback exists only so a missing
            // provider could never fail a narration run; it is still a fingerprint of the same text.
            units.joinToString("\u0000").hashCode().toString()
        }

        private fun digest(text: String): String =
            hex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))

        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
