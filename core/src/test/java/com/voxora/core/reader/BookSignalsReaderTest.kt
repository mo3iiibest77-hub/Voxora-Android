package com.voxora.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading identification signals out of a document's opening.
 *
 * The load-bearing claim is that an ISBN is only accepted when its **check digit proves it**, and
 * that a title is only taken when the line does not look like page furniture. These tests are built
 * from realistic title pages, including the ones that should be rejected.
 */
class BookSignalsReaderTest {

    @Test
    fun isbnCheckDigitsAcceptRealIdentifiers() {
        assertTrue(BookSignalsReader.isValidIsbn("9780306406157"))
        assertTrue(BookSignalsReader.isValidIsbn("9780140328721"))
        assertTrue(BookSignalsReader.isValidIsbn("0306406152"))
        // A trailing X is a legal ISBN-10 check digit, and only in the last position.
        assertTrue(BookSignalsReader.isValidIsbn("097522980X"))
        assertTrue(BookSignalsReader.isValidIsbn("043942089X"))
    }

    @Test
    fun isbnCheckDigitsRejectPlausibleLookingNumbers() {
        // One digit changed in the check position, and a number that is simply not an ISBN.
        assertFalse(BookSignalsReader.isValidIsbn("9780306406158"))
        assertFalse(BookSignalsReader.isValidIsbn("0306406153"))
        assertFalse(BookSignalsReader.isValidIsbn("1234567890"))
        assertFalse(BookSignalsReader.isValidIsbn("030640615X"))
        // X may not appear before the end.
        assertFalse(BookSignalsReader.isValidIsbn("X306406152"))
        assertFalse(BookSignalsReader.isValidIsbn(""))
        assertFalse(BookSignalsReader.isValidIsbn("978030640615"))
    }

    @Test
    fun anIsbnTenIsConvertedToTheThirteenDigitFormCataloguesStore() {
        assertEquals("9780306406157", BookSignalsReader.toIsbn13("0306406152"))
        assertEquals("9780975229804", BookSignalsReader.toIsbn13("097522980X"))
        // Already thirteen digits: unchanged.
        assertEquals("9780306406157", BookSignalsReader.toIsbn13("9780306406157"))
        assertNull(BookSignalsReader.toIsbn13("1234567890"))
        assertNull(BookSignalsReader.toIsbn13("nonsense"))
    }

    @Test
    fun normalizeStripsSeparatorsAndKeepsATrailingX() {
        assertEquals("9780306406157", BookSignalsReader.normalizeIsbn("978-0-306-40615-7"))
        assertEquals("097522980X", BookSignalsReader.normalizeIsbn("0-9752298-0-X"))
    }

    @Test
    fun aTitlePageYieldsItsTitleAuthorAndIsbn() {
        val page = """
            THE SELFISH GENE
            Richard Dawkins
            Oxford University Press
            ISBN 978-0-19-878860-7
            Copyright 1976
        """.trimIndent()

        val signals = BookSignalsReader.from("scan_0042.pdf", page)
        assertEquals("THE SELFISH GENE", signals.title)
        assertEquals("Richard Dawkins", signals.author)
        assertEquals("9780198788607", signals.isbn13)
        assertEquals("9780198788607", signals.isbn)
        assertTrue(signals.hasDocumentSignal)
    }

    @Test
    fun anExplicitByMarkerIsTheStrongestAuthorSignal() {
        val page = """
            Silent Spring
            by Rachel Carson
            1962
        """.trimIndent()
        val signals = BookSignalsReader.from("book.pdf", page)
        assertEquals("Silent Spring", signals.title)
        assertEquals("Rachel Carson", signals.author)
    }

    @Test
    fun aByLineThatIsActuallyASentenceIsNotAnAuthor() {
        // "By 1976, ..." is prose, not a credit, and a digit disqualifies it.
        val page = """
            On the Origin of Species
            By 1976, the theory had been widely accepted.
        """.trimIndent()
        assertNull(BookSignalsReader.from("origin.pdf", page).author)
    }

    @Test
    fun copyrightAndCatalogueLinesAreNotTitles() {
        val page = """
            Copyright © 1976 by the publisher
            All rights reserved
            ISBN 0-306-40615-2
            Table of Contents
            Chapter 1
            The Selfish Gene
        """.trimIndent()
        assertEquals("The Selfish Gene", BookSignalsReader.from("x.pdf", page).title)
    }

    @Test
    fun aRunningHeaderOfPageNumbersIsNotATitle() {
        val page = """
            12
            13
            A Real Title
        """.trimIndent()
        assertEquals("A Real Title", BookSignalsReader.from("x.pdf", page).title)
    }

    @Test
    fun aSentenceEndingInAFullStopIsNotATitle() {
        val page = """
            This is the first sentence of the document.
            A Real Title
        """.trimIndent()
        assertEquals("A Real Title", BookSignalsReader.from("x.pdf", page).title)
    }

    @Test
    fun theFileNameIsTheLastResortTitle() {
        val signals = BookSignalsReader.from("the_selfish_gene.pdf", "Copyright 1976")
        assertEquals("the selfish gene", signals.title)
        assertNull(signals.author)
        // Nothing came from the document, so there is no identifier and no credit to trust.
        assertNull(signals.isbn)
        assertNull(signals.isbn13)
    }

    @Test
    fun aNameOnlyDocumentStillCountsAsASearchableSignal() {
        // The fallback title is the cleaned file name, and it is treated as a usable query because
        // a file is very often named after its book. Safety comes from BookMatch, not from refusing
        // to ask: a generic name like "report" is rejected there outright.
        val signals = BookSignalsReader.from("report.pdf", "")
        assertTrue(signals.hasDocumentSignal)
        assertEquals("report", signals.title)
    }

    @Test
    fun aFileNameTitleIsCleanedButNotInvented() {
        assertEquals("the selfish gene", BookSignalsReader.filenameTitle("the-selfish-gene.pdf"))
        assertEquals("book draft two", BookSignalsReader.filenameTitle("book_draft_two.pdf"))
        // Bracketed noise from a download is dropped.
        assertEquals("the selfish gene", BookSignalsReader.filenameTitle("the selfish gene (1).pdf"))
        // A name with no letters at all is not a title.
        assertNull(BookSignalsReader.filenameTitle("12345.pdf"))
    }

    @Test
    fun aSubtitleUnderTheTitleIsNotMistakenForAnAuthor() {
        val page = """
            The Selfish Gene
            A Novel
            Richard Dawkins
        """.trimIndent()
        // "A Novel" announces itself as a subtitle, so the author search must skip it.
        assertEquals("Richard Dawkins", BookSignalsReader.from("x.pdf", page).author)
    }

    @Test
    fun anIsbnPrintedWithSeparatorsIsStillFound() {
        val page = "Some Title\nISBN: 978-0-306-40615-7\n"
        assertEquals("9780306406157", BookSignalsReader.from("x.pdf", page).isbn13)
    }

    @Test
    fun anIsbnTenIsReportedAsSuchWhenNoThirteenIsPresent() {
        val page = "Some Title\nISBN 0-306-40615-2\n"
        val signals = BookSignalsReader.from("x.pdf", page)
        assertNull(signals.isbn13)
        assertEquals("0306406152", signals.isbn10)
        assertEquals("0306406152", signals.isbn)
    }

    @Test
    fun anEmptyDocumentFallsBackToTheFileNameAndClaimsNothingElse() {
        val signals = BookSignalsReader.from("report.pdf", "")
        assertNull(signals.isbn)
        assertNull(signals.author)
        assertEquals("report", signals.title)
    }
}
