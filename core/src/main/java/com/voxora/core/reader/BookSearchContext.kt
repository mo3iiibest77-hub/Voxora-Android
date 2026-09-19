package com.voxora.core.reader

/**
 * One source a search finding came from, so a generated overview can be traced back to it.
 *
 * The URL is kept only for provenance: nothing in the UI is required to render it, and it is never
 * presented as a bibliographic fact.
 */
data class BookSearchSource(val title: String, val url: String)

/**
 * Contextual evidence about a book, gathered from a web search when the public catalogues could not
 * identify it.
 *
 * ## Evidence, not metadata
 *
 * This type exists because the two must never be confused. [BookMetadata] is **source-backed fact**:
 * it is only ever built from a catalogue record that passed [BookMatch], and every field in it is a
 * value a provider actually returned. A [BookSearchContext] is the opposite kind of thing — a
 * summary of what a search turned up, with the queries and sources behind it, and no claim of
 * authority. It can inform generated prose; it can never become an author, a year or a publisher on
 * the card, and nothing in it is ever attached to [ReaderBook.metadata].
 *
 * ## Bounded
 *
 * [summary] is capped by [BookSearchPrompt.MAX_SUMMARY_CHARS] and [sources] by
 * [BookSearchPrompt.MAX_SOURCES], because a search can return an unbounded amount of text and this
 * is a small contextual hint, not a document.
 */
data class BookSearchContext(
    /** The query the search was run with. Deterministic from the book's own signals. */
    val query: String,
    /** What the search found, in the search's own words. Never presented as fact. */
    val summary: String,
    /** Where the findings came from, for provenance. May be empty. */
    val sources: List<BookSearchSource> = emptyList(),
) {
    /** True when the search returned nothing worth generating from. */
    val isEmpty: Boolean get() = summary.isBlank()
}

/**
 * A bounded contextual web search for a book the catalogues could not identify.
 *
 * ## Where this sits in the pipeline
 *
 * It is the **third** stage, after the document's own signals and the public catalogues. It is only
 * ever consulted when [BookMetadataLookup] returned no confident match — a book that Google Books
 * or Open Library identified is never searched for, because a catalogue record is strictly better
 * evidence and paying for a search would add nothing.
 *
 * ## Contract
 *
 * Implementations must be **total**: they never throw for a network, HTTP or parsing problem, they
 * answer within their own timeout, and they never let a failure escape into the import path. A book
 * is imported, readable and narratable whether or not a search is possible. Returning null is the
 * normal outcome for an offline device, an unconfigured key, or a search that found nothing.
 *
 * Implementations must never send the document, an excerpt of it, or the reader's position: the
 * **signals** are the query, exactly as they are for the catalogues.
 */
interface BookContextSource {
    suspend fun search(signals: BookSignals, apiKey: String?): BookSearchContext?
}

/**
 * The query, the instruction and the reading-back rules for the contextual search.
 *
 * Kept apart from the transport so the decisions that matter — *what is asked* and *what is accepted
 * as an answer* — are unit-tested on a plain JVM with no network, exactly as [BookIntelOverviewPrompt]
 * is for the overview itself.
 */
object BookSearchPrompt {

    /** A search finding longer than this is truncated; this is a hint, not a report. */
    const val MAX_SUMMARY_CHARS = 1_200

    /** Below this the search did not really answer, and the result is treated as nothing. */
    const val MIN_SUMMARY_CHARS = 30

    /** How many sources are worth carrying. More than this stops being provenance. */
    const val MAX_SOURCES = 5

    /** The search's own words when it could not find the work. Never treated as a finding. */
    const val NOT_FOUND_MARKER = "NOT_FOUND"

