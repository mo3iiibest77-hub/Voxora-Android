package com.voxora.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two light candidates are complete, independent themes — not one palette with overrides.
 *
 * These tests do four things:
 *  1. pin each theme's declared tokens to the exact values it was specified with, so a "tidy-up"
 *     cannot blur the difference the owner is being asked to compare;
 *  2. prove the two themes are genuinely separate systems (they disagree on their signature tokens,
 *     and neither inherits anything from the other);
 *  3. prove neither light theme is the dark theme or the previous Google-inspired palette;
 *  4. verify the WCAG contrast of the new contrast theme, including the two documented adjustments.
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

    // ---- Light Test 2 — Voxora Contrast Light --------------------------------------------

    @Test
    fun lightTest2DeclaresItsSpecifiedSurfacesAndBorders() {
        assertEquals(0xFFF5F7FB, LightTest2Palette.Background)
        assertEquals(0xFFEDF1F7, LightTest2Palette.Surface)
        assertEquals(0xFFE4E9F1, LightTest2Palette.SurfaceVariant)
        assertEquals(0xFFD3D9E3, LightTest2Palette.OutlineVariant)
    }

    @Test
    fun lightTest2DeclaresItsSpecifiedCoolIndigoIdentity() {
        // "gold becomes a refined indigo/blue complement".
        assertEquals(0xFF375CD4, LightTest2Palette.Primary)
        assertEquals(0xFF2E50B8, LightTest2Palette.PrimaryDim)
        assertEquals(0xFFDDE5FF, LightTest2Palette.PrimaryContainer)
        assertEquals(0xFF1D2E6B, LightTest2Palette.OnPrimaryContainer)
        assertEquals(0xFFE1E7F0, LightTest2Palette.SecondaryContainer)
        assertEquals(0xFF2A3444, LightTest2Palette.OnSecondaryContainer)
    }

    @Test
    fun lightTest2DeclaresItsSpecifiedCoolSlateText() {
        // "warm beige text becomes cool slate text".
        assertEquals(0xFF101827, LightTest2Palette.OnSurface)
        assertEquals(0xFF4B5870, LightTest2Palette.OnSurfaceVariant)
        assertEquals(0xFF585E6B, LightTest2Palette.Disabled)
    }

    @Test
    fun lightTest2WarningKeepsTheSuppliedComplementaryBlue() {
        // The supplied Warning was already legible on every surface, so it is kept exactly.
        assertEquals(0xFF2254E6, LightTest2Palette.Warning)
    }

    @Test
    fun lightTest2ErrorAndSuccessAreTheMinimalLegibleAdjustments() {
        // The supplied vivid Error (#5DE8E8) measured 1.22:1 and Success (#DC3D95) 3.35:1 on the
        // card — invisible and below AA. They keep their hue families but are darkened to the
        // lightest members that clear 4.5:1, as required by the task's WCAG instruction.
        assertEquals(0xFF0B6E8A, LightTest2Palette.Error)
        assertEquals(0xFFBE185D, LightTest2Palette.Success)
        assertFalse(LightTest2Palette.Error == 0xFF5DE8E8)
        assertFalse(LightTest2Palette.Success == 0xFFDC3D95)
    }

    @Test
    fun lightTest2ExplanationIsTheMinimalLegibleAdjustment() {
        // Supplied #6B7384 measured 3.91:1 on the card; #5F6775 clears AA while staying lighter
        // than secondary content, i.e. still the less prominent role.
        assertEquals(0xFF5F6775, LightTest2Palette.Explanation)
    }

    @Test
    fun lightTest2DangerMatchesErrorLikeTheDarkPalette() {
        assertEquals(LightTest2Palette.Error, LightTest2Palette.Danger)
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
    }

    @Test
    fun lightTest2IsNotLightTest1WithADifferentPrimary() {
        // If Light Test 2 were an override of Light Test 1, these would coincide.
        assertFalse(LightTest2Palette.Background == LightTest1Palette.Background)
        assertFalse(LightTest2Palette.OnSurface == LightTest1Palette.OnSurface)
        assertFalse(LightTest2Palette.SurfaceVariant == LightTest1Palette.SurfaceVariant)
        assertFalse(LightTest2Palette.Explanation == LightTest1Palette.Explanation)
    }

    @Test
    fun lightTest2IsNotTheOldNovaStylePalette() {
        // The previous Light Test 2 (cyan -> indigo -> purple) is gone; nothing of it survives.
        assertFalse(LightTest2Palette.Background == 0xFFF8FAFC)
        assertFalse(LightTest2Palette.SurfaceVariant == 0xFFF1F5F9)
        assertFalse(LightTest2Palette.Primary == 0xFF6366F1)
        assertFalse(LightTest2Palette.OnSurface == 0xFF111827)
        assertFalse(LightTest2Palette.Explanation == 0xFF64748B)
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
            LightTest2Palette.Warning, LightTest2Palette.Error,
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
            listOf(LightTest1Palette.Surface, LightTest1Palette.Background),
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
            listOf(LightTest2Palette.Surface, LightTest2Palette.Background),
        )
    }

    @Test
    fun lightTest2TextRolesClearAaOnTheCardToo() {
        // The contrast theme was designed so that every text role also reads on its card
        // (surfaceVariant), which is the darkest surface the UI puts text on.
        assertContentClearsAa(
            "LightTest2",
            LightTest2Palette.OnSurface,
            LightTest2Palette.OnSurfaceVariant,
            LightTest2Palette.Explanation,
            LightTest2Palette.Neutral,
            listOf(LightTest2Palette.SurfaceVariant),
        )
    }

    @Test
    fun lightTest2StatusRolesClearAaOnTheCard() {
        for ((role, value) in listOf(
            "primary" to LightTest2Palette.Primary,
            "error" to LightTest2Palette.Error,
            "success" to LightTest2Palette.Success,
            "warning" to LightTest2Palette.Warning,
            "disabled" to LightTest2Palette.Disabled,
        )) {
            val ratio = PaletteContrast.ratio(value, LightTest2Palette.SurfaceVariant)
            assertTrue(
                "LightTest2 $role contrast $ratio on the card is below AA",
                ratio >= PaletteContrast.AA,
            )
        }
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
