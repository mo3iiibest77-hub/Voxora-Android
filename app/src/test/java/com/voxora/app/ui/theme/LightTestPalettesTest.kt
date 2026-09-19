package com.voxora.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The light appearances are complete, independent colour systems — not one palette with overrides.
 *
 * These tests do five things:
 *  1. pin each appearance's declared tokens to the exact values it was specified with, so a
 *     "tidy-up" cannot blur the appearance the owner asked for;
 *  2. prove the appearances are genuinely separate systems (they disagree on their signature tokens,
 *     and neither inherits anything from the other);
 *  3. prove neither light appearance is the dark appearance or the previous Nova-style palette;
 *  4. verify the WCAG contrast where it holds, and **document the measured shortfalls** of the
 *     supplied Voxora Light palette rather than silently altering it — the owner requires the
 *     supplied hex values verbatim;
 *  5. pin the per-appearance glow and the light shadow language, so a light screen can never inherit
 *     the dark gold wash.
 */
class LightTestPalettesTest {

    // ---- Light Test 1: declared tokens ---------------------------------------------------

    @Test
    fun lightTest1DeclaresItsSpecifiedSurfacesAndBorders() {
        assertEquals(0xFFF8F9FC, LightTest1Palette.Background)
        assertEquals(0xFFFFFFFF, LightTest1Palette.Surface)
        assertEquals(0xFFF1F3F8, LightTest1Palette.SurfaceVariant)
        assertEquals(0xFFE1E5EE, LightTest1Palette.OutlineVariant)
    }

    @Test
    fun lightTest1DeclaresItsSpecifiedAccents() {
        assertEquals(0xFF4F46E5, LightTest1Palette.Primary)
        assertEquals(0xFF0891B2, LightTest1Palette.Secondary)
        assertEquals(0xFF7C3AED, LightTest1Palette.Tertiary)
        assertEquals(0xFF9333EA, LightTest1Palette.AccentPurple)
    }

    @Test
    fun lightTest1DeclaresItsSpecifiedTextAndStatusTokens() {
        assertEquals(0xFF171923, LightTest1Palette.OnSurface)
        assertEquals(0xFF596174, LightTest1Palette.OnSurfaceVariant)
        assertEquals(0xFF64748B, LightTest1Palette.Explanation)
        assertEquals(0xFF16A34A, LightTest1Palette.Success)
        assertEquals(0xFFD97706, LightTest1Palette.Warning)
        assertEquals(0xFFDC2626, LightTest1Palette.Error)
    }

    @Test
    fun lightTest1DeclaresItsSpecifiedGradient() {
        assertEquals(0xFF22D3EE, LightTest1Palette.GradientStart)
        assertEquals(0xFF818CF8, LightTest1Palette.GradientMiddle)
        assertEquals(0xFFA855F7, LightTest1Palette.GradientEnd)
    }

    // ---- Voxora Light (the `LIGHT_TEST_2` slot) — supplied values, verbatim ----------------

    @Test
    fun voxoraLightDeclaresItsSuppliedSurfacesAndBorders() {
        assertEquals(0xFFF5F2EC, LightTest2Palette.Background)
        assertEquals(0xFFEDE9DF, LightTest2Palette.Surface)
        assertEquals(0xFFE4DFD3, LightTest2Palette.SurfaceVariant)
        assertEquals(0xFFEAE6DC, LightTest2Palette.SurfaceContainer)
        assertEquals(0xFFDEDAD0, LightTest2Palette.SurfaceContainerHigh)
        assertEquals(0xFFD5D0C4, LightTest2Palette.SurfaceContainerHighest)
        assertEquals(0xFFB8B0A0, LightTest2Palette.Outline)
        assertEquals(0xFFD0C9BC, LightTest2Palette.OutlineVariant)
    }

    @Test
    fun voxoraLightDeclaresItsSuppliedGoldIdentity() {
        assertEquals(0xFF8B6914, LightTest2Palette.Primary)
        assertEquals(0xFFA07820, LightTest2Palette.Secondary)
        assertEquals(0xFFF0E4B8, LightTest2Palette.PrimaryContainer)
        assertEquals(0xFF5C4A10, LightTest2Palette.OnPrimaryContainer)
        assertEquals(0xFFE8DFC8, LightTest2Palette.SecondaryContainer)
        assertEquals(0xFF4A3E20, LightTest2Palette.OnSecondaryContainer)
    }

