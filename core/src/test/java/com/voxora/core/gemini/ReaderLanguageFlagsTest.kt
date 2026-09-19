package com.voxora.core.gemini

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The language rows show a flag next to each language, so the mapping has to be
 * deliberate and stable. These tests pin the representative choices and make sure
 * no catalog entry silently falls through to an unrelated flag.
 */
class ReaderLanguageFlagsTest {

    @Test
    fun representativeLanguagesMapToTheirDocumentedFlags() {
        assertEquals("🇬🇧", ReaderLanguageFlags.flagFor("en"))
        assertEquals("🇮🇷", ReaderLanguageFlags.flagFor("fa"))
        assertEquals("🇩🇪", ReaderLanguageFlags.flagFor("de"))
        assertEquals("🇫🇷", ReaderLanguageFlags.flagFor("fr"))
        assertEquals("🇪🇸", ReaderLanguageFlags.flagFor("es"))
        assertEquals("🇮🇹", ReaderLanguageFlags.flagFor("it"))
        assertEquals("🇯🇵", ReaderLanguageFlags.flagFor("ja"))
        assertEquals("🇰🇷", ReaderLanguageFlags.flagFor("ko"))
    }

    @Test
    fun portugueseVariantsAndChineseScriptsStayDistinct() {
        assertEquals("🇧🇷", ReaderLanguageFlags.flagFor("pt-BR"))
        assertEquals("🇵🇹", ReaderLanguageFlags.flagFor("pt-PT"))
        assertNotEquals(ReaderLanguageFlags.flagFor("pt-BR"), ReaderLanguageFlags.flagFor("pt-PT"))

        assertEquals("🇨🇳", ReaderLanguageFlags.flagFor("zh-Hans"))
        assertEquals("🇹🇼", ReaderLanguageFlags.flagFor("zh-Hant"))
        assertNotEquals(ReaderLanguageFlags.flagFor("zh-Hans"), ReaderLanguageFlags.flagFor("zh-Hant"))
    }

    @Test
    fun arabicUsesTheDocumentedRepresentativeFlag() {
        // Deliberate representative choice for Modern Standard Arabic.
        assertEquals("🇸🇦", ReaderLanguageFlags.flagFor("ar"))
        assertTrue(ReaderLanguageFlags.hasNationalFlag("ar"))
    }

    @Test
    fun codesAreNormalizedBeforeMapping() {
        assertEquals(ReaderLanguageFlags.flagFor("pt-BR"), ReaderLanguageFlags.flagFor("PT-br"))
        assertEquals(ReaderLanguageFlags.flagFor("en"), ReaderLanguageFlags.flagFor("EN"))
        // "nb" is normalized to "no" by the catalog.
        assertEquals(ReaderLanguageFlags.flagFor("no"), ReaderLanguageFlags.flagFor("nb"))
        // Unknown codes fall back to the catalog default rather than crashing.
        assertEquals(ReaderLanguageFlags.flagFor("en"), ReaderLanguageFlags.flagFor("not-a-language"))
    }

    @Test
    fun everyCatalogLanguageResolvesToAFlagOrTheNeutralMarker() {
        for (language in ReaderLanguages.all) {
            val flag = ReaderLanguageFlags.flagFor(language.code)
            assertTrue("blank flag for ${language.code}", flag.isNotBlank())
            val codePoints = flag.codePointCount(0, flag.length)
            assertTrue(
                "unexpected flag width for ${language.code}: $codePoints",
                codePoints == 2 || flag == ReaderLanguageFlags.NEUTRAL,
            )
        }
    }

    @Test
    fun everyCatalogLanguageIsMappedDeliberatelyRatherThanByAccident() {
        // Anything that is not intentionally neutral must have a real national flag,
        // so a language added without a mapping is visible here instead of silently
        // showing a globe.
        val unmapped = ReaderLanguages.all
            .map { it.code }
            .filter { !ReaderLanguageFlags.hasNationalFlag(it) }
        assertEquals(listOf("ca", "eu", "ku", "qu"), unmapped.sorted())
    }

    @Test
    fun languagesWithoutASingleAssociatedStateUseTheNeutralMarker() {
        for (code in listOf("eu", "ca", "ku", "qu")) {
            assertEquals(ReaderLanguageFlags.NEUTRAL, ReaderLanguageFlags.flagFor(code))
            assertFalse(ReaderLanguageFlags.hasNationalFlag(code))
        }
    }

    @Test
    fun regionalIndicatorEncodingIsValidated() {
        assertEquals("🇬🇧", ReaderLanguageFlags.regionalIndicator("GB"))
        assertEquals("🇮🇷", ReaderLanguageFlags.regionalIndicator("ir"))
        assertEquals("🇧🇷", ReaderLanguageFlags.regionalIndicator(" br "))
        // Anything that is not a two-letter code must not invent an emoji.
        assertEquals(ReaderLanguageFlags.NEUTRAL, ReaderLanguageFlags.regionalIndicator(""))
        assertEquals(ReaderLanguageFlags.NEUTRAL, ReaderLanguageFlags.regionalIndicator("G"))
        assertEquals(ReaderLanguageFlags.NEUTRAL, ReaderLanguageFlags.regionalIndicator("GBR"))
        assertEquals(ReaderLanguageFlags.NEUTRAL, ReaderLanguageFlags.regionalIndicator("12"))
    }

    @Test
    fun theCatalogModelExposesTheSameFlag() {
        assertEquals("🇮🇷", ReaderLanguages.language("fa").flagEmoji)
        assertEquals("🇬🇧", ReaderLanguages.language("en").flagEmoji)
        assertEquals(ReaderLanguageFlags.flagFor("ja"), ReaderLanguages.language("ja").flagEmoji)
    }
}
