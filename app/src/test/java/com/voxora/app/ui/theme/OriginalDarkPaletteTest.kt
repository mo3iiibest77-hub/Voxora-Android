package com.voxora.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Original Voxora Dark theme: the restored historical identity, plus the owner's two semantic
 * redesign swaps.
 *
 * The gold identity, the surfaces and the surface ramp are pinned to the values verified against
 * Git (`4228f1b` "feat: Voxora dark gold Material3 theme", `f25cc5b`), so a later edit cannot
 * quietly drift the product's primary identity back toward a Google- or Nova-style palette. On top
 * of that, the two swaps the owner asked for are pinned explicitly: the status and explanation hues
 * exchanged places, and the two text roles exchanged their values. Those two tests fail if either
 * swap is reverted.
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
    fun theTwoTextRolesCarryTheSwappedValues() {
        // The redesign moved primary headings onto the khaki and supporting text onto the cream.
        assertEquals(0xFFC4BBA8, OriginalDarkPalette.OnSurface)
        assertEquals(0xFFF5F0E6, OriginalDarkPalette.OnSurfaceVariant)
    }

    @Test
    fun primaryContentIsDeliberatelyDimmerThanSupportingContent() {
        // The documented consequence of the text swap: the roles are ordered by purpose, not by
        // luminance. If someone "fixes" this by brightening OnSurface, that is a design change and
        // this test is where it has to be a conscious one.
        val primary = PaletteContrast.luminance(OriginalDarkPalette.OnSurface)
        val supporting = PaletteContrast.luminance(OriginalDarkPalette.OnSurfaceVariant)
        assertTrue(
            "OnSurface ($primary) is expected to be the dimmer of the two text roles",
            primary < supporting,
        )
    }

    @Test
    fun theStatusColoursAreTheHistoricalValues() {
        // Success now carries the icy blue — see the semantic swap below.
        assertEquals(0xFF7DD3FC, OriginalDarkPalette.Success)
        assertEquals(0xFFE6B422, OriginalDarkPalette.Warning)
        assertEquals(0xFFE85D5D, OriginalDarkPalette.Danger)
        assertEquals(0xFFE85D5D, OriginalDarkPalette.Error)
    }

    @Test
    fun theStatusAndExplanationRolesExchangedTheirHues() {
        // The semantic swap, pinned as a swap rather than as two loose values: status took the icy
        // blue and guidance took the green. Reverting either half fails here.
        assertEquals(0xFF7DD3FC, OriginalDarkPalette.Success)
        assertEquals(0xFF3DDC84, OriginalDarkPalette.Explanation)
        // The decorative waveform stop `VoxoraBrand.waveGreen` is a *different* value (#3DDC97) and
        // is deliberately not swept up in the swap. It is asserted here by value because
        // `VoxoraBrand` lives in the Compose-only Theme.kt, which this pure-JVM suite cannot compile.
        assertFalse(OriginalDarkPalette.Explanation == 0xFF3DDC97)
        assertFalse(OriginalDarkPalette.Success == 0xFF3DDC97)
    }

    @Test
    fun theGoldGlowIsTheUnchangedDarkWash() {
        // The accent wash behind icons used to be `primary.copy(alpha = 0.15f)`. The theme token now
        // carries the same value, so the dark appearance renders exactly as it did before.
        assertEquals(0x26D4AF37, OriginalDarkPalette.Glow)
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
        // Guidance is kept distinct by hue and by dedicated use rather than by being the dimmest
        // role: after the text swap it is neither the brightest nor the dimmest of the text tones,
        // so the contract that matters is that it shares its value with nothing else. A screen that
        // asks for "help" must never accidentally get content or a status tone.
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