    @Test
    fun voxoraLightDeclaresItsSuppliedTextTokens() {
        assertEquals(0xFF1A1610, LightTest2Palette.OnSurface)
        assertEquals(0xFF4A4438, LightTest2Palette.OnSurfaceVariant)
        assertEquals(0xFF0369A1, LightTest2Palette.Explanation)
        assertEquals(0xFF6B6558, LightTest2Palette.Neutral)
        assertEquals(0xFFA09888, LightTest2Palette.Disabled)
    }

    @Test
    fun voxoraLightDeclaresItsSuppliedStatusTokens() {
        assertEquals(0xFF1A9E57, LightTest2Palette.Success)
        assertEquals(0xFFB88A10, LightTest2Palette.Warning)
        assertEquals(0xFFC0392B, LightTest2Palette.Error)
        assertEquals(0xFFFAD7D7, LightTest2Palette.ErrorContainer)
        assertEquals(0xFF7A1515, LightTest2Palette.OnErrorContainer)
    }

    @Test
    fun voxoraLightGoldIsDarkerThanTheDarkThemeGold() {
        // The owner's design rule: the light gold must be darker than the dark theme's gold.
        val darkGold = PaletteContrast.luminance(OriginalDarkPalette.Gold)
        assertTrue(PaletteContrast.luminance(LightTest2Palette.Primary) < darkGold)
        assertTrue(PaletteContrast.luminance(LightTest2Palette.Secondary) < darkGold)
    }

    @Test
    fun voxoraLightNeverUsesPureWhiteAsItsPage() {
        assertFalse(LightTest2Palette.Background == 0xFFFFFFFF)
        assertFalse(LightTest2Palette.Surface == 0xFFFFFFFF)
    }

    @Test
    fun voxoraLightHasItsOwnGlowAndWarmShadow() {
        // rgba(139,105,20,0.12) and rgba(100,80,30,0.12) — the light language, not the dark one.
        assertEquals(0x1F8B6914, LightTest2Palette.Glow)
        assertEquals(0x1F64501E, LightTest2Palette.Shadow)
        assertFalse("the light glow must not reuse the dark gold glow", LightTest2Palette.Glow == OriginalDarkPalette.Glow)
    }

    @Test
    fun voxoraLightDangerMatchesErrorLikeTheDarkPalette() {
        assertEquals(LightTest2Palette.Error, LightTest2Palette.Danger)
    }

    // ---- independence --------------------------------------------------------------------

    @Test
    fun theTwoLightAppearancesDisagreeOnEverySignatureToken() {
        assertFalse(LightTest1Palette.Background == LightTest2Palette.Background)
        assertFalse(LightTest1Palette.Primary == LightTest2Palette.Primary)
        assertFalse(LightTest1Palette.OnSurface == LightTest2Palette.OnSurface)
        assertFalse(LightTest1Palette.OnSurfaceVariant == LightTest2Palette.OnSurfaceVariant)
        assertFalse(LightTest1Palette.SurfaceVariant == LightTest2Palette.SurfaceVariant)
        assertFalse(LightTest1Palette.OutlineVariant == LightTest2Palette.OutlineVariant)
    }

    @Test
    fun voxoraLightIsNotLightTest1WithADifferentPrimary() {
        // If Voxora Light were an override of Light Test 1, these would coincide.
        assertFalse(LightTest2Palette.Background == LightTest1Palette.Background)
        assertFalse(LightTest2Palette.OnSurface == LightTest1Palette.OnSurface)
        assertFalse(LightTest2Palette.SurfaceVariant == LightTest1Palette.SurfaceVariant)
        assertFalse(LightTest2Palette.Explanation == LightTest1Palette.Explanation)
    }

    @Test
    fun voxoraLightIsNotTheOldNovaStylePalette() {
        // The previous Light Test 2 (cyan -> indigo -> purple) is gone; nothing of it survives.
        assertFalse(LightTest2Palette.Background == 0xFFF8FAFC)
        assertFalse(LightTest2Palette.SurfaceVariant == 0xFFF1F5F9)
        assertFalse(LightTest2Palette.Primary == 0xFF6366F1)
        assertFalse(LightTest2Palette.OnSurface == 0xFF111827)
        assertFalse(LightTest2Palette.Explanation == 0xFF64748B)
    }

    @Test
    fun voxoraLightIsNotThePreviousCoolContrastPalette() {
        // The cool near-white / indigo Contrast Light that occupied this slot before is gone.
        assertFalse(LightTest2Palette.Background == 0xFFF5F7FB)
        assertFalse(LightTest2Palette.Primary == 0xFF375CD4)
        assertFalse(LightTest2Palette.OnSurface == 0xFF101827)
        assertFalse(LightTest2Palette.Error == 0xFF0B6E8A)
        assertFalse(LightTest2Palette.Success == 0xFFBE185D)
    }

