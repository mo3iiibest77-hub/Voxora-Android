package com.voxora.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Contract for the Voxora colour system.
 *
 * The palette is the part of the theme worth pinning: it is pure data, and the rules it has to obey
 * are the ones a screenshot cannot check — that the four accent families are genuinely distinct,
 * that the two appearances really differ, that help text can never be mistaken for an action or a
 * status, and that every role used as text clears WCAG AA on the surface it actually sits on.
 *
 * `Theme.kt` cannot be compiled off-device (it needs Compose), which is exactly why the values live
 * in [VoxoraPalette]: the rules stay testable here while `Theme.kt` only turns them into `Color`.
 */
class VoxoraPaletteTest {

    // ---- helpers -------------------------------------------------------------------------

    private fun srgb(component: Int): Double {
        val s = component / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return 0.2126 * srgb(r) + 0.7152 * srgb(g) + 0.0722 * srgb(b)
    }

    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun alpha(argb: Long): Int = ((argb shr 24) and 0xFF).toInt()

    /** The AA bar for text; WCAG exempts disabled content, which is asserted separately. */
    private val textContrast = 4.5

    // ---- the accent families -------------------------------------------------------------

    @Test
    fun theFourAccentFamiliesAreDistinct() {
        val accents = listOf(
            VoxoraPalette.Blue,
            VoxoraPalette.Green,
            VoxoraPalette.Yellow,
            VoxoraPalette.Red,
        )
        assertEquals(accents.size, accents.distinct().size)
    }

    @Test
    fun eachAccentHasItsOwnDarkAppearanceValue() {
        assertNotEquals(VoxoraPalette.Blue, VoxoraPalette.BlueDark)
        assertNotEquals(VoxoraPalette.Green, VoxoraPalette.GreenDark)
        assertNotEquals(VoxoraPalette.Yellow, VoxoraPalette.YellowDark)
        assertNotEquals(VoxoraPalette.Red, VoxoraPalette.RedDark)
    }

    /** Blue is the action colour, so it must not be any of the three status colours. */
    @Test
    fun theActionColourIsNotAStatusColour() {
        for (status in listOf(VoxoraPalette.Green, VoxoraPalette.Yellow, VoxoraPalette.Red)) {
            assertNotEquals(VoxoraPalette.Blue, status)
            assertNotEquals(VoxoraPalette.BlueDark, status)
        }
    }

    // ---- explanation is its own role -----------------------------------------------------

    /**
     * The defect this pins: explanatory text used to inherit a warm gold/tan tone, so a sentence
     * about a control read as a brand accent. Help text must be a neutral, and it must not be
     * mistakable for an action or a status.
     */
    @Test
    fun explanationIsNeverAnActionOrAStatusColour() {
        val light = listOf(
            VoxoraPalette.Blue,
            VoxoraPalette.Green,
            VoxoraPalette.Yellow,
            VoxoraPalette.Red,
            VoxoraPalette.GoldInk,
        )
        val dark = listOf(
            VoxoraPalette.BlueDark,
            VoxoraPalette.GreenDark,
            VoxoraPalette.YellowDark,
            VoxoraPalette.RedDark,
            VoxoraPalette.Gold,
        )
        assertTrue(light.none { it == VoxoraPalette.ExplanationLight })
        assertTrue(dark.none { it == VoxoraPalette.ExplanationDark })
    }

    /** Help text is muted: lighter than the body grey on white, dimmer than it on black. */
    @Test
    fun explanationIsLessProminentThanSecondaryContent() {
        assertTrue(
            "light: help text should be lighter than secondary content",
            luminance(VoxoraPalette.ExplanationLight) >
                luminance(VoxoraPalette.OnSurfaceVariantLight),
        )
        assertTrue(
            "dark: help text should be dimmer than secondary content",
            luminance(VoxoraPalette.ExplanationDark) <
                luminance(VoxoraPalette.OnSurfaceVariantDark),
        )
    }

    // ---- gold stays narrow ---------------------------------------------------------------

    /**
     * Gold survives as the brand accent, but it must not be a status colour — otherwise a paused
     * Reader would look like a warning, or a warning like a brand moment.
     */
    @Test
    fun theBrandGoldIsNotAStatusColour() {
        for (status in listOf(VoxoraPalette.Green, VoxoraPalette.Yellow, VoxoraPalette.Red)) {
            assertNotEquals(VoxoraPalette.GoldInk, status)
            assertNotEquals(VoxoraPalette.Gold, status)
        }
        // And the deep ink is genuinely darker than the bright brand gold, so it can be text.
        assertTrue(luminance(VoxoraPalette.GoldInk) < luminance(VoxoraPalette.Gold))
    }

    // ---- opacity -------------------------------------------------------------------------

