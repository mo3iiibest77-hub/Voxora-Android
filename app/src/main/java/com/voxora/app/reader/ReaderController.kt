package com.voxora.app.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.voxora.app.R
import com.voxora.app.util.VoxoraLog
import com.voxora.core.gemini.GeminiReaderSession
import com.voxora.core.gemini.ReaderLanguages
import com.voxora.core.gemini.ReaderNarrationModes
import com.voxora.core.gemini.ReaderSessionStatus
import com.voxora.core.prefs.UserPrefs
import com.voxora.core.prefs.UsagePrefs
import com.voxora.core.usage.UsageFailureCategory
import com.voxora.core.usage.UsageRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class ReaderController @Inject constructor(@ApplicationContext private val context: Context) {
    private val documentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val playbackMutex = Mutex()
    private val persistenceMutex = Mutex()
    private val prefs = UserPrefs(context)
    /**
     * Records what Voxora observed about its own Gemini requests, for the Settings usage
     * dashboard. Purely observational: nothing in the narration path reads it back, so it can
     * never influence audio, ordering or timing.
     */
    private val usageRecorder = UsageRecorder(
        store = UsagePrefs(context),
        onStoreFailure = { reason -> VoxoraLog.w("Reader", "Usage record dropped: $reason") },
    )
    private val extractor = TextExtractor(context)
    private val mutableState = MutableStateFlow(ReaderState())
    internal val state = mutableState.asStateFlow()
    private val mutableNarrationText = MutableStateFlow("")
    internal val narrationText = mutableNarrationText.asStateFlow()
    private var queue = ChunkQueue(emptyList())
    private var documentName = ""
    private var segmentIndex = 0
    /**
     * Selected-language reading text, keyed by language. The canonical extracted
     * document stays in [queue]; this only holds the selected-language rendering of
     * individual units, derived from the Gemini transcript for that unit.
     */
    private val displayText = ReaderDisplayText()
    /** Language the display text and the narration instruction are rendered in. */
    private var outputLanguage = ReaderLanguages.DEFAULT
    private var generation = 0L
    private val navigationRevision = MutableStateFlow(0L)
    private val navigation: Long get() = navigationRevision.value
    private var activeJob: Job? = null
    private var pipelineJob: Job? = null
    private var loadJob: Job? = null
    private var cleanedOrphans = false
    private var audibleProgress: (() -> Unit)? = null

    private data class Position(val chunk: Int, val segment: Int, val revision: Long)
    private data class Slot(
        val index: Int,
        val units: List<String>,
        val spool: ReaderSpool,
        var producer: Job? = null,
        val attempts: Int,
    )
    private data class AudibleUnit(val index: Int, val startFrame: Long, var transcript: String = "")
    private class NarrationFailure(val resource: Int) : Exception()
    private class EmptyPrefetchFailure : Exception()

    fun load(uri: Uri) = startLoad(uri, restoring = false)

    suspend fun restoreLastDocument() {
        if (synchronized(lock) { state.value.phase != ReaderPhase.IDLE || queue.size != 0 }) return
        try {
            val saved = prefs.lastDocUri.first()
            if (saved.isNotBlank()) startLoad(Uri.parse(saved), restoring = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VoxoraLog.w("Reader", "Document restoration failed: ${e.javaClass.simpleName}")
        }
    }

    private fun startLoad(uri: Uri, restoring: Boolean) = synchronized(lock) {
        if (restoring && (state.value.phase != ReaderPhase.IDLE || queue.size != 0)) return@synchronized
        cancelOwned()
        val run = generation
        val previous = loadJob
        queue = ChunkQueue(emptyList())
        // Chunk indices now refer to a different document, so no rendering from the
        // previous one may survive.
        displayText.clear()
        documentName = ""
        segmentIndex = 0
        publish(ReaderPhase.EXTRACTING)
        loadJob = documentScope.launch(start = CoroutineStart.LAZY) {
            previous?.cancelAndJoin()
            try {
                if (!restoring) {
                    saveDocument(run, null)
                    try {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: SecurityException) {
                        VoxoraLog.w("Reader", "Document provider did not grant persistent access")
                    }
                }
                val document = extractor.extract(uri)
                currentCoroutineContext().ensureActive()
                saveDocument(run, uri.toString())
                synchronized(lock) {
                    if (run == generation) {
                        queue = ChunkQueue(document.chunks)
                        documentName = document.name
                        segmentIndex = 0
                        loadJob = null
                        publish(ReaderPhase.READY)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VoxoraLog.w("Reader", "Document extraction failed: ${e.javaClass.simpleName}")
                if (restoring) saveDocument(run, null)
                synchronized(lock) {
                    if (run == generation) {
                        loadJob = null
                        publish(if (restoring) ReaderPhase.IDLE else ReaderPhase.ERROR)
                        if (!restoring) mutableState.value = state.value.copy(error = context.getString(R.string.reader_document_failed))
                    }
                }
            }
        }.also { it.start() }
    }

    private suspend fun saveDocument(run: Long, uri: String?) = persistenceMutex.withLock {
        if (synchronized(lock) { run != generation }) return@withLock
        try {
            prefs.updateReaderDocument(uri) { synchronized(lock) { run == generation } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VoxoraLog.w("Reader", "Document preference update failed: ${e.javaClass.simpleName}")
        }
    }

    suspend fun play(mode: String) = withContext(Dispatchers.IO) {
        val requested = synchronized(lock) {
            if (activeJob?.isActive == true) return@withContext
            generation
        }
        playbackMutex.withLock {
            coroutineScope {
                val owner = currentCoroutineContext()[Job]!!
                val run = synchronized(lock) {
                    if (requested != generation || queue.size == 0 || state.value.phase == ReaderPhase.EXTRACTING) return@coroutineScope
                    if (state.value.phase == ReaderPhase.COMPLETE) {
                        queue.reset()
                        segmentIndex = 0
                    } else if (state.value.phase in setOf(ReaderPhase.IDLE, ReaderPhase.EXTRACTING)) return@coroutineScope
                    generation++
                    activeJob = owner
                    publish(ReaderPhase.CONNECTING)
                    generation
                }
                try {
                    if (!cleanedOrphans) {
                        ReaderSpool.removeOrphans(context.cacheDir)
                        cleanedOrphans = true
                    }
                    if (!ReaderNarrationModes.isValid(mode)) throw NarrationFailure(R.string.reader_mode_missing)
                    val key = prefs.apiKey.first().trim()
                    if (key.isEmpty()) throw NarrationFailure(R.string.error_no_api_key)
                    val language = prefs.readerOutputLang.first()
                    // The instruction and the displayed reading text must always agree on
                    // one language for the whole run, so the run's language is recorded
                    // here and every unit is stored under exactly this key.
                    synchronized(lock) { outputLanguage = language }
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val position = synchronized(lock) {
                            checkOwned(run)
                            Position(queue.index, segmentIndex, navigation)
                        }
                        val child = launch(start = CoroutineStart.LAZY) {
                            try {
                                runPipeline(run, position, key, mode, language)
                            } catch (e: TimeoutCancellationException) {
                                fail(run, position.revision, NarrationFailure(R.string.reader_audio_unavailable))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                fail(run, position.revision, e)
                            }
                        }
                        synchronized(lock) {
                            checkOwned(run)
                            pipelineJob = child
                            if (position.revision != navigation) child.cancel() else child.start()
                        }
                        child.join()
                        currentCoroutineContext().ensureActive()
                        val again = synchronized(lock) {
                            checkOwned(run)
                            pipelineJob = null
                            if (position.revision == navigation) {
                                activeJob = null
                                if (state.value.phase in activePhases) publish(ReaderPhase.PAUSED)
                                false
                            } else true
                        }
                        if (!again) break
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    synchronized(lock) { fail(run, navigation, e) }
                } finally {
                    withContext(NonCancellable) {
                        synchronized(lock) {
                            if (activeJob === owner) {
                                activeJob = null
                                pipelineJob = null
                                if (run == generation && state.value.phase in activePhases) publish(ReaderPhase.PAUSED)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun runPipeline(run: Long, position: Position, key: String, mode: String, language: String) = coroutineScope {
        val slots = mutableListOf<Slot>()
        val output = ReaderPlayback(context) {
            synchronized(lock) {
                if (run == generation && position.revision == navigation) pause()
            }
        }
        fun prepare(index: Int, firstUnit: Int, attempts: Int): Slot {
            val units = synchronized(lock) {
                checkOwned(run, position.revision)
                queue.segments(index)
            }
            val slot = Slot(index, units, ReaderSpool(context.cacheDir, firstUnit), attempts = attempts)
            slots.add(slot)
            slot.producer = launch { produce(slot, key, mode, language, run, position.revision) }
            return slot
        }
        fun prefetch(index: Int): Slot? {
            if (index >= synchronized(lock) { queue.size }) return null
            return try {
                prepare(index, 0, 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VoxoraLog.w("Reader", "Prefetch allocation failed chunk=${index + 1}: ${e.javaClass.simpleName}")
                null
            }
        }
        try {
            var current = prepare(position.chunk, position.segment, MAX_UNIT_ATTEMPTS)
            var next = prefetch(position.chunk + 1)
            // The run's first unit must already have its selected-language text before a single
            // frame is audible, so the wait happens here — before the output track is started, so
            // the app does not sit holding audio focus in silence while that unit is synthesised.
            awaitInitialRendering(current, position.segment, language, run, position.revision)
            try {
                output.start()
            } catch (e: Exception) {
                throw NarrationFailure(R.string.reader_audio_unavailable)
            }
            while (true) {
                currentCoroutineContext().ensureActive()
                try {
                    consume(current, output, run, position.revision)
                } catch (e: EmptyPrefetchFailure) {
                    current.producer?.join()
                    current.spool.close()
                    slots.remove(current)
                    VoxoraLog.w("Reader", "Retrying empty promoted chunk=${current.index + 1} with a fresh spool")
                    current = prepare(current.index, 0, MAX_UNIT_ATTEMPTS - 1)
                    continue
                }
                current.producer?.join()
                current.spool.close()
                slots.remove(current)
                synchronized(lock) {
                    checkOwned(run, position.revision)
                    if (current.index + 1 >= queue.size) {
                        publish(ReaderPhase.COMPLETE)
                    } else {
                        queue.jumpTo(current.index + 1)
                        segmentIndex = 0
                        publish(ReaderPhase.NEXT)
                    }
                }
                val promoted = next
                if (promoted == null) {
                    if (current.index + 1 < synchronized(lock) { queue.size }) {
                        throw NarrationFailure(R.string.reader_retry_unit)
                    }
                    break
                }
                current = promoted
                next = prefetch(current.index + 1)
            }
        } finally {
            withContext(NonCancellable) {
                output.stop()
                slots.forEach { it.producer?.cancel() }
                slots.forEach { it.producer?.join() }
                slots.forEach { it.spool.close() }
                // A run boundary is the natural point to force the batched usage record to disk,
                // so a finished narration is fully accounted for even if the process dies later.
                usageRecorder.flush()
            }
        }
    }

    /**
     * Waits until the unit a run starts on has a selected-language rendering.
     *
     * This is the only place the narration path waits for text, and it waits for exactly one
     * unit. Every later unit keeps streaming as the producer writes it, so the Reader never
     * blocks on the whole document and nothing large is translated up front.
     *
     * The reading text *is* the Gemini transcript, so this wait is what buys the ordering the
     * product needs: the producer finishes this unit and records its rendering, and only then
     * does playback start — from the audio the spool buffered while the unit was produced.
     *
     * Every read happens under [lock]. The producer ends the unit and records the rendering in
     * one critical section, but it publishes the spool snapshot *before* recording, so observing
     * the snapshot alone would not make the rendering visible; taking the lock is what
     * establishes the happens-before edge, and it also keeps the plain map inside
     * [ReaderDisplayText] from being read while it is written.
     *
     * Failure is terminal rather than something to wait out, and it does not drain partial audio:
     * a unit whose transcript never arrived has no selected-language text, so playing it would be
     * precisely the "audio first, text later" behaviour this gate exists to prevent.
     */
    private suspend fun awaitInitialRendering(
        slot: Slot,
        unit: Int,
        language: String,
        run: Long,
        revision: Long,
    ) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val rendering: String?
            val failed: Boolean
            synchronized(lock) {
                checkOwned(run, revision)
                val snapshot = slot.spool.state.value
                rendering = displayText.text(language, slot.index, unit)
                failed = snapshot.ends.any { it.index == unit } ||
                    snapshot.failure?.unit == unit ||
                    snapshot.complete ||
                    slot.producer?.isCompleted == true
            }
            when (ReaderInitialPlayback.gate(rendering, failed)) {
                ReaderInitialPlayback.Gate.READY -> return
                ReaderInitialPlayback.Gate.FAILED -> throw NarrationFailure(R.string.reader_retry_unit)
                ReaderInitialPlayback.Gate.AWAIT -> delay(10)
            }
        }
    }

    private suspend fun produce(slot: Slot, key: String, mode: String, language: String, run: Long, revision: Long) {
        val producerContext = currentCoroutineContext()
        var session: GeminiReaderSession? = null
        var sessionStarted = 0L
        var unit = slot.spool.firstUnit
        try {
            while (unit < slot.units.size) {
                val before = slot.spool.state.value.committed
                var attempts = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    try {
                        if (session != null && (!session.reusable || System.nanoTime() - sessionStarted >= SESSION_GROUP_NANOS)) {
                            session.closeAndJoin()
                            session = null
                        }
                        val connection = session ?: GeminiReaderSession().also {
                            sessionStarted = System.nanoTime()
                            session = it
                            it.onLog = { line -> VoxoraLog.d("ReaderSession", line) }
                            // The server only *may* report token usage, so this fires only when
                            // it actually did. Nothing is synthesised when it stays silent.
                            it.onUsage = { reported -> usageRecorder.noteReportedUsage(reported) }
                            it.connect(key, instructionFor(mode, language), "models/gemini-3.8-live")
                            when (it.status.first { status -> status is ReaderSessionStatus.Ready || status is ReaderSessionStatus.Error }) {
                                ReaderSessionStatus.Ready -> Unit
                                else -> throw NarrationFailure(R.string.reader_connect_timeout)
                            }
                        }
                        val transcript = connection.narrate(slot.units[unit]) { pcm ->
                            synchronized(lock) {
                                producerContext.ensureActive()
                                checkOwned(run, revision)
                                slot.spool.append(pcm)
                            }
                        } ?: throw NarrationFailure(R.string.reader_no_audio)
                        // One completed request. Recorded outside the lock because the recorder
                        // suspends; it never participates in the narration ordering itself.
                        usageRecorder.recordSuccess()
                        synchronized(lock) {
                            producerContext.ensureActive()
                            checkOwned(run, revision)
                            slot.spool.endUnit(unit, transcript)
                            // Gemini narrated this unit in `language`, so the transcript is
                            // that unit's selected-language reading text.
                            displayText.record(language, slot.index, unit, transcript)
                            // The producer renders whole chunks ahead of playback, so a
                            // recording only becomes visible state when it belongs to the
                            // chunk on screen and to the language being displayed. Without
                            // this the UI would keep showing the extracted source until some
                            // unrelated event republished, which is what made the reading
                            // text lag the narration by a chunk.
                            if (displayText.shouldRepublish(language, slot.index, outputLanguage, queue.index)) {
                                publish(state.value.phase)
                            }
                        }
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        session?.closeAndJoin()
                        session = null
                        currentCoroutineContext().ensureActive()
                        attempts++
                        // Only the classified category is stored — never the exception message,
                        // which an HTTP client could populate with the key-bearing request URL.
                        usageRecorder.recordFailure(usageCategoryFor(e))
                        VoxoraLog.w("Reader", "Unit production failed chunk=${slot.index + 1} unit=${unit + 1} attempt=$attempts")
                        if (slot.spool.state.value.committed > before || attempts >= slot.attempts || e is ReaderSpool.CapacityException) {
                            slot.spool.fail(unit, e)
                            return
                        }
                        delay(500L * attempts)
                    }
                }
                unit++
            }
            slot.spool.finish()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            session?.closeAndJoin()
            session = null
            slot.spool.fail(unit, e)
        } finally {
            session?.closeAndJoin()
        }
    }

    private suspend fun consume(
        slot: Slot,
        output: ReaderPlayback,
        run: Long,
        revision: Long,
    ) {
        if (slot.attempts == 1 && slot.spool.state.value.canRetryPrefetch(0L)) throw EmptyPrefetchFailure()
        var cursor = 0L
        val audible = ArrayDeque<AudibleUnit>()
        fun progress() = synchronized(lock) {
            checkOwned(run, revision)
            val head = output.playedFrames()
            while (audible.size > 1 && head > audible.elementAt(1).startFrame) audible.removeFirst()
            val current = audible.firstOrNull() ?: return@synchronized
            if (head <= current.startFrame) return@synchronized
            if (queue.index == slot.index) {
                segmentIndex = current.index
                publish(ReaderPhase.SPEAKING)
                mutableNarrationText.value = current.transcript
            }
        }
        val reportProgress: () -> Unit = ::progress
        synchronized(lock) {
            checkOwned(run, revision)
            audibleProgress = reportProgress
        }
        try {
            for (unit in slot.spool.firstUnit until slot.units.size) {
                val marker = AudibleUnit(unit, output.writtenFrames)
                synchronized(lock) {
                    // A unit the producer has already finished carries its transcript with it.
                    // Seeding it here means the first audible progress report is published with
                    // the real narration line instead of an empty one — which is exactly the case
                    // for the gated first unit, whose transcript is known before playback starts.
                    slot.spool.state.value.ends.firstOrNull { it.index == unit }
                        ?.let { marker.transcript = it.transcript }
                    audible.addLast(marker)
                }
                while (true) {
                    currentCoroutineContext().ensureActive()
                    progress()
                    val snapshot = slot.spool.state.value
                    if (slot.attempts == 1 && snapshot.canRetryPrefetch(cursor)) throw EmptyPrefetchFailure()
                    val end = snapshot.ends.firstOrNull { it.index == unit }
                    val limit = end?.bytes ?: snapshot.committed
                    if (cursor < limit) {
                        val bytes = slot.spool.read(cursor, limit)
                        output.writePcm(bytes, ::progress)
                        cursor += bytes.size
                        continue
                    }
                    if (end != null) {
                        synchronized(lock) { marker.transcript = end.transcript }
                        progress()
                        break
                    }
                    val failure = snapshot.failure
                    if (failure != null && failure.unit == unit) {
                        output.drain(::progress)
                        synchronized(lock) {
                            checkOwned(run, revision)
                            segmentIndex = unit
                            publish(ReaderPhase.NEXT)
                        }
                        throw NarrationFailure(
                            if (failure.partial) R.string.reader_partial_audio else R.string.reader_retry_unit,
                        )
                    }
                    if (snapshot.complete || (slot.producer?.isCompleted == true && slot.spool.state.value == snapshot)) {
                        output.drain(::progress)
                        throw NarrationFailure(R.string.reader_retry_unit)
                    }
                    delay(10)
                }
            }
            output.drain(::progress)
            progress()
        } catch (e: CancellationException) {
            synchronized(lock) {
                if (run == generation && revision == navigation) captureAudibleProgress()
            }
            throw e
        } finally {
            synchronized(lock) {
                if (audibleProgress === reportProgress) audibleProgress = null
            }
        }
    }

    private fun captureAudibleProgress() {
        try {
            audibleProgress?.invoke()
        } catch (e: Exception) {
            VoxoraLog.w("Reader", "Audible progress unavailable: ${e.javaClass.simpleName}")
        }
    }

    fun pause() = synchronized(lock) {
        if (state.value.phase in activePhases) {
            captureAudibleProgress()
            cancelOwned()
            publish(ReaderPhase.PAUSED)
        }
    }

    fun stop() = synchronized(lock) {
        cancelOwned()
        queue.reset()
        segmentIndex = 0
        publish(if (queue.size == 0) ReaderPhase.IDLE else ReaderPhase.STOPPED)
    }

    /**
     * Whole-chunk navigation: the explicit Previous/Next controls.
     *
     * @return true when the position actually moved. The page animation needs to know: a
     * refused move must return the card to rest rather than leave it faded out, and the
     * controller is the only layer that can say whether the move happened.
     */
    fun jumpToChunk(index: Int): Boolean = synchronized(lock) {
        if (queue.size == 0 || state.value.phase == ReaderPhase.EXTRACTING) return@synchronized false
        val target = index.coerceIn(0, maxOf(0, queue.size - 1))
        if (target == queue.index) return@synchronized false
        queue.jumpTo(target)
        segmentIndex = 0
        navigate()
        true
    }

    /**
     * Segment navigation: the horizontal swipe and the segment arrows.
     *
     * Bounded by the **current chunk's** units, so it can never move the document to another
     * chunk — that is [jumpToChunk]'s job. An out-of-range index is clamped rather than
     * rejected, so a stale gesture can never produce an invalid segment index.
     *
     * @return true when the position actually moved; see [jumpToChunk].
     */
    fun jumpToSegment(index: Int): Boolean = synchronized(lock) {
        val segments = queue.segments(queue.index)
        if (segments.isEmpty() || state.value.phase == ReaderPhase.EXTRACTING) return@synchronized false
        val target = index.coerceIn(0, segments.lastIndex)
        if (target == segmentIndex) return@synchronized false
        segmentIndex = target
        navigate()
        true
    }

    private fun navigate() {
        navigationRevision.value++
        pipelineJob?.cancel()
        val nextPhase = when {
            activeJob?.isActive == true -> ReaderPhase.CONNECTING
            state.value.phase == ReaderPhase.COMPLETE -> ReaderPhase.READY
            else -> ReaderPhase.PAUSED
        }
        publish(nextPhase)
    }

    private fun cancelOwned() {
        generation++
        activeJob?.cancel()
        pipelineJob?.cancel()
        loadJob?.cancel()
    }

    private fun checkOwned(run: Long, revision: Long = navigation) {
        if (run != generation || revision != navigation) throw CancellationException()
    }

    /**
     * Selects the language of the displayed reading text.
     *
     * Display text is cached per language, so switching languages can never surface a
     * rendering produced for another one; units not yet narrated in the new language
     * fall back to the extracted source until narration renders them. The canonical
     * extracted document in [queue] is untouched.
     */
    fun setOutputLanguage(language: String) = synchronized(lock) {
        val normalized = ReaderLanguages.normalize(language)
        if (normalized == outputLanguage) return@synchronized
        outputLanguage = normalized
        publish(state.value.phase)
    }

    private fun publish(phase: ReaderPhase) {
        val units = queue.segments(queue.index)
        // Reading text follows the selected language, and it follows it *from the moment
        // the chunk becomes the current one*: the producer renders whole chunks ahead of
        // playback, so a chunk that is about to be narrated already has its renderings
        // cached and this resolves them immediately. Nothing here waits for audio, and
        // nothing here can delay audio — the spool and the consumer never read this
        // state. A unit with no rendering yet falls back to the extracted source and is
        // reported in `pendingSegments` so the UI can present it as still being prepared
        // rather than as settled text.
        val displayed = displayText.readingText(outputLanguage, queue.index, units)
        mutableState.value = ReaderState(
            phase = phase,
            chunk = if (queue.size == 0) 0 else queue.index + 1,
            total = queue.size,
            segment = if (units.isEmpty()) 0 else segmentIndex + 1,
            segmentTotal = units.size,
            text = queue.current.orEmpty(),
            segments = displayed,
            pendingSegments = displayText.pending(outputLanguage, queue.index, units.size),
            documentName = documentName,
            // Republishing a phase must not swallow the reason it is showing, and any
            // other phase transition means the failure no longer applies.
            error = if (phase == ReaderPhase.ERROR) state.value.error else null,
        )
        if (phase != ReaderPhase.SPEAKING) mutableNarrationText.value = ""
    }

    private fun fail(run: Long, revision: Long, error: Exception) = synchronized(lock) {
        if (run != generation || revision != navigation) return@synchronized
        VoxoraLog.w("Reader", "Narration failed: ${error.javaClass.simpleName}")
        val resource = (error as? NarrationFailure)?.resource ?: R.string.reader_retry_unit
        mutableState.value = state.value.copy(phase = ReaderPhase.ERROR, error = context.getString(resource))
    }

    private fun instructionFor(mode: String, outputLang: String): String =
        ReaderNarrationModes.instruction(mode, ReaderLanguages.language(outputLang).englishName)

    /**
     * Maps a narration failure onto a short usage category.
     *
     * [NarrationFailure] carries a string resource rather than a message, so the resource is the
     * honest source here; anything else falls back to classifying the exception. Only the category
     * is stored — never the message, which can contain the key-bearing request URL.
     */
    private fun usageCategoryFor(error: Exception): String = when ((error as? NarrationFailure)?.resource) {
        R.string.reader_no_audio,
        R.string.reader_partial_audio,
        R.string.reader_audio_unavailable,
        -> UsageFailureCategory.AUDIO
        R.string.error_no_api_key,
        R.string.reader_mode_missing,
        -> UsageFailureCategory.CONFIGURATION
        R.string.reader_connect_timeout -> UsageFailureCategory.NETWORK
        else -> UsageFailureCategory.classify(error.javaClass.name, error.message)
    }

    private companion object {
        val activePhases = setOf(ReaderPhase.CONNECTING, ReaderPhase.REWRITING, ReaderPhase.SPEAKING, ReaderPhase.NEXT)
        const val MAX_UNIT_ATTEMPTS = 3
        const val SESSION_GROUP_NANOS = 300_000_000_000L
    }
}
