package com.voxora.core.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the persisted appearance preference.
 *
 * The stored value is a short id rather than the enum's ordinal, so reordering the enum can never
 * silently change a user's choice. Normalization is total: an absent, blank, differently-cased or
 * unrecognised value resolves to the default instead of throwing, because a damaged preference
 * must not stop the app from starting.
 */
class ThemeModeTest {

    @Test
    fun everyModeRoundTripsThroughItsStoredId() {
        for (mode in ThemeMode.all) {
            assertEquals(mode, ThemeMode.normalize(mode.id))
        }
    }

    @Test
    fun idsAreUniqueAndNonBlank() {
        val ids = ThemeMode.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.all { it.isNotBlank() })
        assertTrue(ids.all { it == it.lowercase() })
    }

    @Test
    fun allListsEveryDeclaredModeExactlyOnce() {
        assertEquals(ThemeMode.entries.toList(), ThemeMode.all)
    }

    @Test
    fun parsingIsCaseAndWhitespaceInsensitive() {
        assertEquals(ThemeMode.DARK, ThemeMode.normalize("DARK"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.normalize("  light  "))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.normalize("System"))
    }

    @Test
    fun anAbsentOrUnrecognisedValueFallsBackToTheDefault() {
        assertEquals(ThemeMode.DEFAULT, ThemeMode.normalize(null))
        assertEquals(ThemeMode.DEFAULT, ThemeMode.normalize(""))
        assertEquals(ThemeMode.DEFAULT, ThemeMode.normalize("   "))
        assertEquals(ThemeMode.DEFAULT, ThemeMode.normalize("darkmode"))
        assertEquals(ThemeMode.DEFAULT, ThemeMode.normalize("1"))
    }

    @Test
    fun theDefaultIsSystemSoAnInstallFollowsTheDevice() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DEFAULT)
    }
}
