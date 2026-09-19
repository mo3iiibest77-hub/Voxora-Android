package com.voxora.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rule that decides whether a document is a PDF or a text file.
 *
 * ## Why this class exists
 *
 * Reopening a saved book failed on a real device with `Unsupported file type` while the same file
 * imported and narrated perfectly. The cause was the order of evidence: Voxora's own copy is a
 * `file://` URI with no MIME type, so the type fell through to the *name* — and the name it was
 * given was the book's display title, which automatic identification had replaced with the
 * catalogue title (`"The Selfish Gene"`, no extension). The document was never the problem.
 *
 * These tests pin the order, and the last one is the regression itself.
 */
class ReaderDocumentTypeTest {

    @Test
    fun knownTypeWinsOverEverythingElse() {
        // The record already knows what the import read. Nothing observed later may contradict it —
        // not a missing MIME type, not a title that looks like nothing in particular.
        assertEquals(ReaderSourceType.PDF, ReaderDocumentType.resolve(ReaderSourceType.PDF, null, "x.txt"))
        assertEquals(ReaderSourceType.TXT, ReaderDocumentType.resolve(ReaderSourceType.TXT, null, "x.pdf"))
        assertEquals(
            ReaderSourceType.PDF,
            ReaderDocumentType.resolve(ReaderSourceType.PDF, "text/plain", "x.txt"),
        )
    }

    @Test
    fun providerMimeTypeDecidesWhenNothingIsKnown() {
        assertEquals(ReaderDocumentType.MIME_PDF, "application/pdf")
        assertEquals(
            ReaderSourceType.PDF,
            ReaderDocumentType.resolve(null, "application/pdf", "no-extension"),
        )
        assertEquals(
            ReaderSourceType.TXT,
            ReaderDocumentType.resolve(null, "text/plain", "no-extension"),
        )
        // A charset parameter is part of the same MIME type, not a different one.
        assertEquals(
            ReaderSourceType.TXT,
            ReaderDocumentType.resolve(null, "text/plain; charset=utf-8", "no-extension"),
        )
        assertEquals(
            ReaderSourceType.PDF,
            ReaderDocumentType.resolve(null, "APPLICATION/PDF", "no-extension"),
        )
    }

    @Test
    fun fileNameExtensionDecidesWhenThereIsNoMimeType() {
        assertEquals(ReaderSourceType.PDF, ReaderDocumentType.resolve(null, null, "deep-work.pdf"))
        assertEquals(ReaderSourceType.TXT, ReaderDocumentType.resolve(null, null, "notes.txt"))
        assertEquals(ReaderSourceType.PDF, ReaderDocumentType.resolve(null, null, "REPORT.PDF"))
    }

    @Test
    fun nothingDeterminesTheTypeForANameWithoutAnExtension() {
        assertNull(ReaderDocumentType.resolve(null, null, "The Selfish Gene"))
        assertNull(ReaderDocumentType.resolve(null, null, ""))
        assertNull(ReaderDocumentType.resolve(null, null, null))
        assertNull(ReaderDocumentType.resolve(null, null, "archive.epub"))
    }

    /**
     * The regression.
     *
     * A stored book is re-extracted from a `file://` copy — no MIME type — and its display title is
     * the catalogue title, because identification succeeded. Before the fix this resolved to null
     * and the book was refused; it must resolve to the type the record holds.
     */
    @Test
    fun aStoredBookSurvivesItsTitleBeingReplacedByTheCatalogueTitle() {
        val resolved = ReaderDocumentType.resolve(
            known = ReaderSourceType.PDF,
            mime = null,
            name = "The Selfish Gene",
        )
        assertEquals(ReaderSourceType.PDF, resolved)

        // And without the known type — the shape of the original defect — the same inputs cannot be
        // resolved, which is exactly why the persisted type has to be passed in.
        assertNull(ReaderDocumentType.resolve(known = null, mime = null, name = "The Selfish Gene"))
    }

    @Test
    fun aTxtBookSurvivesItsTitleBeingReplacedToo() {
        assertEquals(
            ReaderSourceType.TXT,
            ReaderDocumentType.resolve(ReaderSourceType.TXT, null, "Some Collected Essays"),
        )
    }
}
