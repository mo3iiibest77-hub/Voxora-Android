package com.voxora.app.ui.theme

/**
 * **Light Test 2 — "Voxora Contrast Light"** — raw ARGB values.
 *
 * The light-side contrast of the original Voxora dark theme. The concept is a deliberate mirror of
 * [OriginalDarkPalette]'s visual language:
 *
 * | Original Dark            | Contrast Light                    |
 * |--------------------------|-----------------------------------|
 * | near-black page          | cool near-white page              |
 * | dark charcoal surfaces   | cool light surfaces               |
 * | gold primary accent      | refined indigo/blue complement    |
 * | warm beige text          | cool slate text                   |
 *
 * It is **not** Light Test 1 with different accents and it is **not** a Nova palette. Every value
 * below is stated here; the object shares no constant or mutable state with [LightTest1Palette] or
 * with [OriginalDarkPalette], so either light theme can be deleted without touching the others.
 *
 * ## Contrast verification and the two required adjustments
 * The owner supplied the target values together with an explicit instruction to verify WCAG
 * contrast and make the smallest adjustment required for legibility. Two supplied values are not
 * usable as *text* on these light surfaces:
 *
 * - `Error` was specified as the vivid cyan `#5DE8E8`; on this card it is **1.22:1** — invisible.
 *   It is darkened to `#0B6E8A`, the same cyan family, which reads at **4.77:1** on the card. The
 *   vivid cyan survives as the error container tint (`#CCF2F5`).
 * - `Success` was specified as the vivid magenta `#DC3D95`; on this card it is **3.35:1**. It is
 *   darkened to `#BE185D`, the same magenta family, at **4.95:1**.
 *
 * `Explanation` `#6B7384` measured 3.91:1 on the card, so it is darkened minimally to `#5F6775`
 * (4.68:1) while staying lighter than `OnSurfaceVariant`, i.e. still the less prominent role.
 * `Warning` `#2254E6` (4.95:1), `Primary` `#375CD4` (4.73:1) and every content role already clear
 * AA and are kept exactly as specified. `Neutral` was not supplied; it is derived as a cool slate
 * distinct from both `OnSurfaceVariant` and `Explanation`, at 4.74:1.
 *
 * ## Why raw values and not `Color`
 * The local harness cannot compile Compose, so keeping the numbers in a plain Kotlin object is what
 * lets the palette stay unit-testable on a JVM. `Theme.kt` is the only place these become a `Color`.
 */
internal object LightTest2Palette {

    // ---- surfaces ------------------------------------------------------------------------

    /** Cool near-white page — the light counterpart of the dark theme's near-black. */
    const val Background: Long = 0xFFF5F7FB

    /** The base cool light surface. */
    const val Surface: Long = 0xFFEDF1F7

    /** Card / secondary surface: grouped rows, the selector's resting fill. */
    const val SurfaceVariant: Long = 0xFFE4E9F1

    /** Subtle border. */
    const val OutlineVariant: Long = 0xFFD3D9E3

    const val SurfaceContainerLowest: Long = 0xFFFFFFFF
    const val SurfaceContainerLow: Long = 0xFFF5F7FB
    const val SurfaceContainer: Long = 0xFFEDF1F7
    const val SurfaceContainerHigh: Long = 0xFFE4E9F1
    const val SurfaceContainerHighest: Long = 0xFFDCE2EC

    /** A stronger border, for a focused or selected outline. */
    const val Outline: Long = 0xFF667085

    // ---- text hierarchy ------------------------------------------------------------------

    /** Primary content and screen titles — the cool counterpart of the warm light text. */
    const val OnSurface: Long = 0xFF101827

    /** Secondary content: supporting labels, values, subtitles. */
    const val OnSurfaceVariant: Long = 0xFF4B5870

    /**
     * Help and explanatory text. Less prominent than secondary content by design; see the class
     * note for the minimal darkening from the supplied `#6B7384` to clear AA on the card.
     */
    const val Explanation: Long = 0xFF5F6775

    /** Present but not actionable. */
    const val Disabled: Long = 0xFF585E6B

    /** Idle or connecting. Derived: a cool slate distinct from secondary content and from help. */
    const val Neutral: Long = 0xFF556687

    // ---- accents -------------------------------------------------------------------------

    /** The refined indigo/blue that replaces the dark theme's gold as the primary action colour. */
    const val Primary: Long = 0xFF375CD4

    /** The deeper member of the same blue, for containers and the secondary/tertiary accents. */
    const val PrimaryDim: Long = 0xFF2E50B8

    const val OnPrimary: Long = 0xFFFFFFFF
    const val PrimaryContainer: Long = 0xFFDDE5FF
    const val OnPrimaryContainer: Long = 0xFF1D2E6B

    /** Secondary accent — the deeper blue of the same family. */
    const val Secondary: Long = PrimaryDim
    const val OnSecondary: Long = 0xFFFFFFFF
    const val SecondaryContainer: Long = 0xFFE1E7F0
    const val OnSecondaryContainer: Long = 0xFF2A3444

    /** Tertiary accent — the same blue family keeps the theme monochrome-cool and on-concept. */
    const val Tertiary: Long = PrimaryDim
    const val OnTertiary: Long = 0xFFFFFFFF
    const val TertiaryContainer: Long = 0xFFE1E7F0
    const val OnTertiaryContainer: Long = 0xFF2A3444

    // ---- status --------------------------------------------------------------------------

    /**
     * The complementary of the dark theme's success green: a magenta. Adjusted from the supplied
     * `#DC3D95` to `#BE185D` for text legibility (see the class note).
     */
    const val Success: Long = 0xFFBE185D

    /** The complementary of the dark theme's gold warning: a strong blue. Kept as supplied. */
    const val Warning: Long = 0xFF2254E6

    /**
     * The complementary of the dark theme's error red: a cyan. Adjusted from the supplied
     * `#5DE8E8` to `#0B6E8A` for text legibility (see the class note).
     */
    const val Error: Long = 0xFF0B6E8A

    /** Stopped, failed, refused — the same value as [Error], as in the dark palette. */
    const val Danger: Long = Error

    const val OnError: Long = 0xFFFFFFFF

    /** The light tint of the supplied vivid cyan, so the error family stays visible as a fill. */
    const val ErrorContainer: Long = 0xFFCCF2F5
    const val OnErrorContainer: Long = 0xFF083B44
}
