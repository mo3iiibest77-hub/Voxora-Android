package com.voxora.app.reader

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxora.app.R
import com.voxora.app.util.VoxoraLog
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A book cover, loaded asynchronously, with a graceful placeholder.
 *
 * ## Why this is hand-written
 *
 * Voxora ships no image-loading library, and adding one for a single thumbnail would be a large
 * dependency for a small job: fetch a URL, decode it, cache it, show nothing if anything fails.
 * That is what this is. It uses the OkHttp client the app already depends on and the platform's own
 * `BitmapFactory`.
 *
 * ## The rule it must never break
 *
 * **A missing cover never breaks the Reader.** Every step — no URL, no network, a timeout, an HTTP
 * error, a response that is not an image, an image that will not decode, no cache directory — ends
 * in the placeholder, never in an exception and never in a permanent loading state.
 *
 * The cache is two-level: an in-memory LRU for the visible list, and a small on-disk cache so a
 * cover already seen once is shown while offline. Both are bounded, and the disk cache lives in
 * `cacheDir`, so the system may clear it at any time without consequence.
 */
@Composable
internal fun ReaderCoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 64.dp,
) {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = url?.let { ReaderCoverLoader.load(context, it) }
    }
    Box(
        modifier = modifier
            .size(width = size, height = size * 1.5f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.reader_book_cover),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MenuBook,
                contentDescription = stringResource(R.string.reader_book_cover),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2),
            )
        }
    }
}

/**
 * The cover fetcher. Deliberately not a general-purpose image loader.
 *
 * Concurrent requests for the same URL are collapsed by [mutex] and the memory cache is checked
 * twice, so a list of books each asking for their cover performs one fetch per URL, not one per
 * recomposition.
 */
internal object ReaderCoverLoader {

    /** Covers are small; a dozen in memory is plenty for a library screen. */
    private const val MEMORY_ENTRIES = 12

    /** Anything larger than this is not a thumbnail, so it is shown but not written to disk. */
    private const val MAX_CACHE_BYTES = 2L * 1024 * 1024

    private const val CONNECT_TIMEOUT_SECONDS = 8L
    private const val READ_TIMEOUT_SECONDS = 10L

    private val memory = LruCache<String, ImageBitmap>(MEMORY_ENTRIES)
    private val mutex = Mutex()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CONNECT_TIMEOUT_SECONDS + READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** Returns the cover, or null when it cannot be shown. Never throws. */
    suspend fun load(context: Context, url: String): ImageBitmap? {
        if (url.isBlank()) return null
        memory.get(url)?.let { return it }
        return try {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    memory.get(url)?.let { return@withLock it }
                    val bytes = readFromDisk(context, url) ?: download(url)?.also { writeToDisk(context, url, it) }
                    if (bytes == null) return@withLock null
                    val decoded = try {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (_: OutOfMemoryError) {
                        null
                    } catch (_: Exception) {
                        null
                    }
                    decoded?.asImageBitmap()?.also { memory.put(url, it) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VoxoraLog.w(TAG, "Cover load failed: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun download(url: String): ByteArray? = try {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.bytes()?.takeIf { it.isNotEmpty() }
        }
    } catch (_: Exception) {
        null
    }

    /** The cover cache directory, or null when it cannot be created. */
    private fun cacheDir(context: Context): File? {
        val dir = File(context.cacheDir, "reader-covers")
        if (!dir.exists() && !dir.mkdirs()) return null
        return dir.takeIf { it.isDirectory }
    }

    private fun cacheFile(context: Context, url: String): File? =
        cacheDir(context)?.let { File(it, "${digest(url)}.img") }

    private fun readFromDisk(context: Context, url: String): ByteArray? = try {
        cacheFile(context, url)?.takeIf { it.isFile && it.length() > 0 }?.readBytes()
    } catch (_: Exception) {
        null
    }

    private fun writeToDisk(context: Context, url: String, bytes: ByteArray) {
        if (bytes.size > MAX_CACHE_BYTES) return
        try {
            cacheFile(context, url)?.writeBytes(bytes)
        } catch (_: Exception) {
            // A cache that cannot be written is not a failure; the memory cache still holds it.
        }
    }

    /** A stable file name for a URL. Hashing keeps the name short and path-safe. */
    private fun digest(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private const val TAG = "ReaderCover"
}
