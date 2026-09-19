package com.voxora.core.reader

/**
 * One catalogue entry as a provider returned it, before any decision has been made about it.
 *
 * Sources produce these; [BookMatch] decides whether one of them is this book. Keeping the raw
 * candidate separate from [BookMetadata] is what stops "the provider returned something" from being
 * mistaken for "this is the book": [BookMetadata] only exists once a match has been accepted, and
 * it carries the [MatchConfidence] that justified it.
 */
data class BookCandidate(
    val provider: MetadataProvider,
    val providerId: String?,
    val title: String,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val publishedDate: String? = null,
    val description: String? = null,
    val categories: List<String> = emptyList(),
    val language: String? = null,
    val pageCount: Int? = null,
    val isbn10: String? = null,
    val isbn13: String? = null,
    val coverUrl: String? = null,
) {
    /** True when the entry carries anything beyond a bare title. */
    val hasSupportingMetadata: Boolean
        get() = !publishedDate.isNullOrBlank() || !publisher.isNullOrBlank() ||
            pageCount != null || categories.isNotEmpty() || !description.isNullOrBlank()
}

/**
 * Decides whether a catalogue entry really is the imported document.
 *
 * ## The safety rule
 *
 * Attaching the wrong book's author, publisher and synopsis to a document is worse than attaching
 * nothing: the reader would be told a falsehood with the app's authority behind it. So this layer
 * is built to say **"not confidently identified"** and is allowed to say it often.
 *
 * The order of trust is fixed and comes from the task's own policy:
 *
 * 1. **Exact ISBN.** An ISBN in the document that equals an identifier in the catalogue. This is a
 *    unique global identifier, so no other signal is needed and none can override it.
 * 2. **Title + author.** The title matches strongly *and* an author matches.
 * 3. **Title with supporting metadata.** A near-exact title match, no contradicting author, and the
 *    candidate actually carries publication data. Only accepted when it is the **only** such
 *    candidate.
 *
 * Anything else is rejected. In particular:
 *
 * - a **tie is never broken**: two candidates at the same confidence produce [Result.Ambiguous],
 *   never an arbitrary winner;
 * - a **generic title** (`"Untitled"`, `"Scan"`, `"Book"`, a bare number, a single word like
 *   `"Report"`) can never carry a match, because it would match thousands of works;
 * - a **filename-derived title** must match near-exactly, so `"the selfish gene.pdf"` is usable but
 *   `"final version 3.pdf"` is not.
 *
 * Pure JVM — no `android.*` — so the policy is unit-testable, including its refusals.
 */
object BookMatch {

    /** Titles that identify nothing. Matching one of these would attach an arbitrary work. */
    private val GENERIC_TITLES = setOf(
        "untitled", "document", "book", "scan", "scanned", "text", "file", "copy", "final",
        "report", "notes", "manuscript", "draft", "the book", "unknown", "no title", "sample",
        "output", "print", "download", "paper", "article", "pdf", "txt",
    )

    sealed class Result {
        /** A candidate was accepted. [metadata] carries [confidence] as its justification. */
        data class Matched(val metadata: BookMetadata, val confidence: MatchConfidence) : Result()

        /** More than one candidate was equally plausible. Nothing is attached. */
        data object Ambiguous : Result()

        /** Nothing was plausible. Nothing is attached. */
        data object NoMatch : Result()
    }

    /**
     * One candidate together with the verdict on it.
     *
     * Public so the scoring can be asserted directly in tests — "this candidate scored
     * `TITLE_AUTHOR`" is the interesting fact, and it is invisible through [pick] alone.
     */
    data class Scored(
        val candidate: BookCandidate,
        val confidence: MatchConfidence,
        val titleScore: Double,
    )

