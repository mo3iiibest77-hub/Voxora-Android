package com.voxora.app.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The recency rule behind "Last read …".
 *
 * The boundaries are the point: the label must never overstate how long ago something was read, and
 * a clock that has moved backwards must not produce a negative age.
 */
class ReaderRecencyTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val now = 1_700_000_000_000L

    @Test
    fun underAMinuteIsJustNow() {
        assertEquals(ReaderRecencyKind.JUST_NOW, readerRecencyOf(now, now).kind)
        assertEquals(ReaderRecencyKind.JUST_NOW, readerRecencyOf(now - minute + 1, now).kind)
    }

    @Test
    fun minutesAreReportedUntilAnHourHasPassed() {
        assertEquals(ReaderRecency(ReaderRecencyKind.MINUTES, 1), readerRecencyOf(now - minute, now))
        assertEquals(ReaderRecency(ReaderRecencyKind.MINUTES, 59), readerRecencyOf(now - 59 * minute, now))
        assertEquals(ReaderRecency(ReaderRecencyKind.HOURS, 1), readerRecencyOf(now - hour, now))
    }

    @Test
    fun hoursThenDaysThenWeeksThenMonths() {
        assertEquals(ReaderRecency(ReaderRecencyKind.HOURS, 23), readerRecencyOf(now - 23 * hour, now))
        assertEquals(ReaderRecency(ReaderRecencyKind.DAYS, 1), readerRecencyOf(now - day, now))
        assertEquals(ReaderRecency(ReaderRecencyKind.DAYS, 6), readerRecencyOf(now - 6 * day, now))
        assertEquals(ReaderRecency(ReaderRecencyKind.WEEKS, 1), readerRecencyOf(now - 7 * day, now))
        assertEquals(ReaderRecency(ReaderRecencyKind.WEEKS, 4), readerRecencyOf(now - 29 * day, now))
        assertEquals(ReaderRecency(ReaderRecencyKind.MONTHS, 1), readerRecencyOf(now - 30 * day, now))
    }

    @Test
    fun theCountRoundsDownRatherThanOverstating() {
        // 90 seconds is "1 minute", never "2 minutes".
        assertEquals(ReaderRecency(ReaderRecencyKind.MINUTES, 1), readerRecencyOf(now - 90_000L, now))
        // 90 minutes is "1 hour".
        assertEquals(ReaderRecency(ReaderRecencyKind.HOURS, 1), readerRecencyOf(now - 90 * minute, now))
    }

    @Test
    fun aClockThatMovedBackwardsReportsJustNowRatherThanANegativeAge() {
        assertEquals(ReaderRecencyKind.JUST_NOW, readerRecencyOf(now + 10 * day, now).kind)
    }
}
