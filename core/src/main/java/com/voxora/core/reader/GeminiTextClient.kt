package com.voxora.core.reader

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * The result of one text generation.
 *
 * A failure is a value, not an exception, for the same reason the catalogue lookups are: this is an
 * optional enhancement, and nothing about it may reach the reading path as an error.
 */
sealed class GeminiTextResult {
    data class Text(val value: GeneratedOverview) : GeminiTextResult()
    data class Failed(val reason: MetadataUnavailable) : GeminiTextResult()
}

/**
 * Performs one `generateContent` call.
 *
 * Abstracted so the generator's decisions — what is asked, what is accepted, what is cached — are
 * testable without a network, exactly as [BookMetadataSource] is for the catalogues.
 *
 * Implementations must never log, persist or embed the API key in an exception message.
 */
interface GeminiTextTransport {
    suspend fun generate(apiKey: String, model: String, prompt: String): GeminiTextResult
}

/**
 * Turns a catalogue record into a Book Intelligence overview in the reader's output language.
 *
 * ## What leaves the device
 *
 * The prompt built by [BookIntelOverviewPrompt] — the book's title, author, publisher, date,
 * subjects, page count and the catalogue's own description. **The document is never sent**, no
 * excerpt of it is sent, and the narration session is not involved: this is a separate, one-shot
 * text call that happens after an import and never while audio is being produced.
 *
 * ## Failure is expected and harmless
 *
 * A missing key, an offline device, a rate limit or a model that the key cannot use all end in
 * `null`. The book is still imported, still readable and still shows its source-backed facts; only
 * the generated paragraph is absent. Nothing here is retried on a loop.
 */
class BookIntelOverviewGenerator(
    private val transport: GeminiTextTransport,
    private val model: String = DEFAULT_MODEL,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    suspend fun generate(
        apiKey: String?,
        metadata: BookMetadata,
        language: String,
    ): BookIntelOverview? {
        val key = apiKey?.trim().orEmpty()
        // An unconfigured install makes no request at all: there is nothing to authenticate with,
        // and a call without a key would only produce a failure the reader cannot act on.
        if (key.isEmpty() || language.isBlank()) return null
        val prompt = BookIntelOverviewPrompt.build(metadata, targetLanguage = language)
        val answer = request(key, prompt) ?: return null
        return store(language = language, answer = answer, contextFingerprint = "")
    }

    /**
     * Generates the overview for a book **no catalogue identified**, from its own signals and the
     * findings of a bounded web search.
     *
     * ## Why an empty context generates nothing
     *
     * The product rule is that the Book Intelligence section is source-backed or absent. With no
     * catalogue record and no search finding there is nothing to restate and nothing to summarise —
     * only a title and a file name — and a paragraph generated from those would be invented
     * content wearing the label "generated from what is known". So this returns null and the card
     * simply keeps showing what the book's own signals support. The prompt's own rules would not
     * stop a model from producing something plausible; not asking is what stops it.
     *
     * The context is carried into the cache key by its fingerprint, so a text generated for one
     * question is never served as the answer to a different one.
     */
    suspend fun generateFromContext(
        apiKey: String?,
        signals: BookSignals,
        context: BookSearchContext,
        language: String,
    ): BookIntelOverview? {
        val key = apiKey?.trim().orEmpty()
        if (key.isEmpty() || language.isBlank()) return null
        if (context.isEmpty) return null
        val prompt = BookIntelOverviewPrompt.buildFromContext(signals, context, targetLanguage = language)
        val answer = request(key, prompt) ?: return null
        return store(
            language = language,
            answer = answer,
            contextFingerprint = BookSearchPrompt.fingerprint(signals),
        )
    }

    /** One `generateContent` call, with every failure already reduced to null. */
    private suspend fun request(key: String, prompt: String): GeneratedOverview? {
        val result = try {
            transport.generate(key, model, prompt)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            GeminiTextResult.Failed(MetadataUnavailable.SERVER_ERROR)
        }
        return when (result) {
            is GeminiTextResult.Failed -> null
            is GeminiTextResult.Text -> result.value
        }
    }

    /**
     * The one place an answer becomes a stored overview.
     *
     * The answer is re-sanitized here even though [BookIntelOverviewPrompt.parse] already did: this
     * generator is the boundary that decides what may be stored, and a transport that bypassed
     * `parse` must not be able to persist prose the rule would have rejected.
     */
    private fun store(
        language: String,
        answer: GeneratedOverview,
        contextFingerprint: String,
    ): BookIntelOverview? {
        val text = BookIntelOverviewPrompt.sanitize(answer.text) ?: return null
        return BookIntelOverview(
            language = language,
            text = text,
            model = model,
            generatedAt = clock(),
            themes = answer.themes,
            contextFingerprint = contextFingerprint,
        )
    }

    companion object {
        /**
         * The text model used for the overview.
         *
         * Deliberately **not** the Reader's narration model: that one is a Live model, built for
         * streaming audio and not for `generateContent`. This is a plain text model.
         *
         * It is a constant rather than a setting because a wrong value here degrades to "no
         * overview" and nothing else — see the class doc. The name is confirmed against the live
         * API as a device-verification item, not assumed from documentation.
         */
        const val DEFAULT_MODEL = "models/gemini-2.0-flash"

        /** The documented `generateContent` method on the Generative Language API. */
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta"
    }
}

