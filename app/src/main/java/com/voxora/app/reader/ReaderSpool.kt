package com.voxora.app.reader

import com.voxora.app.util.VoxoraLog
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ReaderSpool(cacheDir: File, val firstUnit: Int) : Closeable {
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

    init {
        require(firstUnit >= 0)
    }

    private val file = File.createTempFile("voxora-reader-", ".pcm", cacheDir)
    private val storage = try {
        RandomAccessFile(file, "rw")
    } catch (e: Exception) {
        if (!file.delete()) VoxoraLog.w("ReaderSpool", "Could not delete unopened spool")
        throw e
    }
    private val lock = Any()
    private var closed = false
    private val mutableState = MutableStateFlow(Snapshot())
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

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        try {
            storage.close()
        } catch (e: Exception) {
            VoxoraLog.w("ReaderSpool", "Spool close failed: ${e.javaClass.simpleName}")
        } finally {
            if (!file.delete()) VoxoraLog.w("ReaderSpool", "Spool deletion failed")
        }
    }

    class CapacityException : IllegalStateException("Reader PCM disk capacity exceeded")

    companion object {
        const val MAX_BYTES = 28_800_000L

        fun removeOrphans(cacheDir: File) {
            cacheDir.listFiles { file -> file.name.startsWith("voxora-reader-") && file.extension == "pcm" }
                ?.forEach { if (!it.delete()) VoxoraLog.w("ReaderSpool", "Orphan spool deletion failed") }
        }
    }
}
