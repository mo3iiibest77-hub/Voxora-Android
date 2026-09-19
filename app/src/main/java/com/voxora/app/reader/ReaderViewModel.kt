package com.voxora.app.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxora.app.R
import com.voxora.app.reader.library.ReaderBookRepository
import com.voxora.app.util.VoxoraLog
import com.voxora.core.gemini.ReaderLanguages
import com.voxora.core.gemini.ReaderNarrationModes
import com.voxora.core.gemini.ReaderVoice
import com.voxora.core.prefs.UserPrefs
import com.voxora.core.reader.BookIntelOverviewPlan
import com.voxora.core.reader.MetadataLookupState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: ReaderController,
    private val library: ReaderBookRepository,
) : ViewModel() {
    private val prefs = UserPrefs(context)
    internal val state = controller.state
    internal val narrationText = controller.narrationText
    private val mutableMode = MutableStateFlow("faithful")
    val mode = mutableMode.asStateFlow()
    private val mutableOutputLang = MutableStateFlow(ReaderLanguages.DEFAULT)
    val outputLang = mutableOutputLang.asStateFlow()
    private val mutableVoice = MutableStateFlow(ReaderVoice.DEFAULT)
    val voice = mutableVoice.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private val mutableSettingsError = MutableStateFlow<String?>(null)
    val settingsError = mutableSettingsError.asStateFlow()
    private val mutableLanguageOptions = MutableStateFlow<List<ReaderLanguageOption>>(emptyList())
    val languageOptions = mutableLanguageOptions.asStateFlow()
    private val mutableLanguageLabel = MutableStateFlow("")
    val languageLabel = mutableLanguageLabel.asStateFlow()
    private val mutableLanguageFlag = MutableStateFlow(ReaderLanguages.language(ReaderLanguages.DEFAULT).flagEmoji)
    val languageFlag = mutableLanguageFlag.asStateFlow()
    private val mutableReaderBubble = MutableStateFlow(true)
    val readerBubble = mutableReaderBubble.asStateFlow()

    /**
     * The library, straight from the repository.
     *
     * The screen reads the books and the active id from here rather than keeping its own copy, so a
     * change made by the controller (an import, a new position) is visible without the UI having to
     * be told about it twice.
     */
    val books = library.books
    val activeBookId = library.activeBookId
    val libraryReady = library.ready

    private var commandJob: Job? = null
    private var languageJob: Job? = null
    @Volatile private var languageLocale = Locale.ENGLISH

    /**
     * The "start narration when extraction finishes" job a Continue leaves behind.
     *
     * Continue cannot start playback until the restored document has been extracted, and holding the
     * serialized command queue open for that would block Stop — which is exactly the action a reader
     * reaches for when a large document is taking too long. So the wait lives on its own job, and
     * every command cancels it: asking for anything else supersedes "keep listening".
     */
    private var pendingAutoPlay: Job? = null

    /**
     * Books whose automatic identification has already been started.
     *
     * The library is observed rather than polled: a book is created in the `NONE` state, and the
     * first time it is seen there the lookup is launched and the id recorded, so the search runs
     * exactly once per book. A lookup that ends in `UNAVAILABLE` moves the book out of `NONE`, which
     * is what stops an offline reader from retrying in a loop; retrying is then an explicit choice.
     */
    private val metadataStarted = mutableSetOf<String>()

    /**
     * Book/language pairs whose overview generation has already been started.
     *
     * Keyed by language as well as book, so choosing a new output language is a new request while a
     * failed one is not repeated for the same language in the same session. A generation that fails
     * (offline, no key, a rejected model) therefore costs one attempt, not one per recomposition —
     * and the cached source-backed facts stay on screen throughout.
     */
    private val overviewStarted = mutableSetOf<String>()

    init {
        runCommand {
            try {
                prefs.migrateReaderLanguage()
                mutableMode.value = ReaderNarrationModes.normalize(prefs.readerMode.first())
                mutableOutputLang.value = prefs.readerOutputLang.first()
                mutableVoice.value = prefs.readerVoice.first()
                mutableReaderBubble.value = prefs.readerBubble.first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VoxoraLog.w("ReaderVM", "Reader settings load failed: ${e.javaClass.simpleName}")
                mutableSettingsError.value = context.getString(R.string.reader_settings_load_failed)
            } finally {
                mutableReady.value = true
            }
            // Keep the display language and style in step with the persisted selection before
            // the document is restored, so the reading text is never rendered from a stale one.
            controller.setOutputLanguage(mutableOutputLang.value)
            controller.setNarrationMode(mutableMode.value)
            controller.restoreLastDocument()
        }
        observeLibraryForLookups()
        observeLibraryForOverviews()
    }

    /**
     * Starts the automatic book identification for every book that has not had one.
     *
     * Runs beside playback and never gates it: a book is readable the moment it is imported, whether
     * or not a catalogue has answered yet. Nothing here blocks the Main thread — the lookup itself
     * is a suspend call the repository runs on IO.
     */
    private fun observeLibraryForLookups() {
        viewModelScope.launch {
            library.books.collect { books ->
                for (book in books) {
                    if (book.lookup != MetadataLookupState.NONE) continue
                    if (!metadataStarted.add(book.id)) continue
                    launch(Dispatchers.IO) { runLookup(book.id) }
                }
            }
        }
    }

    private suspend fun runLookup(id: String) {
        try {
            library.refreshMetadata(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The repository is total, so this is belt-and-braces only. A failed lookup is never
            // surfaced as an error: the book is already imported and readable.
            VoxoraLog.w("ReaderVM", "Book identification failed: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Keeps the Book Intelligence overview in step with the chosen output language.
     *
     * A book that needs an overview for the current language gets one generated — once. That
     * includes a book **no catalogue could identify**: for one of those the repository runs a bounded
     * web search first and generates from its findings, which is what makes Book Intelligence exist
     * for a title the public catalogues do not carry. This is what makes "explanatory content
     * follows the Reader output language" true even though no public catalogue carries a Persian
     * description: the source text is translated and condensed, and clearly labelled as generated.
     *
     * Whether an overview is needed is decided by [BookIntelOverviewPlan], the **same** pure
     * decision the repository makes. Testing only "a catalogue record exists" here — which is what
     * this used to do — meant the search fallback was never reached, because the UI skipped exactly
     * the books it exists for. Testing only "an entry exists" would be the opposite fault: a record
     * cached by an older prompt would be treated as current, so a prompt improvement would be
     * silently masked by an answer that was already paid for. Both layers have to agree on what
     * "cached" means.
     *
     * Runs entirely beside playback. Nothing here gates narration, and a book with no catalogue
     * match, no search finding, no API key or no network simply never gets an overview.
     */
    private fun observeLibraryForOverviews() {
        viewModelScope.launch {
            combine(library.books, outputLang) { books, language -> books to language }
                .collect { (books, language) ->
                    for (book in books) {
                        if (BookIntelOverviewPlan.need(book, language) == BookIntelOverviewPlan.Need.None) continue
                        if (!overviewStarted.add("${book.id}|$language")) continue
                        launch(Dispatchers.IO) { runOverview(book.id, language) }
                    }
                }
        }
    }

    private suspend fun runOverview(id: String, language: String) {
        try {
            val key = prefs.apiKey.first()
            library.ensureOverview(id, language, key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VoxoraLog.w("ReaderVM", "Book overview failed: ${e.javaClass.simpleName}")
        }
    }

    fun searchLanguages(query: String, locale: Locale) {
        languageLocale = locale
        languageJob?.cancel()
        languageJob = viewModelScope.launch(Dispatchers.Default) {
            val options = languageOptions(locale, query)
            val selected = ReaderLanguages.language(outputLang.value)
            ensureActive()
            mutableLanguageOptions.value = options
            mutableLanguageLabel.value = selected.displayName(locale)
            mutableLanguageFlag.value = selected.flagEmoji
        }
    }

    fun setMode(value: String) = runCommand {
        if (!canConfigure() || value == mode.value || !ReaderNarrationModes.isValid(value)) return@runCommand
        prefs.setReaderSettings("", value)
        mutableMode.value = value
        // The controller owns the reading text, so it has to learn about the change too. It
        // keeps one rendering cache per narration mode, which is what stops text produced for
        // the previous style from being shown after the switch.
        controller.setNarrationMode(value)
    }

    fun setOutputLang(language: String) = runCommand {
        if (!canConfigure() || !ReaderLanguages.isValid(language) || language == outputLang.value) return@runCommand
        prefs.setReaderOutputLang(language)
        mutableOutputLang.value = language
        val selected = ReaderLanguages.language(language)
        mutableLanguageLabel.value = selected.displayName(languageLocale)
        mutableLanguageFlag.value = selected.flagEmoji
        // The controller owns the reading text, so it has to learn about the change too. Its
        // cache is keyed by language within a mode, which is what stops text rendered for the
        // previous language (or the other narration style) from being shown after the switch.
        controller.setOutputLanguage(language)
    }

    /**
     * Selects the narrator voice.
     *
     * A global Reader preference, like the narration language: it is stored once and read by the
     * controller when a run starts, so every new Gemini session — including one opened after a
     * pause — uses it. Nothing has to be pushed into a session, and no session is left holding a
     * stale voice.
     */
    fun setVoice(id: String) = runCommand {
        if (!canConfigure() || !ReaderVoice.isValid(id)) return@runCommand
        val selected = ReaderVoice.normalize(id)
        if (selected == voice.value) return@runCommand
        prefs.setReaderVoice(selected)
        mutableVoice.value = selected
    }

    /**
     * Chunk and segment navigation publish **synchronously** on the caller's thread.
     *
     * The page animation depends on that. `ReaderController.jumpToChunk` / `jumpToSegment`
     * mutate the queue position and publish the new [ReaderState] inside one `synchronized`
     * block, so by the time these return the arriving unit is already the current one and the
     * page can compose it at zero presence. Routing them through the IO command queue made
     * the publish asynchronous, which let the outgoing unit flash back at full opacity before
     * the new one arrived — the page-turn flicker.
     *
     * The navigation path is pure state plus a job cancellation, so Main is safe for it. The
     * genuinely slow work (extraction, narration) still runs on IO.
     */
    fun jumpToChunk(index: Int): Boolean = controller.jumpToChunk(index)

    fun jumpToSegment(index: Int): Boolean = controller.jumpToSegment(index)

    /**
     * Turns the floating bubble on or off.
     *
     * This is a preference rather than a gate, so it is writable at any time and the running
     * service reacts to it immediately. A stop from the bubble itself clears the same preference,
     * which is why the ViewModel reads it back rather than keeping a second copy.
     */
    fun setReaderBubble(enabled: Boolean) = runCommand {
        if (enabled == readerBubble.value) return@runCommand
        prefs.setReaderBubble(enabled)
        mutableReaderBubble.value = enabled
    }

    fun load(uri: Uri) = runCommand { controller.load(uri) }

    /**
     * Continue: opens a library book at its exact saved chunk **and starts narration**.
     *
     * The reader's action means "keep listening", not "load this and then press Play". The book is
     * opened through the controller's ordinary path, which re-extracts Voxora's own copy and jumps
     * to the persisted chunk, and then playback is started once the transport can actually run.
     * Nothing is regenerated on the way: a chunk whose text and audio are already in the persisted
     * cache is replayed from disk, and only a chunk that was never produced asks Gemini for
     * anything.
     *
     * Extraction runs beside this command, so the book is not playable the instant `openBook`
     * returns. Rather than block the command queue for the length of an extraction, the wait for
     * extraction to finish runs on its own job ([pendingAutoPlay]) which any later command cancels
     * — so Stop, Pause or another book still take effect immediately. Playback starts only when the
     * restored queue can actually be played; an empty or failed restore simply does not start.
     */
    fun continueBook(id: String) = runCommand {
        if (!ReaderGates.canPickDocument(ready.value)) return@runCommand
        controller.openBook(id)
        pendingAutoPlay = viewModelScope.launch(Dispatchers.IO) {
            val resumed = controller.state.first { !ReaderGates.isExtracting(it.phase) }
            // The job may have been cancelled while the state was being read; this turns that into
            // the same outcome as being cancelled before it.
            ensureActive()
            if (!ReaderGates.canPlay(ready.value, resumed.phase, resumed.total > 0)) return@launch
            startPlayback()
        }
    }

    /** Removes a book and its stored document. */
    fun removeBook(id: String) = runCommand { library.remove(id) }

    /** Re-runs identification for a book whose earlier lookup failed or found nothing. */
    fun retryBookInfo(id: String) = runCommand {
        // Recorded as started so the automatic pass cannot fire a second lookup for it later.
        metadataStarted.add(id)
        runLookup(id)
    }

    fun play() = runCommand {
        // Never start narration before the queue is ready; see ReaderGates.canPlay.
        if (!ReaderGates.canPlay(ready.value, state.value.phase, state.value.total > 0)) return@runCommand
        startPlayback()
    }

    /** Asks the service to start (or resume) narration in the current style. */
    private fun startPlayback() {
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ReaderService::class.java)
                    .setAction(ReaderService.ACTION_PLAY)
                    .putExtra(ReaderService.EXTRA_MODE, mode.value),
            )
            mutableSettingsError.value = null
        } catch (e: Exception) {
            VoxoraLog.w("ReaderVM", "Could not start Reader service: ${e.javaClass.simpleName}")
            mutableSettingsError.value = context.getString(R.string.reader_service_start_failed)
        }
    }

    fun pause() = runCommand { controller.pause() }

    fun stop() = runCommand { controller.stop() }

    /**
     * Mode, language and voice stay editable while a document is being extracted. They are
     * only preferences and never touch extraction, so freezing them was what made a
     * restored PDF feel locked. Playback keeps its own, stricter gate.
     */
    private fun canConfigure(): Boolean = ReaderGates.canConfigure(ready.value, state.value.phase)

    private fun runCommand(command: suspend () -> Unit) {
        // Every command supersedes a pending "play when extraction finishes". Without this, a Stop
        // tapped during a slow restore would be followed by narration starting anyway.
        pendingAutoPlay?.cancel()
        pendingAutoPlay = null
        val previous = commandJob
        commandJob = viewModelScope.launch(Dispatchers.IO) {
            previous?.join()
            try {
                command()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VoxoraLog.w("ReaderVM", "Reader command failed: ${e.javaClass.simpleName}")
                mutableSettingsError.value = context.getString(R.string.reader_settings_save_failed)
            }
        }
    }
}