/**
 * The production transport.
 *
 * The key travels in the `x-goog-api-key` header, never as a `?key=` query parameter, so it cannot
 * end up in a URL log — the same rule [com.voxora.core.usage.GeminiHttpProbeTransport] follows.
 */
class GeminiHttpTextTransport(
    private val client: OkHttpClient = defaultClient(),
    /** Overridable so the request can be pinned against a local server instead of Google. */
    private val endpoint: String = BookIntelOverviewGenerator.ENDPOINT,
) : GeminiTextTransport {

    override suspend fun generate(
        apiKey: String,
        model: String,
        prompt: String,
    ): GeminiTextResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt)),
                    ),
                ),
            )
            // Low temperature: this is a restatement of supplied facts, not a creative task, and
            // the output is cached and shown as if it were knowledge.
            // JSON output is requested so the prose and the localized subject headings arrive as
            // separate fields. It is a request, not an assumption: `parse` still accepts a plain
            // prose answer, because a usable overview matters more than the shape it arrives in.
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("maxOutputTokens", 700)
                    .put("responseMimeType", JSON),
            )
            .toString()
        val request = Request.Builder()
            .url("$endpoint/$model:generateContent")
            .header("Content-Type", JSON)
            .header("Accept", JSON)
            .header("x-goog-api-key", apiKey)
            .post(body.toRequestBody(JSON.toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> GeminiTextResult.Failed(MetadataUnavailable.RATE_LIMITED)
                    response.isSuccessful -> {
                        val text = BookIntelOverviewPrompt.parse(response.body?.string())
                        if (text == null) {
                            GeminiTextResult.Failed(MetadataUnavailable.PARSE_ERROR)
                        } else {
                            GeminiTextResult.Text(text)
                        }
                    }
                    else -> GeminiTextResult.Failed(MetadataUnavailable.SERVER_ERROR)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SocketTimeoutException) {
            GeminiTextResult.Failed(MetadataUnavailable.TIMEOUT)
        } catch (_: IOException) {
            GeminiTextResult.Failed(MetadataUnavailable.OFFLINE)
        } catch (_: Exception) {
            GeminiTextResult.Failed(MetadataUnavailable.SERVER_ERROR)
        }
    }

    private companion object {
        const val JSON = "application/json"

        /**
         * A tighter budget than the catalogue client: this call is optional, so a slow answer is
         * worse than no answer.
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
