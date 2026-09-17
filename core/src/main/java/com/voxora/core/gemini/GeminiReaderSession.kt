package com.voxora.core.gemini

import com.voxora.core.GeminiLiveConfig
import com.voxora.core.audio.PcmUtils
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeout
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

/**
 * Text → audio narration session over BidiGenerateContent (same host family as
 * [GeminiLiveConfig.WS_PATH]) for the Reader feature.
 *
 * The model/voice setup is supplied per [connect] call so the caller can run a
 * fallback chain across candidate Live models (each attempt on a fresh socket).
 * Deliberately NOT [GeminiLiveConfig.MODEL]: the translate-preview model used by
 * Live Dub is tuned for live audio translation, not text→audio narration.
 *
 * Setup payload shape verified against the official v1beta schema
 * (google/ai/generativelanguage/v1beta/generative_service.proto):
 * BidiGenerateContentSetup { model, generation_config { response_modalities,
 * speech_config { voice_config { prebuilt_voice_config { voice_name } } } },
 * system_instruction }. `language_code` is OPTIONAL, so no language is sent.
 * Wire format is camelCase, proven in production by [GeminiLiveSession].
 *
 * Diagnostics: [onLog] receives one-line lifecycle events (never the API key or
 * URL — the key is embedded in the WS URL, so URLs are never logged). The core
 * module cannot depend on the app module, so the caller bridges these lines into
 * its ring-buffer logger.
 *
 * Live Dub ([GeminiLiveSession]) is untouched; this class only adds a parallel API.
 */
