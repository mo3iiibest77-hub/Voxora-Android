package com.voxora.core.reader

/** Which public catalogue a piece of bibliographic data came from. */
enum class MetadataProvider(val id: String) {
    GOOGLE_BOOKS("google_books"),
    OPEN_LIBRARY("open_library"),
    ;

    companion object {
        fun normalize(id: String?): MetadataProvider? =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/**
 * How far the automatic identification of an imported document has got.
 *
 * These are four genuinely different states and the UI must keep them apart — an unreachable
 * catalogue is not "this book does not exist", and neither is "the match was too weak to trust".
 */
enum class MetadataLookupState(val id: String) {
    /** Nothing has been attempted (for example a book imported before this feature existed). */
    NONE("none"),

    /** A lookup is running. */
    PENDING("pending"),

    /** A confident match was found and cached. */
    FOUND("found"),

    /** The catalogues answered and no match was confident enough to attach. */
    NOT_FOUND("not_found"),

    /**
     * Several catalogue entries fitted equally well, so none was chosen.
     *
     * Kept apart from [NOT_FOUND] because it is a different statement — "we found more than one
     * candidate and refused to guess" rather than "we found nothing" — and the UI says so.
     */
    AMBIGUOUS("ambiguous"),

    /** The lookup could not run or could not be completed (offline, timeout, quota, bad response). */
    UNAVAILABLE("unavailable"),
    ;

    companion object {
        fun normalize(id: String?): MetadataLookupState =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NONE
    }
}

/**
 * Why a match was accepted.
 *
 * Ordered weakest to strongest; the ordering is the product's confidence policy and is what
 * [BookMatch] compares against. Nothing below [TITLE_ONLY] is ever attached to a book.
 */
enum class MatchConfidence(val id: String) {
    /** Nothing was attached. */
    NONE("none"),

    /** The title matched and supporting metadata agreed, but no author confirmed it. */
    TITLE_ONLY("title_only"),

    /** The title and an author both matched. */
    TITLE_AUTHOR("title_author"),

    /** An ISBN in the document matched an identifier in the catalogue exactly. */
    ISBN_EXACT("isbn_exact"),
    ;

    companion object {
        fun normalize(id: String?): MatchConfidence =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NONE
    }
}

/**
 * Bibliographic data for one book, as a public catalogue reported it.
 *
 * ## Source-backed only
 *
 * Every field here is a value a provider actually returned, or null. There is no synthesised
 * author, no inferred publication year, no generated description and no invented subject: the
 * product rule is that an unknown field is shown as unknown rather than guessed. Nothing in this
 * model is derived from the document's *contents* — only from what a catalogue says about the work
 * the document was matched to.
 *
 * ## Why the strings are plain
 *
 * [description] and [categories] are stored exactly as the provider returned them, in the
 * provider's language. Voxora does not machine-translate catalogue data: a translated synopsis
 * would be Voxora's text presented as the publisher's. When the reader's output language differs
 * from the catalogue's, the translated explanatory text is a **separate, clearly labelled**
 * [BookIntelOverview] generated from this record — it never replaces these fields, and the card
 * shows both, each named for what it is.
 */
data class BookMetadata(
    val provider: MetadataProvider,
    /** The provider's own identifier for the matched volume/work. */
    val providerId: String?,
    val title: String,
    val subtitle: String?,
    val authors: List<String>,
    val publisher: String?,
    /** As published by the provider, for example `"1976"` or `"1976-10-01"`. */
    val publishedDate: String?,
    val description: String?,
    val categories: List<String>,
    /** Provider language code, for example `"en"`. */
    val language: String?,
    val pageCount: Int?,
    val isbn10: String?,
    val isbn13: String?,
    /** Absolute https URL of a cover image, or null. */
    val coverUrl: String?,
    val confidence: MatchConfidence,
    /** When the lookup that produced this record completed, in wall-clock epoch millis. */
    val fetchedAt: Long,
) {
    /** True when there is nothing worth showing beyond the title. */
    val isEmpty: Boolean
        get() = subtitle == null && authors.isEmpty() && publisher == null && publishedDate == null &&
            description.isNullOrBlank() && categories.isEmpty() && language == null &&
            pageCount == null && isbn10 == null && isbn13 == null && coverUrl == null

    /** The ISBN to display, preferring the 13-digit form. */
    val displayIsbn: String? get() = isbn13 ?: isbn10

    /** A single-line author credit, or null when no author is known. */
    val authorLine: String? get() = authors.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(", ")
}
