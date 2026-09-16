package com.voxora.app.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.voxora.app.util.VoxoraLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

internal enum class ReaderPhase { IDLE, EXTRACTING, READY, REWRITING, SPEAKING, NEXT, PAUSED, STOPPED, COMPLETE, ERROR }

internal data class ReaderState(
    val phase: ReaderPhase = ReaderPhase.IDLE,
    val chunk: Int = 0,
    val total: Int = 0,
    val text: String = "",
    val error: String? = null,
)

class ReaderService @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(ReaderState())
    internal val state = mutableState.asStateFlow()
    private val extractor = TextExtractor(context)
    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private var queue = ChunkQueue(emptyList())
    private var job: Job? = null
    private var generation = 0L
    private var segments = emptyList<String>()
    private var segmentIndex = 0
    private var engine: TextToSpeech? = null
    private var initialization: CompletableDeferred<Unit>? = null
    private var utterance: Pair<String, CompletableDeferred<Unit>>? = null
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            if (change != AudioManager.AUDIOFOCUS_GAIN) scope.launch { pause() }
        }
        .build()

    fun load(uri: Uri) {
        cancelPlayback()
        queue = ChunkQueue(emptyList())
        segments = emptyList()
        segmentIndex = 0
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

    fun play(endpoint: String, mode: String) {
        if (job?.isActive == true || queue.size == 0) return
        val url = endpoint.trim().toHttpUrlOrNull()
        if (url == null || !url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) {
            fail("Configure a valid HTTPS rewrite endpoint without embedded credentials.")
            return
        }
        if (mode !in setOf("simple", "fluent")) {
            fail("Select a rewrite mode.")
            return
        }
        if (queue.current == null) queue.reset()
        val run = ++generation
        job = scope.launch {
            try {
                publish(ReaderPhase.REWRITING)
                prepareSpeech()
                if (audioManager?.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    throw IOException("Audio is unavailable. Try Play again.")
                }
                while (queue.current != null) {
                    if (segments.isEmpty()) {
                        publish(ReaderPhase.REWRITING)
                        val rewritten = rewrite(url.toString(), queue.current!!, mode)
                        segments = ChunkQueue.speechSegments(rewritten, TextToSpeech.getMaxSpeechInputLength())
                        segmentIndex = 0
                    }
                    while (segmentIndex < segments.size) {
                        publish(ReaderPhase.SPEAKING, segments[segmentIndex])
                        speak(segments[segmentIndex])
                        segmentIndex++
                    }
                    publish(ReaderPhase.NEXT)
                    queue.advance()
                    segments = emptyList()
                    segmentIndex = 0
                }
                publish(ReaderPhase.COMPLETE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.message ?: "Reader failed. Try Play again.")
            } finally {
                utterance = null
                engine?.stop()
                audioManager?.abandonAudioFocusRequest(focusRequest)
            }
        }
    }

    fun pause() {
        if (state.value.phase !in setOf(ReaderPhase.REWRITING, ReaderPhase.SPEAKING, ReaderPhase.NEXT)) return
        cancelPlayback()
        publish(ReaderPhase.PAUSED, state.value.text)
    }

    fun stop() {
        cancelPlayback()
        queue.reset()
        segments = emptyList()
        segmentIndex = 0
        publish(ReaderPhase.STOPPED)
    }

    fun close() {
        cancelPlayback()
        scope.cancel()
        engine?.shutdown()
        engine = null
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    private fun cancelPlayback() {
        job?.cancel()
        job = null
        utterance = null
        engine?.stop()
        audioManager?.abandonAudioFocusRequest(focusRequest)
    }

    private fun publish(phase: ReaderPhase, text: String = "") {
        mutableState.value = ReaderState(phase, minOf(queue.index + 1, queue.size), queue.size, text)
        VoxoraLog.d("Reader", phase.name)
    }

    private fun fail(message: String) {
        mutableState.value = state.value.copy(phase = ReaderPhase.ERROR, error = message)
        VoxoraLog.w("Reader", "Reader operation failed")
    }

    private suspend fun prepareSpeech() {
        if (engine == null) {
            val ready = CompletableDeferred<Unit>()
            initialization = ready
            engine = TextToSpeech(context) { status ->
                scope.launch {
                    if (status == TextToSpeech.SUCCESS) ready.complete(Unit)
                    else ready.completeExceptionally(IOException("Android text-to-speech is unavailable."))
                }
            }
        }
        try {
            withTimeout(15_000) { initialization!!.await() }
            val tts = engine!!
            val result = tts.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                throw IOException("Install a text-to-speech voice for your device language in Android settings.")
            }
            tts.setAudioAttributes(attributes)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = finishUtterance(utteranceId, false)
                @Deprecated("Deprecated in Android")
                override fun onError(utteranceId: String?) = finishUtterance(utteranceId, true)
                override fun onError(utteranceId: String?, errorCode: Int) = finishUtterance(utteranceId, true)
            })
        } catch (e: Exception) {
            if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            engine?.shutdown()
            engine = null
            throw IOException("Could not initialize text-to-speech. Check installed voices in Android settings.")
        }
    }

    private fun finishUtterance(id: String?, failed: Boolean) {
        scope.launch {
            val pending = utterance ?: return@launch
            if (pending.first != id) return@launch
            if (failed) pending.second.completeExceptionally(IOException("Speech failed. Check your Android TTS voice."))
            else pending.second.complete(Unit)
        }
    }

    private suspend fun speak(text: String) {
        val id = UUID.randomUUID().toString()
        val completion = CompletableDeferred<Unit>()
        utterance = id to completion
        if (engine!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) {
            throw IOException("Could not start speech. Check your Android TTS voice.")
        }
        try {
            withTimeout(300_000) { completion.await() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw IOException("Speech timed out. Try Play again.")
        }
    }

    private suspend fun rewrite(endpoint: String, text: String, mode: String): String =
        suspendCancellableCoroutine { continuation ->
            val payload = JSONObject().put("text", text).put("mode", mode).toString()
            val request = Request.Builder().url(endpoint)
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("Rewrite request failed. Check your connection and endpoint."))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val rewritten = response.use {
                            if (!it.isSuccessful) throw IOException("Rewrite backend returned HTTP ${it.code}.")
                            val body = it.body ?: throw IOException("Rewrite backend returned an empty response.")
                            val bytes = body.byteStream().use { stream ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = stream.read(buffer)
                                    if (count == -1) break
                                    if (output.size() + count > 1_048_576) throw IOException("Rewrite response is too large.")
                                    output.write(buffer, 0, count)
                                }
                                output.toByteArray()
                            }
                            val value = JSONObject(bytes.toString(Charsets.UTF_8)).opt("rewritten_text")
                            if (value !is String || value.isBlank()) throw IOException("Backend must return a non-empty rewritten_text string.")
                            value
                        }
                        if (continuation.isActive) continuation.resume(rewritten)
                    } catch (e: Exception) {
                        val error = if (e is IOException) e else IOException("Invalid rewrite response.")
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
}
