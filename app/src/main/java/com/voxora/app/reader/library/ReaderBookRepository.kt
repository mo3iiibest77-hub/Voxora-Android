package com.voxora.app.reader.library

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voxora.app.reader.ReaderChunkCache
import com.voxora.app.util.VoxoraLog
import com.voxora.core.reader.BookIntelOverview
import com.voxora.core.reader.BookIntelOverviewGenerator
import com.voxora.core.reader.BookIntelOverviewPrompt
import com.voxora.core.reader.BookLookupOutcome
import com.voxora.core.reader.BookMetadataLookup
import com.voxora.core.reader.BookSignals
import com.voxora.core.reader.GeminiHttpTextTransport
import com.voxora.core.reader.GoogleBooksSource
import com.voxora.core.reader.MetadataLookupState
import com.voxora.core.reader.MetadataUnavailable
import com.voxora.core.reader.OpenLibrarySource
import com.voxora.core.reader.ReaderBook
import com.voxora.core.reader.ReaderBookCodec
import com.voxora.core.reader.ReaderBookState
import com.voxora.core.reader.ReaderLibrary
import com.voxora.core.reader.ReaderSourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.readerLibraryStore by preferencesDataStore("voxora_reader_library")

/** A document that has been copied into app storage but has no library record yet. */
data class StagedImport(
    val id: String,
    val file: File,
    val displayName: String,
    val sourceType: ReaderSourceType,
)

/**
 * The persistent Reader library: imported books, their saved positions and their cached metadata.
 *
 * ## What this class is, and what it is not
 *
 * It is a **shell around [ReaderLibrary] and [ReaderBookCodec]**. Every decision about what the
 * library means — that importing a second book leaves the first one's progress alone, that the most
 * recently read book is the one to resume, that a completed book stays completed — lives in the
 * pure core object and is unit-tested there. This class only moves bytes: it copies documents into
 * app storage, reads and writes one DataStore key, and asks the catalogues for metadata.
 *
 * ## Storage
 *
 * - The **records** are one versioned JSON document under a single DataStore preference
 *   (`voxora_reader_library`). A separate store, not `UserPrefs`: the library is a collection with
 *   its own lifecycle, and mixing it into the global preference file would make every settings read
 *   carry it.
 * - The **documents** are files under `filesDir/reader/books/`, owned by Voxora (see
 *   [ReaderDocumentStore]). A record is meaningless without its file, so both are written and
 *   removed together.
 *
 * ## Threading
 *
 * Every public function is a `suspend` function that switches to [Dispatchers.IO] for its file and
 * DataStore work, and read-modify-write cycles are serialized by [mutex] so two concurrent saves
 * cannot lose one another. Nothing here is ever called from the narration path: the controller
 * persists a position *beside* playback, never inside it.
 *
 * ## Failure handling
 *
 * A metadata lookup can fail in every way a network can, and none of it is allowed to matter: the
 * book is already committed before the lookup starts, and a failed lookup only moves the book's
 * [MetadataLookupState] to `UNAVAILABLE`. There is no path in which a catalogue outage loses a book.
 */
