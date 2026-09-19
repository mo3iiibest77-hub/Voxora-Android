package com.voxora.core.reader

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * Book identification against the **Google Books Volumes API** — the primary catalogue.
 *
 * Reference: <https://developers.google.com/books/docs/v1/using> (`volumes.list`).
 *
 * ## Why this one is asked first
 *
 * It indexes by identifier as well as by text, it returns `industryIdentifiers` (so an ISBN in the
 * document can be matched *exactly* rather than by title), and its `volumeInfo` carries the fields
 * the "About This Book" section needs — authors, publisher, published date, description, categories,
 * language, page count and cover thumbnails. Nothing else in this feature needs a Google API and no
 * Google account, OAuth token or Gemini API key is involved: `volumes.list` is a public read.
 *
 * ## The query, and why it is built this way
 *
 * - An **ISBN is the strongest possible query**, so when the document yielded one the search is
 *   `isbn:<isbn>` and nothing else. There is no point also sending a title that could only add
 *   noise.
 * - Otherwise the title and author are sent as *fielded* terms (`intitle:` / `inauthor:`), which is
 *   what stops a common title from matching a different work that merely mentions the words.
 * - `maxResults` is bounded: this is a lookup, not a search engine, and the matcher only needs a few
 *   candidates to decide. `printType=books` keeps magazine and newspaper hits out.
 *
 * Pure JVM (OkHttp + `org.json`); no `android.*`, so the parser is unit-tested against a local
 * server that serves real response shapes.
 */
class GoogleBooksSource(
    private val client: OkHttpClient = MetadataHttp.defaultClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) : BookMetadataSource {

    override val provider: MetadataProvider = MetadataProvider.GOOGLE_BOOKS

    override suspend fun search(signals: BookSignals): MetadataResult {
        val query = queryFor(signals) ?: return MetadataResult.NotFound
        val url = url(query) ?: return MetadataResult.Unavailable(MetadataUnavailable.SERVER_ERROR)
        return MetadataHttp.get(client, url, ::parse)
    }

    /**
     * The `q` value, or null when the signals are too weak to ask a sensible question.
     *
     * A query is still sent when the only signal is the file name, because [BookSignals.title] falls
     * back to the cleaned name and a file is very often named after its book. That is not a
     * mis-association risk: the candidate still has to survive [BookMatch], which rejects a generic
     * name (`"report"`, `"scan"`) outright and demands a near-exact title for anything it cannot
     * confirm with an author.
     */
    fun queryFor(signals: BookSignals): String? {
        signals.isbn?.let { return "isbn:$it" }
        val title = signals.title?.trim()?.takeIf { it.length >= MIN_TITLE_CHARS } ?: return null
        val author = signals.author?.trim()
        return if (author.isNullOrBlank()) {
            "intitle:\"$title\""
        } else {
            "intitle:\"$title\" inauthor:\"$author\""
        }
    }

    private fun url(query: String): String? {
        val base = baseUrl.toHttpUrlOrNull() ?: return null
        return base.newBuilder()
            .addPathSegments("books/v1/volumes")
            .addQueryParameter("q", query)
            .addQueryParameter("maxResults", MAX_RESULTS.toString())
            .addQueryParameter("printType", "books")
            .build()
            .toString()
    }

    /**
     * Parses `items[].volumeInfo`.
     *
     * An absent `items` array is an empty candidate list, not an error: the API omits `items`
     * entirely when `totalItems` is zero, and "the catalogue has nothing" must stay distinct from
     * "the catalogue could not be read".
     */
    private fun parse(json: JSONObject): List<BookCandidate> {
        val items = json.optJSONArray("items") ?: return emptyList()
        val candidates = ArrayList<BookCandidate>(items.length())
        for (i in 0 until items.length()) {
            val volume = items.optJSONObject(i) ?: continue
            val info = volume.optJSONObject("volumeInfo") ?: continue
            val title = MetadataHttp.optionalString(info, "title") ?: continue
            val identifiers = info.optJSONArray("industryIdentifiers")
            var isbn10: String? = null
            var isbn13: String? = null
            if (identifiers != null) {
                for (j in 0 until identifiers.length()) {
                    val identifier = identifiers.optJSONObject(j) ?: continue
                    val type = MetadataHttp.optionalString(identifier, "type").orEmpty().uppercase()
                    val value = MetadataHttp.optionalString(identifier, "identifier") ?: continue
                    when (type) {
                        "ISBN_10" -> isbn10 = value
                        "ISBN_13" -> isbn13 = value
                    }
                }
            }
            candidates.add(
                BookCandidate(
                    provider = MetadataProvider.GOOGLE_BOOKS,
                    providerId = MetadataHttp.optionalString(volume, "id"),
                    title = title,
                    subtitle = MetadataHttp.optionalString(info, "subtitle"),
                    authors = MetadataHttp.stringList(info, "authors"),
                    publisher = MetadataHttp.optionalString(info, "publisher"),
                    publishedDate = MetadataHttp.optionalString(info, "publishedDate"),
                    description = MetadataHttp.optionalString(info, "description"),
                    categories = MetadataHttp.stringList(info, "categories"),
                    language = MetadataHttp.optionalString(info, "language"),
                    pageCount = MetadataHttp.optionalPositiveInt(info, "pageCount"),
                    isbn10 = isbn10,
                    isbn13 = isbn13,
                    coverUrl = coverUrl(info),
                ),
            )
        }
        return candidates
    }

    /**
     * A cover URL, forced to https.
     *
     * Google returns `http://books.google.com/books/content?...`, and Android's default network
     * security configuration blocks cleartext traffic, so the `http` form would simply never load.
     * The same host serves the same image over `https`, which is also why only `https://` URLs are
     * ever stored.
     */
    private fun coverUrl(info: JSONObject): String? {
        val links = info.optJSONObject("imageLinks") ?: return null
        val raw = MetadataHttp.optionalString(links, "thumbnail")
            ?: MetadataHttp.optionalString(links, "smallThumbnail")
            ?: return null
        return raw.replaceFirst("http://", "https://").takeIf { it.startsWith("https://") }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://www.googleapis.com"

        /** A handful of candidates is enough to decide; more would only add ambiguous matches. */
        const val MAX_RESULTS = 5

        /** A one- or two-character "title" is not a title and would match anything. */
        private const val MIN_TITLE_CHARS = 3
    }
}
