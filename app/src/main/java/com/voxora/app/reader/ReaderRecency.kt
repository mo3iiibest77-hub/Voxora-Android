package com.voxora.app.reader

/**
 * How long ago a book was last read, as a *kind and a count* rather than a sentence.
 *
 * The split matters: the unit ("minutes", "days") and the number are both needed by the UI, and
 * neither belongs in a pure object. Keeping the label in `strings.xml` is what lets the same rule
 * render in English and Persian; keeping the arithmetic here is what makes it unit-testable.
 *
 * Pure JVM — no `android.*`, no `Context` — so the boundaries are pinned by a plain JUnit test
 * instead of by looking at a screen.
 */
internal enum class ReaderRecencyKind { JUST_NOW, MINUTES, HOURS, DAYS, WEEKS, MONTHS }

internal data class ReaderRecency(val kind: ReaderRecencyKind, val count: Int)

/**
 * The recency of [lastReadAt] relative to [now], both in wall-clock epoch millis.
 *
 * Boundaries are chosen so the label never overstates: something read 90 seconds ago is "1 minute",
 * not "2 minutes", and a clock that has moved backwards (a timezone or manual change) reports
 * "just now" rather than a negative age.
 */
internal fun readerRecencyOf(lastReadAt: Long, now: Long): ReaderRecency {
    val elapsedMinutes = ((now - lastReadAt).coerceAtLeast(0L)) / 60_000L
    return when {
        elapsedMinutes < 1 -> ReaderRecency(ReaderRecencyKind.JUST_NOW, 0)
        elapsedMinutes < 60 -> ReaderRecency(ReaderRecencyKind.MINUTES, elapsedMinutes.toInt())
        elapsedMinutes < 60 * 24 -> ReaderRecency(ReaderRecencyKind.HOURS, (elapsedMinutes / 60).toInt())
        elapsedMinutes < 60 * 24 * 7 -> ReaderRecency(ReaderRecencyKind.DAYS, (elapsedMinutes / (60 * 24)).toInt())
        elapsedMinutes < 60 * 24 * 30 -> ReaderRecency(ReaderRecencyKind.WEEKS, (elapsedMinutes / (60 * 24 * 7)).toInt())
        else -> ReaderRecency(ReaderRecencyKind.MONTHS, (elapsedMinutes / (60 * 24 * 30)).toInt())
    }
}
