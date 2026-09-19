package com.voxora.core.reader

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue orchestrator.
 *
 * The behaviour that matters is what happens when a catalogue misbehaves: an unreachable primary
 * must not stop the fallback being asked, and "nobody could be reached" must stay distinguishable
 * from "everybody was asked and this book is not in any of them".
 */
class BookMetadataLookupTest {

    private class FakeSource(
        override val provider: MetadataProvider,
        private val result: MetadataResult,
    ) : BookMetadataSource {
        var calls: Int = 0
            private set

        override suspend fun search(signals: BookSignals): MetadataResult {
            calls++
            return result
        }
    }

    private fun signals(
        isbn13: String? = null,
        title: String? = "The Selfish Gene",
        author: String? = "Richard Dawkins",
    ) = BookSignals(isbn13 = isbn13, isbn10 = null, title = title, author = author, filename = "book.pdf")

    private fun candidate(
        title: String = "The Selfish Gene",
        provider: MetadataProvider = MetadataProvider.GOOGLE_BOOKS,
        providerId: String? = "id-1",
        authors: List<String> = listOf("Richard Dawkins"),
        isbn13: String? = null,
        publisher: String? = null,
    ) = BookCandidate(
        provider = provider,
        providerId = providerId,
        title = title,
        authors = authors,
        isbn13 = isbn13,
        publisher = publisher,
    )

    private fun lookup(
        vararg sources: BookMetadataSource,
        now: Long = 7_000L,
    ) = BookMetadataLookup(sources.toList(), clock = { now })

    @Test
    fun aCandidateFromEitherCatalogueCanBeMatched() = runBlocking {
        val google = FakeSource(MetadataProvider.GOOGLE_BOOKS, MetadataResult.Found(listOf(candidate())))
        val openLibrary = FakeSource(MetadataProvider.OPEN_LIBRARY, MetadataResult.NotFound)

        val outcome = lookup(google, openLibrary).find(signals())
        val matched = outcome as BookLookupOutcome.Matched
        assertEquals(MetadataProvider.GOOGLE_BOOKS, matched.metadata.provider)
        assertEquals(1, google.calls)
        assertEquals(1, openLibrary.calls)
    }

    @Test
    fun theFallbackIsStillAskedWhenThePrimaryCannotAnswer() = runBlocking {
        // A Google quota rejection must not mean "this book cannot be identified".
        val google = FakeSource(
            MetadataProvider.GOOGLE_BOOKS,
            MetadataResult.Unavailable(MetadataUnavailable.RATE_LIMITED),
        )
        val openLibrary = FakeSource(
            MetadataProvider.OPEN_LIBRARY,
            MetadataResult.Found(
                listOf(candidate(provider = MetadataProvider.OPEN_LIBRARY, providerId = "/works/OL1W")),
            ),
        )

        val outcome = lookup(google, openLibrary).find(signals())
        val matched = outcome as BookLookupOutcome.Matched
        assertEquals(MetadataProvider.OPEN_LIBRARY, matched.metadata.provider)
        assertEquals(1, openLibrary.calls)
    }

    @Test
    fun whenNoCatalogueCouldBeReachedTheOutcomeIsUnavailableRatherThanNotFound() = runBlocking {
        val google = FakeSource(
            MetadataProvider.GOOGLE_BOOKS,
            MetadataResult.Unavailable(MetadataUnavailable.TIMEOUT),
        )
        val openLibrary = FakeSource(
            MetadataProvider.OPEN_LIBRARY,
            MetadataResult.Unavailable(MetadataUnavailable.OFFLINE),
        )

        // The most recent failure is the one reported.
        assertEquals(
            BookLookupOutcome.Unavailable(MetadataUnavailable.OFFLINE),
            lookup(google, openLibrary).find(signals()),
        )
    }

