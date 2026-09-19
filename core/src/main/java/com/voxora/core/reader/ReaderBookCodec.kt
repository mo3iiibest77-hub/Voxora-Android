package com.voxora.core.reader

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The on-disk form of the Reader library: a versioned JSON document, encoded and decoded by hand.
 *
 * ## Why hand-written and why tolerant
 *
 * The library is a handful of small records, so it does not need a database, and Voxora has no
 * serialization library it already depends on — `org.json` ships with Android. A hand-written codec
 * also lets the decode side be **total**: a record that is malformed, truncated or written by a
 * newer build is *skipped*, never allowed to take the whole library with it. Losing one book is
 * recoverable; losing the library because one field failed to parse is not.
 *
 * ## What is deliberately absent
 *
 * No document text and no PCM. The record stores a path to a copy Voxora owns, so the library stays
 * small no matter how large the books are. Long provider descriptions are the only variable-size
 * field, and they are capped at [MAX_DESCRIPTION_CHARS] so one verbose catalogue entry cannot make
 * the store grow without bound.
 *
 * Pure JVM apart from `org.json`, which Android provides and the `:core` test source set supplies
 * a real implementation for — so round-tripping is unit-tested without a device.
 */
object ReaderBookCodec {

    /** Bumped only when the shape changes incompatibly. Decoding is best-effort across versions. */
    const val VERSION = 1

    /** A provider description longer than this is truncated rather than stored whole. */
    const val MAX_DESCRIPTION_CHARS = 4_000

    /** Encodes the whole library. An empty library encodes to a valid document with no books. */
    fun encode(books: List<ReaderBook>): String {
        val array = JSONArray()
        for (book in books) array.put(encodeBook(book))
        return JSONObject()
            .put("version", VERSION)
            .put("books", array)
            .toString()
    }

    /**
     * Decodes a stored document.
     *
     * Returns an empty list for blank input, for a document that is not an object, and for one whose
     * `books` is missing or not an array. Individual malformed records are dropped silently: the
     * library's contract is "every book that could be read is returned", not "all or nothing".
     */
    fun decode(raw: String?): List<ReaderBook> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = try {
            JSONObject(raw)
        } catch (_: JSONException) {
            return emptyList()
        }
        val array = root.optJSONArray("books") ?: return emptyList()
        val books = ArrayList<ReaderBook>(array.length())
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            decodeBook(entry)?.let(books::add)
        }
        return books
    }

    private fun encodeBook(book: ReaderBook): JSONObject = JSONObject()
        .put("id", book.id)
        .put("localPath", book.localPath)
        .put("title", book.title)
        .put("sourceType", book.sourceType.id)
        .put("chunkCount", book.chunkCount)
        .put("currentChunk", book.currentChunk)
        .put("state", book.state.id)
        .put("importedAt", book.importedAt)
        .put("lastReadAt", book.lastReadAt)
        .put("lookup", book.lookup.id)
        .put("signals", book.signals?.let(::encodeSignals))
        .put("metadata", book.metadata?.let(::encodeMetadata))

    private fun encodeSignals(signals: BookSignals): JSONObject = JSONObject()
        .put("isbn13", signals.isbn13)
        .put("isbn10", signals.isbn10)
        .put("title", signals.title)
        .put("author", signals.author)
        .put("filename", signals.filename)

    private fun encodeMetadata(metadata: BookMetadata): JSONObject = JSONObject()
        .put("provider", metadata.provider.id)
        .put("providerId", metadata.providerId)
        .put("title", metadata.title)
        .put("subtitle", metadata.subtitle)
        .put("authors", JSONArray(metadata.authors))
        .put("publisher", metadata.publisher)
        .put("publishedDate", metadata.publishedDate)
        .put("description", metadata.description?.take(MAX_DESCRIPTION_CHARS))
        .put("categories", JSONArray(metadata.categories))
        .put("language", metadata.language)
        .put("pageCount", metadata.pageCount ?: JSONObject.NULL)
        .put("isbn10", metadata.isbn10)
        .put("isbn13", metadata.isbn13)
        .put("coverUrl", metadata.coverUrl)
        .put("confidence", metadata.confidence.id)
        .put("fetchedAt", metadata.fetchedAt)

    private fun decodeBook(entry: JSONObject): ReaderBook? {
        // Identity and the file it points at are the only fields a record cannot work without.
        val id = entry.optionalString("id") ?: return null
        val localPath = entry.optionalString("localPath") ?: return null
        val chunkCount = entry.optInt("chunkCount", 0).coerceAtLeast(0)
        val currentChunk = entry.optInt("currentChunk", 0).coerceIn(0, maxOf(0, chunkCount - 1))
        val importedAt = entry.optLong("importedAt", 0L)
        return ReaderBook(
            id = id,
            localPath = localPath,
            title = entry.optionalString("title") ?: "",
            sourceType = ReaderSourceType.normalize(entry.optionalString("sourceType")),
            chunkCount = chunkCount,
            currentChunk = currentChunk,
            state = ReaderBookState.normalize(entry.optionalString("state")),
            importedAt = importedAt,
            lastReadAt = entry.optLong("lastReadAt", importedAt),
            metadata = entry.optJSONObject("metadata")?.let(::decodeMetadata),
            lookup = MetadataLookupState.normalize(entry.optionalString("lookup")),
            signals = entry.optJSONObject("signals")?.let(::decodeSignals),
        )
    }

    private fun decodeSignals(json: JSONObject): BookSignals = BookSignals(
        isbn13 = json.optionalString("isbn13"),
        isbn10 = json.optionalString("isbn10"),
        title = json.optionalString("title"),
        author = json.optionalString("author"),
        filename = json.optionalString("filename") ?: "",
    )

    private fun decodeMetadata(json: JSONObject): BookMetadata? {
        val title = json.optionalString("title") ?: return null
        val provider = MetadataProvider.normalize(json.optionalString("provider")) ?: return null
        return BookMetadata(
            provider = provider,
            providerId = json.optionalString("providerId"),
            title = title,
            subtitle = json.optionalString("subtitle"),
            authors = json.stringList("authors"),
            publisher = json.optionalString("publisher"),
            publishedDate = json.optionalString("publishedDate"),
            description = json.optionalString("description"),
            categories = json.stringList("categories"),
            language = json.optionalString("language"),
            // Stored as JSON null when unknown, so a missing page count stays missing rather than
            // becoming the 0 that would be shown as a real page count.
            pageCount = if (json.isNull("pageCount")) null else json.optInt("pageCount").takeIf { it > 0 },
            isbn10 = json.optionalString("isbn10"),
            isbn13 = json.optionalString("isbn13"),
            coverUrl = json.optionalString("coverUrl"),
            confidence = MatchConfidence.normalize(json.optionalString("confidence")),
            fetchedAt = json.optLong("fetchedAt", 0L),
        )
    }

    /**
     * A string field, or null when it is absent, JSON null or blank.
     *
     * `optString` alone cannot be used: it returns the literal `"null"` for a JSON null, which would
     * turn "unknown author" into an author named "null".
     */
    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key, "").trim()
        return value.ifEmpty { null }
    }

    /** A list of strings from a JSON array; a non-array or a missing field yields an empty list. */
    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        val values = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val value = array.optString(i, "").trim()
            if (value.isNotEmpty()) values.add(value)
        }
        return values
    }
}
