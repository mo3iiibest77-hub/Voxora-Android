package com.voxora.core.reader

/**
 * What the imported document itself can tell us about which book it is.
 *
 * These are **signals, not conclusions**. Everything here is a candidate: an ISBN that passed its
 * own checksum, or a line that *looks* like a title. Deciding whether a catalogue entry actually is
 * this book is [BookMatch]'s job, and it is deliberately conservative — a wrong attachment is worse
 * than no attachment, because it would present another book's author and synopsis as fact.
 *
 * Nothing here is derived from the file name alone when the document has something better to say,
 * and nothing is sent anywhere: the signals are the *query*, never the document. The book's text is
 * never uploaded to identify it.
 */
data class BookSignals(
    val isbn13: String?,
    val isbn10: String?,
    /** Best title candidate from the document's opening lines, or null when none looked like one. */
    val title: String?,
    /** Best author candidate, or null. */
    val author: String?,
    /** The provider's file name, kept as the weakest signal and as the last-resort display title. */
    val filename: String,
) {
    /**
     * True when there is anything worth asking a catalogue about.
     *
     * Note that this is **not** "the document itself yielded something": [title] falls back to the
     * cleaned file name when the opening lines offered no title, so a name-only document reports
     * `true` here too. That is deliberate — a file is very often named after its book, so the
     * fallback title is still a reasonable query, and it is safe because [BookMatch] refuses to
     * attach anything on a weak or generic title (`"report"`, `"scan"`) no matter what it matched.
     */
    val hasDocumentSignal: Boolean
        get() = isbn13 != null || isbn10 != null || !title.isNullOrBlank() || !author.isNullOrBlank()

    /** The ISBN to search with, preferring the 13-digit form. */
    val isbn: String? get() = isbn13 ?: isbn10
}

/**
 * Reads [BookSignals] out of a file name and the opening of an extracted document.
 *
 * ## Why the rules are narrow
 *
 * A title page is not a structured record. The first lines of a PDF may be a running header, a
 * library stamp, a dedication, a copyright notice or a chapter heading, and the file name may be a
 * scan code. So the reader here accepts only what it can defend:
 *
 * - **ISBN is the strong signal.** Candidates must match the shape of an ISBN-10 or ISBN-13 *and*
 *   pass the check-digit algorithm. That is a real proof of self-consistency, and it is why an ISBN
 *   is preferred over everything else. Page numbers, years and phone numbers cannot pass it.
 * - **The title is the first line that does not look like boilerplate.** Copyright lines, ISBN
 *   lines, contents/contents-page entries, chapter headings, running headers and sentences are
 *   rejected by name, so the first surviving line of a title page is usually the title.
 * - **The author is only taken from an explicit marker or an unambiguous name line.** `by X`,
 *   `Author: X`, or a short title-case line directly under the title with no sentence punctuation
 *   and no digits. Anything else yields null.
 *
 * A wrong-but-plausible signal is not a correctness problem *here*, because [BookMatch] refuses to
 * attach metadata without a strong agreement. It would only be a problem if this layer decided
 * anything, which it does not.
 *
 * Pure JVM — no `android.*` — so the heuristics are unit-testable against real title pages.
 */
object BookSignalsReader {
    /** How much of the document is inspected. A title page lives in the first few hundred chars. */
    private const val HEAD_CHARS = 6_000

    /** How many of the opening lines are considered. */
    private const val HEAD_LINES = 40

    /** ISBN-13: the 978/979 prefix, nine more digits, then the check digit. */
    private val isbn13Pattern = Regex("(?<![0-9])(?:97[89][- ]?)(?:[0-9][- ]?){9}[0-9](?![0-9])")

    /** ISBN-10: nine digits then a digit or `X`. */
    private val isbn10Pattern = Regex("(?<![0-9])(?:[0-9][- ]?){9}[0-9Xx](?![0-9])")

    private val authorMarker = Regex("""(?i)^(?:by|author|authors|written by)\s*[:\-–]?\s*(.+)$""")

    private val boilerplate = Regex(
        "(?i)\\b(copyright|all rights reserved|isbn|issn|table of contents|contents|" +
            "chapter|part|section|published|publisher|printed|printing|edition|translated|" +
            "translation|library of congress|dedicat|acknowledg|prologue|epilogue|foreword|" +
            "preface|introduction|www\\.|https?://|©|®)\\b",
    )

