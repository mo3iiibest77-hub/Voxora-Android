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
 * Model choice: `gemini-2.0-flash-live-001` — a public Live model that accepts text
 * client content and streams spoken PCM (16-bit, 24 kHz). Deliberately NOT
 * [GeminiLiveConfig.MODEL]: that translate-preview model is tuned for live audio
 * translation and does not fit text→audio narration.
 *
 * Live Dub ([GeminiLiveSession]) is untouched; this class only adds a parallel API
 * with its own setup (responseModalities=AUDIO, no translationConfig).
 */
class GeminiReaderSession(
    client: OkHttpClient = defaultClient(),
) {
    private val wsClient = client
    private val _status = MutableStateFlow<ReaderSessionStatus>(ReaderSessionStatus.Idle)
    val status: StateFlow<ReaderSessionStatus> = _status.asStateFlow()

    private val audioChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)
    val audio: Flow<FloatArray> = audioChannel.receiveAsFlow()

    private val closedByUs = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)
    private var ws: WebSocket? = null
    private var narrationInstruction: String = ""

    @Volatile
    private var pendingTurn: Channel<Unit>? = null

    private val hadAudio = AtomicBoolean(false)
    private val spokenText = StringBuilder()

    fun connect(apiKey: String, instruction: String) {
        stop()
        closedByUs.set(false)
        ready.set(false)
        narrationInstruction = instruction
        _status.value = ReaderSessionStatus.Connecting
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
            val setup = JSONObject()
                .put(
                    "setup",
                    JSONObject()
                        .put("model", READER_MODEL)
                        .put(
                            "generationConfig",
                            JSONObject()
                                .put("responseModalities", JSONArray().put("AUDIO"))
                                .put(
                                    "speechConfig",
                                    JSONObject().put(
                                        "voiceConfig",
                                        JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", READER_VOICE)),
                                    ),
                                ),
                        )
                        .put(
                            "systemInstruction",
                            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", narrationInstruction))),
                        ),
                )
                .toString()
            webSocket.send(setup)
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handleMessage(text)

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handleMessage(bytes.utf8())

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (closedByUs.get()) return
            fail(t.message ?: "Connection failed.")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (closedByUs.get()) return
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
            return
        }
        if (msg.has("error")) {
            val m = msg.optJSONObject("error")?.optString("message").orEmpty()
            fail(m.ifBlank { "Gemini error." })
            return
        }
        if (msg.has("setupComplete") || msg.has("setup_complete")) {
            ready.set(true)
            _status.value = ReaderSessionStatus.Ready
            return
        }
        val content = msg.optJSONObject("serverContent") ?: msg.optJSONObject("server_content") ?: return
        content.optJSONObject("modelTurn")?.let { turn -> emitParts(turn) }
        if (content.optBoolean("turnComplete")) {
            hadAudio.set(true)
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
            if (pcm.isNotEmpty()) audioChannel.trySend(pcm)
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

    private fun mapClose(code: Int, reason: String): String = when {
        code == 1008 || reason.contains("key", true) -> "Invalid API key. Check the Gemini key in Settings."
        reason.contains("quota", true) -> "Gemini quota exceeded. Wait or use another key."
        code == 1006 -> "Network disconnected. Check your connection and try again."
        reason.isNotBlank() -> reason
        else -> "Gemini refused the connection."
    }

    class ReaderSessionException(message: String) : Exception(message)

    companion object {
        private const val READER_MODEL = "models/gemini-2.0-flash-live-001"
        private const val READER_VOICE = "Kore"
        private const val TURN_TIMEOUT_MS = 180_000L

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
