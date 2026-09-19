package com.voxora.core.reader

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

/**
 * The shared transport rules for the bibliographic catalogues.
 *
 * ## Why these two sources share a file
 *
 * Both must fail the same way: a timeout, a quota rejection, an HTML error page and an unreachable
 * network have to be classified identically no matter which catalogue produced them, because the UI
 * tells the reader one story about it. Writing that classification once is what keeps them from
 * drifting apart.
 *
 * ## No credentials, ever
 *
 * These are **public** read-only endpoints. The request carries no API key, no authorization header
 * and nothing derived from the user's Gemini key — the rule that the Gemini key never travels to a
 * new destination applies here absolutely. Only the query built from the document's own signals
 * leaves the device, and the document's text is never sent.
 */
internal object MetadataHttp {

    /** Identifies Voxora to the catalogues; Open Library asks clients to send one. */
    const val USER_AGENT = "Voxora-Android/0.6.7 (Reader book identification)"

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 12L
    private const val CALL_TIMEOUT_SECONDS = 25L

    fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Performs one GET and hands the body to [parse].
     *
     * Runs on [Dispatchers.IO] so a caller can never accidentally issue a network call on the Main
     * thread, and converts every failure into a [MetadataResult] rather than an exception:
     *
     * - [IOException] is [MetadataUnavailable.OFFLINE], a socket timeout is
     *   [MetadataUnavailable.TIMEOUT] (checked first, because a timeout *is* an `IOException`);
     * - HTTP `429` is [MetadataUnavailable.RATE_LIMITED] and any other non-2xx is
     *   [MetadataUnavailable.SERVER_ERROR] — a quota rejection must not look like a broken network;
     * - a body that does not parse is [MetadataUnavailable.PARSE_ERROR], never an empty result, so
     *   "the catalogue sent something we could not read" is not reported as "no such book".
     *
     * Cancellation is rethrown, as it must be: it is the caller's own coroutine being cancelled, not
     * a catalogue failure.
     */
    suspend fun get(
        client: OkHttpClient,
        url: String,
        parse: (JSONObject) -> List<BookCandidate>,
    ): MetadataResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> MetadataResult.Unavailable(MetadataUnavailable.RATE_LIMITED)
                    response.isSuccessful -> {
                        val body = response.body?.string()
                        if (body.isNullOrBlank()) return@use MetadataResult.NotFound
                        val json = try {
                            JSONObject(body)
                        } catch (_: JSONException) {
                            return@use MetadataResult.Unavailable(MetadataUnavailable.PARSE_ERROR)
                        }
                        try {
                            MetadataResult.Found(parse(json))
                        } catch (_: JSONException) {
                            MetadataResult.Unavailable(MetadataUnavailable.PARSE_ERROR)
                        }
                    }
                    else -> MetadataResult.Unavailable(MetadataUnavailable.SERVER_ERROR)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SocketTimeoutException) {
            MetadataResult.Unavailable(MetadataUnavailable.TIMEOUT)
        } catch (_: IOException) {
            MetadataResult.Unavailable(MetadataUnavailable.OFFLINE)
        } catch (_: Exception) {
            MetadataResult.Unavailable(MetadataUnavailable.SERVER_ERROR)
        }
    }

    /** A string field that is absent, JSON-null or blank becomes null rather than `"null"`. */
    fun optionalString(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.optString(key, "").trim().ifEmpty { null }
    }

    /** A positive integer field, or null. */
    fun optionalPositiveInt(json: JSONObject, key: String): Int? {
        if (!json.has(key) || json.isNull(key)) return null
        val value = json.optInt(key, 0)
        return value.takeIf { it > 0 }
    }

    /** A list of non-blank strings from an array field; anything else yields an empty list. */
    fun stringList(json: JSONObject, key: String): List<String> {
        val array = json.optJSONArray(key) ?: return emptyList()
        val values = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val value = array.optString(i, "").trim()
            if (value.isNotEmpty()) values.add(value)
        }
        return values
    }
}
