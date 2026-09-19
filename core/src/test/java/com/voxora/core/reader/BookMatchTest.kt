package com.voxora.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The matching policy: when a catalogue entry may be attached to a document, and when it must not.
 *
 * The refusals matter more than the acceptances. Attaching the wrong book's author and synopsis is
 * a falsehood presented with the app's authority, so these tests spend as much effort on the
 * candidates that must be rejected — a contradicting ISBN, a disagreeing author, a generic title,
 * and above all a tie.
 */
class BookMatchTest {

    private val fetchedAt = 1_700_000_000_000L

    private fun candidate(
        title: String,
        authors: List<String> = emptyList(),
        isbn13: String? = null,
        isbn10: String? = null,
        provider: MetadataProvider = MetadataProvider.GOOGLE_BOOKS,
        publishedDate: String? = null,
        publisher: String? = null,
        pageCount: Int? = null,
        categories: List<String> = emptyList(),
        description: String? = null,
    ) = BookCandidate(
        provider = provider,
        providerId = "id-$title",
        title = title,
        authors = authors,
        publisher = publisher,
        publishedDate = publishedDate,
        description = description,
        categories = categories,
        pageCount = pageCount,
        isbn10 = isbn10,
        isbn13 = isbn13,
    )

    private fun signals(
        isbn13: String? = null,
        isbn10: String? = null,
        title: String? = null,
        author: String? = null,
    ) = BookSignals(isbn13 = isbn13, isbn10 = isbn10, title = title, author = author, filename = "book.pdf")

    @Test
    fun anExactIsbnMatchIsAcceptedOnItsOwn() {
        val result = BookMatch.pick(
            signals(isbn13 = "9780199291151"),
            listOf(candidate(title = "The Selfish Gene", isbn13 = "9780199291151")),
            fetchedAt,
        )
        val matched = result as BookMatch.Result.Matched
        assertEquals(MatchConfidence.ISBN_EXACT, matched.confidence)
        assertEquals("The Selfish Gene", matched.metadata.title)
        assertEquals(fetchedAt, matched.metadata.fetchedAt)
    }

    @Test
    fun anIsbnTenInTheDocumentMatchesTheIsbnThirteenInTheCatalogue() {
        val result = BookMatch.pick(
            signals(isbn10 = "0199291152"),
            listOf(candidate(title = "The Selfish Gene", isbn13 = "9780199291151")),
            fetchedAt,
        )
        assertEquals(MatchConfidence.ISBN_EXACT, (result as BookMatch.Result.Matched).confidence)
    }

    @Test
    fun aCandidateThatContradictsThePrintedIsbnIsRejectedHoweverSimilarTheTitle() {
        val result = BookMatch.pick(
            signals(isbn13 = "9780199291151"),
            listOf(candidate(title = "The Selfish Gene", isbn13 = "9780140328721")),
            fetchedAt,
        )
        assertEquals(BookMatch.Result.NoMatch, result)
    }

    @Test
    fun aStrongTitleWithAnAgreeingAuthorIsAccepted() {
        val result = BookMatch.pick(
            signals(title = "The Selfish Gene", author = "Richard Dawkins"),
            listOf(candidate(title = "The Selfish Gene", authors = listOf("Richard Dawkins"))),
            fetchedAt,
        )
        assertEquals(MatchConfidence.TITLE_AUTHOR, (result as BookMatch.Result.Matched).confidence)
    }

    @Test
    fun theCatalogueFormOfANameMatchesTheTitlePageForm() {
        // "Dawkins, Richard" and "Richard Dawkins" are the same person.
        val result = BookMatch.pick(
            signals(title = "The Selfish Gene", author = "Richard Dawkins"),
            listOf(candidate(title = "The Selfish Gene", authors = listOf("Dawkins, Richard"))),
            fetchedAt,
        )
        assertEquals(MatchConfidence.TITLE_AUTHOR, (result as BookMatch.Result.Matched).confidence)
    }

    @Test
    fun aCandidateThatContradictsTheDocumentsAuthorIsRejected() {
        val result = BookMatch.pick(
            signals(title = "The Selfish Gene", author = "Richard Dawkins"),
            listOf(candidate(title = "The Selfish Gene", authors = listOf("Someone Else"))),
            fetchedAt,
        )
        assertEquals(BookMatch.Result.NoMatch, result)
    }

    @Test
    fun aNearExactTitleWithSupportingMetadataIsAcceptedWhenNoAuthorWasRead() {
        val result = BookMatch.pick(
            signals(title = "The Selfish Gene"),
            listOf(
                candidate(
                    title = "The Selfish Gene",
                    publishedDate = "1976",
                    pageCount = 224,
                ),
            ),
            fetchedAt,
        )
        assertEquals(MatchConfidence.TITLE_ONLY, (result as BookMatch.Result.Matched).confidence)
    }