    @Test
    fun neitherLightAppearanceIsTheDarkTheme() {
        for (value in listOf(
            LightTest1Palette.Background, LightTest1Palette.Primary, LightTest1Palette.OnSurface,
            LightTest2Palette.Background, LightTest2Palette.Primary, LightTest2Palette.OnSurface,
        )) {
            assertFalse(value == OriginalDarkPalette.NearBlack)
            assertFalse(value == OriginalDarkPalette.Gold)
            assertFalse(value == OriginalDarkPalette.OnSurface)
        }
    }

    @Test
    fun neitherLightAppearanceUsesTheGooglePalette() {
        val googleFamily = setOf(
            0xFF1967D2, 0xFF8AB4F8, 0xFF137333, 0xFF81C995,
            0xFF8A5200, 0xFFFDD663, 0xFFB3261E, 0xFFF28B82,
            0xFF1F1F1F, 0xFF444746, 0xFFDADCE0,
        )
        val lightValues = listOf(
            LightTest1Palette.Primary, LightTest1Palette.Secondary, LightTest1Palette.Tertiary,
            LightTest1Palette.OnSurface, LightTest1Palette.OnSurfaceVariant,
            LightTest1Palette.Explanation, LightTest1Palette.Success,
            LightTest1Palette.Warning, LightTest1Palette.Error,
            LightTest2Palette.Primary, LightTest2Palette.Secondary, LightTest2Palette.Tertiary,
            LightTest2Palette.OnSurface, LightTest2Palette.OnSurfaceVariant,
            LightTest2Palette.Explanation, LightTest2Palette.Success,
            LightTest2Palette.Warning, LightTest2Palette.Error,
        )
        val leaked = lightValues.filter { it in googleFamily }
        assertTrue("a light appearance leaked Google colours: $leaked", leaked.isEmpty())
    }

    // ---- structure and legibility --------------------------------------------------------

    @Test
    fun theDarkThemeIsActuallyDarkAndTheLightAppearancesAreLight() {
        assertTrue(PaletteContrast.luminance(OriginalDarkPalette.NearBlack) < 0.05)
        assertTrue(PaletteContrast.luminance(LightTest1Palette.Background) > 0.8)
        assertTrue(PaletteContrast.luminance(LightTest2Palette.Background) > 0.8)
    }

    @Test
    fun everyOpaqueLightValueIsFullyOpaque() {
        // Glow and Shadow are deliberately translucent; every other value is a solid colour.
        for (value in listOf(
            LightTest1Palette.Background, LightTest1Palette.Surface, LightTest1Palette.SurfaceVariant,
            LightTest1Palette.OnSurface, LightTest1Palette.OnSurfaceVariant, LightTest1Palette.Explanation,
            LightTest1Palette.Neutral, LightTest1Palette.Disabled, LightTest1Palette.Success,
            LightTest1Palette.Warning, LightTest1Palette.Error, LightTest1Palette.GradientStart,
            LightTest2Palette.Background, LightTest2Palette.Surface, LightTest2Palette.SurfaceVariant,
            LightTest2Palette.SurfaceContainer, LightTest2Palette.SurfaceContainerHigh,
            LightTest2Palette.SurfaceContainerHighest, LightTest2Palette.OnSurface,
            LightTest2Palette.OnSurfaceVariant, LightTest2Palette.Explanation,
            LightTest2Palette.Neutral, LightTest2Palette.Disabled, LightTest2Palette.Success,
            LightTest2Palette.Warning, LightTest2Palette.Error,
        )) {
            assertTrue("not opaque: $value", PaletteContrast.isOpaque(value))
        }
        assertFalse(PaletteContrast.isOpaque(LightTest2Palette.Glow))
        assertFalse(PaletteContrast.isOpaque(LightTest2Palette.Shadow))
    }

    @Test
    fun lightTest1ContentClearsAaOnBothTheCardAndThePage() {
        assertContentClearsAa(
            "LightTest1",
            LightTest1Palette.OnSurface,
            LightTest1Palette.OnSurfaceVariant,
            LightTest1Palette.Explanation,
            LightTest1Palette.Neutral,
            listOf(LightTest1Palette.Surface, LightTest1Palette.Background),
        )
    }

    @Test
    fun voxoraLightContentClearsAaOnThePageAndTheSurface() {
        // The primary reading surfaces: every content and help role clears AA here.
        assertContentClearsAa(
            "VoxoraLight",
            LightTest2Palette.OnSurface,
            LightTest2Palette.OnSurfaceVariant,
            LightTest2Palette.Explanation,
            LightTest2Palette.Neutral,
            listOf(LightTest2Palette.Surface, LightTest2Palette.Background),
        )
    }

