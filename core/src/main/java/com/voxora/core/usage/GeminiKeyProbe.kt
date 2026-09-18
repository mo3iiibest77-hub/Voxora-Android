package com.voxora.core.usage

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Performs the key check. Abstracted so the classification can be tested without network
 * access, and so a future authenticated provider can be substituted.
 *
 * Implementations must never log, persist or embed the key in an exception message.
 */
interface GeminiProbeTransport {
    suspend fun listModels(apiKey: String): GeminiProbeResponse
}

/**
 * Checks whether a Gemini API key is usable, using only official, non-billable API surface.
 *
 * ## Why `models.list`
 * The check calls the Generative Language API's `models.list` endpoint. It is the documented
 * way to enumerate available models, it accepts an API key, and it generates no content — so
 * it consumes no tokens and cannot incur generation charges. A paid or generative endpoint is
 * deliberately **not** used to test a key.
 *
 * ## What this can and cannot tell you
 * It answers "does this key authenticate against this API, and what is it allowed to do right
 * now". It cannot answer "which Google account or Cloud project owns this key", and it cannot
 * read project quota or billing — those need OAuth credentials and the Cloud APIs, which this
 * app does not hold. Nothing here guesses at them.
 */
class GeminiKeyProbe(private val transport: GeminiProbeTransport = GeminiHttpProbeTransport()) {

    /**
     * Probes [apiKey]. A missing or placeholder key short-circuits to
     * [GeminiKeyStatus.CONFIGURATION_INCOMPLETE] without any network call, so an
     * unconfigured install cannot generate traffic or a misleading error.
     */
    suspend fun probe(apiKey: String?): GeminiKeyProbeResult {
        if (!ApiKeyMask.isConfigured(apiKey)) {
            return GeminiKeyProbeResult(
                status = GeminiKeyStatus.CONFIGURATION_INCOMPLETE,
                detail = "no-key",
            )
        }
        val response = try {
            transport.listModels(apiKey!!.trim())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            GeminiProbeResponse.failed()
        }
        val status = GeminiKeyClassifier.classify(response)
        return GeminiKeyProbeResult(
            status = status,
            modelCount = if (status == GeminiKeyStatus.CONNECTED) GeminiKeyClassifier.modelCount(response.body) else null,
            detail = response.transportFailure?.name?.lowercase()?.takeIf { status == GeminiKeyStatus.NETWORK_UNAVAILABLE }
                ?: response.statusCode?.let { "http-$it" },
        )
    }

    companion object {
        /** The documented `models.list` method on the Generative Language API. */
        const val MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }
}

/**
 * The production transport.
 *
 * The key is sent in the `x-goog-api-key` header rather than as a `?key=` query parameter, so
 * the secret never appears in a URL. That matters because URLs are the part of an HTTP call
 * most likely to end up in a log, a crash report or a proxy trace. For the same reason the
 * URL logged here is the constant endpoint, never a request-specific string.
 */
class GeminiHttpProbeTransport(
    private val client: OkHttpClient = defaultClient(),
) : GeminiProbeTransport {

    override suspend fun listModels(apiKey: String): GeminiProbeResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(GeminiKeyProbe.MODELS_URL)
            .header("x-goog-api-key", apiKey)
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                GeminiProbeResponse.http(response.code, response.body?.string())
            }
        } catch (_: IOException) {
            GeminiProbeResponse.network()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            GeminiProbeResponse.failed()
        }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 15L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
            .build()
    }
}
