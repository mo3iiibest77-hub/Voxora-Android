package com.voxora.core.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the persisted theme preference.
 *
 * The stored value is a short id rather than the enum's ordinal, so reordering the enum can never
 * silently change a user's choice. Normalization is total: an absent, blank, differently-cased or
 * unrecognised value resolves to the default instead of throwing, because a damaged preference must
 * not stop the app from starting. Values written by the previous `system`/`light`/`dark` model are
 * migrated rather than discarded.
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
    fun thereAreExactlyThreeSelectableThemes() {
        assertEquals(3, ThemeMode.all.size)
    }

    @Test
    fun parsingIsCaseAndWhitespaceInsensitive() {
        assertEquals(ThemeMode.ORIGINAL_DARK, ThemeMode.normalize("ORIGINAL_DARK"))
        assertEquals(ThemeMode.LIGHT_TEST_1, ThemeMode.normalize("  Light_Test_1  "))
        assertEquals(ThemeMode.LIGHT_TEST_2, ThemeMode.normalize("light_test_2"))
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
    fun theDefaultIsTheOriginalVoxoraDarkTheme() {
        assertEquals(ThemeMode.ORIGINAL_DARK, ThemeMode.DEFAULT)
    }

    @Test
    fun theDefaultIsNotOneOfTheLightTests() {
        assertTrue(ThemeMode.DEFAULT != ThemeMode.LIGHT_TEST_1)
        assertTrue(ThemeMode.DEFAULT != ThemeMode.LIGHT_TEST_2)
    }

    @Test
    fun legacySystemAndDarkValuesMigrateToTheOriginalDarkTheme() {
        assertEquals(ThemeMode.ORIGINAL_DARK, ThemeMode.normalize("system"))
        assertEquals(ThemeMode.ORIGINAL_DARK, ThemeMode.normalize("dark"))
        assertEquals(ThemeMode.ORIGINAL_DARK, ThemeMode.normalize("SYSTEM"))
    }

    @Test
    fun theLegacyLightValueMigratesToALightThemeRatherThanToDark() {
        assertEquals(ThemeMode.LIGHT_TEST_1, ThemeMode.normalize("light"))
        assertEquals(ThemeMode.LIGHT_TEST_1, ThemeMode.normalize("LIGHT"))
    }
}
