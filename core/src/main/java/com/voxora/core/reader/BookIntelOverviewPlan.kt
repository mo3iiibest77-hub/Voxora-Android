package com.voxora.core.reader

/**
 * The one decision about Book Intelligence that both the UI and the repository have to agree on:
 * **does this book need an overview for this language, and what would it be generated from?**
 *
 * ## Why it is a shared, pure decision
 *
 * Two layers act on this. The ViewModel decides whether to start a generation at all, and the
 * repository decides which generator to call. If they disagreed, one of two things would happen:
 * the UI would skip a book the repository could have handled, or the repository would be asked for
 * an overview it must refuse. The UI's previous test — "the book has a catalogue record" — was
 * exactly the first failure: a book the catalogues could not identify was never offered to the
 * generator, which is why a book like an Ahmad Kasravi title could sit with no Book Intelligence
 * even though a search could have described it.
 *
 * Keeping the rule in one pure function is also what makes it testable without a network, a device
 * or a repository.
 */
object BookIntelOverviewPlan {

    /** What should happen for one book in one Reader output language. */
    sealed interface Need {
        /** A usable text is already cached. Nothing to do, and nothing to pay for. */
        data object None : Need

        /** Generate from the book's catalogue record. */
        data object FromMetadata : Need

        /**
         * Generate from the book's own signals plus the findings of a web search.
         *
         * [fingerprint] is the search question the text must have been generated for; see
         * [BookSearchPrompt.fingerprint]. A cached text with a different one is not reusable.
         */
        data class FromContext(val fingerprint: String) : Need
    }

    /**
     * The plan for [book] in [language].
     *
     * The catalogue path is preferred whenever a record exists, because a catalogue record is
     * strictly better evidence than a search — it is why the search is a fallback and not a first
     * choice. A book with neither a record nor any usable signal has no plan at all: there would be
     * nothing to generate from, and generating anyway is the fabrication the product forbids.
     */
    fun need(book: ReaderBook, language: String): Need {
        if (language.isBlank()) return Need.None
        val cached = book.overviewFor(language)
        val metadata = book.metadata
        if (metadata != null) {
            return if (BookIntelOverviewPrompt.isUsableFor(cached, language)) Need.None else Need.FromMetadata
        }
        val signals = book.signals ?: return Need.None
        if (!signals.hasDocumentSignal && signals.filename.isBlank()) return Need.None
        val fingerprint = BookSearchPrompt.fingerprint(signals)
        return if (BookIntelOverviewPrompt.isUsableFor(cached, language, fingerprint)) {
            Need.None
        } else {
            Need.FromContext(fingerprint)
        }
    }
}