    /**
     * Picks the one candidate that may be attached to [signals], or refuses.
     *
     * @param fetchedAt wall-clock time the lookup completed, recorded on the accepted metadata.
     */
    fun pick(signals: BookSignals, candidates: List<BookCandidate>, fetchedAt: Long): Result {
        val scored = candidates.mapNotNull { score(signals, it) }.sortedWith(
            compareByDescending<Scored> { it.confidence.ordinal }.thenByDescending { it.titleScore },
        )
        val best = scored.firstOrNull() ?: return Result.NoMatch
        // An ISBN is unique, so a single exact identifier match stands on its own.
        if (best.confidence == MatchConfidence.ISBN_EXACT) return accept(best, fetchedAt)
        // Otherwise the winner must be strictly better than the runner-up: a tie is not a decision.
        val runnerUp = scored.getOrNull(1)
        if (runnerUp != null && runnerUp.confidence == best.confidence) return Result.Ambiguous
        return accept(best, fetchedAt)
    }

    /** Scores one candidate, or null when it may not be attached at all. */
    fun score(signals: BookSignals, candidate: BookCandidate): Scored? {
        if (candidate.title.isBlank()) return null
        if (isGeneric(candidate.title)) return null

        // 1. Exact identifier. The document's ISBN-10 is compared in its ISBN-13 form, because the
        //    same identifier is printed both ways and a catalogue stores whichever it has.
        val signalIsbn = signals.isbn13 ?: signals.isbn10?.let { BookSignalsReader.toIsbn13(it) }
        if (signalIsbn != null) {
            val candidateIsbn = candidate.isbn13?.let { BookSignalsReader.normalizeIsbn(it) }
                ?: candidate.isbn10?.let { BookSignalsReader.toIsbn13(it) }
            if (candidateIsbn != null && candidateIsbn == signalIsbn) {
                return Scored(candidate, MatchConfidence.ISBN_EXACT, 1.0)
            }
            // An ISBN was printed in the document and this candidate disagrees with it: this is a
            // different book, however similar the title looks.
            return null
        }

        val titleScore = titleSimilarity(signals.title, candidate.title)
        if (titleScore < TITLE_FLOOR) return null
        val authorAgrees = authorMatches(signals.author, candidate.authors)
        // An author we read off the document that the catalogue contradicts is a strong signal that
        // this is a different work by a different person.
        if (signals.author != null && !authorAgrees && candidate.authors.isNotEmpty()) return null

        return when {
            titleScore >= STRONG_TITLE && authorAgrees ->
                Scored(candidate, MatchConfidence.TITLE_AUTHOR, titleScore)

            titleScore >= NEAR_EXACT_TITLE && candidate.hasSupportingMetadata ->
                Scored(candidate, MatchConfidence.TITLE_ONLY, titleScore)

            else -> null
        }
    }

    private fun accept(scored: Scored, fetchedAt: Long): Result.Matched {
        val candidate = scored.candidate
        return Result.Matched(
            metadata = BookMetadata(
                provider = candidate.provider,
                providerId = candidate.providerId,
                title = candidate.title.trim(),
                subtitle = candidate.subtitle?.trim()?.takeIf { it.isNotEmpty() },
                authors = candidate.authors.map { it.trim() }.filter { it.isNotEmpty() },
                publisher = candidate.publisher?.trim()?.takeIf { it.isNotEmpty() },
                publishedDate = candidate.publishedDate?.trim()?.takeIf { it.isNotEmpty() },
                description = candidate.description?.trim()?.takeIf { it.isNotEmpty() },
                categories = candidate.categories.map { it.trim() }.filter { it.isNotEmpty() },
                language = candidate.language?.trim()?.takeIf { it.isNotEmpty() },
                pageCount = candidate.pageCount?.takeIf { it > 0 },
                isbn10 = candidate.isbn10?.let { BookSignalsReader.normalizeIsbn(it) }?.takeIf { it.length == 10 },
                isbn13 = candidate.isbn13?.let { BookSignalsReader.normalizeIsbn(it) }?.takeIf { it.length == 13 },
                coverUrl = candidate.coverUrl?.trim()?.takeIf { it.startsWith("https://") },
                confidence = scored.confidence,
                fetchedAt = fetchedAt,
            ),
            confidence = scored.confidence,
        )
    }

