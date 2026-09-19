package com.voxora.app.reader

import com.voxora.app.util.VoxoraLog
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The PCM audio of one chunk, and the transcript of every unit inside it.
 *
 * ## Two ways to get one
 *
 * - **Produced.** [ReaderSpool] creates a temp file in the cache directory and the producer appends
 *   each unit's audio to it as Gemini streams it. The spool owns that file and deletes it when it
 *   closes, so a run leaves nothing behind.
 * - **Restored.** [fromCache] wraps a file that [ReaderChunkCache] already holds, with the unit
 *   boundaries it recorded. Such a spool is complete from the moment it exists, has no producer, and
 *   does **not** own its file: closing it must leave the entry for the next Stop → Continue, which is
 *   the whole point of having persisted it.
 *
 * The two share every read path, so a chunk replayed from disk is consumed exactly like a chunk just
 * synthesised — same unit boundaries, same transcripts, same ordering.
 */
internal class ReaderSpool private constructor(
    private val file: File,
    val firstUnit: Int,
    /**
     * Whether [close] owns [file].
     *
     * True for a spool a run produced; false for one restored from [ReaderChunkCache], whose file
     * belongs to the cache and must survive the run.
     */
    private val ownsFile: Boolean,
    initial: Snapshot,
) : Closeable {
    data class UnitEnd(val index: Int, val bytes: Long, val transcript: String)
    data class Failure(val unit: Int, val cause: Exception, val partial: Boolean)
    data class Snapshot(
        val committed: Long = 0,
        val ends: List<UnitEnd> = emptyList(),
        val complete: Boolean = false,
        val failure: Failure? = null,
    ) {
        fun canRetryPrefetch(consumedBytes: Long): Boolean =
            consumedBytes == 0L && committed == 0L && failure != null && ends.isEmpty() && failure.cause !is CapacityException
    }

    constructor(cacheDir: File, firstUnit: Int) : this(
        file = createTemp(cacheDir),
        firstUnit = firstUnit,
        ownsFile = true,
        initial = Snapshot(),
    )

    init {
        require(firstUnit >= 0)
    }

    private val storage = try {
        RandomAccessFile(file, "rw")
    } catch (e: Exception) {
        if (ownsFile && !file.delete()) VoxoraLog.w("ReaderSpool", "Could not delete unopened spool")
        throw e
    }
    private val lock = Any()
    private var closed = false
    private val mutableState = MutableStateFlow(initial)
    val state = mutableState.asStateFlow()

    fun append(pcm: ByteArray) = synchronized(lock) {
        check(!closed && !state.value.complete && state.value.failure == null)
        require(pcm.size % 2 == 0)
        val before = state.value
        if (pcm.size > MAX_BYTES - before.committed) throw CapacityException()
        storage.seek(before.committed)
        storage.write(pcm)
        mutableState.value = before.copy(committed = before.committed + pcm.size)
    }

    fun endUnit(index: Int, transcript: String) = synchronized(lock) {
        check(!closed && !state.value.complete && state.value.failure == null)
        val before = state.value
        val start = before.ends.lastOrNull()?.bytes ?: 0L
        check(index == firstUnit + before.ends.size && before.committed > start)
        mutableState.value = before.copy(ends = before.ends + UnitEnd(index, before.committed, transcript))
    }

    fun finish() = synchronized(lock) {
        check(!closed && state.value.failure == null)
        check(state.value.ends.lastOrNull()?.bytes == state.value.committed)
        mutableState.value = state.value.copy(complete = true)
    }

    fun fail(unit: Int, cause: Exception) = synchronized(lock) {
        check(!closed && !state.value.complete)
        val before = state.value
        if (before.failure != null) return@synchronized
        check(unit == firstUnit + before.ends.size)
        val start = before.ends.lastOrNull()?.bytes ?: 0L
        mutableState.value = before.copy(failure = Failure(unit, cause, before.committed > start))
    }

    fun read(position: Long, limit: Long): ByteArray = synchronized(lock) {
        check(!closed)
        require(position >= 0 && position % 2 == 0L && limit % 2 == 0L)
        require(limit > position && position < state.value.committed)
        val count = minOf(9_600L, limit - position, state.value.committed - position).toInt()
        require(count > 0 && count % 2 == 0)
        ByteArray(count).also {
            storage.seek(position)
            storage.readFully(it)
        }
    }

    /**
     * Hands the backing file to the caller **without deleting it**.
     *
     * This is what lets a produced chunk become a cache entry without copying its audio: the caller
     * renames the file it receives. Returns null when this spool does not own its file — it was
     * restored from the cache, so the entry is already exactly where it belongs — or when it is
     * already closed. A successful detach closes the spool, so a later [close] is a no-op and can
     * never delete the file the caller now holds.
     */
    fun detach(): File? = synchronized(lock) {
        if (closed || !ownsFile) return@synchronized null
        closed = true
        try {
            storage.close()
        } catch (e: Exception) {
            VoxoraLog.w("ReaderSpool", "Spool detach close failed: ${e.javaClass.simpleName}")
            if (!file.delete()) VoxoraLog.w("ReaderSpool", "Spool deletion failed")
            return@synchronized null
        }
        file
    }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        try {
            storage.close()
        } catch (e: Exception) {
            VoxoraLog.w("ReaderSpool", "Spool close failed: ${e.javaClass.simpleName}")
        } finally {
            if (ownsFile && !file.delete()) VoxoraLog.w("ReaderSpool", "Spool deletion failed")
        }
    }

    class CapacityException : IllegalStateException("Reader PCM disk capacity exceeded")

    companion object {
        const val MAX_BYTES = 28_800_000L

        private fun createTemp(cacheDir: File): File =
            File.createTempFile("voxora-reader-", ".pcm", cacheDir)

        /**
         * A spool over a chunk that is already complete on disk.
         *
         * [ends] must describe the whole chunk — every unit, in order, the last one ending exactly at
         * [committed] — because a spool restored from a partial or inconsistent entry would play one
         * unit's audio under another unit's text. The caller validates the entry before calling this;
         * the checks here are the last line of defence.
         */
        fun fromCache(file: File, committed: Long, ends: List<UnitEnd>): ReaderSpool {
            require(committed > 0L)
            require(ends.isNotEmpty() && ends.last().bytes == committed)
            return ReaderSpool(
                file = file,
                firstUnit = 0,
                ownsFile = false,
                initial = Snapshot(committed = committed, ends = ends, complete = true),
            )
        }

        fun removeOrphans(cacheDir: File) {
            cacheDir.listFiles { file -> file.name.startsWith("voxora-reader-") && file.extension == "pcm" }
                ?.forEach { if (!it.delete()) VoxoraLog.w("ReaderSpool", "Orphan spool deletion failed") }
        }
    }
}
