package com.voxora.core.reader

/**
 * Every state change the Reader library can make, as pure functions over an immutable list.
 *
 * ## Why a reducer and not just a repository
 *
 * The library's real rules — "importing a second book must not disturb the first one's progress",
 * "the most recently read book is the one to resume", "a completed book stays completed" — are
 * decisions, not storage. Putting them in a pure object means they are unit-tested directly, on a
 * plain JVM, without a device or a DataStore. The repository's job is then only to persist whatever
 * this object produced, and it has no policy of its own to get wrong.
 *
 * ## Ordering
 *
 * The stored list keeps **import order** and is never re-sorted: rewriting the list on every read
 * would make the stored bytes churn for no reason. Recency is a *derived* property
 * ([mostRecent], [byRecency]) computed from `lastReadAt`, which is why a book's position in the
 * list carries no meaning.
 *
 * Every function is total: an id that is not in the list is a no-op rather than an error, so a
 * stale save arriving after a deletion cannot resurrect a book or crash the library.
 */
object ReaderLibrary {

    /** Adds [book], replacing any existing record with the same id. */
    fun upsert(books: List<ReaderBook>, book: ReaderBook): List<ReaderBook> {
        val index = books.indexOfFirst { it.id == book.id }
        if (index < 0) return books + book
        return books.toMutableList().also { it[index] = book }
    }

    /** The book with [id], or null. */
    fun find(books: List<ReaderBook>, id: String?): ReaderBook? =
        if (id == null) null else books.firstOrNull { it.id == id }

    /** Records a reading position, leaving every other book untouched. */
    fun withPosition(
        books: List<ReaderBook>,
        id: String,
        chunk: Int,
        atMillis: Long,
    ): List<ReaderBook> = mapBook(books, id) { it.withPosition(chunk, atMillis) }

    /** Records that the reader was in a book, without moving its position. */
    fun touched(books: List<ReaderBook>, id: String, atMillis: Long): List<ReaderBook> =
        mapBook(books, id) { it.touched(atMillis) }

    /** Records that narration finished. */
    fun completed(books: List<ReaderBook>, id: String, atMillis: Long): List<ReaderBook> =
        mapBook(books, id) { it.completed(atMillis) }

    /** Restarts a finished book at its first chunk. */
    fun reopened(books: List<ReaderBook>, id: String, atMillis: Long): List<ReaderBook> =
        mapBook(books, id) { it.reopened(atMillis) }

    /** Attaches cached bibliographic data. */
    fun withMetadata(books: List<ReaderBook>, id: String, metadata: BookMetadata): List<ReaderBook> =
        mapBook(books, id) { it.withMetadata(metadata) }

    /** Records where an identification attempt ended. */
    fun withLookup(
        books: List<ReaderBook>,
        id: String,
        state: MetadataLookupState,
    ): List<ReaderBook> = mapBook(books, id) { it.withLookup(state) }

    /** Caches a generated Book Intelligence overview for one output language. */
    fun withOverview(
        books: List<ReaderBook>,
        id: String,
        overview: BookIntelOverview,
    ): List<ReaderBook> = mapBook(books, id) { it.withOverview(overview) }

    /** Removes a book. */
    fun remove(books: List<ReaderBook>, id: String): List<ReaderBook> = books.filterNot { it.id == id }

    /**
     * Records that a book's document copy is gone.
     *
     * A no-op for an id that is not in the list, like every other function here, so a stale failure
     * arriving after a deletion cannot resurrect a book.
     */
    fun markedUnavailable(books: List<ReaderBook>, id: String): List<ReaderBook> =
        mapBook(books, id) { it.unavailable() }

    /**
     * The book to offer first: the one the reader was most recently in.
     *
     * Ties are broken by import time and then by id, so the answer is stable rather than dependent
     * on list order. Returns null for an empty library.
     */
    fun mostRecent(books: List<ReaderBook>): ReaderBook? =
        books.maxWithOrNull(compareBy({ it.lastReadAt }, { it.importedAt }, { it.id }))

    /** The library as the UI lists it: most recently read first. */
    fun byRecency(books: List<ReaderBook>): List<ReaderBook> =
        books.sortedWith(
            compareByDescending<ReaderBook> { it.lastReadAt }
                .thenByDescending { it.importedAt }
                .thenBy { it.id },
        )

    private inline fun mapBook(
        books: List<ReaderBook>,
        id: String,
        transform: (ReaderBook) -> ReaderBook,
    ): List<ReaderBook> {
        val index = books.indexOfFirst { it.id == id }
        if (index < 0) return books
        val updated = transform(books[index])
        if (updated == books[index]) return books
        return books.toMutableList().also { it[index] = updated }
    }
}