    @Test
    fun voxoraLightContentTextClearsAaOnTheCard() {
        assertClearsAa(LightTest2Palette.OnSurface, LightTest2Palette.SurfaceVariant, "onSurface")
        assertClearsAa(
            LightTest2Palette.OnSurfaceVariant,
            LightTest2Palette.SurfaceVariant,
            "onSurfaceVariant",
        )
    }

    @Test
    fun voxoraLightContainerPairsClearAa() {
        assertClearsAa(LightTest2Palette.OnPrimary, LightTest2Palette.Primary, "onPrimary")
        assertClearsAa(
            LightTest2Palette.OnPrimaryContainer,
            LightTest2Palette.PrimaryContainer,
            "onPrimaryContainer",
        )
        assertClearsAa(
            LightTest2Palette.OnSecondaryContainer,
            LightTest2Palette.SecondaryContainer,
            "onSecondaryContainer",
        )
        assertClearsAa(LightTest2Palette.OnError, LightTest2Palette.Error, "onError")
        assertClearsAa(
            LightTest2Palette.OnErrorContainer,
            LightTest2Palette.ErrorContainer,
            "onErrorContainer",
        )
    }

    @Test
    fun voxoraLightDocumentsItsMeasuredAaShortfallsInsteadOfAlteringThePalette() {
        // The owner requires the supplied hex values verbatim, so the shortfalls are pinned here as
        // the honest record rather than designed around. If a value is ever changed to clear AA,
        // this test fails and the change has to be a deliberate, documented decision.
        val card = LightTest2Palette.SurfaceVariant
        assertTrue(
            "Explanation on the card is now ${PaletteContrast.ratio(LightTest2Palette.Explanation, card)}",
            PaletteContrast.ratio(LightTest2Palette.Explanation, card) < PaletteContrast.AA,
        )
        assertTrue(
            "Neutral on the card is now ${PaletteContrast.ratio(LightTest2Palette.Neutral, card)}",
            PaletteContrast.ratio(LightTest2Palette.Neutral, card) < PaletteContrast.AA,
        )
        assertTrue(
            "Primary on the card is now ${PaletteContrast.ratio(LightTest2Palette.Primary, card)}",
            PaletteContrast.ratio(LightTest2Palette.Primary, card) < PaletteContrast.AA,
        )
        assertTrue(
            "Secondary on the page is now ${PaletteContrast.ratio(LightTest2Palette.Secondary, LightTest2Palette.Background)}",
            PaletteContrast.ratio(LightTest2Palette.Secondary, LightTest2Palette.Background) <
                PaletteContrast.AA,
        )
    }

    @Test
    fun theLightCardsAreDistinguishableFromThePage() {
        assertFalse(LightTest1Palette.Surface == LightTest1Palette.Background)
        assertFalse(LightTest2Palette.Surface == LightTest2Palette.Background)
        assertFalse(LightTest2Palette.SurfaceVariant == LightTest2Palette.Background)
    }

    @Test
    fun lightHelpTextIsLessProminentThanSecondaryContent() {
        val l1Explanation = PaletteContrast.luminance(LightTest1Palette.Explanation)
        val l1Secondary = PaletteContrast.luminance(LightTest1Palette.OnSurfaceVariant)
        assertTrue(l1Explanation > l1Secondary)

        // The navy information tone is a different hue from the warm secondary text, but it is the
        // lighter of the two by luminance, so the "help is the quieter role" ordering still holds.
        val l2Explanation = PaletteContrast.luminance(LightTest2Palette.Explanation)
        val l2Secondary = PaletteContrast.luminance(LightTest2Palette.OnSurfaceVariant)
        assertTrue(l2Explanation > l2Secondary)
    }

    private fun assertClearsAa(foreground: Long, background: Long, role: String) {
        val ratio = PaletteContrast.ratio(foreground, background)
        assertTrue(
            "$role contrast $ratio on $background is below AA",
            ratio >= PaletteContrast.AA,
        )
    }

    private fun assertContentClearsAa(
        theme: String,
        onSurface: Long,
        onSurfaceVariant: Long,
        explanation: Long,
        neutral: Long,
        backgrounds: List<Long>,
    ) {
        for (bg in backgrounds) {
            for ((role, value) in listOf(
                "onSurface" to onSurface,
                "onSurfaceVariant" to onSurfaceVariant,
                "explanation" to explanation,
                "neutral" to neutral,
            )) {
                val ratio = PaletteContrast.ratio(value, bg)
                assertTrue(
                    "$theme $role contrast $ratio on $bg is below AA",
                    ratio >= PaletteContrast.AA,
                )
            }
        }
    }
}
