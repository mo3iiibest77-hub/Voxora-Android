package com.voxora.app.ui.theme

/**
 * **Light Test 1 — "Voxora Light — Nova Inspired"** — raw ARGB values.
 *
 * One of two independent light candidates, added so the owner can compare them on a device. This is
 * *Voxora using a light theme inspired by Nova's modern language*, not a Nova clone: the layouts,
 * components and hierarchy are untouched — only the colour language differs.
 *
 * ## Independence is a hard requirement
 * This object is a **complete** colour definition. It shares no value, no constant and no mutable
 * state with [LightTest2Palette] or with [OriginalDarkPalette]; the three are separate systems by
 * construction. Removing this theme later means deleting this file, its `ThemeMode` entry, its
 * scheme block in `Theme.kt` and its string — and nothing else. It must never be implemented as
 * "the other light theme with a few overrides".
 *
 * ## Why raw values and not `Color`
 * The local harness cannot compile Compose, so keeping the numbers in a plain Kotlin object is what
 * lets the palette stay unit-testable on a JVM. `Theme.kt` is the only place these become a `Color`.
 *
 * Design language: bright, clean, premium — a light neutral page, white cards, subtle borders, and
 * cyan/indigo/violet accents with a restrained gradient.
 */
internal object LightTest1Palette {

    // ---- surfaces ------------------------------------------------------------------------

    /** Light neutral page. */
    const val Background: Long = 0xFFF8F9FC

    /** White cards and sheets. */
    const val Surface: Long = 0xFFFFFFFF

    /** Secondary surface: grouped rows, inert chips, the selector's resting fill. */
    const val SurfaceVariant: Long = 0xFFF1F3F8

    /** Subtle border. */
    const val OutlineVariant: Long = 0xFFE1E5EE

    const val SurfaceContainerLowest: Long = 0xFFFFFFFF
    const val SurfaceContainerLow: Long = 0xFFF8F9FC
    const val SurfaceContainer: Long = 0xFFF1F3F8
    const val SurfaceContainerHigh: Long = 0xFFECEFF6
    const val SurfaceContainerHighest: Long = 0xFFE6E9F2

    /** A stronger border, for a focused or selected outline. */
    const val Outline: Long = 0xFF6B7280

    // ---- text hierarchy ------------------------------------------------------------------

    /** Primary content and screen titles. */
    const val OnSurface: Long = 0xFF171923

    /** Secondary content: supporting labels, values, subtitles. */
    const val OnSurfaceVariant: Long = 0xFF596174

    /** Help and explanatory text. Deliberately lighter than secondary content. */
    const val Explanation: Long = 0xFF64748B

    /** Present but not actionable. */
    const val Disabled: Long = 0xFF9AA3B2

    /** Idle or connecting. Neither good nor bad. Distinct from secondary content and from help. */
    const val Neutral: Long = 0xFF6B7280

    // ---- accents -------------------------------------------------------------------------

    /** Indigo — primary action and the selected state. */
    const val Primary: Long = 0xFF4F46E5
    const val OnPrimary: Long = 0xFFFFFFFF
    const val PrimaryContainer: Long = 0xFFE0E7FF
    const val OnPrimaryContainer: Long = 0xFF1E1B4B

    /** Cyan — the secondary accent. */
    const val Secondary: Long = 0xFF0891B2
    const val OnSecondary: Long = 0xFFFFFFFF
    const val SecondaryContainer: Long = 0xFFCFFAFE
    const val OnSecondaryContainer: Long = 0xFF083344

    /** Violet — the tertiary accent. */
    const val Tertiary: Long = 0xFF7C3AED
    const val OnTertiary: Long = 0xFFFFFFFF
    const val TertiaryContainer: Long = 0xFFEDE9FE
    const val OnTertiaryContainer: Long = 0xFF2E1065

    /** The fourth declared accent of this theme's identity. */
    const val AccentPurple: Long = 0xFF9333EA

    // ---- restrained gradient -------------------------------------------------------------

    /**
     * The declared gradient stops. Part of this theme's identity; no new UI consumes them yet,
     * because this feature is not allowed to restructure screens.
     */
    const val GradientStart: Long = 0xFF22D3EE
    const val GradientMiddle: Long = 0xFF818CF8
    const val GradientEnd: Long = 0xFFA855F7

    // ---- status --------------------------------------------------------------------------

    const val Success: Long = 0xFF16A34A
    const val Warning: Long = 0xFFD97706
    const val Danger: Long = 0xFFDC2626
    const val Error: Long = 0xFFDC2626
    const val OnError: Long = 0xFFFFFFFF
    const val ErrorContainer: Long = 0xFFFEE2E2
    const val OnErrorContainer: Long = 0xFF450A0A
}
