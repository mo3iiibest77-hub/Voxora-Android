package com.voxora.core.reader

/** What kind of work a book is, as far as the catalogue's own subject headings can say. */
enum class BookWorkKind {
    FICTION,
    NONFICTION,

    /** The catalogue's subjects do not say. The UI must not guess. */
    UNKNOWN,
}

/** One labelled fact in the "About This Book" section. */
enum class BookIntelFactKind {
    AUTHOR,
    PUBLISHER,
    PUBLISHED,
    LANGUAGE,
    PAGES,
    ISBN,
    PROVIDER,
}

data class BookIntelFact(val kind: BookIntelFactKind, val value: String)

/**
 * Turns cached bibliographic data into the facts the "About This Book" section shows.
 *
 * ## The rule this object exists to enforce
 *
 * The section is **source-backed or it is absent**. Every fact returned here is a field a provider
 * actually returned; there is no inference, no completion and no generated prose. A book with a
 * title and nothing else produces one fact, not a plausible-looking paragraph.
 *
 * In particular:
 *
 * - an unknown author is **not shown** — it is never rendered as "Unknown author" beside a real
 *   author line, and it is never guessed from the title;
 * - a missing publication year is not derived from an ISBN prefix, a copyright line or the
 *   document;
 * - subjects come only from the catalogue's own subject headings ([topics]);
 * - the description is the provider's text, unedited and untranslated. Voxora does not write a
 *   synopsis, and it does not rewrite the publisher's one — a generated summary presented next to
 *   real metadata would be indistinguishable from it;
 * - the section never claims more than it has: [workKind] returns [BookWorkKind.UNKNOWN] rather
 *   than guessing, and the UI then labels the description neutrally instead of calling it a plot.
 *
 * Author biography is deliberately **not** modelled: neither Google Books volumes nor Open Library
 * search results carry it, and inventing one is exactly what this rule forbids.
 *
 * Pure JVM — no `android.*`, no resource ids — so the fact list is unit-testable. The UI maps
 * [BookIntelFactKind] to a string resource; this object never names a label.
 */
object BookIntel {

    /**
     * The facts worth showing, in a fixed display order, omitting every unknown field.
     *
     * Subjects are deliberately **not** a fact here: they are rendered by [topics] under their own
     * heading, and listing them twice would make the section look padded.
     *
     * @param languageName resolves a provider language code (`"en"`) to a display name. Returning
     *   null drops the fact rather than showing a bare code the reader cannot read.
     */
    fun facts(metadata: BookMetadata, languageName: (String) -> String?): List<BookIntelFact> {
        val facts = ArrayList<BookIntelFact>(7)
        metadata.authorLine?.let { facts += BookIntelFact(BookIntelFactKind.AUTHOR, it) }
        metadata.publisher?.let { facts += BookIntelFact(BookIntelFactKind.PUBLISHER, it) }
        metadata.publishedDate?.let { facts += BookIntelFact(BookIntelFactKind.PUBLISHED, it) }
        metadata.language?.let { code ->
            languageName(code)?.let { facts += BookIntelFact(BookIntelFactKind.LANGUAGE, it) }
        }
        metadata.pageCount?.let { facts += BookIntelFact(BookIntelFactKind.PAGES, it.toString()) }
        metadata.displayIsbn?.let { facts += BookIntelFact(BookIntelFactKind.ISBN, it) }
        facts += BookIntelFact(BookIntelFactKind.PROVIDER, providerName(metadata))
        return facts
    }

    /** The provider's own description, or null when it did not supply one. */
    fun description(metadata: BookMetadata): String? =
        metadata.description?.trim()?.takeIf { it.isNotEmpty() }

    /** The catalogue's subject headings, de-duplicated and order-preserving. */
    fun topics(metadata: BookMetadata): List<String> =
        metadata.categories.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /**
     * Whether the catalogue's subjects describe fiction or non-fiction.
     *
     * Read from the subject headings only — never from the description's wording, which would be a
     * guess dressed as a fact. Google Books writes compound subjects (`"Fiction / Science Fiction /
     * General"`), so both halves are inspected.
     */
    fun workKind(metadata: BookMetadata): BookWorkKind {
        val subjects = topics(metadata).map { it.lowercase() }
        if (subjects.isEmpty()) return BookWorkKind.UNKNOWN
        val fiction = subjects.any { subject -> FICTION_MARKERS.any { subject.contains(it) } }
        val nonfiction = subjects.any { subject -> NONFICTION_MARKERS.any { subject.contains(it) } }
        return when {
            fiction && !nonfiction -> BookWorkKind.FICTION
            nonfiction && !fiction -> BookWorkKind.NONFICTION
            else -> BookWorkKind.UNKNOWN
        }
    }

    /** The provider's name, for the "source" line. Always known once metadata exists. */
    fun providerName(metadata: BookMetadata): String = when (metadata.provider) {
        MetadataProvider.GOOGLE_BOOKS -> "Google Books"
        MetadataProvider.OPEN_LIBRARY -> "Open Library"
    }

    private val FICTION_MARKERS = listOf(
        "fiction", "novel", "poetry", "poems", "drama", "comic", "graphic novel", "short stories",
        "fantasy", "romance", "thriller", "mystery", "horror", "literary",
    )

    private val NONFICTION_MARKERS = listOf(
        "nonfiction", "non-fiction", "science", "history", "biography", "autobiography",
        "philosophy", "business", "economics", "technology", "computers", "reference",
        "self-help", "psychology", "religion", "politics", "political", "education", "nature",
        "mathematics", "medical", "medicine", "law", "art", "music", "travel", "cooking",
        "criticism", "language arts", "social science", "study aids", "true crime", "essays",
    )
}
