package com.voxora.core.usage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.YearMonth
import org.json.JSONArray
import org.json.JSONObject

/**
 * What Voxora actually observed for one UTC day.
 *
 * These are **Voxora's own counts of requests it made**, not a quota reading. Nothing here is
 * fetched from Google, so it can never be mistaken for project-level usage — see
 * [ApiUsageState] for how the UI must present that distinction.
 *
 * [tokensReported] is the number of requests that included a `usageMetadata` object. It exists
 * so the UI can say "token counts were reported for N of M requests" instead of implying the
 * token totals are complete. The Live API only *may* return usage metadata, so this is a real
 * and expected state, not a failure.
 */
data class UsageDay(
    /** ISO-8601 UTC date, e.g. `2026-09-18`. */
    val day: String,
    val requests: Int = 0,
    val successes: Int = 0,
    val failures: Int = 0,
    val promptTokens: Long = 0,
    val responseTokens: Long = 0,
    val totalTokens: Long = 0,
    val tokensReported: Int = 0,
) {
    fun plusSuccess(usage: GeminiUsageMetadata?): UsageDay = copy(
        requests = requests + 1,
        successes = successes + 1,
        promptTokens = promptTokens + (usage?.promptTokens ?: 0),
        responseTokens = responseTokens + (usage?.responseTokens ?: 0),
        totalTokens = totalTokens + (usage?.totalTokens ?: 0),
        tokensReported = tokensReported + if (usage?.hasAny == true) 1 else 0,
    )

    fun plusFailure(): UsageDay = copy(requests = requests + 1, failures = failures + 1)
}

/** Aggregated observed usage for one UTC month. */
data class UsageMonth(
    val month: String,
    val requests: Int,
    val successes: Int,
    val failures: Int,
    val promptTokens: Long,
    val responseTokens: Long,
    val totalTokens: Long,
    val tokensReported: Int,
)

/**
 * Aggregated observed usage over a rolling window of UTC days.
 *
 * A rolling window rather than an ISO week because "the last seven days" is what a reader means by
 * "this week's usage", and because it cannot silently show a partial week at a month boundary. The
 * window is Voxora's own count of requests it made, exactly like [UsageMonth] — never a Google
 * quota reading.
 */
data class UsageWindow(
    /** How many days the window spans, including the day it ends on. */
    val days: Int,
    val requests: Int,
    val successes: Int,
    val failures: Int,
    val promptTokens: Long,
    val responseTokens: Long,
    val totalTokens: Long,
    val tokensReported: Int,
)

/**
 * A bounded, local record of the requests Voxora itself made.
 *
 * ## What it deliberately does not hold
 * No API keys, no ID tokens, no prompts, no document text, no audio, no raw responses. Only
 * counts, token totals the server reported, and timestamps. That is what makes it safe to keep
 * on disk and safe to display.
 *
 * ## Retention
 * Only the most recent [MAX_DAYS] days are kept. The ledger is bounded by construction, so it
 * cannot grow without limit no matter how long the app is installed.
 *
 * Pure JVM (`java.time` only, no `android.*`) so aggregation, retention and the JSON round-trip
 * are all unit-testable.
 */
