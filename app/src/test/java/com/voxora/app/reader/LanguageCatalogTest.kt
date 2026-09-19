package com.voxora.app.reader

import com.voxora.core.gemini.ReaderLanguages
import com.voxora.core.i18n.AppLocales
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the one language catalog every picker is built from.
 *
 * The Reader's narration picker and the Settings "Dubbing language" picker call the
 * same [languageOptions], so they are the same list by construction: same count, same
 * order, same labels, same flags. The app's own UI locale is a genuinely different set
 * — bounded by the translations packaged in the APK — but it still resolves names and
 * flags from the same catalog, so a language is never described two ways.
 */
class LanguageCatalogTest {

    private val english = Locale.ENGLISH
    private val persian = Locale.forLanguageTag("fa")

    @Test
    fun everyPickerCoversTheWholeCatalogExactlyOnce() {
        for (locale in listOf(english, persian)) {
            val options = languageOptions(locale, "")
            val codes = options.map { it.code }

            assertEquals(ReaderLanguages.all.size, options.size)
            assertEquals(codes.size, codes.toSet().size)
            assertEquals(ReaderLanguages.all.map { it.code }.toSet(), codes.toSet())
        }
    }

    @Test
    fun labelsAndFlagsComeFromTheCatalogRatherThanThePicker() {
        for (option in languageOptions(english, "")) {
            val language = ReaderLanguages.language(option.code)
            assertEquals(language.displayName(english), option.label)
            assertEquals(language.englishName, option.englishName)
            assertEquals(language.flagEmoji, option.flagEmoji)
            assertTrue(option.flagEmoji.isNotBlank())
            assertTrue(option.label.isNotBlank())
        }
    }

    @Test
    fun orderIsDeterministicAndSortedByLocalizedLabel() {
        val first = languageOptions(persian, "").map { it.code }
        val second = languageOptions(persian, "").map { it.code }
        assertEquals(first, second)

        val labels = languageOptions(persian, "").map { it.label.lowercase(persian) }
        assertEquals(labels.sorted(), labels)
    }

    @Test
    fun searchingFiltersWithoutReorderingOrDroppingTheSelection() {
        val all = languageOptions(english, "")
        val query = "persian"
        val filtered = languageOptions(english, query)

        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.searchText.contains(query, ignoreCase = true) })
        // Relative order is preserved, so a picker never reshuffles while typing.
        assertEquals(all.filter { it.code in filtered.map { hit -> hit.code } }, filtered)
        assertTrue(filtered.any { it.code == "fa" })
    }

    @Test
    fun aLanguageIsFindableByCodeEnglishNameAndItsOwnName() {
        val all = languageOptions(english, "")
        for (query in listOf("fa", "Persian", "فارسی")) {
            assertTrue("$query should find Persian", languageOptions(english, query).any { it.code == "fa" })
        }
        assertTrue(all.first { it.code == "fa" }.searchText.contains("fa"))
    }

    @Test
    fun theEnglishNameIsOnlyShownWhenItAddsSomething() {
        val englishOptions = languageOptions(english, "")
        val englishLabel = englishOptions.first { it.code == "en" }
        // Shown in English, the English name is the label: a second line would be noise.
        assertEquals("English", englishLabel.label)
        assertNull(englishLabel.secondaryLabel)

        val persianLabel = englishOptions.first { it.code == "fa" }
        assertEquals("Persian", persianLabel.label)
        assertNull(persianLabel.secondaryLabel)

        // Shown in Persian, the localized name differs, so the English name is useful.
        val fromPersian = languageOptions(persian, "").first { it.code == "fa" }
        assertNotNull(fromPersian.secondaryLabel)
        assertEquals("Persian", fromPersian.secondaryLabel)
    }

    @Test
    fun theAppLocalePickerIsTheShippedSubsetOfTheSameCatalog() {
        assertEquals(AppLocales.shipped.size, AppLocales.languages.size)
        assertEquals(AppLocales.shipped, AppLocales.languages.map { it.code })

        val catalogCodes = ReaderLanguages.all.map { it.code }.toSet()
        assertTrue(AppLocales.shipped.all { it in catalogCodes })
        assertTrue(AppLocales.isShipped(AppLocales.DEFAULT))

        // Every UI locale the app can be set to is also a narration/dubbing language.
        val pickerCodes = languageOptions(english, "").map { it.code }.toSet()
        assertTrue(AppLocales.shipped.all { it in pickerCodes })
    }

    @Test
    fun anUnknownCodeIsNormalizedRatherThanInventingALanguage() {
        assertEquals(ReaderLanguages.DEFAULT, ReaderLanguages.normalize("xx"))
        assertEquals(ReaderLanguages.DEFAULT, ReaderLanguages.normalize(null))
        assertEquals("no", ReaderLanguages.normalize("nb"))
        assertNull(ReaderLanguages.languageOrNull("xx"))
        assertNotNull(ReaderLanguages.languageOrNull("fa"))
    }
}