    /**
     * How alike two titles are, in `0.0..1.0`.
     *
     * Exact normalized equality is `1.0`; a clean containment (a subtitle added or dropped) is
     * `0.9`; otherwise the Jaccard overlap of their word sets, which is what catches word order and
     * small wording differences without letting two different long titles look similar.
     */
    fun titleSimilarity(a: String?, b: String?): Double {
        val left = normalizeText(a) ?: return 0.0
        val right = normalizeText(b) ?: return 0.0
        if (left.isEmpty() || right.isEmpty()) return 0.0
        if (left == right) return 1.0
        val shorter = if (left.length <= right.length) left else right
        val longer = if (left.length <= right.length) right else left
        if (shorter.length >= MIN_CONTAINMENT_CHARS && longer.contains(shorter)) {
            return CONTAINMENT_SCORE
        }
        val leftWords = left.split(' ').filter { it.isNotEmpty() }.toSet()
        val rightWords = right.split(' ').filter { it.isNotEmpty() }.toSet()
        if (leftWords.isEmpty() || rightWords.isEmpty()) return 0.0
        val shared = leftWords.count { it in rightWords }
        return shared.toDouble() / (leftWords.size + rightWords.size - shared).toDouble()
    }

    /**
     * Whether a document's author signal agrees with a catalogue entry's authors.
     *
     * Compared on the surname, because catalogues write `"Dawkins, Richard"` where a title page
     * writes `"Richard Dawkins"`. Initials and given names are therefore not required to line up,
     * but the family name is.
     */
    fun authorMatches(signal: String?, candidateAuthors: List<String>): Boolean {
        val wanted = normalizeText(signal) ?: return false
        if (wanted.isEmpty()) return false
        val wantedTokens = wanted.split(' ').filter { it.isNotEmpty() }
        if (wantedTokens.isEmpty()) return false
        val wantedSurname = wantedTokens.last()
        return candidateAuthors.any { author ->
            val tokens = normalizeText(author)?.split(' ')?.filter { it.isNotEmpty() } ?: return@any false
            if (tokens.isEmpty()) return@any false
            // Either the family name appears anywhere in the catalogue's form of the name (which is
            // how "Dawkins, Richard" matches "Richard Dawkins"), or the full normalized names agree.
            tokens.contains(wantedSurname) || wantedTokens.all { it in tokens }
        }
    }

    /** True for a title that cannot identify a specific work. */
    fun isGeneric(title: String): Boolean {
        val normalized = normalizeText(title) ?: return true
        if (normalized.isEmpty()) return true
        if (normalized in GENERIC_TITLES) return true
        // A bare number, a date, or a single short word identifies nothing on its own.
        if (normalized.all { it.isDigit() }) return true
        val words = normalized.split(' ').filter { it.isNotEmpty() }
        if (words.size == 1 && words[0].length < 4) return true
        return false
    }

    /**
     * Case-, accent- and punctuation-insensitive form of a text, for comparison only.
     *
     * Accents are stripped so `"García Márquez"` and `"Garcia Marquez"` agree, and every
     * non-alphanumeric run becomes a single space so `"Selfish Gene, The"` and `"the selfish gene"`
     * reduce to the same word set.
     */
    fun normalizeText(value: String?): String? {
        if (value == null) return null
        val decomposed = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        val builder = StringBuilder(decomposed.length)
        var lastWasSpace = true
        for (char in decomposed) {
            when {
                char.code in 0x0300..0x036F -> Unit // combining mark left by NFD
                char.isLetterOrDigit() -> {
                    builder.append(char.lowercaseChar())
                    lastWasSpace = false
                }
                !lastWasSpace -> {
                    builder.append(' ')
                    lastWasSpace = true
                }
            }
        }
        return builder.toString().trim().ifEmpty { null }
    }

    /** Below this, a title is not even considered. */
    private const val TITLE_FLOOR = 0.60

    /** Title + author acceptance threshold. */
    private const val STRONG_TITLE = 0.80

    /** Title-with-supporting-metadata acceptance threshold. */
    private const val NEAR_EXACT_TITLE = 0.95

    /** A containment this long is meaningful; a short one could be a coincidence. */
    private const val MIN_CONTAINMENT_CHARS = 8

    private const val CONTAINMENT_SCORE = 0.9
}
