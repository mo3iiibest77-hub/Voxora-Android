package com.voxora.core.usage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * One day of Voxora's own observed usage, as a chart point.
 *
 * [requests] is zero for a day on which Voxora made no request. That is a measured zero rather than
 * a missing value: the ledger records every request the app makes, so a day with no record had no
 * requests. It is deliberately **not** a quota or a limit — nothing here is compared against a
 * number Google never reported.
 */
data class UsagePoint(
    /** ISO-8601 UTC date, e.g. `2026-09-18`. */
    val day: String,
    val requests: Int,
    val totalTokens: Long,
)

/**
 * Builds a chart series from the observed-usage ledger.
 *
 * The series is the only thing the dashboard draws, and it is derived from the ledger rather than
 * from a second store: the ledger already holds one timestamped row per active UTC day, so a chart
 * over those rows is a view of data Voxora actually recorded. Nothing is interpolated and no value
 * is invented — a day with no row is a zero, which is the truth for an observed count.
 *
 * Pure JVM so the window arithmetic is unit-testable.
 */
object UsageSeries {

    /**
     * [dayCount] points, oldest first, ending on the UTC day containing [atMillis].
     *
     * Returns an empty list for a non-positive window, so a caller that has no period to show gets
     * nothing to draw rather than a misleading empty axis.
     */
    fun daily(ledger: GeminiUsageLedger?, atMillis: Long, dayCount: Int): List<UsagePoint> {
        val span = dayCount.coerceAtLeast(0)
        if (span == 0) return emptyList()
        val end = Instant.ofEpochMilli(atMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return (span - 1 downTo 0).map { offset ->
            val date = end.minusDays(offset.toLong())
            val day = ledger?.day(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            UsagePoint(
                day = date.toString(),
                requests = day?.requests ?: 0,
                totalTokens = day?.totalTokens ?: 0L,
            )
        }
    }

    /** The UTC date of a series point, or null when the stored label is not a date. */
    fun dateOf(point: UsagePoint): LocalDate? = runCatching { LocalDate.parse(point.day) }.getOrNull()
}
