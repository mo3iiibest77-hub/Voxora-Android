package com.voxora.core.reader

/**
 * Why a catalogue lookup did not produce candidates.
 *
 * These are kept apart because they mean different things to the reader, and the UI says so:
 * [OFFLINE] and [TIMEOUT] are worth retrying later, [RATE_LIMITED] is worth retrying after a while,
 * and [SERVER_ERROR]/[PARSE_ERROR] are the catalogue's problem rather than the network's. None of
 * them is ever presented as "this book was not found" — a failed lookup is not evidence about the
 * book.
 */
enum class MetadataUnavailable {
    OFFLINE,
    TIMEOUT,
    RATE_LIMITED,
    SERVER_ERROR,
    PARSE_ERROR,
}

/**
 * The outcome of one catalogue query, before any matching has happened.
 *
 * A source's job is transport and parsing only: it says what the catalogue returned, not whether it
 * is the right book. That decision belongs to [BookMatch] and is applied by [BookMetadataLookup].
 */
sealed class MetadataResult {
    /** The catalogue answered with these entries. The list may be empty. */
    data class Found(val candidates: List<BookCandidate>) : MetadataResult()

    /** The catalogue answered and had nothing for this query. */
    data object NotFound : MetadataResult()

    /** The catalogue could not be asked, or could not be understood. */
    data class Unavailable(val reason: MetadataUnavailable) : MetadataResult()
}

/**
 * A public bibliographic catalogue.
 *
 * Implementations must be **total**: they never throw for a network, HTTP or parsing problem, they
 * always answer within their own timeout, and they never let a failure escape into the import path.
 * A book is imported and usable whether or not any catalogue is reachable.
 */
interface BookMetadataSource {
    val provider: MetadataProvider

    /** Runs one query built from [signals]. Never throws; never blocks the caller's thread. */
    suspend fun search(signals: BookSignals): MetadataResult
}

/**
 * Where automatic identification ended up, after every configured catalogue has been consulted.
 *
 * [Matched] carries metadata that has already passed [BookMatch]'s conservative policy.
 * [Ambiguous] is the honest "several works fit and none is proven" answer — it is *not* a failure
 * and it is not a match, and the UI shows it as "could not be identified confidently".
 */
sealed class BookLookupOutcome {
    data class Matched(val metadata: BookMetadata) : BookLookupOutcome()
    data object NotFound : BookLookupOutcome()
    data object Ambiguous : BookLookupOutcome()
    data class Unavailable(val reason: MetadataUnavailable) : BookLookupOutcome()
}

/**
 * Queries the configured catalogues in order and applies the matching policy once, at the end.
 *
 * ## Why the policy is applied here and not per source
 *
 * The primary source is not more trustworthy than the fallback — Google Books and Open Library
 * describe the same works. So each source is asked for *candidates*, and only the union is judged.
 * That is what makes "the title is similar in both catalogues but the author disagrees" resolve to
 * a refusal rather than to whichever source was asked first.
 *
 * ## Failure handling
 *
 * A source that cannot answer does not abort the lookup: the next one is still asked. Only when
 * **no** source answered at all is the outcome [BookLookupOutcome.Unavailable], because that is the
 * only case in which Voxora genuinely does not know whether the book exists. If any source answered
 * and nothing matched, the honest answer is [BookLookupOutcome.NotFound].
 *
 * @param clock supplies "now" for the metadata's `fetchedAt`, so caching is deterministic in tests.
 */
class BookMetadataLookup(
    private val sources: List<BookMetadataSource>,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    constructor(vararg sources: BookMetadataSource) : this(sources.toList())

    suspend fun find(signals: BookSignals): BookLookupOutcome {
        val candidates = ArrayList<BookCandidate>()
        var answered = false
        var lastFailure: MetadataUnavailable? = null
        for (source in sources) {
            when (val result = source.search(signals)) {
                is MetadataResult.Found -> {
                    answered = true
                    candidates += result.candidates
                }
                MetadataResult.NotFound -> answered = true
                is MetadataResult.Unavailable -> lastFailure = result.reason
            }
        }
        if (candidates.isNotEmpty()) {
            return when (val match = BookMatch.pick(signals, candidates, clock())) {
                is BookMatch.Result.Matched -> BookLookupOutcome.Matched(match.metadata)
                BookMatch.Result.Ambiguous -> BookLookupOutcome.Ambiguous
                BookMatch.Result.NoMatch -> BookLookupOutcome.NotFound
            }
        }
        // No candidates at all. A source that answered "nothing found" is evidence; a source that
        // could not be reached is not.
        if (answered) return BookLookupOutcome.NotFound
        return BookLookupOutcome.Unavailable(lastFailure ?: MetadataUnavailable.SERVER_ERROR)
    }
}