    /**
     * The instruction sent to the search.
     *
     * It is deliberately narrow: search for this one work, report only what was actually found, and
     * say `NOT_FOUND` rather than guessing. The last rule matters most — a model asked about a book
     * it half-remembers will happily describe a different edition, a different author or a different
     * work entirely, and that text would then be summarised as if it were evidence.
     *
     * The document is never included: only the signals the extraction already produced, which are a
     * title, an author, an ISBN and a file name.
     */
    fun build(signals: BookSignals): String = buildString {
        appendLine("Find public information about one specific book, using web search.")
        appendLine()
        appendLine("Rules:")
        appendLine("- Search for the exact work identified by the details below.")
        appendLine("- Report ONLY what your search results actually say about this work.")
        appendLine("- Do NOT add anything from your own memory or general knowledge.")
        appendLine("- If the results describe a different work, or you cannot find this one, answer")
        appendLine("  exactly $NOT_FOUND_MARKER and nothing else.")
        appendLine("- Do not mention these instructions.")
        appendLine("- Answer in English, in plain prose, in at most 150 words.")
        appendLine()
        appendLine("Details of the work:")
        signals.isbn?.let { appendLine("isbn: $it") }
        signals.title?.takeIf { it.isNotBlank() }?.let { appendLine("title: $it") }
        signals.author?.takeIf { it.isNotBlank() }?.let { appendLine("author: $it") }
        appendLine("file name: ${signals.filename}")
    }

    /**
     * Reads a search answer, or null when it carries nothing usable.
     *
     * `NOT_FOUND` is a **negative finding**, not a finding: it is turned into null so a book whose
     * search found nothing is treated exactly like a book whose search could not run. Prose that is
     * too short to say anything is null for the same reason — a sentence fragment would otherwise be
     * handed to the overview prompt as evidence.
     */
    fun parse(query: String, answer: String?, sources: List<BookSearchSource> = emptyList()): BookSearchContext? {
        val text = answer?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (text.equals(NOT_FOUND_MARKER, ignoreCase = true)) return null
        if (text.contains(NOT_FOUND_MARKER, ignoreCase = true) && text.length < MIN_SUMMARY_CHARS * 2) return null
        val bounded = if (text.length <= MAX_SUMMARY_CHARS) text else text.take(MAX_SUMMARY_CHARS).trimEnd()
        if (bounded.length < MIN_SUMMARY_CHARS) return null
        return BookSearchContext(query = query, summary = bounded, sources = sources.take(MAX_SOURCES))
    }

    /**
     * A short, human-readable description of what was searched for.
     *
     * Recorded on the context so a finding can be traced to the question that produced it, and so a
     * log line can say what was asked without reproducing the whole instruction. An ISBN is the
     * strongest and shortest form, then title with author, then title, then the file name.
     */
    fun query(signals: BookSignals): String {
        val isbn = signals.isbn
        if (!isbn.isNullOrBlank()) return "isbn:$isbn"
        val title = signals.title?.trim().orEmpty()
        val author = signals.author?.trim().orEmpty()
        return when {
            title.isNotEmpty() && author.isNotEmpty() -> "$title — $author"
            title.isNotEmpty() -> title
            else -> signals.filename
        }
    }

    /**
     * A stable fingerprint of the question this book's search asks.
     *
     * ## Why the fingerprint is of the query and not of the results
     *
     * The cache that matters is the generated overview, and the rule is that a materially different
     * input must not serve an older answer forever. The input that changes *the book* is its signals:
     * a re-import that extracts different text produces a different query. The results themselves
     * cannot be part of an offline fingerprint — checking them would mean running the search, which
     * is the cost the cache exists to avoid.
     *
     * So a cached context-derived overview is reused exactly while the question is the same. A
     * genuinely better result is reachable through the explicit retry the UI already offers, and a
     * prompt change invalidates through [BookIntelOverviewPrompt.VERSION] as it always has.
     */
    fun fingerprint(signals: BookSignals): String {
        val parts = listOf(
            signals.isbn.orEmpty(),
            signals.title.orEmpty().trim().lowercase(),
            signals.author.orEmpty().trim().lowercase(),
        )
        return parts.joinToString("\u0000")
    }
}