    @Test
    fun everyRoleIsFullyOpaque() {
        val roles = listOf(
            VoxoraPalette.Blue, VoxoraPalette.BlueDark,
            VoxoraPalette.Green, VoxoraPalette.GreenDark,
            VoxoraPalette.Yellow, VoxoraPalette.YellowDark,
            VoxoraPalette.Red, VoxoraPalette.RedDark,
            VoxoraPalette.ExplanationLight, VoxoraPalette.ExplanationDark,
            VoxoraPalette.NeutralLight, VoxoraPalette.NeutralDark,
            VoxoraPalette.DisabledLight, VoxoraPalette.DisabledDark,
            VoxoraPalette.OnSurfaceLight, VoxoraPalette.OnSurfaceDark,
            VoxoraPalette.OnSurfaceVariantLight, VoxoraPalette.OnSurfaceVariantDark,
            VoxoraPalette.OutlineLight, VoxoraPalette.OutlineDark,
            VoxoraPalette.OutlineVariantLight, VoxoraPalette.OutlineVariantDark,
            VoxoraPalette.SurfaceLight, VoxoraPalette.SurfaceDark,
            VoxoraPalette.SurfaceHighLight, VoxoraPalette.SurfaceHighDark,
        )
        for (role in roles) {
            assertEquals("0x${role.toString(16)} is translucent", 0xFF, alpha(role))
        }
    }

    // ---- surface hierarchy ---------------------------------------------------------------

    /** In light the card is a light grey on white; in dark it is a lifted grey on near-black. */
    @Test
    fun aCardIsDistinguishableFromTheBackgroundInBothAppearances() {
        assertNotEquals(VoxoraPalette.White, VoxoraPalette.SurfaceHighLight)
        assertTrue(
            "light: the card should be darker than the page",
            luminance(VoxoraPalette.SurfaceHighLight) < luminance(VoxoraPalette.White),
        )
        assertNotEquals(VoxoraPalette.NearBlack, VoxoraPalette.SurfaceHighDark)
        assertTrue(
            "dark: the card should be lighter than the page",
            luminance(VoxoraPalette.SurfaceHighDark) > luminance(VoxoraPalette.NearBlack),
        )
    }

    @Test
    fun theLightNeutralRampGetsDarkerAsProminenceDrops() {
        // content > secondary > help > idle status, all on the light card.
        val card = VoxoraPalette.SurfaceHighLight
        assertTrue(luminance(VoxoraPalette.OnSurfaceLight) < luminance(card))
        assertTrue(
            luminance(VoxoraPalette.OnSurfaceLight) < luminance(VoxoraPalette.OnSurfaceVariantLight),
        )
        assertTrue(
            luminance(VoxoraPalette.OnSurfaceVariantLight) < luminance(VoxoraPalette.ExplanationLight),
        )
        assertTrue(luminance(VoxoraPalette.ExplanationLight) < luminance(VoxoraPalette.DisabledLight))
    }

    // ---- contrast: every text role clears AA on the surface it sits on -------------------

    @Test
    fun lightTextRolesClearAaOnTheLightSurfaces() {
        val card = VoxoraPalette.SurfaceHighLight
        val page = VoxoraPalette.White
        for (surface in listOf(card, page)) {
            assertContrast(VoxoraPalette.OnSurfaceLight, surface, "content")
            assertContrast(VoxoraPalette.OnSurfaceVariantLight, surface, "secondary content")
            assertContrast(VoxoraPalette.ExplanationLight, surface, "explanation")
            assertContrast(VoxoraPalette.NeutralLight, surface, "neutral status")
            assertContrast(VoxoraPalette.Green, surface, "success")
            assertContrast(VoxoraPalette.Yellow, surface, "warning")
            assertContrast(VoxoraPalette.Red, surface, "danger")
        }
    }

    @Test
    fun darkTextRolesClearAaOnTheDarkSurfaces() {
        val card = VoxoraPalette.SurfaceHighDark
        val page = VoxoraPalette.NearBlack
        for (surface in listOf(card, page)) {
            assertContrast(VoxoraPalette.OnSurfaceDark, surface, "content")
            assertContrast(VoxoraPalette.OnSurfaceVariantDark, surface, "secondary content")
            assertContrast(VoxoraPalette.ExplanationDark, surface, "explanation")
            assertContrast(VoxoraPalette.NeutralDark, surface, "neutral status")
            assertContrast(VoxoraPalette.GreenDark, surface, "success")
            assertContrast(VoxoraPalette.YellowDark, surface, "warning")
            assertContrast(VoxoraPalette.RedDark, surface, "danger")
        }
    }

    /** A filled button carries its own label, so the pair has to be readable. */
    @Test
    fun thePrimaryActionLabelIsReadableOnItsContainer() {
        assertContrast(VoxoraPalette.OnBlue, VoxoraPalette.Blue, "onPrimary over primary")
        assertContrast(VoxoraPalette.OnBlueDark, VoxoraPalette.BlueDark, "onPrimary over primary")
        assertContrast(
            VoxoraPalette.OnBlueContainer,
            VoxoraPalette.BlueContainer,
            "onPrimaryContainer over primaryContainer",
        )
        assertContrast(
            VoxoraPalette.OnBlueContainerDark,
            VoxoraPalette.BlueContainerDark,
            "onPrimaryContainer over primaryContainer",
        )
    }

    /** Disabled content is exempt from AA, but it must still be visible rather than invisible. */
    @Test
    fun disabledContentIsStillVisible() {
        assertTrue(contrast(VoxoraPalette.DisabledLight, VoxoraPalette.SurfaceHighLight) > 2.0)
        assertTrue(contrast(VoxoraPalette.DisabledDark, VoxoraPalette.SurfaceHighDark) > 2.0)
    }

    private fun assertContrast(foreground: Long, background: Long, role: String) {
        val ratio = contrast(foreground, background)
        assertTrue(
            "$role has contrast ${"%.2f".format(ratio)}:1 on " +
                "0x${background.toString(16)}, below the $textContrast:1 bar",
            ratio >= textContrast,
        )
    }
}