class GeminiReaderSession(
    client: OkHttpClient = defaultClient(),
) {
    private val wsClient = client
    private val _status = MutableStateFlow<ReaderSessionStatus>(ReaderSessionStatus.Idle)
    val status: StateFlow<ReaderSessionStatus> = _status.asStateFlow()

    /** One-line diagnostic hook; called from WebSocket threads. */
    var onLog: ((String) -> Unit)? = null

    private val audioChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)
    val audio: Flow<FloatArray> = audioChannel.receiveAsFlow()

    private val closedByUs = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)
    private var ws: WebSocket? = null
    private var narrationInstruction: String = ""
    private var setupModel: String = ""
    private var withSpeechConfig: Boolean = true
    private var setupCompleteLogged = false

    @Volatile
    private var pendingTurn: Channel<Unit>? = null

    private val hadAudio = AtomicBoolean(false)
    private val spokenText = StringBuilder()

    fun connect(apiKey: String, instruction: String, model: String, withSpeechConfig: Boolean = true) {
        stop()
        closedByUs.set(false)
        ready.set(false)
        setupCompleteLogged = false
        narrationInstruction = instruction
        setupModel = model
        this.withSpeechConfig = withSpeechConfig
        _status.value = ReaderSessionStatus.Connecting
        log("connecting (model=$model, speechConfig=${if (withSpeechConfig) "on" else "off"})")
        val url = "${GeminiLiveConfig.WS_PATH}?key=${java.net.URLEncoder.encode(apiKey.trim(), "UTF-8")}"
        ws = wsClient.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    fun stop() {
        closedByUs.set(true)
        ready.set(false)
        completeTurn()
        ws?.close(1000, "stop")
        ws = null
        _status.value = ReaderSessionStatus.Idle
    }

    /**
     * Sends one chunk as a text turn and suspends until the model finishes speaking
     * it. Audio arrives via [audio]. Returns the model's text parts (may be empty —
     * some models speak without emitting text), or null if the turn finished
     * without any audio. Throws on connection/protocol failure.
     */
    suspend fun narrate(text: String): String? {
        val socket = ws ?: throw ReaderSessionException("Gemini session is not connected.")
        if (!ready.get()) throw ReaderSessionException("Gemini session is not ready.")
        val channel = Channel<Unit>(capacity = 1)
        pendingTurn = channel
        hadAudio.set(false)
        spokenText.setLength(0)
        val payload = JSONObject()
            .put(
                "clientContent",
                JSONObject()
                    .put(
                        "turns",
                        JSONArray().put(
                            JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", text))),
                        ),
                    )
                    .put("turnComplete", true),
            )
            .toString()
        if (!socket.send(payload)) {
            pendingTurn = null
            throw ReaderSessionException("Gemini connection was lost.")
        }
        log("turn sent (${text.length} chars)")
        try {
            withTimeout(TURN_TIMEOUT_MS) { channel.receive() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            pendingTurn = null
            throw ReaderSessionException("Gemini took too long for this chunk. Try again.")
        } catch (e: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
            pendingTurn = null
            throw ReaderSessionException("Gemini session closed before the chunk finished.")
        }
        pendingTurn = null
        if (!hadAudio.get()) return null
        return spokenText.toString().trim()
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val genConfig = JSONObject().put("responseModalities", JSONArray().put("AUDIO"))
            if (withSpeechConfig) {
                genConfig.put(
                    "speechConfig",
                    JSONObject().put(
                        "voiceConfig",
                        JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", READER_VOICE)),
                    ),
                )
            }
            val setup = JSONObject()
                .put(
                    "setup",
                    JSONObject()
                        .put("model", setupModel)
                        .put("generationConfig", genConfig)
                        .put(
                            "systemInstruction",
                            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", narrationInstruction))),
                        ),
                )
                .toString()
            log("connected; sending setup (model=$setupModel, speechConfig=${if (withSpeechConfig) "on" else "off"})")
            webSocket.send(setup)
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handleMessage(text)

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handleMessage(bytes.utf8())

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (closedByUs.get()) return
            val httpCode = response?.code?.let { " (http $it)" } ?: ""
            log("onFailure: ${t.message.orEmpty().take(LOG_LIMIT)}$httpCode")
            val msg = t.message?.takeIf { it.isNotBlank() } ?: "Connection failed."
            fail(msg + httpCode)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (closedByUs.get()) return
            log("onClosed: code=$code reason=${reason.take(LOG_LIMIT)}")
            if (!ready.get()) {
                fail(mapClose(code, reason))
                return
            }
            fail(reason.ifBlank { "Disconnected ($code)." })
        }
    }

    private fun handleMessage(text: String) {
        val msg = try {
            JSONObject(text)
        } catch (_: Exception) {
            log("unparsable server message (${text.length} chars)")
            return
        }
        if (msg.has("error")) {
            val m = msg.optJSONObject("error")?.optString("message").orEmpty()
            log("server error: ${m.take(LOG_LIMIT)}")
            fail(m.ifBlank { "Gemini error." })
            return
        }
        if (msg.has("setupComplete") || msg.has("setup_complete")) {
            if (!setupCompleteLogged) {
                setupCompleteLogged = true
                log("setup complete (model=$setupModel)")
            }
            ready.set(true)
            _status.value = ReaderSessionStatus.Ready
            return
        }
        if (msg.has("goAway") || msg.has("go_away")) {
            log("goAway from server")
            fail("Gemini is rebalancing the connection. Try Play again.")
            return
        }
        val content = msg.optJSONObject("serverContent") ?: msg.optJSONObject("server_content") ?: run {
            log("server message keys: ${msg.keys().asSequence().take(4).joinToString(",")}")
            return
        }
        content.optJSONObject("modelTurn")?.let { turn -> emitParts(turn) }
        if (content.optBoolean("turnComplete")) {
            log("turn complete (hadAudio=${hadAudio.get()})")
            completeTurn()
        }
    }

    private fun emitParts(turn: JSONObject) {
        val parts = turn.optJSONArray("parts") ?: return
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            part.optString("text").takeIf { it.isNotEmpty() }?.let { spokenText.append(it) }
            val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data") ?: continue
            val data = inline.optString("data")
            if (data.isNullOrBlank()) continue
            val pcm = PcmUtils.pcm16ToFloat(PcmUtils.fromBase64(data))
            if (pcm.isNotEmpty()) {
                if (hadAudio.compareAndSet(false, true)) log("first audio received")
                audioChannel.trySend(pcm)
            }
        }
    }

    private fun completeTurn() {
        pendingTurn?.trySend(Unit)
        pendingTurn = null
    }

    private fun fail(message: String) {
        _status.value = ReaderSessionStatus.Error(message)
        completeTurn()
    }

    private fun log(line: String) {
        onLog?.invoke(line)
    }

    private fun mapClose(code: Int, reason: String): String = when {
        code == 1008 || reason.contains("key", true) -> "Invalid API key. Check the Gemini key in Settings."
        reason.contains("quota", true) -> "Gemini quota exceeded. Wait or use another key."
        code == 1006 -> "Network disconnected. Check your connection and try again."
        reason.isNotBlank() -> reason
        else -> "Gemini refused the connection."
    }

    class ReaderSessionException(message: String) : Exception(message)

    companion object {
        private const val READER_VOICE = "Kore"
        private const val TURN_TIMEOUT_MS = 180_000L
        private const val LOG_LIMIT = 300

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(20, TimeUnit.SECONDS)
                .pingInterval(12, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
    }
}
