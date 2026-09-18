package com.voxora.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two light candidates are complete, independent themes — not one palette with overrides.
 *
 * These tests do three things:
 *  1. pin each theme's declared tokens to the exact values it was specified with, so a "tidy-up"
 *     cannot blur the difference the owner is being asked to compare;
 *  2. prove the two themes are genuinely separate systems (they disagree on their signature tokens,
 *     and neither inherits anything from the other);
 *  3. prove neither light theme is the dark theme or the previous Google-inspired palette.
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

    // ---- Light Test 2: declared tokens ---------------------------------------------------

    @Test
    fun lightTest2DeclaresItsSpecifiedSurfacesAndBorders() {
        assertEquals(0xFFF8FAFC, LightTest2Palette.Background)
        assertEquals(0xFFFFFFFF, LightTest2Palette.Surface)
        assertEquals(0xFFF1F5F9, LightTest2Palette.SurfaceVariant)
        assertEquals(0xFFE2E8F0, LightTest2Palette.OutlineVariant)
    }

    @Test
    fun lightTest2DeclaresItsSpecifiedAccents() {
        assertEquals(0xFF22D3EE, LightTest2Palette.Cyan)
        assertEquals(0xFF0891B2, LightTest2Palette.CyanDark)
        assertEquals(0xFF6366F1, LightTest2Palette.Indigo)
        assertEquals(0xFF4F46E5, LightTest2Palette.IndigoDark)
        assertEquals(0xFF8B5CF6, LightTest2Palette.Violet)
        assertEquals(0xFFA855F7, LightTest2Palette.Purple)
    }

    @Test
    fun lightTest2DeclaresItsSpecifiedTextAndStatusTokens() {
        assertEquals(0xFF111827, LightTest2Palette.OnSurface)
        assertEquals(0xFF475569, LightTest2Palette.OnSurfaceVariant)
        assertEquals(0xFF64748B, LightTest2Palette.Explanation)
        assertEquals(0xFF16A34A, LightTest2Palette.Success)
        assertEquals(0xFFD97706, LightTest2Palette.Warning)
        assertEquals(0xFFDC2626, LightTest2Palette.Error)
    }

    @Test
    fun lightTest2DeclaresItsSpecifiedGradient() {
        assertEquals(0xFF22D3EE, LightTest2Palette.GradientStart)
        assertEquals(0xFF6366F1, LightTest2Palette.GradientMiddle)
        assertEquals(0xFFA855F7, LightTest2Palette.GradientEnd)
    }

    // ---- independence --------------------------------------------------------------------

    @Test
    fun theTwoLightThemesDisagreeOnEverySignatureToken() {
        assertFalse(LightTest1Palette.Background == LightTest2Palette.Background)
        assertFalse(LightTest1Palette.Primary == LightTest2Palette.Primary)
        assertFalse(LightTest1Palette.OnSurface == LightTest2Palette.OnSurface)
        assertFalse(LightTest1Palette.OnSurfaceVariant == LightTest2Palette.OnSurfaceVariant)
        assertFalse(LightTest1Palette.SurfaceVariant == LightTest2Palette.SurfaceVariant)
        assertFalse(LightTest1Palette.OutlineVariant == LightTest2Palette.OutlineVariant)
        assertFalse(LightTest1Palette.GradientMiddle == LightTest2Palette.GradientMiddle)
    }

    @Test
    fun lightTest2IsNotLightTest1WithADifferentPrimary() {
        // If Light Test 2 were an override of Light Test 1, these would coincide.
        assertFalse(LightTest2Palette.Background == LightTest1Palette.Background)
        assertFalse(LightTest2Palette.OnSurface == LightTest1Palette.OnSurface)
        assertFalse(LightTest2Palette.SurfaceVariant == LightTest1Palette.SurfaceVariant)
        assertFalse(LightTest2Palette.GradientMiddle == LightTest1Palette.GradientMiddle)
    }

    @Test
    fun neitherLightThemeIsTheDarkTheme() {
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
    fun neitherLightThemeUsesTheGooglePalette() {
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
        assertTrue("a light theme leaked Google colours: $leaked", leaked.isEmpty())
    }

    // ---- structure and legibility --------------------------------------------------------

    @Test
    fun theDarkThemeIsActuallyDarkAndTheLightThemesAreLight() {
        assertTrue(PaletteContrast.luminance(OriginalDarkPalette.NearBlack) < 0.05)
        assertTrue(PaletteContrast.luminance(LightTest1Palette.Background) > 0.8)
        assertTrue(PaletteContrast.luminance(LightTest2Palette.Background) > 0.8)
    }

    @Test
    fun everyLightValueIsFullyOpaque() {
        for (value in listOf(
            LightTest1Palette.Background, LightTest1Palette.Surface, LightTest1Palette.SurfaceVariant,
            LightTest1Palette.OnSurface, LightTest1Palette.OnSurfaceVariant, LightTest1Palette.Explanation,
            LightTest1Palette.Neutral, LightTest1Palette.Disabled, LightTest1Palette.Success,
            LightTest1Palette.Warning, LightTest1Palette.Error, LightTest1Palette.GradientStart,
            LightTest2Palette.Background, LightTest2Palette.Surface, LightTest2Palette.SurfaceVariant,
            LightTest2Palette.OnSurface, LightTest2Palette.OnSurfaceVariant, LightTest2Palette.Explanation,
            LightTest2Palette.Neutral, LightTest2Palette.Disabled, LightTest2Palette.Success,
            LightTest2Palette.Warning, LightTest2Palette.Error, LightTest2Palette.GradientStart,
        )) {
            assertTrue("not opaque: $value", PaletteContrast.isOpaque(value))
        }
    }

    @Test
    fun lightTest1ContentClearsAaOnBothTheCardAndThePage() {
        assertContentClearsAa(
            "LightTest1",
            LightTest1Palette.OnSurface,
            LightTest1Palette.OnSurfaceVariant,
            LightTest1Palette.Explanation,
            LightTest1Palette.Neutral,
            LightTest1Palette.Surface,
            LightTest1Palette.Background,
        )
    }

    @Test
    fun lightTest2ContentClearsAaOnBothTheCardAndThePage() {
        assertContentClearsAa(
            "LightTest2",
            LightTest2Palette.OnSurface,
            LightTest2Palette.OnSurfaceVariant,
            LightTest2Palette.Explanation,
            LightTest2Palette.Neutral,
            LightTest2Palette.Surface,
            LightTest2Palette.Background,
        )
    }

    @Test
    fun theLightCardsAreDistinguishableFromThePage() {
        assertFalse(LightTest1Palette.Surface == LightTest1Palette.Background)
        assertFalse(LightTest2Palette.Surface == LightTest2Palette.Background)
    }

    @Test
    fun lightHelpTextIsLessProminentThanSecondaryContent() {
        val l1Explanation = PaletteContrast.luminance(LightTest1Palette.Explanation)
        val l1Secondary = PaletteContrast.luminance(LightTest1Palette.OnSurfaceVariant)
        assertTrue(l1Explanation > l1Secondary)

        val l2Explanation = PaletteContrast.luminance(LightTest2Palette.Explanation)
        val l2Secondary = PaletteContrast.luminance(LightTest2Palette.OnSurfaceVariant)
        assertTrue(l2Explanation > l2Secondary)
    }

    private fun assertContentClearsAa(
        theme: String,
        onSurface: Long,
        onSurfaceVariant: Long,
        explanation: Long,
        neutral: Long,
        surface: Long,
        background: Long,
    ) {
        for (bg in listOf(surface, background)) {
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
