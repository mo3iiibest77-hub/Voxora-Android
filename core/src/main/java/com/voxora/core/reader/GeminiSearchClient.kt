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
 * The result of one grounded search.
 *
 * A failure is a value, not an exception, for the same reason the catalogue lookups are: this is an
 * optional enhancement, and nothing about it may reach the reading path as an error.
 */
sealed class GeminiSearchResult {
    data class Found(
        val summary: String,
        val sources: List<BookSearchSource> = emptyList(),
    ) : GeminiSearchResult()

    data class Failed(val reason: MetadataUnavailable) : GeminiSearchResult()
}

/**
 * Performs one web-grounded `generateContent` call.
 *
 * Abstracted so the search's decisions — when it runs, what is accepted as a finding, what is
 * discarded — are testable without a network, exactly as [BookMetadataSource] is for the catalogues.
 *
 * Implementations must never log, persist or embed the API key in an exception message.
 */
interface GeminiSearchTransport {
    suspend fun search(apiKey: String, model: String, prompt: String): GeminiSearchResult
}

/**
 * The third identification stage: a bounded web search for a book the catalogues could not identify.
 *
 * ## Why a Gemini call rather than a search provider
 *
 * Voxora already holds a Gemini API key and already makes `generateContent` calls for the overview.
 * The Generative Language API supports **Google Search grounding** as a tool on that same call, so a
 * contextual search needs no new credential, no new endpoint and no new vendor — and the key never
 * travels to a destination that did not already receive it. Inventing a search provider, or scraping
 * a search page, would both add a credential the product does not have and a parsing surface that
 * breaks whenever the page changes.
 *
 * ## What leaves the device
 *
 * Only the query built from the book's own signals — the title, author, ISBN and file name the
 * extraction already produced. **The document is never sent**, no excerpt of it is sent, and the
 * narration session is not involved.
 *
 * ## Failure is expected and harmless
 *
 * A missing key, an offline device, a rate limit, a model that cannot use the tool and a search that
 * finds nothing all end in `null`. The book is still imported, still readable and still narratable;
 * only the generated paragraph is absent. Nothing here is retried on a loop.
 */
class GeminiGroundedBookSearch(
    private val transport: GeminiSearchTransport,
    private val model: String = BookIntelOverviewGenerator.DEFAULT_MODEL,
) : BookContextSource {

    override suspend fun search(signals: BookSignals, apiKey: String?): BookSearchContext? {
        val key = apiKey?.trim().orEmpty()
        // An unconfigured install makes no request at all: there is nothing to authenticate with,
        // and a call without a key would only produce a failure the reader cannot act on.
        if (key.isEmpty()) return null
        if (!signals.hasDocumentSignal && signals.filename.isBlank()) return null
        val result = try {
            transport.search(key, model, BookSearchPrompt.build(signals))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            GeminiSearchResult.Failed(MetadataUnavailable.SERVER_ERROR)
        }
        val found = when (result) {
            is GeminiSearchResult.Failed -> return null
            is GeminiSearchResult.Found -> result
        }
        // The answer is re-read here even though the transport already parsed it: this is the
        // boundary that decides what may be used, and a transport that bypassed the rules must not
        // be able to feed unvetted text to the overview prompt.
        return BookSearchPrompt.parse(
            query = BookSearchPrompt.query(signals),
            answer = found.summary,
            sources = found.sources,
        )
    }
}

/**
 * The production transport: one `generateContent` call with Google Search grounding enabled.
 *
 * The key travels in the `x-goog-api-key` header, never as a `?key=` query parameter, so it cannot
 * end up in a URL log — the same rule [GeminiHttpTextTransport] follows.
 *
 * No `responseMimeType` is requested, unlike the overview call: the answer here is prose that a
 * search produced, and forcing JSON output alongside a grounding tool would constrain a call whose
 * value is the search itself. The overview call, which runs afterwards, asks for JSON as before.
 */
class GeminiHttpSearchTransport(
    private val client: OkHttpClient = defaultClient(),
    /** Overridable so the request can be pinned against a local server instead of Google. */
    private val endpoint: String = BookIntelOverviewGenerator.ENDPOINT,
) : GeminiSearchTransport {

    override suspend fun search(
        apiKey: String,
        model: String,
        prompt: String,
    ): GeminiSearchResult = withContext(Dispatchers.IO) {
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
            // The documented grounding tool. This is what makes the call a *search* rather than a
            // recollection: the model answers from results it retrieved, and cites them back in
            // `groundingMetadata`.
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            // Zero temperature: the answer is evidence to be reported, not prose to be composed.
            .put("generationConfig", JSONObject().put("temperature", 0.0).put("maxOutputTokens", 400))
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
                    response.code == 429 -> GeminiSearchResult.Failed(MetadataUnavailable.RATE_LIMITED)
                    response.isSuccessful -> {
                        val payload = response.body?.string()
                        val text = GeminiContent.text(payload)
                        if (text == null) {
                            GeminiSearchResult.Failed(MetadataUnavailable.PARSE_ERROR)
                        } else {
                            GeminiSearchResult.Found(text, GeminiContent.sources(payload))
                        }
                    }
                    else -> GeminiSearchResult.Failed(MetadataUnavailable.SERVER_ERROR)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SocketTimeoutException) {
            GeminiSearchResult.Failed(MetadataUnavailable.TIMEOUT)
        } catch (_: IOException) {
            GeminiSearchResult.Failed(MetadataUnavailable.OFFLINE)
        } catch (_: Exception) {
            GeminiSearchResult.Failed(MetadataUnavailable.SERVER_ERROR)
        }
    }

    private companion object {
        const val JSON = "application/json"

        /**
         * A tighter budget than the catalogue client: the search is optional, so a slow answer is
         * worse than no answer — and the overview call still has to run after it.
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
