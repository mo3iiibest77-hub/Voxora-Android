package com.voxora.app.ui.theme

/**
 * **Light Test 2 — "Nova-style Light"** — raw ARGB values.
 *
 * The second of two independent light candidates, added so the owner can compare them on a device.
 * This one follows Nova's light colour language more closely than [LightTest1Palette]: the cyan →
 * indigo → purple ramp is the identity, not a supporting accent.
 *
 * ## Independence is a hard requirement
 * This object is a **complete** colour definition. It shares no value, no constant and no mutable
 * state with [LightTest1Palette] or with [OriginalDarkPalette]. In particular it is **not**
 * "Light Test 1 plus a few overrides": every role below is stated here, and the two light themes
 * agree on nothing by inheritance. Removing this theme later means deleting this file, its
 * `ThemeMode` entry, its scheme block in `Theme.kt` and its string — and nothing else.
 *
 * ## Scope
 * Only the colour language changes. Nova's layouts, components and structure are **not** copied —
 * the same Voxora UI renders under this theme as under the others.
 *
 * ## Why raw values and not `Color`
 * The local harness cannot compile Compose, so keeping the numbers in a plain Kotlin object is what
 * lets the palette stay unit-testable on a JVM. `Theme.kt` is the only place these become a `Color`.
 */
internal object LightTest2Palette {

    // ---- surfaces ------------------------------------------------------------------------

    /** Light neutral page. */
    const val Background: Long = 0xFFF8FAFC

    /** White cards and sheets. */
    const val Surface: Long = 0xFFFFFFFF

    /** Secondary surface: grouped rows, inert chips, the selector's resting fill. */
    const val SurfaceVariant: Long = 0xFFF1F5F9

    /** Subtle border. */
    const val OutlineVariant: Long = 0xFFE2E8F0

    const val SurfaceContainerLowest: Long = 0xFFFFFFFF
    const val SurfaceContainerLow: Long = 0xFFF8FAFC
    const val SurfaceContainer: Long = 0xFFF1F5F9
    const val SurfaceContainerHigh: Long = 0xFFE9EEF5
    const val SurfaceContainerHighest: Long = 0xFFE2E8F0

    /** A stronger border, for a focused or selected outline. */
    const val Outline: Long = 0xFF64748B

    // ---- text hierarchy ------------------------------------------------------------------

    /** Primary content and screen titles. */
    const val OnSurface: Long = 0xFF111827

    /** Secondary content: supporting labels, values, subtitles. */
    const val OnSurfaceVariant: Long = 0xFF475569

    /** Muted help and explanatory text. Deliberately lighter than secondary content. */
    const val Explanation: Long = 0xFF64748B

    /** Present but not actionable. */
    const val Disabled: Long = 0xFF94A3B8

    /** Idle or connecting. Neither good nor bad. Distinct from secondary content and from help. */
    const val Neutral: Long = 0xFF6B7280

    // ---- accents -------------------------------------------------------------------------

    /** Indigo — primary action and the selected state. */
    const val Primary: Long = 0xFF6366F1
    const val OnPrimary: Long = 0xFFFFFFFF
    const val PrimaryContainer: Long = 0xFFE0E7FF
    const val OnPrimaryContainer: Long = 0xFF1E1B4B

    /** Cyan, and its readable dark member. */
    const val Cyan: Long = 0xFF22D3EE
    const val CyanDark: Long = 0xFF0891B2

    /** Cyan dark carries the secondary accent role, because the bright cyan is a fill only. */
    const val Secondary: Long = CyanDark
    const val OnSecondary: Long = 0xFFFFFFFF
    const val SecondaryContainer: Long = 0xFFCFFAFE
    const val OnSecondaryContainer: Long = 0xFF083344

    /** Indigo, and its darker member. */
    const val Indigo: Long = 0xFF6366F1
    const val IndigoDark: Long = 0xFF4F46E5

    /** Violet — the tertiary accent. */
    const val Violet: Long = 0xFF8B5CF6
    const val Tertiary: Long = Violet
    const val OnTertiary: Long = 0xFFFFFFFF
    const val TertiaryContainer: Long = 0xFFEDE9FE
    const val OnTertiaryContainer: Long = 0xFF2E1065

    /** The fourth declared accent of this theme's identity. */
    const val Purple: Long = 0xFFA855F7

    // ---- the cyan → indigo → purple ramp -------------------------------------------------

    /**
     * The declared gradient stops. This ramp is the theme's identity; no new UI consumes it yet,
     * because this feature is not allowed to restructure screens.
     */
    const val GradientStart: Long = 0xFF22D3EE
    const val GradientMiddle: Long = 0xFF6366F1
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
