package com.voxora.core.gemini

import com.voxora.core.usage.GeminiUsageMetadata
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject

sealed class ReaderSessionStatus {
    data object Idle : ReaderSessionStatus()
    data object Connecting : ReaderSessionStatus()
    data object Ready : ReaderSessionStatus()
    data class Error(val message: String) : ReaderSessionStatus()
}

class GeminiReaderSession internal constructor(
    client: WebSocket.Factory,
    private val connectTimeoutMs: Long,
    private val turnTimeoutMs: Long,
) {
    constructor(client: WebSocket.Factory = defaultClient()) : this(client, CONNECT_TIMEOUT_MS, TURN_TIMEOUT_MS)

    init {
        require(connectTimeoutMs > 0 && turnTimeoutMs > 0)
    }

    private val wsClient = client
    private val _status = MutableStateFlow<ReaderSessionStatus>(ReaderSessionStatus.Idle)
    val status: StateFlow<ReaderSessionStatus> = _status.asStateFlow()

    /** One-line diagnostic hook; the caller routes these lines to VoxoraLog. */
    @Volatile
    var onLog: ((String) -> Unit)? = null

    /**
     * Called whenever the server reports token usage for a turn.
     *
     * The Live API only *may* include a `usageMetadata` object, so this fires only when one is
     * actually present and never with a synthesised value. A caller that wants local usage
     * history can record it here without this session knowing anything about persistence.
     */
    @Volatile
    var onUsage: ((GeminiUsageMetadata) -> Unit)? = null

    private val _usage = MutableStateFlow<GeminiUsageMetadata?>(null)

    /** Token usage the server last reported on the current connection, or null if none was sent. */
    val usage: StateFlow<GeminiUsageMetadata?> = _usage.asStateFlow()

    private val lock = Any()
    private val sessionJob = SupervisorJob()
    private val scope = CoroutineScope(sessionJob + Dispatchers.IO)
    private var closed = false
    private var generation = 0L
    private var attempt: Attempt? = null

    val reusable: Boolean
        get() = synchronized(lock) { attempt?.let { isReusable(it) } == true }

    private class Attempt(val generation: Long, val setup: String) {
        val ingress = Channel<Ingress>(QUEUE_MAX_MESSAGES)
        val startedAt = System.nanoTime()
        var socket: WebSocket? = null
        var worker: Job? = null
        var connectDeadline: Job? = null
        var ready = false
        var retireAfterTurn = false
        var terminalMessage: String? = null
        var turn: Turn? = null

        /** Latest usageMetadata the server sent on this connection, if any. */
        var usage: GeminiUsageMetadata? = null
        var queuedMessages = 0
        var queuedBytes = 0L
        var highWaterMessages = 0
        var highWaterBytes = 0L
        var processedMessages = 0L
        var processingNanos = 0L
        var audioBytes = 0L
    }

    private class Turn(val onPcm: (ByteArray) -> Unit) {
        val result = CompletableDeferred<String?>()
        /**
         * Exact exception the caller's sink threw. [narrate] rethrows this object
         * instead of whatever [result] hands back, because coroutine stack trace
         * recovery substitutes a copy of the cause whenever JVM assertions are
         * enabled (which Gradle's test task does by default).
         */
        @Volatile
        var sinkFailure: Throwable? = null
        val text = StringBuilder()
        val startedAt = System.nanoTime()
        var audioBytes = 0L
        var firstMimeLogged = false
    }

    private data class Ingress(val raw: Any, val cost: Long, val turn: Turn?)

    /**
     * Opens a session whose narrator speaks with [voice].
     *
     * [voice] is the **Gemini prebuilt voice name** (`ReaderVoice.geminiVoiceName`), not Voxora's
     * stored id. It is a required argument on purpose: the voice used to be a private constant, so
     * a caller could not forget it and could not choose it either. A default would let a new call
     * site silently narrate in the wrong voice, which is the defect this parameter exists to make
     * unrepresentable. Callers pass `ReaderVoice.voiceName(prefs.readerVoice.first())`.
     */
    fun connect(
        apiKey: String,
        instruction: String,
        model: String,
        voice: String,
        withSpeechConfig: Boolean = true,
    ) {
        val target = synchronized(lock) {
            if (closed) throw ReaderSessionException("Gemini session is closed.")
            attempt?.let { abortLocked(it, "Gemini connection was replaced.") }
            Attempt(++generation, setupPayload(instruction, model, voice, withSpeechConfig)).also {
                attempt = it
                _status.value = ReaderSessionStatus.Connecting
                // Usage belongs to a connection, so a new connection starts from "not reported"
                // rather than exposing the previous connection's numbers as if they were current.
                _usage.value = null
                it.worker = scope.launch(start = CoroutineStart.LAZY) { runWorker(it) }
                it.connectDeadline = scope.launch(start = CoroutineStart.LAZY) {
                    delay(connectTimeoutMs)
                    val message = synchronized(lock) {
                        if (isCurrent(it) && !it.ready) failLocked(it, "Gemini connection setup timed out.") else null
                    }
                    message?.let(::log)
                }
                it.worker?.start()
                it.connectDeadline?.start()
            }
        }
        log("connecting generation=${target.generation}")
        try {
            val url = "${READER_WS_PATH}?key=${java.net.URLEncoder.encode(apiKey.trim(), "UTF-8")}"
            val socket = wsClient.newWebSocket(Request.Builder().url(url).build(), listener(target))
            synchronized(lock) {
                if (isCurrent(target) && target.terminalMessage == null) target.socket = socket else socket.cancel()
            }
        } catch (_: Exception) {
            fail(target, "Gemini connection could not be started.")
        }
    }

    fun stop() {
        synchronized(lock) {
            attempt?.let { abortLocked(it, "Gemini narration stopped.") }
            _status.value = ReaderSessionStatus.Idle
        }
    }

    fun close() {
        synchronized(lock) {
            closed = true
            attempt?.let { abortLocked(it, "Gemini session closed.") }
            _status.value = ReaderSessionStatus.Idle
            sessionJob.cancel()
        }
    }

    suspend fun closeAndJoin() {
        close()
        withContext(NonCancellable) { sessionJob.join() }
    }

    /**
     * Sends one chunk as a text turn and suspends until all PCM has been written
     * to [onPcm] on the ordered IO worker. Returns the model's text parts (may be
     * empty), or null if the turn finished without audio. Throws on connection,
     * protocol, decode, or sink failure.
     */
    suspend fun narrate(text: String, onPcm: (ByteArray) -> Unit): String? {
        currentCoroutineContext().ensureActive()
        val payload = JSONObject().put(
            "clientContent",
            JSONObject()
                .put(
                    "turns",
                    JSONArray().put(
                        JSONObject().put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", text))),
                    ),
                )
                .put("turnComplete", true),
        ).toString()
        val turn = Turn(onPcm)
        val target = synchronized(lock) {
            val current = attempt
            if (current == null || !isReusable(current)) {
                throw ReaderSessionException("Gemini session is not ready.")
            }
            if (current.turn != null) throw ReaderSessionException("Gemini narration is already active.")
            current.turn = turn
            current
        }
        try {
            val failure = synchronized(lock) {
                if (!isCurrent(target) || target.terminalMessage != null) {
                    null
                } else if (target.retireAfterTurn || runCatching { target.socket?.send(payload) }.getOrNull() != true) {
                    terminateLocked(target, "Gemini connection is unavailable (category=send_unavailable).")
                } else {
                    null
                }
            }
            failure?.let(::log)
            log("turn sent generation=${target.generation} chars=${text.length}")
            val completed = withTimeoutOrNull(turnTimeoutMs) {
                turn.result.await()
                true
            } ?: false
            if (!completed) {
                val message = "Gemini took too long for this chunk. Try again."
                fail(target, message)
                throw ReaderSessionException(message)
            }
            return turn.result.await()
        } catch (e: CancellationException) {
            fail(target, "Gemini narration was cancelled.")
            throw e
        } catch (e: Exception) {
            fail(target, "Gemini narration failed.")
            // Rethrow the caller's own exception object: awaiting a deferred
            // recovers the stack trace and hands back a copy when assertions
            // are enabled, which would break sink-failure error classification.
            throw turn.sinkFailure ?: e
        } finally {
            synchronized(lock) {
                if (target.turn === turn) target.turn = null
            }
        }
    }

    private fun listener(target: Attempt) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val failure = synchronized(lock) {
                if (!isCurrent(target) || target.terminalMessage != null) {
                    webSocket.cancel()
                    return
                }
                target.socket = webSocket
                if (!webSocket.send(target.setup)) failLocked(target, "Gemini setup could not be sent.") else null
            }
            failure?.let(::log)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            offer(target, text, text.length.toLong() * 2)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            offer(target, bytes, bytes.size.toLong())
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            terminate(target, "Gemini connection failed (category=transport${response?.code?.let { " http=$it" }.orEmpty()}).")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            terminate(target, closeMessage(code))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            terminate(target, closeMessage(code))
        }
    }

    private fun offer(target: Attempt, raw: Any, cost: Long) {
        val failure = synchronized(lock) {
            if (!isCurrent(target) || target.terminalMessage != null) return
            when {
                cost > MAX_MESSAGE_BYTES -> failLocked(target, "Gemini ingress message exceeds 2 MiB (bytes=$cost).")
                target.queuedMessages >= QUEUE_MAX_MESSAGES || target.queuedBytes + cost > MAX_QUEUE_BYTES ->
                    failLocked(target, "Narration decode/storage ingress overrun; cannot keep up without losing audio.")
                else -> {
                    target.queuedMessages++
                    target.queuedBytes += cost
                    target.highWaterMessages = maxOf(target.highWaterMessages, target.queuedMessages)
                    target.highWaterBytes = maxOf(target.highWaterBytes, target.queuedBytes)
                    if (target.ingress.trySend(Ingress(raw, cost, target.turn)).isFailure) {
                        failLocked(target, "Narration decode/storage ingress rejected a message.")
                    } else {
                        null
                    }
                }
            }
        }
        failure?.let(::log)
    }

    private suspend fun runWorker(target: Attempt) {
        try {
            for (message in target.ingress) {
                currentCoroutineContext().ensureActive()
                val startedAt = System.nanoTime()
                try {
                    val raw = when (val value = message.raw) {
                        is String -> value
                        is ByteString -> value.utf8()
                        else -> throw ReaderSessionException("Gemini ingress format is invalid.")
                    }
                    handleMessage(target, message.turn, JSONObject(raw))
                } finally {
                    synchronized(lock) {
                        if (isCurrent(target)) {
                            target.queuedMessages--
                            target.queuedBytes -= message.cost
                            target.processedMessages++
                            target.processingNanos += System.nanoTime() - startedAt
                        }
                    }
                }
            }
            val failure = synchronized(lock) {
                if (isCurrent(target)) target.terminalMessage?.let { failLocked(target, it) } else null
            }
            failure?.let(::log)
        } catch (e: CancellationException) {
            fail(target, "Gemini narration worker was cancelled.")
            throw e
        } catch (e: ReaderSessionException) {
            fail(target, e.message ?: "Gemini narration failed.")
        } catch (_: Exception) {
            fail(target, "Gemini response could not be decoded.")
        }
    }

    private suspend fun handleMessage(target: Attempt, turn: Turn?, message: JSONObject) {
        if (message.has("error")) {
            throw ReaderSessionException(serverError(message.optJSONObject("error")?.optInt("code")))
        }
        // Usage metadata is a top-level field that the server may attach to any message, and
        // it is often sent alongside the turn-complete message rather than inside serverContent.
        // It is therefore read before the serverContent early-return below, otherwise a
        // usage-only message would be dropped. Absence is normal and records nothing.
        GeminiUsageMetadata.fromMessage(message)?.let { reported ->
            synchronized(lock) {
                if (isCurrent(target)) {
                    target.usage = reported
                    _usage.value = reported
                }
            }
            onUsage?.invoke(reported)
        }
        if (message.has("setupComplete") || message.has("setup_complete")) {
            val ready = synchronized(lock) {
                if (!isCurrent(target) || target.ready || target.terminalMessage != null) return
                target.ready = true
                target.connectDeadline?.cancel()
                target.connectDeadline = null
                _status.value = ReaderSessionStatus.Ready
                "setup complete generation=${target.generation}"
            }
            log(ready)
        }
        if (message.has("goAway") || message.has("go_away")) {
            val diagnostics = synchronized(lock) {
                if (!isCurrent(target)) return
                target.retireAfterTurn = true
                if (target.turn?.result?.isCompleted != false) {
                    terminateLocked(target, "Gemini is retiring the connection (category=go_away).")
                } else {
                    "connection retiring after turn generation=${target.generation}"
                }
            }
            diagnostics?.let(::log)
        }
        val content = message.optJSONObject("serverContent") ?: message.optJSONObject("server_content") ?: return
        if (content.optBoolean("interrupted")) throw ReaderSessionException("Gemini narration was interrupted.")
        if (turn == null || synchronized(lock) { !isCurrent(target) || target.turn !== turn || turn.result.isCompleted }) return
        val modelTurn = content.optJSONObject("modelTurn") ?: content.optJSONObject("model_turn")
        val parts = modelTurn?.optJSONArray("parts")
        if (parts != null) {
            for (index in 0 until parts.length()) {
                currentCoroutineContext().ensureActive()
                val part = parts.getJSONObject(index)
                val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data") ?: continue
                val mime = inline.optString("mimeType").ifBlank { inline.optString("mime_type") }
                if (!validPcmMime(mime)) throw ReaderSessionException("Gemini audio must be PCM16 mono at 24000 Hz.")
                val pcm = Base64.getDecoder().decode(inline.getString("data"))
                if (pcm.size % 2 != 0) throw ReaderSessionException("Gemini PCM16 audio has an odd byte count.")
                if (pcm.isEmpty()) continue
                if (synchronized(lock) { !isCurrent(target) || target.turn !== turn }) return
                if (!turn.firstMimeLogged) {
                    turn.firstMimeLogged = true
                    log("first audio generation=${target.generation} mime=audio/pcm;rate=24000;channels=1 elapsedMs=${elapsedMs(turn.startedAt)}")
                }
                currentCoroutineContext().ensureActive()
                try {
                    turn.onPcm(pcm)
                } catch (e: Exception) {
                    val diagnostics = synchronized(lock) {
                        if (isCurrent(target)) {
                            turn.sinkFailure = e
                            turn.result.completeExceptionally(e)
                            failLocked(target, "Narration PCM storage sink failed.")
                        } else {
                            null
                        }
                    }
                    diagnostics?.let(::log)
                    return
                }
                turn.audioBytes += pcm.size
                synchronized(lock) {
                    if (isCurrent(target)) target.audioBytes += pcm.size
                }
            }
        }
        val transcription = content.optJSONObject("outputTranscription") ?: content.optJSONObject("output_transcription")
        if (transcription != null) {
            val text = transcription.optString("text")
            if (text.length > MAX_TEXT_CHARS - turn.text.length) {
                throw ReaderSessionException("Gemini narration transcript exceeds the per-turn limit.")
            }
            turn.text.append(text)
        }
        if (content.optBoolean("turnComplete") || content.optBoolean("turn_complete")) {
            val diagnostics = synchronized(lock) {
                if (!isCurrent(target) || target.turn !== turn) return
                turn.result.complete(if (turn.audioBytes == 0L) null else turn.text.toString().trim())
                if (target.retireAfterTurn) {
                    terminateLocked(target, "Gemini is retiring the connection (category=go_away).")
                }
                "turn complete generation=${target.generation} audioBytes=${turn.audioBytes} " +
                    "elapsedMs=${elapsedMs(turn.startedAt)} ${metricsLocked(target)}"
            }
            log(diagnostics)
        }
    }

    private fun validPcmMime(mime: String): Boolean {
        if (mime.length > 128) return false
        val fields = mime.lowercase(Locale.ROOT).split(';').map(String::trim)
        if (fields.first() != "audio/pcm") return false
        val parameters = mutableMapOf<String, String>()
        for (field in fields.drop(1)) {
            val pair = field.split('=', limit = 2).map(String::trim)
            if (pair.size != 2 || parameters.put(pair[0], pair[1]) != null) return false
        }
        return parameters["rate"] == "24000" &&
            (parameters["channels"] == null || parameters["channels"] == "1") &&
            (parameters["bits"] == null || parameters["bits"] == "16") &&
            parameters.keys.all { it == "rate" || it == "channels" || it == "bits" }
    }

    private fun isCurrent(target: Attempt): Boolean = !closed && attempt === target && generation == target.generation

    private fun isReusable(target: Attempt): Boolean =
        isCurrent(target) && target.ready && !target.retireAfterTurn && target.terminalMessage == null

    private fun terminateLocked(target: Attempt, message: String): String? {
        if (target.terminalMessage != null) return null
        target.terminalMessage = message
        target.retireAfterTurn = true
        target.connectDeadline?.cancel()
        target.connectDeadline = null
        target.ingress.close()
        target.socket?.cancel()
        target.socket = null
        return "$message generation=${target.generation} draining ${metricsLocked(target)}"
    }

    private fun terminate(target: Attempt, message: String) {
        val diagnostics = synchronized(lock) {
            if (!isCurrent(target)) return
            terminateLocked(target, message)
        }
        diagnostics?.let(::log)
    }

    private fun abortLocked(target: Attempt, message: String) {
        attempt = null
        target.turn?.result?.completeExceptionally(ReaderSessionException(message))
        target.turn = null
        target.ingress.cancel()
        target.queuedMessages = 0
        target.queuedBytes = 0
        target.worker?.cancel()
        target.connectDeadline?.cancel()
        target.socket?.cancel()
        target.socket = null
    }

    private fun failLocked(target: Attempt, message: String): String {
        val diagnostics = "$message generation=${target.generation} ${metricsLocked(target)}"
        abortLocked(target, diagnostics)
        _status.value = ReaderSessionStatus.Error(diagnostics)
        return diagnostics
    }

    private fun fail(target: Attempt, message: String) {
        val diagnostics = synchronized(lock) {
            if (!isCurrent(target)) return
            failLocked(target, message)
        }
        log(diagnostics)
    }

    private fun metricsLocked(target: Attempt): String =
        "queue=${target.queuedMessages}/${target.queuedBytes}B " +
            "queueHW=${target.highWaterMessages}/${target.highWaterBytes}B " +
            "processed=${target.processedMessages} workerMs=${TimeUnit.NANOSECONDS.toMillis(target.processingNanos)} " +
            "audioBytes=${target.audioBytes} elapsedMs=${elapsedMs(target.startedAt)}"

    private fun log(line: String) {
        runCatching { onLog?.invoke(line.take(LOG_LIMIT)) }
    }

    private fun closeMessage(code: Int): String = when (code) {
        1008 -> "Gemini refused the connection. Check the API key and model access."
        1006 -> "Network disconnected. Check your connection and try again."
        else -> "Gemini session closed before further narration (code=$code)."
    }

    private fun serverError(code: Int?): String = when (code) {
        401, 403 -> "Gemini denied access. Check the API key and model access."
        429 -> "Gemini quota exceeded. Wait or use another key."
        else -> "Gemini reported a server error."
    }

    private fun setupPayload(
        instruction: String,
        model: String,
        voice: String,
        withSpeechConfig: Boolean,
    ): String {
        val config = JSONObject().put("responseModalities", JSONArray().put("AUDIO"))
        if (withSpeechConfig) {
            // A blank name is not a usable voice, so it falls back to the product default rather
            // than sending an empty voiceName the server would reject.
            val name = voice.trim().ifEmpty { ReaderVoice.DEFAULT.geminiVoiceName }
            config.put(
                "speechConfig",
                JSONObject().put(
                    "voiceConfig",
                    JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", name)),
                ),
            )
        }
        return JSONObject().put(
            "setup",
            JSONObject().put("model", model)
                .put("generationConfig", config)
                .put("outputAudioTranscription", JSONObject())
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruction)))),
        ).toString()
    }

    class ReaderSessionException(message: String) : Exception(message)

    companion object {
        private const val READER_WS_PATH = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val TURN_TIMEOUT_MS = 180_000L
        private const val CONNECT_TIMEOUT_MS = 20_000L
        private const val LOG_LIMIT = 512
        private const val QUEUE_MAX_MESSAGES = 4096
        private const val MAX_QUEUE_BYTES = 8L * 1024 * 1024
        private const val MAX_MESSAGE_BYTES = 2L * 1024 * 1024
        private const val MAX_TEXT_CHARS = 8 * 1024

        private fun elapsedMs(startedAt: Long): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(20, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
    }
}
