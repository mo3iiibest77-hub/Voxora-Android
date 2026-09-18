package com.voxora.core.cloud

import org.json.JSONObject

/**
 * Authorized reads of Google Cloud, behind one port.
 *
 * Implemented once over OkHttp in this module and faked in tests, so the discovery and selection
 * logic can be exercised without a real Google account, a real project or real credentials.
 *
 * Every method takes the caller's **access token** rather than reading one from storage: the token
 * is a short-lived bearer credential and this interface deliberately has no way to persist it.
 */
interface GoogleCloudDirectory {

    /** Projects the account can actually access, via the Resource Manager API. */
    suspend fun listProjects(accessToken: String): CloudResult<List<CloudProject>>

    /** Key **metadata** in [projectId], via the API Keys API. Never returns key secrets. */
    suspend fun listKeys(accessToken: String, projectId: String): CloudResult<List<CloudApiKey>>

    /** Project-level usage Google is willing to report for [projectId]. */
    suspend fun projectUsage(accessToken: String, projectId: String): CloudResult<CloudProjectUsage>
}

/**
 * Pure-JVM parsing of the three Google Cloud payloads.
 *
 * Kept separate from the HTTP client so every shape — including the malformed and the empty one —
 * is unit-testable without network access. A parser returns `null` when the body is not the shape
 * it expects, which the caller turns into an honest failure rather than a zero.
 */
object CloudParsers {

    /** Reads a Resource Manager `ListProjectsResponse`. */
    fun projects(body: String?): List<CloudProject>? {
        val array = rootObject(body)?.optJSONArray("projects") ?: return null
        val result = mutableListOf<CloudProject>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            CloudProject.from(item)?.let(result::add)
        }
        return result
    }

    /** Reads an API Keys `ListKeysResponse`. */
    fun keys(body: String?): List<CloudApiKey>? {
        val array = rootObject(body)?.optJSONArray("keys") ?: return null
        val result = mutableListOf<CloudApiKey>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            CloudApiKey.from(item)?.let(result::add)
        }
        return result
    }

    /**
     * Sums every point of a Cloud Monitoring `ListTimeSeriesResponse`.
     *
     * The request count is a delta metric, so the points are additive. A response with no
     * `timeSeries` is a real "zero requests in this window" answer and is reported as `0`; a body
     * that is not JSON at all is `null`, which is not the same thing and must not become a zero.
     */
    fun requestCount(body: String?): Long? {
        val root = rootObject(body) ?: return null
        val series = root.optJSONArray("timeSeries") ?: return 0L
        var total = 0L
        for (index in 0 until series.length()) {
            val points = series.optJSONObject(index)?.optJSONArray("points") ?: continue
            for (pointIndex in 0 until points.length()) {
                val value = points.optJSONObject(pointIndex)?.optJSONObject("value") ?: continue
                total += value.longValue()
            }
        }
        return total
    }

    /**
     * Reads a per-day quota limit from a Cloud Quotas `ListQuotaInfosResponse`.
     *
     * Quota dimensions carry the numeric limit in `dimensionsInfos[].details.value`. The first
     * quota that names a generative-content metric wins; when none does, the answer is `null`
     * rather than a guess.
     */
    fun quotaLimit(body: String?): Long? {
        val root = rootObject(body) ?: return null
        val infos = root.optJSONArray("quotaInfos") ?: return null
        for (index in 0 until infos.length()) {
            val info = infos.optJSONObject(index) ?: continue
            val id = (info.optString("quotaId") + " " + info.optString("metric")).lowercase()
            if (!id.contains("generatecontent") && !id.contains("generate_content")) continue
            val dimensions = info.optJSONArray("dimensionsInfos") ?: continue
            for (dimensionIndex in 0 until dimensions.length()) {
                val value = dimensions.optJSONObject(dimensionIndex)
                    ?.optJSONObject("details")
                    ?.optString("value")
                    ?.toLongOrNull()
                if (value != null) return value
            }
        }
        return null
    }

    /** Reads an integer-ish Monitoring value, accepting both `int64Value` and `doubleValue`. */
    private fun JSONObject.longValue(): Long =
        optString("int64Value").toLongOrNull()
            ?: optDouble("doubleValue").takeIf { !it.isNaN() }?.toLong()
            ?: 0L

    /** Parses a body into a JSON object, or null when it is absent or not JSON. */
    private fun rootObject(body: String?): JSONObject? {
        val text = body?.trim().orEmpty()
        if (text.isEmpty()) return null
        return try {
            JSONObject(text)
        } catch (_: Exception) {
            null
        }
    }
}