@Singleton
class ReaderBookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyBooks = stringPreferencesKey("books")
    private val keyActive = stringPreferencesKey("active_book_id")
    private val keyMigrated = booleanPreferencesKey("legacy_document_migrated")

    private val documents = ReaderDocumentStore(context)

    /**
     * The persisted chunk cache, used here only to drop a book's cached audio when the book itself
     * is removed. The Reader owns reading and writing entries; a cache keyed by a book that no
     * longer exists could never be read again, so removing the record removes its entries too.
     */
    private val chunkCache = ReaderChunkCache(File(context.cacheDir, ReaderChunkCache.DIRECTORY))

    /**
     * The two public catalogues, primary then fallback.
     *
     * Constructed here rather than injected because Voxora has no Hilt module layer: the sources
     * have no dependencies of their own beyond an HTTP client, and a binding module for two
     * stateless objects would be noise. Tests inject a fake [BookMetadataLookup] through the
     * internal constructor below.
     */
    private val lookup = BookMetadataLookup(GoogleBooksSource(), OpenLibrarySource())

    /**
     * Turns a cached catalogue record into an overview in the reader's output language.
     *
     * Sends the metadata only — never the document — and is entirely optional: a failure leaves the
     * book exactly as it was.
     */
    private val overviewGenerator = BookIntelOverviewGenerator(GeminiHttpTextTransport())

    private val mutex = Mutex()
    private val metadataMutex = Mutex()
    private val overviewMutex = Mutex()

    private val _books = MutableStateFlow<List<ReaderBook>>(emptyList())
    val books: StateFlow<List<ReaderBook>> = _books.asStateFlow()

    private val _activeBookId = MutableStateFlow<String?>(null)
    val activeBookId: StateFlow<String?> = _activeBookId.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /** True once the stored library has been read, so the UI can tell "empty" from "not loaded". */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var loaded = false

    /**
     * Reads the stored library once.
     *
     * Idempotent and safe to call from anywhere: the first caller does the work and the rest await
     * the same mutex. A decode failure yields an empty library rather than an error — a corrupt
     * store must not make the Reader unusable.
     */
    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (loaded) return@withLock
            val raw = try {
                context.readerLibraryStore.data.first()[keyBooks]
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VoxoraLog.w(TAG, "Library read failed: ${e.javaClass.simpleName}")
                null
            }
            val books = ReaderBookCodec.decode(raw)
            val active = try {
                context.readerLibraryStore.data.first()[keyActive]
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _books.value = books
            _activeBookId.value = active?.takeIf { id -> books.any { it.id == id } }
            loaded = true
            _ready.value = true
        }
    }

    /** The current library, loaded if necessary. */
    suspend fun snapshot(): List<ReaderBook> {
        ensureLoaded()
        return _books.value
    }

    /** The book with [id], or null. */
    suspend fun book(id: String?): ReaderBook? {
        if (id == null) return null
        ensureLoaded()
        return ReaderLibrary.find(_books.value, id)
    }

    /** The book to offer first: the one most recently read. */
    suspend fun mostRecent(): ReaderBook? {
        ensureLoaded()
        return ReaderLibrary.mostRecent(_books.value)
    }

    /**
     * Copies [source] into app storage, ready to be extracted.
     *
     * The id is generated here so the copy and the eventual record share one identity. Nothing is
     * added to the library yet: a staged import that fails extraction is [discard]ed, so a document
     * that cannot be read never appears as an empty book.
     */
    suspend fun stage(source: Uri, displayName: String, sourceType: ReaderSourceType): StagedImport? {
        val id = UUID.randomUUID().toString()
        val file = documents.copy(source, id, sourceType) ?: return null
        return StagedImport(id = id, file = file, displayName = displayName, sourceType = sourceType)
    }

    /** Adds a staged document to the library. Returns the stored record. */
    suspend fun commit(
        staged: StagedImport,
        title: String,
        chunkCount: Int,
        signals: BookSignals?,
        atMillis: Long = System.currentTimeMillis(),
    ): ReaderBook = withContext(Dispatchers.IO) {
        ensureLoaded()
        val book = ReaderBook(
            id = staged.id,
            localPath = staged.file.absolutePath,
            title = title.trim().ifEmpty { staged.displayName },
            sourceType = staged.sourceType,
            chunkCount = chunkCount.coerceAtLeast(0),
            currentChunk = 0,
            state = ReaderBookState.NOT_STARTED,
            importedAt = atMillis,
            lastReadAt = atMillis,
            metadata = null,
            // Nothing has been attempted yet. The UI starts the lookup from this state, and a
            // failed attempt moves it on, so the automatic lookup runs exactly once per book.
            lookup = MetadataLookupState.NONE,
            signals = signals,
        )
        updateLibrary(active = book.id) { ReaderLibrary.upsert(it, book) }
        book
    }

    /** Deletes a staged document that never became a book. */
    suspend fun discard(staged: StagedImport) {
        documents.delete(staged.file.absolutePath)
    }

    /**
     * Records the reading position.
     *
     * Called on the transitions that matter — import, a chunk becoming current, pause, stop,
     * navigation and completion — never per audio callback. It is a suspend call the controller
     * makes *beside* playback, so a slow write can never stall audio.
     */
    suspend fun recordPosition(
        id: String,
        chunk: Int,
        atMillis: Long = System.currentTimeMillis(),
    ) = mutate(id) { ReaderLibrary.withPosition(it, id, chunk, atMillis) }

    /** Records that narration finished. */
    suspend fun complete(id: String, atMillis: Long = System.currentTimeMillis()) =
        mutate(id) { ReaderLibrary.completed(it, id, atMillis) }

    /** Restarts a finished book at its first chunk. */
    suspend fun reopen(id: String, atMillis: Long = System.currentTimeMillis()) =
        mutate(id) { ReaderLibrary.reopened(it, id, atMillis) }

    /** Marks a book as the one being read, and stamps its recency without moving its position. */
    suspend fun markOpened(id: String, atMillis: Long = System.currentTimeMillis()) =
        mutate(id, active = id) { ReaderLibrary.touched(it, id, atMillis) }

    /**
     * Records that a book's document copy could not be found.
     *
     * The record, its saved position and its cached Book Intelligence are all kept: the copy may
     * come back, and a reader's progress must not be destroyed because a file went missing. The
     * state is persisted so the library stops offering an action that cannot work and the Reader
     * stops re-attempting extraction on every open.
     */
    suspend fun markUnavailable(id: String) =
        mutate(id) { ReaderLibrary.markedUnavailable(it, id) }

    /** Removes a book, its document and its cached narration audio. */
    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        ensureLoaded()
        // Read the path before the record disappears; deleting afterwards keeps the file from
        // outliving a record that no longer references it.
        val path = ReaderLibrary.find(_books.value, id)?.localPath
        updateLibrary(active = _activeBookId.value?.takeIf { it != id }) { ReaderLibrary.remove(it, id) }
        documents.delete(path)
        // A cache entry is keyed by the book, so once the book is gone the entry can never be read
        // again — keeping it would only be dead weight on the device.
        chunkCache.remove(id)
    }

    /** True when the legacy single-document preference still needs migrating into the library. */
    suspend fun needsLegacyMigration(): Boolean {
        ensureLoaded()
        val migrated = try {
            context.readerLibraryStore.data.first()[keyMigrated] == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            true // Cannot read the flag: do not risk importing the legacy document twice.
        }
        return !migrated && _books.value.isEmpty()
    }

    /** Records that the legacy single-document preference has been dealt with. */
    suspend fun markLegacyMigrated() {
        try {
            context.readerLibraryStore.edit { it[keyMigrated] = true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VoxoraLog.w(TAG, "Migration flag write failed: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Looks the book up in the public catalogues and caches whatever was confidently matched.
     *
     * Serialized by [metadataMutex] so a user-initiated refresh and the automatic post-import lookup
     * cannot run at the same time, and so a book is never left in `PENDING` by a second attempt that
     * started before the first finished.
     */
    suspend fun refreshMetadata(id: String) = withContext(Dispatchers.IO) {
        metadataMutex.withLock {
            ensureLoaded()
            val book = ReaderLibrary.find(_books.value, id) ?: return@withLock
            val signals = book.signals ?: return@withLock
            if (!signals.hasDocumentSignal && signals.filename.isBlank()) return@withLock
            mutate(id) { ReaderLibrary.withLookup(it, id, MetadataLookupState.PENDING) }
            val outcome = try {
                lookup.find(signals)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // BookMetadataLookup is total, so this is belt-and-braces only.
                VoxoraLog.w(TAG, "Metadata lookup failed: ${e.javaClass.simpleName}")
                BookLookupOutcome.Unavailable(MetadataUnavailable.SERVER_ERROR)
            }
            when (outcome) {
                is BookLookupOutcome.Matched ->
                    mutate(id) { ReaderLibrary.withMetadata(it, id, outcome.metadata) }
                BookLookupOutcome.NotFound ->
                    mutate(id) { ReaderLibrary.withLookup(it, id, MetadataLookupState.NOT_FOUND) }
                BookLookupOutcome.Ambiguous ->
                    mutate(id) { ReaderLibrary.withLookup(it, id, MetadataLookupState.AMBIGUOUS) }
                is BookLookupOutcome.Unavailable ->
                    mutate(id) { ReaderLibrary.withLookup(it, id, MetadataLookupState.UNAVAILABLE) }
            }
        }
    }

    /**
     * Generates and caches the Book Intelligence overview for [id] in [language], when missing.
     *
     * ## What it sends, and what it does not
     *
     * Only the catalogue record is sent — never the document, never an excerpt of it, and never
     * anything about the reader's position. The narration session is not involved: this is a
     * separate one-shot text call, and it runs beside playback rather than in it.
     *
     * ## Why it is safe to call speculatively
     *
     * A cached text for [language] short-circuits before any network work, so switching languages
     * back and forth is free. A missing catalogue match, an unconfigured key, an offline device and
     * a rejected model all end the same way: nothing is generated, nothing is stored, and the book
     * is untouched. Serialized by [overviewMutex] so two triggers cannot generate the same text
     * twice.
     *
     * @return true when a new overview was generated and stored.
     */
    suspend fun ensureOverview(id: String, language: String, apiKey: String?): Boolean =
        withContext(Dispatchers.IO) {
            overviewMutex.withLock {
                ensureLoaded()
                val book = ReaderLibrary.find(_books.value, id) ?: return@withLock false
                val metadata = book.metadata ?: return@withLock false
                if (BookIntelOverviewPrompt.isUsableFor(book.overviewFor(language), language)) {
                    return@withLock false
                }
                val overview = try {
                    overviewGenerator.generate(apiKey, metadata, language)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The generator is total, so this is belt-and-braces only.
                    VoxoraLog.w(TAG, "Overview generation failed: ${e.javaClass.simpleName}")
                    null
                } ?: return@withLock false
                mutate(id) { ReaderLibrary.withOverview(it, id, overview) }
                true
            }
        }

    /** Applies a pure library transform and persists the result, if anything changed. */
    private suspend fun mutate(
        id: String,
        active: String? = null,
        transform: (List<ReaderBook>) -> List<ReaderBook>,
    ) {
        updateLibrary(active ?: _activeBookId.value) { transform(it) }
    }

    /**
     * The single read-modify-write path for the library.
     *
     * [ensureLoaded] is called **before** the lock is taken because it takes the same lock itself;
     * every mutation then happens inside it, so two concurrent saves cannot read the same list and
     * write back over each other. Nothing outside this function writes [_books].
     */
    private suspend fun updateLibrary(
        active: String?,
        transform: (List<ReaderBook>) -> List<ReaderBook>,
    ) {
        ensureLoaded()
        mutex.withLock {
            val current = _books.value
            val updated = transform(current)
            val resolvedActive = active?.takeIf { id -> updated.any { it.id == id } }
            if (updated == current && resolvedActive == _activeBookId.value) return@withLock
            write(updated, resolvedActive)
        }
    }

    /** Writes the library and the active id together, so the two can never disagree. */
    private suspend fun write(books: List<ReaderBook>, active: String?) {
        val previousBooks = _books.value
        val previousActive = _activeBookId.value
        _books.value = books
        _activeBookId.value = active
        try {
            context.readerLibraryStore.edit {
                it[keyBooks] = ReaderBookCodec.encode(books)
                if (active == null) it.remove(keyActive) else it[keyActive] = active
            }
        } catch (e: CancellationException) {
            _books.value = previousBooks
            _activeBookId.value = previousActive
            throw e
        } catch (e: Exception) {
            // The in-memory library stays authoritative for this session; the next successful write
            // persists it. Losing a write must not lose the user's session.
            VoxoraLog.w(TAG, "Library write failed: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "ReaderLibrary"
    }
}