    private val runningHeader = Regex("""^[\p{N}\s\-–—.,:;]+$""")

    /** How many words an explicitly marked author may have ("Gabriel García Márquez" is four). */
    private const val MAX_AUTHOR_WORDS = 6

    /** Lowercase words that legitimately appear inside a personal name. */
    private val NAME_PARTICLES = setOf(
        "of", "de", "del", "della", "van", "von", "der", "den", "la", "le", "el", "al",
        "bin", "ibn", "and", "di", "da", "dos", "ter", "ten", "y", "st", "mac",
    )

    /** Genre words that mark a line as a subtitle rather than an author. */
    private val SUBTITLE_MARKERS = listOf(
        "novel", "story", "stories", "tale", "tales", "memoir", "biography", "essays", "poems",
        "poetry", "introduction", "guide", "handbook", "history of", "notes on", "selected",
        "collected", "the complete", "volume", "edition", "translation", "reader",
    )

    /** Articles a subtitle typically opens with. */
    private val SUBTITLE_ARTICLES = setOf("a", "an", "the")

    fun from(filename: String, documentText: String): BookSignals {
        val head = documentText.take(HEAD_CHARS)
        val isbn13 = firstValidIsbn(head, isbn13Pattern, 13)
        val isbn10 = if (isbn13 == null) firstValidIsbn(head, isbn10Pattern, 10) else null
        val lines = head.split('\n').asSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .take(HEAD_LINES).toList()
        val title = titleFrom(lines) ?: filenameTitle(filename)
        val author = authorFrom(lines, title)
        return BookSignals(
            isbn13 = isbn13,
            isbn10 = isbn10,
            title = title,
            author = author,
            filename = filename,
        )
    }

    /**
     * A human-readable title from a file name: extension dropped, separators turned into spaces,
     * bracketed noise removed. Only used when the document itself offered no title.
     */
    fun filenameTitle(filename: String): String? {
        val base = filename.substringBeforeLast('.').ifBlank { filename }
        val cleaned = base
            .replace(Regex("""[\[(][^\])]*[\])]"""), " ")
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.takeIf { it.count { c -> c.isLetter() } >= 3 }
    }

    /** The first line that reads like a title rather than like page furniture. */
    private fun titleFrom(lines: List<String>): String? = lines.firstOrNull { isTitleLike(it) }

    private fun isTitleLike(line: String): Boolean {
        if (line.length !in 3..140) return false
        if (line.count { it.isLetter() } < 2) return false
        if (runningHeader.matches(line)) return false
        if (boilerplate.containsMatchIn(line)) return false
        // A title does not end in a full stop; a sentence does.
        if (line.endsWith('.') || line.endsWith('؟') || line.endsWith('?')) return false
        // A line that is mostly digits is a date, a page number or a catalogue code.
        if (line.count { it.isDigit() } > line.length / 3) return false
        return true
    }

    /**
     * The author, from an explicit `by …` marker anywhere in the head, or from a name line directly
     * under the title. Never from the file name: a file name carries no authorship claim.
     */
    private fun authorFrom(lines: List<String>, title: String?): String? {
        for (line in lines) {
            val marked = authorMarker.find(line)?.groupValues?.get(1)?.trim()
            if (!marked.isNullOrBlank() && looksLikeAuthorName(marked)) return cleanAuthor(marked)
        }
        val index = lines.indexOfFirst { it == title }
        if (index < 0) return null
        for (offset in 1..2) {
            val candidate = lines.getOrNull(index + offset) ?: continue
            if (looksLikePersonName(candidate) && !looksLikeSubtitle(candidate)) return cleanAuthor(candidate)
        }
        return null
    }

