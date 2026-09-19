package com.voxora.core.reader

import java.util.Locale

/**
 * Decides whether a document is a PDF or a text file.
 *
 * ## Why this is a rule of its own
 *
 * The order below is load-bearing, and getting it wrong broke reopening a saved book in a way that
 * looked like a corrupt file:
 *
 * 1. **A known type always wins.** A stored book's record carries the `sourceType` that the import
 *    that actually read the document determined. Nothing observed later may contradict it.
 * 2. A MIME type from the provider, when there is one.
 * 3. The extension of a **file name**.
 *
 * The trap is step 3. Voxora's own copy of a book is a `file://` URI, which carries **no MIME
 * type**, so a reopen had nothing but a name to go on. And the name it was handed was the book's
 * *display title* — which, once automatic identification succeeds, is the catalogue title
 * (`"The Selfish Gene"`), not a file name. A perfectly readable PDF was therefore rejected as an
 * unsupported file type the moment it had been identified.
 *
 * So the type is resolved from evidence in that order and nowhere else. A display title is not a
 * file name and must never be treated as one.
 *
 * Pure JVM — no `android.*` — so the order is unit-tested directly.
 */
object ReaderDocumentType {

    /**
     * Resolves the type, or null when nothing determines it and the caller must refuse the file.
     *
     * @param known the type the caller already knows, from a persisted record.
     * @param mime the provider MIME type, already normalised to lower case, or null.
     * @param name a file name — never a display title. Null or blank means "no name evidence".
     */
    fun resolve(known: ReaderSourceType?, mime: String?, name: String?): ReaderSourceType? {
        if (known != null) return known
        val normalisedMime = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        return when {
            normalisedMime == MIME_PDF -> ReaderSourceType.PDF
            normalisedMime == MIME_TEXT -> ReaderSourceType.TXT
            else -> fromExtension(name)
        }
    }

    /** The type a file name's extension names, or null when it names neither. */
    private fun fromExtension(name: String?): ReaderSourceType? {
        val extension = name?.substringAfterLast('.', "")?.trim()?.lowercase(Locale.ROOT)
        return when (extension) {
            "pdf" -> ReaderSourceType.PDF
            "txt" -> ReaderSourceType.TXT
            else -> null
        }
    }

    const val MIME_PDF = "application/pdf"
    const val MIME_TEXT = "text/plain"
}
