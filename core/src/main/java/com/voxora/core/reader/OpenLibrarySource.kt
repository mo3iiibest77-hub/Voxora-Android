package com.voxora.core.reader

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * Book identification against **Open Library** — the fallback catalogue.
 *
 * References: <https://openlibrary.org/dev/docs/api/search> and
 * <https://openlibrary.org/dev/docs/api/books>.
 *
 * ## Why a fallback is worth having
 *
 * Google Books has no entry for plenty of works — small-press, older, non-English and
 * public-domain titles especially — and it is a single point of failure when its quota is reached.
 * Open Library is an independent catalogue with its own coverage, so a work Google cannot find is
 * still findable, and a Google quota rejection no longer means "this book cannot be identified".
 *
 * ## What it returns, and the two conversions it needs
 *
 * `search.json` is a *work*-level index: `title`, `author_name`, `first_publish_year`, `publisher`,
 * `subject`, `isbn`, `cover_i` and `number_of_pages_median`. Two fields do not arrive in the shape
 * the rest of the app uses, and both are converted here rather than leaking into the model:
 *
 * - **`language` is a MARC three-letter code** (`"eng"`, `"fre"`). [normalizeLanguage] maps the
 *   common ones to the ISO-639-1 code the app's language catalog speaks, and returns null for
 *   anything it does not recognise — a language code the UI cannot name is dropped, not displayed.
 * - **The cover is an id, not a URL.** `cover_i` becomes
 *   `https://covers.openlibrary.org/b/id/<id>-M.jpg`, the documented cover endpoint.
 *
 * `first_publish_year` is stored as-is (the year, as a string). It is the *first* publication of the
 * work, which is what the catalogue actually states; the app does not present it as the edition's
 * date.
 *
 * Pure JVM (OkHttp + `org.json`); no `android.*`.
 */
class OpenLibrarySource(
    private val client: OkHttpClient = MetadataHttp.defaultClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) : BookMetadataSource {

    override val provider: MetadataProvider = MetadataProvider.OPEN_LIBRARY

    override suspend fun search(signals: BookSignals): MetadataResult {
        val url = urlFor(signals) ?: return MetadataResult.NotFound
        return MetadataHttp.get(client, url, ::parse)
    }

    /**
     * The search URL, or null when there is nothing worth asking.
     *
     * An ISBN goes through the dedicated `isbn` parameter, which is the documented identifier
     * lookup; otherwise the title and author are sent as one free-text query. The `fields` list is
     * explicit so the response stays small and the parser's inputs are fixed.
     */
    fun urlFor(signals: BookSignals): String? {
        val base = baseUrl.toHttpUrlOrNull() ?: return null
        val builder = base.newBuilder().addPathSegments("search.json")
        val isbn = signals.isbn
        if (isbn != null) {
            builder.addQueryParameter("isbn", isbn)
        } else {
            val title = signals.title?.trim()?.takeIf { it.length >= MIN_TITLE_CHARS } ?: return null
            val author = signals.author?.trim()
            val query = if (author.isNullOrBlank()) title else "$title $author"
            builder.addQueryParameter("q", query)
        }
        return builder
            .addQueryParameter("fields", FIELDS)
            .addQueryParameter("limit", MAX_RESULTS.toString())
            .build()
            .toString()
    }

    /** Parses `docs[]`. A missing `docs` array is an empty candidate list, not a failure. */
    private fun parse(json: JSONObject): List<BookCandidate> {
        val docs = json.optJSONArray("docs") ?: return emptyList()
        val candidates = ArrayList<BookCandidate>(docs.length())
        for (i in 0 until docs.length()) {
            val doc = docs.optJSONObject(i) ?: continue
            val title = MetadataHttp.optionalString(doc, "title") ?: continue
            val identifiers = MetadataHttp.stringList(doc, "isbn")
            candidates.add(
                BookCandidate(
                    provider = MetadataProvider.OPEN_LIBRARY,
                    providerId = MetadataHttp.optionalString(doc, "key"),
                    title = title,
                    subtitle = MetadataHttp.optionalString(doc, "subtitle"),
                    authors = MetadataHttp.stringList(doc, "author_name"),
                    publisher = MetadataHttp.stringList(doc, "publisher").firstOrNull(),
                    publishedDate = MetadataHttp.optionalPositiveInt(doc, "first_publish_year")?.toString(),
                    // Open Library's search index has no synopsis field at all. Leaving this null is
                    // the honest answer: the UI then shows the facts it has and no description,
                    // rather than a description Voxora made up.
                    description = null,
                    categories = MetadataHttp.stringList(doc, "subject"),
                    language = MetadataHttp.stringList(doc, "language").firstNotNullOfOrNull(::normalizeLanguage),
                    pageCount = MetadataHttp.optionalPositiveInt(doc, "number_of_pages_median"),
                    isbn10 = identifiers.firstOrNull { it.filter(Char::isDigit).length == 10 },
                    isbn13 = identifiers.firstOrNull { it.filter(Char::isDigit).length == 13 },
                    coverUrl = coverUrl(doc),
                ),
            )
        }
        return candidates
    }

    /** `cover_i` → the documented cover endpoint. Only an https URL is ever stored. */
    private fun coverUrl(doc: JSONObject): String? {
        val id = MetadataHttp.optionalPositiveInt(doc, "cover_i") ?: return null
        return "https://covers.openlibrary.org/b/id/$id-M.jpg"
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://openlibrary.org"

        const val FIELDS = "key,title,subtitle,author_name,first_publish_year,publisher," +
            "subject,isbn,cover_i,number_of_pages_median,language"

        const val MAX_RESULTS = 5

        private const val MIN_TITLE_CHARS = 3

        /**
         * MARC-21 language codes (what Open Library stores) to the ISO-639-1 codes the app's
         * catalog uses. Deliberately partial: an unmapped code returns null and the language fact is
         * omitted, which is better than showing `"eng"` to a reader.
         */
        private val MARC_LANGUAGES = mapOf(
            "eng" to "en", "fre" to "fr", "fra" to "fr", "ger" to "de", "deu" to "de",
            "spa" to "es", "ita" to "it", "por" to "pt", "rus" to "ru", "ara" to "ar",
            "per" to "fa", "fas" to "fa", "nld" to "nl", "dut" to "nl", "jpn" to "ja",
            "chi" to "zh", "zho" to "zh", "kor" to "ko", "tur" to "tr", "pol" to "pl",
            "swe" to "sv", "dan" to "da", "nor" to "no", "fin" to "fi", "gre" to "el",
            "ell" to "el", "heb" to "he", "hin" to "hi", "urd" to "ur", "ben" to "bn",
            "ukr" to "uk", "ces" to "cs", "cze" to "cs", "ron" to "ro", "rum" to "ro",
            "hun" to "hu", "cat" to "ca", "ind" to "id", "vie" to "vi", "tha" to "th",
            "lat" to "la", "san" to "sa",
        )

        /** Maps a MARC code to ISO-639-1, or null when it is not recognised. */
        fun normalizeLanguage(code: String?): String? {
            val normalized = code?.trim()?.lowercase() ?: return null
            if (normalized.isEmpty()) return null
            if (normalized.length == 2) return normalized
            return MARC_LANGUAGES[normalized]
        }
    }
}
