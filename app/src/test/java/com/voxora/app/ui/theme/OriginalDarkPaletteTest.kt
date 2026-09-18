package com.voxora.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Original Voxora Dark theme is restored from the verified historical implementation, not
 * guessed. These tests pin the exact values from Git commits `4228f1b` ("feat: Voxora dark gold
 * Material3 theme") and `f25cc5b`, so a later edit cannot quietly drift the product's primary
 * identity back toward a Google- or Nova-style palette.
 */
class OriginalDarkPaletteTest {

    private val allValues = listOf(
        OriginalDarkPalette.Gold,
        OriginalDarkPalette.GoldDim,
        OriginalDarkPalette.NearBlack,
        OriginalDarkPalette.SurfaceDark,
        OriginalDarkPalette.Card,
        OriginalDarkPalette.SurfaceContainerLowest,
        OriginalDarkPalette.SurfaceContainerLow,
        OriginalDarkPalette.SurfaceContainer,
        OriginalDarkPalette.SurfaceContainerHigh,
        OriginalDarkPalette.SurfaceContainerHighest,
        OriginalDarkPalette.OnSurface,
        OriginalDarkPalette.OnSurfaceVariant,
        OriginalDarkPalette.Outline,
        OriginalDarkPalette.OutlineVariant,
        OriginalDarkPalette.PrimaryContainer,
        OriginalDarkPalette.OnPrimaryContainer,
        OriginalDarkPalette.SecondaryContainer,
        OriginalDarkPalette.OnSecondaryContainer,
        OriginalDarkPalette.Error,
        OriginalDarkPalette.OnError,
        OriginalDarkPalette.ErrorContainer,
        OriginalDarkPalette.OnErrorContainer,
        OriginalDarkPalette.Success,
        OriginalDarkPalette.Warning,
        OriginalDarkPalette.Danger,
        OriginalDarkPalette.Neutral,
        OriginalDarkPalette.Explanation,
        OriginalDarkPalette.Disabled,
    )

    @Test
    fun theGoldIdentityIsTheHistoricalValue() {
        assertEquals(0xFFD4AF37, OriginalDarkPalette.Gold)
        assertEquals(0xFFB8962E, OriginalDarkPalette.GoldDim)
    }

    @Test
    fun theSurfacesAreTheHistoricalValues() {
        assertEquals(0xFF0A0A0B, OriginalDarkPalette.NearBlack)
        assertEquals(0xFF141416, OriginalDarkPalette.SurfaceDark)
        assertEquals(0xFF1C1C1F, OriginalDarkPalette.Card)
    }

    @Test
    fun theTextColoursAreTheHistoricalWarmValues() {
        assertEquals(0xFFF5F0E6, OriginalDarkPalette.OnSurface)
        assertEquals(0xFFC4BBA8, OriginalDarkPalette.OnSurfaceVariant)
    }

    @Test
    fun theStatusColoursAreTheHistoricalValues() {
        assertEquals(0xFF3DDC84, OriginalDarkPalette.Success)
        assertEquals(0xFFE6B422, OriginalDarkPalette.Warning)
        assertEquals(0xFFE85D5D, OriginalDarkPalette.Danger)
        assertEquals(0xFFE85D5D, OriginalDarkPalette.Error)
    }

    @Test
    fun theExplanationRoleIsTheOwnersDedicatedIcyElectricBlue() {
        // The one deliberate change from the historical palette: the warm tan help colour read as a
        // second gold accent, so guidance text is now a dedicated icy/electric blue.
        assertEquals(0xFF7DD3FC, OriginalDarkPalette.Explanation)
    }

    @Test
    fun theIdentityIsGoldAndNotTheBlueTheBrokenPassIntroduced() {
        // 660f6c9 had made the dark primary a lifted Google blue; the original is gold.
        assertEquals(0xFFD4AF37, OriginalDarkPalette.Gold)
        assertFalse(OriginalDarkPalette.Gold == 0xFF8AB4F8)
        assertFalse(OriginalDarkPalette.Gold == 0xFF1967D2)
    }

    @Test
    fun thePaletteContainsNoGoogleColourLanguage() {
        val googleFamily = setOf(
            0xFF1967D2, 0xFF8AB4F8, 0xFF137333, 0xFF81C995,
            0xFF8A5200, 0xFFFDD663, 0xFFB3261E, 0xFFF28B82,
            0xFFD3E3FD, 0xFF041E49,
        )
        val leaked = allValues.filter { it in googleFamily }
        assertTrue("dark palette leaked Google colours: $leaked", leaked.isEmpty())
    }

    @Test
    fun theDeWarmedNeutralsOfTheBrokenPassAreGone() {
        // 660f6c9 used these cool greys for the dark text roles.
        val deWarmed = setOf(0xFFE3E3E3, 0xFFC4C7C5, 0xFF8E9195, 0xFF3C4043)
        val leaked = allValues.filter { it in deWarmed }
        assertTrue("dark palette still has de-warmed neutrals: $leaked", leaked.isEmpty())
    }

    @Test
    fun everyValueIsFullyOpaque() {
        for (value in allValues) {
            assertTrue("not opaque: $value", PaletteContrast.isOpaque(value))
        }
    }

    @Test
    fun contentTextClearsAaOnBothTheCardAndThePage() {
        for (background in listOf(OriginalDarkPalette.SurfaceDark, OriginalDarkPalette.Card, OriginalDarkPalette.NearBlack)) {
            assertClearsAa(OriginalDarkPalette.OnSurface, background, "onSurface")
            assertClearsAa(OriginalDarkPalette.OnSurfaceVariant, background, "onSurfaceVariant")
            assertClearsAa(OriginalDarkPalette.Explanation, background, "explanation")
            assertClearsAa(OriginalDarkPalette.Neutral, background, "neutral")
        }
    }

    @Test
    fun theCardIsDistinguishableFromThePage() {
        assertTrue(OriginalDarkPalette.Card != OriginalDarkPalette.SurfaceDark)
        assertTrue(OriginalDarkPalette.SurfaceDark != OriginalDarkPalette.NearBlack)
    }

    @Test
    fun theExplanationRoleIsDistinctFromEveryContentAndStatusRole() {
        // The historical "explanation is dimmer than secondary content" ordering does not hold for
        // the icy blue, which is brighter than the warm secondary text. The contract that replaces
        // it is distinctness plus dedicated use: guidance is the only role with this value, so a
        // screen that asks for "help" never accidentally gets content or a status tone.
        val others = listOf(
            OriginalDarkPalette.OnSurface,
            OriginalDarkPalette.OnSurfaceVariant,
            OriginalDarkPalette.Gold,
            OriginalDarkPalette.GoldDim,
            OriginalDarkPalette.Success,
            OriginalDarkPalette.Warning,
            OriginalDarkPalette.Danger,
            OriginalDarkPalette.Neutral,
            OriginalDarkPalette.Disabled,
        )
        assertFalse(
            "explanation shares a value with content or a status role",
            OriginalDarkPalette.Explanation in others,
        )
    }

    @Test
    fun theSurfaceRampIsOrderedFromDarkestToLightest() {
        val ramp = listOf(
            OriginalDarkPalette.SurfaceContainerLowest,
            OriginalDarkPalette.SurfaceContainerLow,
            OriginalDarkPalette.SurfaceContainer,
            OriginalDarkPalette.SurfaceContainerHigh,
            OriginalDarkPalette.SurfaceContainerHighest,
        ).map { PaletteContrast.luminance(it) }
        for (i in 1 until ramp.size) {
            assertTrue("surface ramp not monotonic at $i: $ramp", ramp[i] >= ramp[i - 1])
        }
    }

    @Test
    fun theStatusRolesAreDistinctFromEachOtherAndFromContent() {
        val statuses = listOf(
            OriginalDarkPalette.Success,
            OriginalDarkPalette.Warning,
            OriginalDarkPalette.Danger,
            OriginalDarkPalette.Neutral,
        )
        assertEquals(statuses.size, statuses.distinct().size)
        assertFalse(OriginalDarkPalette.Success == OriginalDarkPalette.OnSurface)
        assertFalse(OriginalDarkPalette.Warning == OriginalDarkPalette.Gold)
        assertFalse(OriginalDarkPalette.Neutral == OriginalDarkPalette.Explanation)
    }

    private fun assertClearsAa(foreground: Long, background: Long, role: String) {
        val ratio = PaletteContrast.ratio(foreground, background)
        assertTrue(
            "$role contrast $ratio on $background is below AA",
            ratio >= PaletteContrast.AA,
        )
    }
}
