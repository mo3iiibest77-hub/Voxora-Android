package com.voxora.core.cloud

import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The production [GoogleCloudDirectory], over official Google Cloud REST APIs.
 *
 * ## Endpoints, and why each one
 * - **Resource Manager v1** `projects.list` — the documented way to discover projects the caller
 *   can access. It returns only what the account is actually allowed to see, which is exactly the
 *   honesty requirement: Voxora cannot list a project the user has no access to, and does not try.
 * - **API Keys v1** `projects.locations.keys.list` — key **metadata**. Google does not return the
 *   key secret from `list` or `get`; the only documented way to read it is `keys.getKeyString`,
 *   which needs a separate permission and is deliberately not called here.
 * - **Cloud Monitoring v3** `timeSeries` — the one usage figure Google exposes per project,
 *   `serviceruntime.googleapis.com/api/request_count`.
 * - **Cloud Quotas v1** `quotaInfos` — per-service quota limits, read as a second, independent
 *   call so a quota failure cannot turn a real request count into a failure.
 *
 * ## Credential handling
 * The access token travels in the `Authorization: Bearer` header, never in a URL, and is never
 * logged. This class holds no token of its own: every call takes one, so there is nothing here to
 * persist or leak. Nothing in this file writes a credential, a request URL or a response body to
 * any log.
 */
class GoogleCloudHttpDirectory(
    private val client: OkHttpClient = defaultClient(),
    private val endpoints: CloudEndpoints = CloudEndpoints.PRODUCTION,
    /** How far back the project request count reaches. */
    private val usageWindowDays: Long = 30L,
    /** Supplies "now" so the monitoring window stays deterministic in tests. */
    private val clock: () -> Instant = { Instant.now() },
) : GoogleCloudDirectory {

    override suspend fun listProjects(accessToken: String): CloudResult<List<CloudProject>> =
        get(
            url = url(endpoints.resourceManagerBase, "v1/projects"),
            accessToken = accessToken,
            parse = CloudParsers::projects,
        )

    override suspend fun listKeys(
        accessToken: String,
        projectId: String,
    ): CloudResult<List<CloudApiKey>> =
        get(
            url = url(endpoints.apiKeysBase, "v1/projects/$projectId/locations/global/keys"),
            accessToken = accessToken,
            parse = CloudParsers::keys,
        )

    override suspend fun projectUsage(
        accessToken: String,
        projectId: String,
    ): CloudResult<CloudProjectUsage> {
        val monitoring = get(
            url = requestCountUrl(projectId),
            accessToken = accessToken,
            parse = CloudParsers::requestCount,
        )
        return when (monitoring) {
            is CloudResult.Success -> {
                // A quota failure must not discard a real request count, so the two reads are
                // independent and quota is allowed to be absent.
                val quota = get(
                    url = url(
                        endpoints.quotasBase,
                        "v1/projects/$projectId/locations/global/services/" +
                            "generativelanguage.googleapis.com/quotaInfos",
                    ),
                    accessToken = accessToken,
                    parse = CloudParsers::quotaLimit,
                )
                CloudResult.Success(
                    CloudProjectUsage.observed(
                        projectId = projectId,
                        requests = monitoring.value,
                        quotaLimit = quota.valueOrNull,
                    ),
                )
            }
            CloudResult.NotAuthorized -> CloudResult.NotAuthorized
            CloudResult.PermissionDenied -> CloudResult.PermissionDenied
            CloudResult.NetworkUnavailable -> CloudResult.NetworkUnavailable
            is CloudResult.Failed -> CloudResult.Failed(monitoring.statusCode)
        }
    }

    /**
     * One authorized GET, with the HTTP status mapped onto [CloudResult].
     *
     * `401` and `403` are kept apart because they mean different things to the user: `401` means
     * the grant is gone and re-authorization is needed, `403` means the account is fine but not
     * allowed to read this project. A `2xx` whose body does not parse is a failure, never a zero.
     */
    private suspend fun <T> get(
        url: String,
        accessToken: String,
        parse: (String?) -> T?,
    ): CloudResult<T> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 -> CloudResult.NotAuthorized
                    response.code == 403 -> CloudResult.PermissionDenied
                    response.isSuccessful -> {
                        val parsed = parse(response.body?.string())
                        if (parsed == null) CloudResult.Failed(response.code) else CloudResult.Success(parsed)
                    }
                    else -> CloudResult.Failed(response.code)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            CloudResult.NetworkUnavailable
        } catch (_: Exception) {
            CloudResult.Failed(null)
        }
    }

    private fun requestCountUrl(projectId: String): String {
        val end = clock()
        val start = end.minus(usageWindowDays, ChronoUnit.DAYS)
        val windowSeconds = TimeUnit.DAYS.toSeconds(usageWindowDays)
        return requireUrl(endpoints.monitoringBase)
            .newBuilder()
            .addPathSegments("v3/projects/$projectId/timeSeries")
            .addQueryParameter(
                "filter",
                "metric.type=\"serviceruntime.googleapis.com/api/request_count\" " +
                    "AND resource.labels.service=\"generativelanguage.googleapis.com\"",
            )
            .addQueryParameter("interval.startTime", start.toString())
            .addQueryParameter("interval.endTime", end.toString())
            .addQueryParameter("aggregation.alignmentPeriod", "${windowSeconds}s")
            .addQueryParameter("aggregation.perSeriesAligner", "ALIGN_SUM")
            .build()
            .toString()
    }

    private fun url(base: String, path: String): String =
        requireUrl(base).newBuilder().addPathSegments(path).build().toString()

    private fun requireUrl(base: String): HttpUrl =
        base.toHttpUrlOrNull() ?: error("Invalid Cloud endpoint: $base")

    companion object {
        /** Resource Manager projects list. Paginated; the first page is the visible set. */
        const val PROJECTS_URL = "https://cloudresourcemanager.googleapis.com/v1/projects"

        private const val TIMEOUT_SECONDS = 20L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
            .build()
    }
}
