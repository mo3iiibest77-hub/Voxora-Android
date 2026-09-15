package com.voxora.core.gemini

import com.voxora.core.GeminiLiveConfig
import com.voxora.core.audio.PcmUtils
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

sealed class GeminiStatus {
    data object Idle : GeminiStatus()
    data object Connecting : GeminiStatus()
    data object Ready : GeminiStatus()
    data object Reconnecting : GeminiStatus()
    data class Error(val message: String) : GeminiStatus()
}

/** WebSocket client for Gemini Live Translate — protocol aligned with ParsLiveDub. */
class GeminiLiveSession(
    private val client: OkHttpClient = defaultClient(),
) {
    private val _status = MutableStateFlow<GeminiStatus>(GeminiStatus.Idle)
    val status: StateFlow<GeminiStatus> = _status.asStateFlow()

    private val _audioOut = MutableSharedFlow<FloatArray>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val audioOut: SharedFlow<FloatArray> = _audioOut.asSharedFlow()

    private var ws: WebSocket? = null
    private val ready = AtomicBoolean(false)
    private val closedByUs = AtomicBoolean(false)
    private var apiKey: String = ""
    private var targetLanguage: String = "fa"
    private var reconnectAttempts = 0

    fun connect(apiKey: String, targetLanguageCode: String) {
        stop()
        closedByUs.set(false)
        ready.set(false)
        this.apiKey = apiKey.trim()
        this.targetLanguage = targetLanguageCode.ifBlank { "fa" }
        reconnectAttempts = 0
        openSocket()
    }

    fun stop() {
        closedByUs.set(true)
        ready.set(false)
        ws?.close(1000, "stop")
        ws = null
        _status.value = GeminiStatus.Idle
    }

    fun sendPcm16k(floatSamples16k: FloatArray) {
        val socket = ws ?: return
        if (!ready.get()) return
        if (floatSamples16k.isEmpty()) return
        val bytes = PcmUtils.floatTo16BitPcm(floatSamples16k)
        val payload = JSONObject()
            .put(
                "realtimeInput",
                JSONObject().put(
                    "audio",
                    JSONObject()
                        .put("data", PcmUtils.toBase64(bytes))
                        .put("mimeType", "audio/pcm;rate=16000"),
                ),
            )
        socket.send(payload.toString())
    }

    private fun openSocket() {
        _status.value = if (reconnectAttempts == 0) GeminiStatus.Connecting else GeminiStatus.Reconnecting
        val url = "${GeminiLiveConfig.WS_PATH}?key=${java.net.URLEncoder.encode(apiKey, "UTF-8")}"
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val setup = JSONObject().put(
                "setup",
                JSONObject()
                    .put("model", GeminiLiveConfig.MODEL)
                    .put(
                        "generationConfig",
                        JSONObject()
                            .put("responseModalities", JSONArray().put("AUDIO"))
                            .put(
                                "translationConfig",
                                JSONObject()
                                    .put("targetLanguageCode", targetLanguage)
                                    .put("echoTargetLanguage", true),
                            ),
                    )
                    .put("inputAudioTranscription", JSONObject())
                    .put("outputAudioTranscription", JSONObject()),
            )
            webSocket.send(setup.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleMessage(bytes.utf8())
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (closedByUs.get()) return
            failOrReconnect(t.message ?: "Connection failed")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (closedByUs.get()) return
            if (!ready.get()) {
                _status.value = GeminiStatus.Error(mapClose(code, reason))
                return
            }
            failOrReconnect(reason.ifBlank { "Disconnected ($code)" })
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
            _status.value = GeminiStatus.Error(m.ifBlank { "Gemini error" })
            stop()
            return
        }
        if (msg.has("setupComplete") || msg.has("setup_complete")) {
            ready.set(true)
            reconnectAttempts = 0
            _status.value = GeminiStatus.Ready
            return
        }
        if (msg.has("goAway") || msg.has("go_away")) {
            ws?.close(1000, "goAway")
            if (!closedByUs.get()) openSocket()
            return
        }
        val content = msg.optJSONObject("serverContent") ?: msg.optJSONObject("server_content") ?: return
        val turn = content.optJSONObject("modelTurn") ?: content.optJSONObject("model_turn") ?: return
        val parts = turn.optJSONArray("parts") ?: return
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data") ?: continue
            val data = inline.optString("data")
            if (data.isNullOrBlank()) continue
            val pcm = PcmUtils.pcm16ToFloat(PcmUtils.fromBase64(data))
            if (pcm.isNotEmpty()) _audioOut.tryEmit(pcm)
        }
    }

    private fun failOrReconnect(message: String) {
        if (closedByUs.get()) return
        if (reconnectAttempts >= 4) {
            _status.value = GeminiStatus.Error(message)
            return
        }
        reconnectAttempts++
        _status.value = GeminiStatus.Reconnecting
        val delay = minOf(500L * (1 shl (reconnectAttempts - 1)), 5000L)
        client.dispatcher.executorService.execute {
            try {
                Thread.sleep(delay)
            } catch (_: InterruptedException) {
            }
            if (!closedByUs.get()) openSocket()
        }
    }

    private fun mapClose(code: Int, reason: String): String = when {
        code == 1008 || reason.contains("key", true) -> "Invalid API key. Check Google AI Studio."
        reason.contains("quota", true) -> "API quota exceeded."
        code == 1006 -> "Network disconnected."
        reason.isNotBlank() -> reason
        else -> "Gemini refused the connection."
    }

    companion object {
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(15, TimeUnit.SECONDS)
                .build()
    }
}
