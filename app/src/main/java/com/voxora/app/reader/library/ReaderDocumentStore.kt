package com.voxora.app.reader.library

import android.content.Context
import android.net.Uri
import com.voxora.app.util.VoxoraLog
import com.voxora.core.reader.ReaderDocumentLimits
import com.voxora.core.reader.ReaderSourceType
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The copy of an imported document that Voxora owns.
 *
 * ## Why the file is copied instead of the `Uri` being remembered
 *
 * The previous Reader remembered only the provider `Uri` of the last document. That is not a
 * durable reference: a `content://` URI is a handle into another app's storage, and it survives only
 * while the granting app keeps its side of the permission — a cleared cache, a re-installed provider
 * or a removed SD card breaks it, and the reader's book is simply gone. Persisting the permission
 * helps but does not make the reference Voxora's own.
 *
 * Copying the bytes into app-private storage at import time does make it Voxora's own, and it has a
 * second, load-bearing benefit: **re-extraction is deterministic**. The same file always produces
 * the same chunks, which is exactly what makes a persisted chunk index a valid resume point across
 * restarts.
 *
 * The copy is bounded by the same limit `TextExtractor` enforces — one rule, in
 * [ReaderDocumentLimits], consulted by both boundaries — so a book cannot fill the device, and it
 * is deleted when the book is removed from the library.
 *
 * All file work runs on [Dispatchers.IO]. Nothing here touches the narration path.
 */
class ReaderDocumentStore(private val context: Context) {

    private val root = File(context.filesDir, DIRECTORY)

    /** The directory a book's file lives in, created on demand. */
    fun directory(): File = root.also { if (!it.exists()) it.mkdirs() }

    /**
     * Copies [source] into app storage under [id].
     *
     * @return the copied file, or null when the source could not be read — in which case nothing was
     *   left behind and the caller reports the ordinary extraction failure.
     */
    suspend fun copy(source: Uri, id: String, type: ReaderSourceType): File? =
        withContext(Dispatchers.IO) {
            val target = File(directory(), "$id.${type.id}")
            try {
                val stream = context.contentResolver.openInputStream(source) ?: return@withContext null
                stream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (ReaderDocumentLimits.exceeds(total)) {
                                VoxoraLog.w(TAG, "Imported document exceeds the storage limit")
                                target.delete()
                                return@withContext null
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (target.length() == 0L) {
                    target.delete()
                    return@withContext null
                }
                target
            } catch (e: Exception) {
                VoxoraLog.w(TAG, "Document copy failed: ${e.javaClass.simpleName}")
                target.delete()
                null
            }
        }

    /** The file a stored path points at, or null when it is missing or outside app storage. */
    fun file(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val candidate = File(path)
        if (!candidate.isFile) return null
        // A record whose path escapes the reader directory is treated as missing rather than read.
        val canonicalRoot = runCatching { directory().canonicalPath }.getOrNull() ?: return null
        val canonical = runCatching { candidate.canonicalPath }.getOrNull() ?: return null
        return candidate.takeIf { canonical.startsWith(canonicalRoot) }
    }

    /** Deletes a book's file. A failure is logged and ignored: the record is what matters. */
    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        val file = file(path) ?: return@withContext
        try {
            if (!file.delete()) VoxoraLog.w(TAG, "Document file could not be deleted")
        } catch (e: IOException) {
            VoxoraLog.w(TAG, "Document delete failed: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "ReaderLibrary"
        const val DIRECTORY = "reader/books"
    }
}
