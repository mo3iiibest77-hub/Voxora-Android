package com.voxora.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "About This Book" fact list.
 *
 * The rule under test is that the section is **source-backed or absent**. Every assertion here is
 * about a field *not* appearing: an unknown author is never shown, a missing year is never derived,
 * and a language the app cannot name is dropped rather than displayed as a code.
 */
class BookIntelTest {

    private fun metadata(
        title: String = "The Selfish Gene",
        subtitle: String? = null,
        authors: List<String> = emptyList(),
        publisher: String? = null,
        publishedDate: String? = null,
        description: String? = null,
        categories: List<String> = emptyList(),
        language: String? = null,
        pageCount: Int? = null,
        isbn10: String? = null,
        isbn13: String? = null,
        coverUrl: String? = null,
        provider: MetadataProvider = MetadataProvider.GOOGLE_BOOKS,
    ) = BookMetadata(
        provider = provider,
        providerId = "id-1",
        title = title,
        subtitle = subtitle,
        authors = authors,
        publisher = publisher,
        publishedDate = publishedDate,
        description = description,
        categories = categories,
        language = language,
        pageCount = pageCount,
        isbn10 = isbn10,
        isbn13 = isbn13,
        coverUrl = coverUrl,
        confidence = MatchConfidence.TITLE_AUTHOR,
        fetchedAt = 1L,
    )

    /** Resolves the two codes the tests use, and refuses everything else. */
    private val languageNames: (String) -> String? = mapOf("en" to "English", "fa" to "Persian")::get

    @Test
    fun factsAreListedInAFixedOrderAndOmitEveryUnknownField() {
        val facts = BookIntel.facts(
            metadata(
                authors = listOf("Richard Dawkins"),
                publisher = "Oxford University Press",
                publishedDate = "1976",
                language = "en",
                pageCount = 360,
                isbn13 = "9780198788607",
            ),
            languageNames,
        )
        assertEquals(
            listOf(
                BookIntelFactKind.AUTHOR,
                BookIntelFactKind.PUBLISHER,
                BookIntelFactKind.PUBLISHED,
                BookIntelFactKind.LANGUAGE,
                BookIntelFactKind.PAGES,
                BookIntelFactKind.ISBN,
                BookIntelFactKind.PROVIDER,
            ),
            facts.map { it.kind },
        )
        assertEquals("Richard Dawkins", facts.first().value)
        assertEquals("360", facts.first { it.kind == BookIntelFactKind.PAGES }.value)
    }

    @Test
    fun aBookWithNothingButATitleProducesOnlyTheSourceFact() {
        val facts = BookIntel.facts(metadata(), languageNames)
        assertEquals(listOf(BookIntelFactKind.PROVIDER), facts.map { it.kind })
        assertEquals("Google Books", facts.single().value)
    }

    @Test
    fun anUnknownAuthorIsNotShownAndIsNeverGuessed() {
        val facts = BookIntel.facts(metadata(authors = listOf("  ")), languageNames)
        assertTrue(facts.none { it.kind == BookIntelFactKind.AUTHOR })
    }

    @Test
    fun anUnknownLanguageIsNotDerivedFromAnythingElse() {
        // A publication year is present, but it says nothing about the language, so no language
        // fact is invented from it.
        val facts = BookIntel.facts(metadata(publishedDate = "1976"), languageNames)
        assertTrue(facts.any { it.kind == BookIntelFactKind.PUBLISHED })
        assertTrue(facts.none { it.kind == BookIntelFactKind.LANGUAGE })
    }

    @Test
    fun aLanguageTheAppCannotNameIsDroppedRatherThanShownAsACode() {
        val facts = BookIntel.facts(metadata(language = "xx"), languageNames)
        assertTrue(facts.none { it.kind == BookIntelFactKind.LANGUAGE })

        val known = BookIntel.facts(metadata(language = "fa"), languageNames)
        assertEquals("Persian", known.first { it.kind == BookIntelFactKind.LANGUAGE }.value)
    }

    @Test
    fun theIsbnFactPrefersTheThirteenDigitForm() {
        val facts = BookIntel.facts(metadata(isbn10 = "0198788606", isbn13 = "9780198788607"), languageNames)
        assertEquals("9780198788607", facts.first { it.kind == BookIntelFactKind.ISBN }.value)
    }

    @Test
    fun theSourceLineNamesWhicheverCatalogueAnswered() {
        assertEquals("Google Books", BookIntel.providerName(metadata()))
        assertEquals("Open Library", BookIntel.providerName(metadata(provider = MetadataProvider.OPEN_LIBRARY)))
    }

    @Test
    fun theDescriptionIsTheProvidersOwnTextUnedited() {
        assertEquals("A synopsis.", BookIntel.description(metadata(description = "  A synopsis.  ")))
        // An absent or blank description is absent, not an empty paragraph.
        assertNull(BookIntel.description(metadata(description = null)))
        assertNull(BookIntel.description(metadata(description = "   ")))
    }

    @Test
    fun subjectsAreDeduplicatedAndTrimmed() {
        val topics = BookIntel.topics(metadata(categories = listOf(" Science ", "Science", "Life Sciences", "")))
        assertEquals(listOf("Science", "Life Sciences"), topics)
        assertEquals(emptyList<String>(), BookIntel.topics(metadata()))
    }

    @Test
    fun theWorkKindIsReadFromSubjectHeadingsOnly() {
        assertEquals(BookWorkKind.FICTION, BookIntel.workKind(metadata(categories = listOf("Fiction", "Fantasy"))))
        assertEquals(BookWorkKind.NONFICTION, BookIntel.workKind(metadata(categories = listOf("Science", "History"))))
        // Google Books writes compound subjects; a work that is genuinely both is not claimed to be
        // either.
        assertEquals(BookWorkKind.UNKNOWN, BookIntel.workKind(metadata(categories = listOf("Fiction / Science Fiction / General"))))
        // No subjects at all means no claim.
        assertEquals(BookWorkKind.UNKNOWN, BookIntel.workKind(metadata()))
    }

    @Test
    fun aRecordWithOnlyATitleIsEmptyForDisplayPurposes() {
        assertTrue(metadata().isEmpty)
        assertFalse(metadata(publisher = "OUP").isEmpty)
        assertFalse(metadata(coverUrl = "https://example.com/c.jpg").isEmpty)
    }

    @Test
    fun theAuthorLineJoinsMultipleAuthorsAndIgnoresBlanks() {
        assertEquals("A. One, B. Two", metadata(authors = listOf("A. One", "B. Two")).authorLine)
        assertNull(metadata(authors = listOf("", "  ")).authorLine)
    }
}