class GeminiUsageLedger private constructor(
    private var days: MutableMap<String, UsageDay>,
    private var lastSuccessAtMillis: Long?,
    private var lastFailureAtMillis: Long?,
    private var lastErrorCategory: String?,
) {

    constructor() : this(mutableMapOf(), null, null, null)

    /** Records one successful request. [usage] is whatever the server reported, if anything. */
    fun recordSuccess(atMillis: Long, usage: GeminiUsageMetadata? = null) {
        val day = dayOf(atMillis)
        days[day] = (days[day] ?: UsageDay(day)).plusSuccess(usage)
        lastSuccessAtMillis = atMillis
        prune()
    }

    /**
     * Records one failed request.
     *
     * [category] is a short classification such as `network_unavailable` or `quota_limited` —
     * never a raw error body, which could contain a key echoed back by a proxy.
     */
    fun recordFailure(atMillis: Long, category: String?) {
        val day = dayOf(atMillis)
        days[day] = (days[day] ?: UsageDay(day)).plusFailure()
        lastFailureAtMillis = atMillis
        lastErrorCategory = category?.take(MAX_CATEGORY_CHARS)?.takeIf { it.isNotBlank() }
        prune()
    }

    val lastSuccess: Long? get() = lastSuccessAtMillis
    val lastFailure: Long? get() = lastFailureAtMillis
    val lastError: String? get() = lastErrorCategory

    /** Days with any recorded activity, newest first. */
    fun recentDays(limit: Int = MAX_DAYS): List<UsageDay> =
        days.values.sortedByDescending { it.day }.take(limit.coerceAtLeast(0))

    fun day(atMillis: Long): UsageDay? = days[dayOf(atMillis)]

    /** Observed usage across the UTC month containing [atMillis]. */
    fun month(atMillis: Long): UsageMonth {
        val target = YearMonth.from(Instant.ofEpochMilli(atMillis).atZone(ZoneOffset.UTC))
        val matching = days.values.filter { runCatching { YearMonth.from(LocalDate.parse(it.day)) }.getOrNull() == target }
        return UsageMonth(
            month = target.toString(),
            requests = matching.sumOf { it.requests },
            successes = matching.sumOf { it.successes },
            failures = matching.sumOf { it.failures },
            promptTokens = matching.sumOf { it.promptTokens },
            responseTokens = matching.sumOf { it.responseTokens },
            totalTokens = matching.sumOf { it.totalTokens },
            tokensReported = matching.sumOf { it.tokensReported },
        )
    }

    /** Total observed requests ever recorded, within the retention window. */
    fun totalRequests(): Int = days.values.sumOf { it.requests }

    /**
     * Observed usage over the [dayCount] UTC days ending on the day that contains [atMillis].
     *
     * A day with no activity is not stored, so it contributes nothing rather than being treated as
     * a gap: the window is a sum over the days that were recorded. That is a real zero for a day on
     * which Voxora made no request, which is exactly what an observed count means.
     */
    fun window(atMillis: Long, dayCount: Int): UsageWindow {
        val span = dayCount.coerceAtLeast(0)
        if (span == 0) return UsageWindow(0, 0, 0, 0, 0L, 0L, 0L, 0)
        val end = Instant.ofEpochMilli(atMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val start = end.minusDays((span - 1).toLong())
        val matching = days.values.filter { stored ->
            val date = runCatching { LocalDate.parse(stored.day) }.getOrNull()
            date != null && !date.isBefore(start) && !date.isAfter(end)
        }
        return UsageWindow(
            days = span,
            requests = matching.sumOf { it.requests },
            successes = matching.sumOf { it.successes },
            failures = matching.sumOf { it.failures },
            promptTokens = matching.sumOf { it.promptTokens },
            responseTokens = matching.sumOf { it.responseTokens },
            totalTokens = matching.sumOf { it.totalTokens },
            tokensReported = matching.sumOf { it.tokensReported },
        )
    }

    /**
     * An independent copy.
     *
     * [recordSuccess]/[recordFailure] mutate this instance in place, so a `StateFlow` holding it
     * would never emit a new value. Publishing a snapshot gives collectors a stable object that
     * later records cannot change underneath them.
     */
    fun snapshot(): GeminiUsageLedger = GeminiUsageLedger(
        days = days.mapValuesTo(mutableMapOf()) { it.value },
        lastSuccessAtMillis = lastSuccessAtMillis,
        lastFailureAtMillis = lastFailureAtMillis,
        lastErrorCategory = lastErrorCategory,
    )

    fun clear() {
        days.clear()
        lastSuccessAtMillis = null
        lastFailureAtMillis = null
        lastErrorCategory = null
    }

    private fun dayOf(atMillis: Long): String =
        Instant.ofEpochMilli(atMillis).atZone(ZoneOffset.UTC).toLocalDate().toString()

    private fun prune() {
        if (days.size <= MAX_DAYS) return
        val keep = days.keys.sortedDescending().take(MAX_DAYS).toSet()
        days.keys.retainAll(keep)
    }

    // ---- persistence ----------------------------------------------------------------

    /**
     * Serialises the ledger.
     *
     * The format is versioned so a future change can migrate rather than corrupt, and so an
     * unreadable payload can be discarded safely instead of crashing the dashboard.
     */
    fun toJson(): String {
        val array = JSONArray()
        recentDays().forEach { day ->
            array.put(
                JSONObject()
                    .put("day", day.day)
                    .put("requests", day.requests)
                    .put("successes", day.successes)
                    .put("failures", day.failures)
                    .put("promptTokens", day.promptTokens)
                    .put("responseTokens", day.responseTokens)
                    .put("totalTokens", day.totalTokens)
                    .put("tokensReported", day.tokensReported),
            )
        }
        return JSONObject()
            .put("version", FORMAT_VERSION)
            .put("lastSuccessAt", lastSuccessAtMillis ?: JSONObject.NULL)
            .put("lastFailureAt", lastFailureAtMillis ?: JSONObject.NULL)
            .put("lastErrorCategory", lastErrorCategory ?: JSONObject.NULL)
            .put("days", array)
            .toString()
    }

    companion object {
        const val FORMAT_VERSION = 1

        /** 90 days: enough for a monthly view with headroom, and a hard bound on growth. */
        const val MAX_DAYS = 90

        private const val MAX_CATEGORY_CHARS = 48

        /**
         * Restores a ledger from [text].
         *
         * Any unreadable, truncated, future-versioned or partially corrupt payload yields an
         * empty ledger rather than an exception: usage history is a convenience, and losing it
         * must never stop the user reaching Settings. Rows that cannot be parsed are skipped
         * individually so one bad row does not discard the rest.
         */
        fun fromJson(text: String?): GeminiUsageLedger {
            if (text.isNullOrBlank()) return GeminiUsageLedger()
            return try {
                val root = JSONObject(text)
                if (root.optInt("version", 0) > FORMAT_VERSION) return GeminiUsageLedger()
                val days = mutableMapOf<String, UsageDay>()
                val array = root.optJSONArray("days") ?: JSONArray()
                for (index in 0 until array.length()) {
                    val row = array.optJSONObject(index) ?: continue
                    val day = row.optString("day").takeIf { it.isNotBlank() } ?: continue
                    if (runCatching { LocalDate.parse(day) }.isFailure) continue
                    days[day] = UsageDay(
                        day = day,
                        requests = row.optInt("requests").coerceAtLeast(0),
                        successes = row.optInt("successes").coerceAtLeast(0),
                        failures = row.optInt("failures").coerceAtLeast(0),
                        promptTokens = row.optLong("promptTokens").coerceAtLeast(0),
                        responseTokens = row.optLong("responseTokens").coerceAtLeast(0),
                        totalTokens = row.optLong("totalTokens").coerceAtLeast(0),
                        tokensReported = row.optInt("tokensReported").coerceAtLeast(0),
                    )
                }
                GeminiUsageLedger(
                    days = days,
                    lastSuccessAtMillis = root.longOrNull("lastSuccessAt"),
                    lastFailureAtMillis = root.longOrNull("lastFailureAt"),
                    lastErrorCategory = root.optString("lastErrorCategory").takeIf { it.isNotBlank() },
                ).also { it.prune() }
            } catch (_: Exception) {
                GeminiUsageLedger()
            }
        }

        private fun JSONObject.longOrNull(key: String): Long? {
            if (!has(key) || isNull(key)) return null
            val value = opt(key)
            return when (value) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
        }
    }
}
