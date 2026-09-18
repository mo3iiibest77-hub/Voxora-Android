package com.voxora.app.ui

import com.voxora.app.util.VoxoraLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Contract for the severity → role mapping the Logs screen colours by.
 *
 * The screen must not decide colours by naming a level inline, so the decision is pinned here.
 * Two properties matter beyond the four obvious mappings:
 *
 * - the mapping is **total**: every severity the product can emit resolves to a role, so no line
 *   is left uncoloured by an omission;
 * - an unrecognised severity is **never** dressed up as success. A line whose severity this build
 *   does not understand is a diagnostic, not a reassurance.
 *
 * `DEBUG` is the only severity that maps to [LogSeverityTone.NEUTRAL], which is what keeps the
 * "one severity is neutral" statement a checked fact rather than a comment.
 */
class LogSeverityTest {

    @Test
    fun infoReadsAsSuccess() {
        assertEquals(LogSeverityTone.SUCCESS, LogSeverity.tone("INFO"))
    }

    @Test
    fun warnReadsAsAWarning() {
        assertEquals(LogSeverityTone.WARNING, LogSeverity.tone("WARN"))
    }

    @Test
    fun errorReadsAsDanger() {
        assertEquals(LogSeverityTone.DANGER, LogSeverity.tone("ERROR"))
    }

    @Test
    fun debugStaysNeutral() {
        assertEquals(LogSeverityTone.NEUTRAL, LogSeverity.tone("DEBUG"))
    }

    @Test
    fun everySeverityTheProductEmitsHasARole() {
        for (level in VoxoraLog.Level.entries) {
            val tone = LogSeverity.tone(level.name)
            if (level == VoxoraLog.Level.DEBUG) {
                assertEquals("$level", LogSeverityTone.NEUTRAL, tone)
            } else {
                assertNotEquals("$level must carry a status role", LogSeverityTone.NEUTRAL, tone)
            }
        }
    }

    /** Only debug is neutral, so a future severity cannot silently become one. */
    @Test
    fun debugIsTheOnlyNeutralSeverity() {
        val neutral = VoxoraLog.Level.entries.filter { LogSeverity.tone(it.name) == LogSeverityTone.NEUTRAL }
        assertEquals(listOf(VoxoraLog.Level.DEBUG), neutral)
    }

    @Test
    fun anUnrecognisedSeverityIsNeverSuccess() {
        assertEquals(LogSeverityTone.NEUTRAL, LogSeverity.tone("VERBOSE"))
        assertEquals(LogSeverityTone.NEUTRAL, LogSeverity.tone(""))
        assertEquals(LogSeverityTone.NEUTRAL, LogSeverity.tone("WTF"))
        assertNotEquals(LogSeverityTone.SUCCESS, LogSeverity.tone("VERBOSE"))
    }

    @Test
    fun theMappingIsCaseInsensitive() {
        assertEquals(LogSeverity.tone("INFO"), LogSeverity.tone("info"))
        assertEquals(LogSeverity.tone("WARN"), LogSeverity.tone("warn"))
        assertEquals(LogSeverity.tone("ERROR"), LogSeverity.tone("error"))
        assertEquals(LogSeverity.tone("DEBUG"), LogSeverity.tone("debug"))
    }
}