    @Test
    fun aNearExactTitleWithNothingToSupportItIsNotEnough() {
        val result = BookMatch.pick(
            signals(title = "The Selfish Gene"),
            listOf(candidate(title = "The Selfish Gene")),
            fetchedAt,
        )
        assertEquals(BookMatch.Result.NoMatch, result)
    }

    @Test
    fun twoEquallyPlausibleCandidatesAreNeverArbitrarilyResolved() {
        val result = BookMatch.pick(
            signals(title = "The Selfish Gene", author = "Richard Dawkins"),
            listOf(
                candidate(title = "The Selfish Gene", authors = listOf("Richard Dawkins"), publishedDate = "1976"),
                candidate(title = "The Selfish Gene", authors = listOf("Richard Dawkins"), publishedDate = "1989"),
            ),
            fetchedAt,
        )
        assertEquals(BookMatch.Result.Ambiguous, result)
    }

    @Test
    fun aStrictlyBetterCandidateWinsOverAWeakerOne() {
        val result = BookMatch.pick(
            signals(title = "The Selfish Gene", author = "Richard Dawkins"),
            listOf(
                candidate(title = "The Selfish Gene", publishedDate = "1976", pageCount = 224),
                candidate(title = "The Selfish Gene", authors = listOf("Richard Dawkins")),
            ),
            fetchedAt,
        )
        assertEquals(MatchConfidence.TITLE_AUTHOR, (result as BookMatch.Result.Matched).confidence)
    }

    @Test
    fun aGenericTitleCanNeverCarryAMatch() {
        for (title in listOf("Untitled", "Document", "Scan", "Book", "Final", "123456")) {
            assertEquals(
                "expected '$title' to be rejected",
                BookMatch.Result.NoMatch,
                BookMatch.pick(signals(title = title), listOf(candidate(title = title, pageCount = 100)), fetchedAt),
            )
        }
    }

    @Test
    fun anUnrelatedTitleScoresBelowTheFloorAndIsRejected() {
        val result = BookMatch.pick(
            signals(title = "Deep Work", author = "Cal Newport"),
            listOf(candidate(title = "The Selfish Gene", authors = listOf("Richard Dawkins"))),
            fetchedAt,
        )
        assertEquals(BookMatch.Result.NoMatch, result)
    }

    @Test
    fun noCandidatesIsNoMatch() {
        assertEquals(BookMatch.Result.NoMatch, BookMatch.pick(signals(title = "Anything"), emptyList(), fetchedAt))
    }

    @Test
    fun similarityIgnoresCaseAccentsPunctuationAndWordOrder() {
        assertEquals(1.0, BookMatch.titleSimilarity("The Selfish Gene", "the selfish gene"), 0.0001)
        assertEquals(1.0, BookMatch.titleSimilarity("Selfish Gene, The", "the selfish gene"), 0.0001)
        assertEquals(1.0, BookMatch.titleSimilarity("García Márquez", "garcia marquez"), 0.0001)
        // A subtitle added or dropped still matches strongly.
        assertTrue(BookMatch.titleSimilarity("The Selfish Gene", "The Selfish Gene: 40th Anniversary Edition") >= 0.9)
        // Two different books must not look alike.
        assertTrue(BookMatch.titleSimilarity("Deep Work", "The Selfish Gene") < 0.6)
        assertEquals(0.0, BookMatch.titleSimilarity(null, "anything"), 0.0001)
        assertEquals(0.0, BookMatch.titleSimilarity("", ""), 0.0001)
    }

    @Test
    fun genericTitlesAreRecognised() {
        assertTrue(BookMatch.isGeneric("Untitled"))
        assertTrue(BookMatch.isGeneric("   "))
        assertTrue(BookMatch.isGeneric("2024"))
        assertFalse(BookMatch.isGeneric("The Selfish Gene"))
        assertFalse(BookMatch.isGeneric("Sapiens"))
    }

    @Test
    fun theAcceptedMetadataKeepsOnlyHttpsCoverUrls() {
        val matched = BookMatch.pick(
            signals(isbn13 = "9780199291151"),
            listOf(
                candidate(title = "The Selfish Gene", isbn13 = "9780199291151")
                    .copy(coverUrl = "http://books.google.com/cover.jpg"),
            ),
            fetchedAt,
        ) as BookMatch.Result.Matched
        assertNull(matched.metadata.coverUrl)
    }

    @Test
    fun anAuthorMatchRequiresTheFamilyName() {
        assertTrue(BookMatch.authorMatches("Richard Dawkins", listOf("Dawkins, Richard")))
        assertTrue(BookMatch.authorMatches("Gabriel Garcia Marquez", listOf("García Márquez, Gabriel")))
        assertFalse(BookMatch.authorMatches("Richard Dawkins", listOf("Daniel Dennett")))
        assertFalse(BookMatch.authorMatches(null, listOf("Richard Dawkins")))
        assertFalse(BookMatch.authorMatches("Richard Dawkins", emptyList()))
    }
}