    /**
     * A plausible author for a value captured after a `by`/`author` marker.
     *
     * Stricter than [looksLikePersonName] about shape but wider about length, because an explicit
     * marker is already strong evidence: every word must be capitalised or a naming particle
     * (`van`, `de`, `al`, …), and a digit disqualifies it. Without the digit rule, an ordinary
     * sentence in the document's opening — "By 1976, the theory had…" — would be read as an author.
     */
    private fun looksLikeAuthorName(value: String): Boolean {
        if (value.any { it.isDigit() }) return false
        if (value.count { it.isLetter() } < 2) return false
        val words = value.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > MAX_AUTHOR_WORDS) return false
        return words.all { word ->
            val first = word.firstOrNull { it.isLetter() } ?: return@all false
            !first.isLowerCase() || word.lowercase().trim(',', '.', ';', ':') in NAME_PARTICLES
        }
    }

    /** Two to four capitalised words, no digits, no sentence punctuation: a plausible personal name. */
    private fun looksLikePersonName(line: String): Boolean {
        if (line.length !in 3..80) return false
        if (line.any { it.isDigit() }) return false
        if (line.any { it in ",;:!?" }) return false
        val words = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size !in 2..4) return false
        return words.all { word ->
            val first = word.firstOrNull { it.isLetter() } ?: return@all false
            first.isUpperCase() || !first.isLowerCase()
        }
    }

    /**
     * A line that is more likely a subtitle than a person.
     *
     * A title page often reads `TITLE / SUBTITLE / AUTHOR`, so the line under the title is not
     * always the author. Subtitles announce themselves with a genre word or a leading article;
     * anything else is treated as a name, because a rejected-but-real author only costs a weaker
     * match, while a subtitle accepted as an author would make the matcher reject the real book.
     */
    private fun looksLikeSubtitle(line: String): Boolean {
        val lowered = line.lowercase()
        if (SUBTITLE_MARKERS.any { lowered.contains(it) }) return true
        return lowered.split(' ').firstOrNull() in SUBTITLE_ARTICLES
    }

    private fun cleanAuthor(raw: String): String? {
        val cleaned = raw.trim()
            .trimEnd('.', ',', ';', ':')
            .replace(Regex("""\s*\([^)]*\)\s*$"""), "")
            .trim()
        return cleaned.takeIf { it.length in 2..80 && it.count { c -> c.isLetter() } >= 2 }
    }

    /**
     * The first candidate in [text] that passes the ISBN check-digit algorithm.
     *
     * The checksum is the whole point: it turns "a number that looks like an ISBN" into "a number
     * that *is* an ISBN", which is what makes it safe to prefer over a title match.
     */
    private fun firstValidIsbn(text: String, pattern: Regex, digits: Int): String? {
        for (match in pattern.findAll(text)) {
            val normalized = normalizeIsbn(match.value)
            if (normalized.length != digits) continue
            if (isValidIsbn(normalized)) return normalized
        }
        return null
    }

    /** Strips every separator; keeps a trailing `X` on an ISBN-10. */
    fun normalizeIsbn(raw: String): String =
        raw.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
    /** ISBN-10 (mod 11) and ISBN-13 (mod 10) check digits. */
    fun isValidIsbn(value: String): Boolean {
        return when (value.length) {
            10 -> {
                var sum = 0
                for (i in 0..9) {
                    val digit = if (value[i] == 'X') 10 else value[i].digitToIntOrNull() ?: return false
                    if (value[i] == 'X' && i != 9) return false
                    sum += (10 - i) * digit
                }
                sum % 11 == 0
            }
            13 -> {
                if (!value.all { it.isDigit() }) return false
                var sum = 0
                for (i in 0..12) sum += value[i].digitToInt() * if (i % 2 == 0) 1 else 3
                sum % 10 == 0
            }
            else -> false
        }
    }

    /**
     * The ISBN-13 form of an ISBN-10 (the `978` prefix plus a recomputed check digit), or the value
     * unchanged when it is already 13 digits. Returns null when [value] is neither.
     *
     * This exists so an ISBN-10 printed in a book can be compared with the ISBN-13 a catalogue
     * stores — they are the same identifier written two ways.
     */
    fun toIsbn13(value: String): String? {
        val normalized = normalizeIsbn(value)
        if (normalized.length == 13) return normalized.takeIf { isValidIsbn(it) }
        if (normalized.length != 10 || !isValidIsbn(normalized)) return null
        val body = "978" + normalized.take(9)
        var sum = 0
        for (i in 0..11) sum += body[i].digitToInt() * if (i % 2 == 0) 1 else 3
        val check = (10 - sum % 10) % 10
        return body + check
    }
}
