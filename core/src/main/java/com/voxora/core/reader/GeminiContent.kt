package com.voxora.core.reader

import org.json.JSONException
import org.json.JSONObject

/**
 * The parts of a `generateContent` response both Gemini callers need: the answer's text, and the
 * web sources the model grounded it in.
 *
 * Shared rather than written twice because the two calls read the same response shape and must agree
 * on what "the model said nothing" means. A missing candidate list, a safety block and a malformed
 * document are all *no answer*; none of them is an empty answer, and none of them may be turned into
 * one — "the model said nothing" and "the model said something blank" have to stay different.
 *
 * Pure JVM, apart from `org.json`, which Android provides.
 */
internal object GeminiContent {

    /** The concatenated candidate text of a response body, or null when there is none. */
    fun text(responseBody: String?): String? {
        val root = root(responseBody) ?: return null
        val candidates = root.optJSONArray("candidates") ?: return null
        val text = StringBuilder()
        for (i in 0 until candidates.length()) {
            val candidate = candidates.optJSONObject(i) ?: continue
            val content = candidate.optJSONObject("content") ?: continue
            val parts = content.optJSONArray("parts") ?: continue
            for (p in 0 until parts.length()) {
                val part = parts.optJSONObject(p) ?: continue
                val chunk = part.optString("text", "")
                if (chunk.isNotEmpty()) text.append(chunk)
            }
        }
        return text.toString().takeIf { it.isNotBlank() }
    }

    /**
     * The web sources a grounded answer cited, in the order the model cited them.
     *
     * Read from `groundingMetadata.groundingChunks[].web`. Absent metadata is an empty list rather
     * than a failure: grounding is a capability of the call, and a model that answered without
     * citing anything still answered.
     */
    fun sources(responseBody: String?): List<BookSearchSource> {
        val root = root(responseBody) ?: return emptyList()
        val candidates = root.optJSONArray("candidates") ?: return emptyList()
        val sources = ArrayList<BookSearchSource>()
        for (i in 0 until candidates.length()) {
            val candidate = candidates.optJSONObject(i) ?: continue
            val metadata = candidate.optJSONObject("groundingMetadata") ?: continue
            val chunks = metadata.optJSONArray("groundingChunks") ?: continue
            for (c in 0 until chunks.length()) {
                val web = chunks.optJSONObject(c)?.optJSONObject("web") ?: continue
                val uri = web.optString("uri", "").trim()
                if (uri.isEmpty()) continue
                val title = web.optString("title", "").trim()
                if (sources.none { it.url == uri }) {
                    sources.add(BookSearchSource(title = title, url = uri))
                    if (sources.size >= BookSearchPrompt.MAX_SOURCES) return sources
                }
            }
        }
        return sources
    }

    private fun root(responseBody: String?): JSONObject? {
        if (responseBody.isNullOrBlank()) return null
        return try {
            JSONObject(responseBody)
        } catch (_: JSONException) {
            null
        }
    }
}
