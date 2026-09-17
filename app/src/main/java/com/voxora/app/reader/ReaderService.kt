package com.voxora.app.reader

import android.content.Context
import android.net.Uri
import com.voxora.app.R
import com.voxora.app.util.VoxoraLog
import com.voxora.core.gemini.GeminiReaderSession
import com.voxora.core.gemini.ReaderSessionStatus
import com.voxora.core.prefs.UserPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal enum class ReaderPhase { IDLE, EXTRACTING, READY, REWRITING, SPEAKING, NEXT, PAUSED, STOPPED, COMPLETE, ERROR }

internal data class ReaderState(
    val phase: ReaderPhase = ReaderPhase.IDLE,
    val chunk: Int = 0,
    val total: Int = 0,
    val text: String = "",
    val error: String? = null,
)

/**
 * Reader pipeline: extract → chunk → per chunk: Gemini rewrites + speaks it
 * (text → AUDIO over BidiGenerateContent) → PCM playback. There is no device TTS
 * and no external rewrite endpoint; the Gemini key from UserPrefs is the only
 * credential, shared with Live Dub.
 */
class ReaderService @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(ReaderState())
    internal val state = mutableState.asStateFlow()
    private val extractor = TextExtractor(context)
    private val prefs = UserPrefs(context)
    private var session: GeminiReaderSession? = null
    private var sessionJob: Job? = null
    private var playback: ReaderPlayback? = null
    private var queue = ChunkQueue(emptyList())
    private var job: Job? = null
    private var generation = 0L
    private val mutableNarrationText = MutableStateFlow("")
    internal val narrationText = mutableNarrationText.asStateFlow()
    private val mutableAudio = MutableSharedFlow<FloatArray>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    internal val audio: SharedFlow<FloatArray> = mutableAudio.asSharedFlow()

    fun load(uri: Uri) {
        cancelPlayback()
        queue = ChunkQueue(emptyList())
        mutableNarrationText.value = ""
        mutableState.value = ReaderState(phase = ReaderPhase.EXTRACTING)
        job = scope.launch {
            try {
                queue = ChunkQueue(extractor.extract(uri))
                publish(ReaderPhase.READY)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.message ?: "Could not read this document.")
            }
        }
    }

    fun play(mode: String) {
        if (job?.isActive == true || queue.size == 0) return
        if (mode !in setOf("simple", "fluent")) {
            fail(context.getString(R.string.reader_mode_missing))
            return
        }
        if (queue.current == null) queue.reset()
        val run = ++generation
        job = scope.launch {
            try {
                val apiKey = prefs.apiKey.first().trim()
                if (apiKey.isEmpty()) {
                    VoxoraLog.w(TAG, "no API key; aborting play")
                    throw NarrationException(context.getString(R.string.error_no_api_key))
                }
                val narration = GeminiReaderSession()
                session = narration
                val collector = launch { collectAudio(narration, run) }
                connectAndAwait(narration, apiKey, instructionFor(mode))
                playback = ReaderPlayback(context)
                try {
                    playback?.start()
                } catch (e: IllegalStateException) {
                    throw NarrationException(context.getString(R.string.reader_audio_unavailable))
                }
                mutableState.value = ReaderState(
                    phase = ReaderPhase.SPEAKING,
                    chunk = minOf(queue.index + 1, queue.size),
                    total = queue.size,
                )
                while (queue.current != null) {
                    if (run != generation) throw NarrationException(STALE_RUN)
                    publish(ReaderPhase.REWRITING)
                    mutableNarrationText.value = queue.current!!.take(NARRATION_PREVIEW_CHARS)
                    val spoken = narration.narrate(queue.current!!)
                    if (spoken == null) {
                        throw NarrationException(context.getString(R.string.reader_no_audio))
                    }
                    if (spoken.isNotBlank()) {
                        mutableNarrationText.value = spoken.take(NARRATION_PREVIEW_CHARS)
                    }
                    publish(ReaderPhase.SPEAKING)
                    awaitPlaybackDrain(run)
                    publish(ReaderPhase.NEXT)
                    queue.advance()
                }
                publish(ReaderPhase.COMPLETE)
                collector.cancel()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.message ?: context.getString(R.string.reader_failed_generic))
            } finally {
                closeSession()
                if (run == generation) {
                    playback?.stop()
                    playback = null
                }
            }
        }
    }

    private suspend fun connectAndAwait(session: GeminiReaderSession, apiKey: String, instruction: String) {
        session.onLog = { line -> VoxoraLog.d(SESSION_LOG_TAG, line) }
        var lastError: String? = null
        try {
            withTimeout(CONNECT_TIMEOUT_MS) {
                var attempt = 0
                while (attempt < MODEL_CANDIDATES.size) {
                    val (model, withSpeechConfig) = MODEL_CANDIDATES[attempt]
                    val attemptStart = System.currentTimeMillis()
                    session.connect(apiKey, instruction, model, withSpeechConfig)
                    while (true) {
                        when (val s = session.status.value) {
                            is ReaderSessionStatus.Ready -> {
                                VoxoraLog.i(TAG, "narration ready on $model")
                                return@withTimeout
                            }
                            is ReaderSessionStatus.Error -> {
                                VoxoraLog.w(TAG, "candidate failed: $model" +
                                    (if (withSpeechConfig) "" else " (no voiceConfig)") +
                                    " — ${s.message.take(200)}")
                                lastError = s.message
                                break
                            }
                            else -> {
                                if (System.currentTimeMillis() - attemptStart > PER_ATTEMPT_TIMEOUT_MS) {
                                    VoxoraLog.w(TAG, "candidate silent: $model; trying next")
                                    session.stop()
                                    break
                                }
                            }
                        }
                        delay(50)
                    }
                    attempt++
                }
            }
            // Chain exhausted without Ready: surface the last specific server error,
            // or a clear generic message when every attempt was silent.
            throw NarrationException(lastError ?: context.getString(R.string.reader_models_exhausted))
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw NarrationException(context.getString(R.string.reader_connect_timeout))
        }
    }

    private suspend fun collectAudio(narration: GeminiReaderSession, run: Long) {
        try {
            narration.audio.collect { pcm ->
                if (run == generation) {
                    playback?.writeFloats(pcm)
                    mutableAudio.emit(pcm)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            VoxoraLog.w(TAG, "audio collection stopped: ${e.message}")
        }
    }

    private suspend fun awaitPlaybackDrain(run: Long) {
        val p = playback ?: return
        val deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (run != generation) throw NarrationException(STALE_RUN)
            val t = p.undrainedFrames()
            if (t <= DRAIN_THRESHOLD_FRAMES) return
            kotlinx.coroutines.delay(50)
        }
        VoxoraLog.w(TAG, "drain timeout; continuing to next chunk")
    }

    fun pause() {
        if (state.value.phase !in setOf(ReaderPhase.REWRITING, ReaderPhase.SPEAKING, ReaderPhase.NEXT)) return
        cancelPlayback()
        publish(ReaderPhase.PAUSED, mutableNarrationText.value)
    }

    fun stop() {
        cancelPlayback()
        queue.reset()
        publish(ReaderPhase.STOPPED)
    }

    fun close() {
        cancelPlayback()
        scope.cancel()
    }

    private fun cancelPlayback() {
        job?.cancel()
        job = null
        closeSession()
        if (generation > 0) {
            playback?.stop()
            playback = null
        }
    }

    private fun closeSession() {
        sessionJob?.cancel()
        sessionJob = null
        session?.stop()
        session = null
    }

    private fun publish(phase: ReaderPhase, text: String = "") {
        mutableState.value = ReaderState(phase, minOf(queue.index + 1, queue.size), queue.size, text)
        VoxoraLog.d("Reader", phase.name)
    }

    private fun fail(message: String) {
        mutableState.value = state.value.copy(phase = ReaderPhase.ERROR, error = message)
        VoxoraLog.w("Reader", "Reader operation failed: $message")
    }

    private class NarrationException(message: String) : Exception(message)

    private companion object {
        const val TAG = "Reader"
        const val SESSION_LOG_TAG = "ReaderSession"
        const val STALE_RUN = "stale"
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val PER_ATTEMPT_TIMEOUT_MS = 10_000L
        const val DRAIN_TIMEOUT_MS = 10_000L
        const val DRAIN_THRESHOLD_FRAMES = 2_000
        const val NARRATION_PREVIEW_CHARS = 400

        /**
         * Fallback chain for text→AUDIO Live narration: per model, with Kore
         * speechConfig first, then once without (in case a model rejects the
         * voice config). Each attempt runs on a fresh socket within the overall
         * connect budget; fast server rejections skip through quickly, silent
         * sockets are capped at [PER_ATTEMPT_TIMEOUT_MS].
         */
        val MODEL_CANDIDATES: List<Pair<String, Boolean>> = listOf(
            "models/gemini-2.5-flash-native-audio-preview-12-2025" to true,
            "models/gemini-2.5-flash-native-audio-preview-12-2025" to false,
            "models/gemini-2.0-flash-live-001" to true,
            "models/gemini-2.0-flash-live-001" to false,
            "models/gemini-live-2.5-flash-preview" to true,
            "models/gemini-live-2.5-flash-preview" to false,
        )

        fun instructionFor(mode: String): String = when (mode) {
            "fluent" -> "You are a professional audiobook narrator. First silently rewrite the text you are " +
                "given into fluent, natural prose that reads aloud smoothly while preserving its exact meaning, " +
                "facts and language — never invent facts, never summarize, never translate. " +
                "Then speak the rewritten text aloud. Speak only the rewritten text."
            else -> "You are a professional audiobook narrator. First silently rewrite the text you are " +
                "given using clear, simple vocabulary and short, easy sentences while preserving its exact " +
                "meaning, facts and language — no invented facts, no summarizing, no translating. " +
                "Then speak the rewritten text aloud. Speak only the rewritten text."
        }
    }
}
