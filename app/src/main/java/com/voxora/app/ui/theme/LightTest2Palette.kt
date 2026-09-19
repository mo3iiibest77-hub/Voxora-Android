package com.voxora.app.ui.theme

/**
 * **Voxora Light — the warm cream and gold-leaf appearance** — raw ARGB values.
 *
 * This is a **complete, independent light appearance**, not an override of [OriginalDarkPalette].
 * It owns every value below; it shares no constant and no mutable state with the dark palette, so a
 * change here can never alter the dark theme and a change there can never alter this one.
 *
 * It occupies the `ThemeMode.LIGHT_TEST_2` slot because that is the stored preference id; the
 * identifier is historical and is kept so a user's saved choice keeps working. The *appearance* is
 * the final Voxora Light: warm parchment surfaces, a darker gold identity, and warm brown shadows —
 * "daytime premium / parchment / gold leaf", deliberately not sterile white Material, not an
 * Apple-style white UI, and not the generic Android light mode.
 *
 * ## The palette is supplied verbatim
 * Every value the owner specified is used **exactly** as given; nothing is darkened, lightened or
 * "corrected" to satisfy a contrast target. The measured WCAG ratios are documented below instead,
 * so the limitation is visible rather than silently designed around. `LightTestPalettesTest` pins
 * the supplied values and asserts AA only for the roles and surfaces where it actually holds.
 *
 * ## Measured contrast (WCAG 2.x, on the page `#F5F2EC` / surface `#EDE9DF` / card `#E4DFD3`)
 *
 * | Role | Page | Surface | Card |
 * |------|------|---------|------|
 * | `OnSurface` #1A1610 | 16.12 | 14.85 | 13.54 |
 * | `OnSurfaceVariant` #4A4438 | 8.64 | 7.96 | 7.26 |
 * | `Explanation` #0369A1 | 5.31 | 4.89 | 4.46 |
 * | `Neutral` #6B6558 | 5.18 | 4.78 | 4.35 |
 * | `Primary` (gold) #8B6914 | 4.55 | 4.20 | 3.83 |
 * | `Secondary` (dim gold) #A07820 | 3.61 | 3.33 | 3.04 |
 * | `Success` #1A9E57 | 3.10 | 2.85 | 2.60 |
 * | `Warning` #B88A10 | 2.81 | 2.59 | 2.36 |
 * | `Error` #C0392B | 4.87 | 4.49 | 4.09 |
 *
 * **AA holds for the content and help roles on the page and the surface** (the primary reading
 * surfaces). It is short on the card for `Explanation` (4.46) and `Neutral` (4.35) — a hair below
 * 4.5 — and the accent/status tones are below AA as *text* on the neutral card by design; they are
 * used as indicators and as fills paired with their own `on*` colour, where the pair clears AA
 * (`onPrimary`/`primary` 5.09, `onPrimaryContainer`/`primaryContainer` 6.75,
 * `onSecondaryContainer`/`secondaryContainer` 7.91, `onError`/`error` 5.44,
 * `onErrorContainer`/`errorContainer` 8.10). `Disabled` is exempt but stays visible (2.15–2.56).
 *
 * ## Why raw values and not `Color`
 * The local harness cannot compile Compose, so keeping the numbers in a plain Kotlin object is what
 * lets the palette stay unit-testable on a JVM. `Theme.kt` is the only place these become a `Color`.
 */
internal object LightTest2Palette {

    // ---- surfaces: warm cream, never white ------------------------------------------------

    /** The warm cream page. Deliberately not `#FFFFFF`. */
    const val Background: Long = 0xFFF5F2EC

    /** The base light surface. */
    const val Surface: Long = 0xFFEDE9DF

    /** Card / secondary surface: grouped rows, the selector's resting fill. */
    const val SurfaceVariant: Long = 0xFFE4DFD3

    const val SurfaceContainerLowest: Long = 0xFFFAF8F3
    const val SurfaceContainerLow: Long = 0xFFF5F2EC
    const val SurfaceContainer: Long = 0xFFEAE6DC
    const val SurfaceContainerHigh: Long = 0xFFDEDAD0
    const val SurfaceContainerHighest: Long = 0xFFD5D0C4

    // ---- borders: visible but soft, never harsh black --------------------------------------

    /** A stronger border, for a focused or selected outline. */
    const val Outline: Long = 0xFFB8B0A0

    /** Subtle border. */
    const val OutlineVariant: Long = 0xFFD0C9BC

    // ---- text hierarchy -------------------------------------------------------------------

    /** Primary content and screen titles. */
    const val OnSurface: Long = 0xFF1A1610

    /** Secondary content: supporting labels, values, subtitles. */
    const val OnSurfaceVariant: Long = 0xFF4A4438

    /** Help and explanatory text: the owner's deeper navy-blue information tone. */
    const val Explanation: Long = 0xFF0369A1

    /** Idle or connecting. Neither good nor bad. */
    const val Neutral: Long = 0xFF6B6558

    /** Present but not actionable. */
    const val Disabled: Long = 0xFFA09888

    // ---- the gold identity ----------------------------------------------------------------

    /** The darker Voxora Light gold. Primary action and the tint of the tonal surfaces. */
    const val Primary: Long = 0xFF8B6914

    const val OnPrimary: Long = 0xFFFFFFFF
    const val PrimaryContainer: Long = 0xFFF0E4B8
    const val OnPrimaryContainer: Long = 0xFF5C4A10

    /** The dim gold secondary accent. */
    const val Secondary: Long = 0xFFA07820

    /**
     * Content on a [Secondary] fill.
     *
     * Not supplied by the owner; derived as the same warm near-black as [OnSurface]. No component
     * paints text on a `secondary` fill today, so this pair is declared for completeness and
     * measures 4.46:1 if it is ever used.
     */
    const val OnSecondary: Long = OnSurface

    const val SecondaryContainer: Long = 0xFFE8DFC8
    const val OnSecondaryContainer: Long = 0xFF4A3E20

    /** Tertiary stays in the gold family so the appearance reads as one warm system. */
    const val Tertiary: Long = Secondary
    const val OnTertiary: Long = OnSecondary
    const val TertiaryContainer: Long = SecondaryContainer
    const val OnTertiaryContainer: Long = OnSecondaryContainer

    // ---- status --------------------------------------------------------------------------

    const val Success: Long = 0xFF1A9E57
    const val Warning: Long = 0xFFB88A10
    const val Error: Long = 0xFFC0392B

    /** Stopped, failed, refused — the same value as [Error], as in the dark palette. */
    const val Danger: Long = Error

    const val OnError: Long = 0xFFFFFFFF
    const val ErrorContainer: Long = 0xFFFAD7D7
    const val OnErrorContainer: Long = 0xFF7A1515

    // ---- the light design language ---------------------------------------------------------

    /**
     * The gold glow / accent wash behind icons and brand marks: `rgba(139,105,20,0.12)` — the
     * supplied [Primary] at 12 % alpha. Deliberately **not** the dark theme's gold-at-15 % wash.
     */
    const val Glow: Long = 0x1F8B6914

    /**
     * The warm brown shadow tone: `rgba(100,80,30,0.12)`. The declared shadow language for this
     * appearance — never a generic `rgba(0,0,0,…)`. Nothing draws a custom shadow today (the UI
     * uses tonal elevation, not drop shadows), so this is a declared token rather than a call site.
     */
    const val Shadow: Long = 0x1F64501E
}
