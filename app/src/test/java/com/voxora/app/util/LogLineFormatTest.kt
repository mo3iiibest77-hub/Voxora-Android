package com.voxora.app.util

import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract for the one line shape a log entry renders as.
 *
 * This matters beyond tidiness: the header's Copy-all, the Share payload and a single entry
 * copied out of the list must agree, and the only way to guarantee that is for all three to come
 * from the same rule. The tests below pin the shape itself, so a change to it is a deliberate
 * decision rather than a side effect of editing one call site.
 *
 * The timestamp is rendered in the device's own zone, so the tests pin UTC and restore the
 * previous zone afterwards; without that the expected string would depend on where the test ran.
 */
class LogLineFormatTest {

    private lateinit var previousZone: TimeZone

    /** 2023-11-14T22:13:20.000Z and the same instant six milliseconds later. */
    private val wholeSecond = 1_700_000_000_000L
    private val sixMillisLater = 1_700_000_000_006L

    @Before
    fun pinTimeZone() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(previousZone)
    }

    @Test
    fun aLineIsTimestampThenLevelThenTagThenMessage() {
        assertEquals(
            "22:13:20.000 INFO  [Reader] Document loaded",
            LogLineFormat.format(wholeSecond, "INFO", "Reader", "Document loaded"),
        )
    }

    @Test
    fun theTimestampCarriesMilliseconds() {
        assertEquals(
            "22:13:20.006 ERROR [Reader] Narration failed",
            LogLineFormat.format(sixMillisLater, "ERROR", "Reader", "Narration failed"),
        )
    }

    /**
     * The level is padded to a fixed width, so the tag starts in the same column on every line.
     * Without it a `DEBUG` line would push its message five characters further left than an
     * `ERROR` line and the list would stop scanning as a column.
     */
    @Test
    fun everyLevelOccupiesTheSameWidth() {
        val lines = VoxoraLog.Level.entries.map { level ->
            LogLineFormat.format(wholeSecond, level.name, "Reader", "message")
        }
        val tagColumns = lines.map { it.indexOf("[Reader]") }.toSet()
        assertEquals("the tag must start at one column", 1, tagColumns.size)
        assertTrue("and not at the start of the line", tagColumns.single() > 0)
    }

    @Test
    fun aShortLevelIsPaddedAndAFullWidthOneIsNot() {
        assertEquals(
            "22:13:20.000 WARN  [Reader] m",
            LogLineFormat.format(wholeSecond, "WARN", "Reader", "m"),
        )
        assertEquals(
            "22:13:20.000 DEBUG [Reader] m",
            LogLineFormat.format(wholeSecond, "DEBUG", "Reader", "m"),
        )
    }

    /**
     * The message is appended verbatim: no trimming, wrapping or reordering. A log line is
     * evidence, and evidence that has been quietly tidied is no longer evidence.
     */
    @Test
    fun theMessageIsAppendedVerbatim() {
        val message = "chunk=3 unit=2  spaced   out | Cause: IOException"
        assertEquals(
            "22:13:20.000 INFO  [Reader] $message",
            LogLineFormat.format(wholeSecond, "INFO", "Reader", message),
        )
    }

    /** An empty tag or message still produces the same shape rather than a malformed line. */
    @Test
    fun emptyFieldsStillProduceTheLineShape() {
        assertEquals(
            "22:13:20.000 INFO  [] ",
            LogLineFormat.format(wholeSecond, "INFO", "", ""),
        )
    }
}