    @Test
    fun whenACatalogueAnsweredAndNothingMatchedTheOutcomeIsNotFound() = runBlocking {
        // One source answered "nothing here"; the other was unreachable. The book is not in the
        // catalogue that answered, so "not found" is the honest answer.
        val google = FakeSource(MetadataProvider.GOOGLE_BOOKS, MetadataResult.NotFound)
        val openLibrary = FakeSource(
            MetadataProvider.OPEN_LIBRARY,
            MetadataResult.Unavailable(MetadataUnavailable.OFFLINE),
        )
        assertEquals(BookLookupOutcome.NotFound, lookup(google, openLibrary).find(signals()))
    }

    @Test
    fun anAnsweredSourceWithNoCandidatesIsNotFoundRatherThanUnavailable() = runBlocking {
        val google = FakeSource(MetadataProvider.GOOGLE_BOOKS, MetadataResult.Found(emptyList()))
        assertEquals(BookLookupOutcome.NotFound, lookup(google).find(signals()))
    }

    @Test
    fun withNoCatalogueConfiguredTheOutcomeIsUnavailable() = runBlocking {
        // Nothing was asked, so Voxora genuinely does not know.
        assertEquals(
            BookLookupOutcome.Unavailable(MetadataUnavailable.SERVER_ERROR),
            lookup().find(signals()),
        )
    }

    @Test
    fun aTieBetweenCataloguesIsRefusedRatherThanBroken() = runBlocking {
        // Both catalogues describe the work equally well, so neither is chosen.
        val google = FakeSource(MetadataProvider.GOOGLE_BOOKS, MetadataResult.Found(listOf(candidate(providerId = "a"))))
        val openLibrary = FakeSource(
            MetadataProvider.OPEN_LIBRARY,
            MetadataResult.Found(
                listOf(candidate(provider = MetadataProvider.OPEN_LIBRARY, providerId = "b")),
            ),
        )
        assertEquals(BookLookupOutcome.Ambiguous, lookup(google, openLibrary).find(signals()))
    }

    @Test
    fun anExactIdentifierMatchWinsOverEverythingAndIsNotAmbiguous() = runBlocking {
        val google = FakeSource(
            MetadataProvider.GOOGLE_BOOKS,
            MetadataResult.Found(listOf(candidate(providerId = "a", isbn13 = "9780306406157"))),
        )
        val openLibrary = FakeSource(
            MetadataProvider.OPEN_LIBRARY,
            MetadataResult.Found(
                listOf(
                    candidate(
                        provider = MetadataProvider.OPEN_LIBRARY,
                        providerId = "b",
                        isbn13 = "9780306406157",
                    ),
                ),
            ),
        )
        val outcome = lookup(google, openLibrary).find(signals(isbn13 = "9780306406157"))
        val matched = outcome as BookLookupOutcome.Matched
        assertEquals(MatchConfidence.ISBN_EXACT, matched.metadata.confidence)
    }

    @Test
    fun theClockSuppliesTheCacheTimestamp() = runBlocking {
        val google = FakeSource(MetadataProvider.GOOGLE_BOOKS, MetadataResult.Found(listOf(candidate())))
        val matched = lookup(google, now = 123_456L).find(signals()) as BookLookupOutcome.Matched
        assertEquals(123_456L, matched.metadata.fetchedAt)
    }

    @Test
    fun aGenericTitleFromTheDocumentIsNeverMatched() = runBlocking {
        val google = FakeSource(
            MetadataProvider.GOOGLE_BOOKS,
            MetadataResult.Found(listOf(candidate(title = "Report", authors = emptyList(), publisher = "X"))),
        )
        assertEquals(BookLookupOutcome.NotFound, lookup(google).find(signals(title = "Report")))
    }

    @Test
    fun everySourceIsAskedEvenAfterAMatchWouldBePossible() = runBlocking {
        // Both are always consulted, so the union is judged rather than the first answer.
        val google = FakeSource(MetadataProvider.GOOGLE_BOOKS, MetadataResult.NotFound)
        val openLibrary = FakeSource(MetadataProvider.OPEN_LIBRARY, MetadataResult.NotFound)
        lookup(google, openLibrary).find(signals())
        assertTrue(google.calls == 1 && openLibrary.calls == 1)
    }
}
