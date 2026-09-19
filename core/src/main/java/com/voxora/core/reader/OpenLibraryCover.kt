package com.voxora.core.reader

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The rules for a cover that did not come from the catalogue match itself.
 *
 * ## Why this is a separate decision
 *
 * A cover is the one piece of Book Intelligence a reader reads at a glance and never questions: an
 * unrelated image is worse than no image, because it is a claim about the book that no text
 * contradicts. So a fallback cover is only ever considered when there is **nothing to replace**, and
 * it is only ever looked up by an **identifier the document itself carried** — an ISBN that passed
 * its own check digit. A title-similarity search for an image is exactly the "random image service"
 * this avoids.
 *
 * ## Where the fallback comes from
 *
 * Open Library's cover endpoint, which is already one of the two configured catalogues. It is
 * identifier-addressed, so the image is tied to the book by the strongest signal a document can
 * carry rather than by a guess.
 */
object OpenLibraryCover {

    const val DEFAULT_BASE_URL = "https://covers.openlibrary.org"

    /** The medium size; enough for the library thumbnail and the Book Intelligence card. */
    private const val SIZE = "M"

    /**
     * The cover URL for [isbn], or null when it is not a valid ISBN.
     *
     * `default=false` is what makes the endpoint honest: without it Open Library answers with a
     * generic placeholder image for every ISBN it does not have, which would put a picture of
     * nothing on every unmatched book. With it, a missing cover is an error the caller can see.
     */
    fun urlForIsbn(isbn: String, baseUrl: String = DEFAULT_BASE_URL): String? {
        val normalized = BookSignalsReader.normalizeIsbn(isbn)
        if (!BookSignalsReader.isValidIsbn(normalized)) return null
        return "$baseUrl/b/isbn/$normalized-$SIZE.jpg?default=false"
    }

    /**
     * The cover to show: the verified one if there is one, otherwise the fallback.
     *
     * The order is the whole policy. A cover the catalogue returned is source-backed and is never
     * displaced by a lookup, so the fallback can only ever fill a gap.
     */
    fun preferred(verified: String?, fallback: String?): String? = verified ?: fallback
}

/**
 * Resolves and **verifies** a fallback cover for a document's ISBN.
 *
 * Verification is the point. The URL alone is not evidence that Open Library holds a cover for that
 * ISBN, and storing one that 404s would leave the card showing a placeholder that looks like a
 * loading failure. So the image is fetched once, bounded, and the URL is only returned when the
 * answer really is an image.
 *
 * Total, like every other optional network path in this feature: offline, a timeout, a rate limit, a
 * non-image body and a missing cover all end in null, and none of them reaches the import path.
 */
class OpenLibraryCoverLookup(
    private val client: OkHttpClient = MetadataHttp.defaultClient(),
    private val baseUrl: String = OpenLibraryCover.DEFAULT_BASE_URL,
) {

    suspend fun verifiedCover(isbn: String?): String? {
        val url = isbn?.takeIf { it.isNotBlank() }?.let { OpenLibraryCover.urlForIsbn(it, baseUrl) }
            ?: return null
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val contentType = response.header("Content-Type").orEmpty()
                    if (!contentType.startsWith("image/")) return@use null
                    // A body that is present but tiny is not a cover. A chunked response reports -1,
                    // which is accepted rather than rejected — the content type is the real gate.
                    val length = response.body?.contentLength() ?: -1L
                    if (length in 1 until MIN_COVER_BYTES) return@use null
                    url
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    private companion object {
        /** Below this a response is a tracking pixel or an error page, not a book cover. */
        const val MIN_COVER_BYTES = 1_024L
    }
}
