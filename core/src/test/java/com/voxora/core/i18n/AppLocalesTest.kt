package com.voxora.core.i18n

import com.voxora.core.gemini.ReaderLanguages
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the app's own UI locales.
 *
 * The UI locale is bounded by the translations actually packaged in the APK
 * (`resourceConfigurations` in `app/build.gradle.kts`), which is why it is a smaller
 * set than [ReaderLanguages.all]. It is still resolved through the one language
 * catalog, so every shipped locale is described with the same name and flag the Reader
 * and the dubbing picker use.
 */
class AppLocalesTest {

    @Test
    fun shippedLocalesAreUniqueAndNonEmpty() {
        assertTrue(AppLocales.shipped.isNotEmpty())
        assertEquals(AppLocales.shipped.size, AppLocales.shipped.toSet().size)
        assertTrue(AppLocales.shipped.all { it.isNotBlank() })
    }

    @Test
    fun everyShippedLocaleExistsInTheOneCatalog() {
        for (code in AppLocales.shipped) {
            assertTrue("$code is not in ReaderLanguages", ReaderLanguages.isValid(code))
            assertNotNull(ReaderLanguages.languageOrNull(code))
        }
    }

    @Test
    fun noShippedLocaleIsSilentlyDropped() {
        // languages is the picker's source: if a shipped code had no catalog entry it
        // would vanish from the UI without anyone noticing.
        assertEquals(AppLocales.shipped.size, AppLocales.languages.size)
        assertEquals(AppLocales.shipped, AppLocales.languages.map { it.code })
    }

    @Test
    fun shippedLocalesCarryADisplayNameAndAFlag() {
        for (language in AppLocales.languages) {
            val endonym = language.displayName(Locale.forLanguageTag(language.code))
            assertTrue("${language.code} has no display name", endonym.isNotBlank())
            assertTrue("${language.code} has no flag", language.flagEmoji.isNotBlank())
        }
    }

    @Test
    fun theDefaultLocaleIsShipped() {
        assertTrue(AppLocales.isShipped(AppLocales.DEFAULT))
        assertEquals("en", AppLocales.DEFAULT)
    }

    @Test
    fun isShippedIsCaseInsensitiveAndRejectsUnknownCodes() {
        assertTrue(AppLocales.isShipped("fa"))
        assertTrue(AppLocales.isShipped("FA"))
        assertFalse(AppLocales.isShipped("ja"))
        assertFalse(AppLocales.isShipped(""))
        assertFalse(AppLocales.isShipped("xx"))
    }

    @Test
    fun theUiLocaleSetIsSmallerThanTheGeminiOutputCatalog() {
        // Not a cosmetic check: the two sets answer different questions, and a future
        // change that conflates them would either offer untranslated UI languages or
        // restrict narration to the translated ones.
        assertTrue(AppLocales.shipped.size < ReaderLanguages.all.size)
        assertNull(ReaderLanguages.languageOrNull("zz"))
    }
}
