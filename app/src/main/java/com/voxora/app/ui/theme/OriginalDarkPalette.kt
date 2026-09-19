package com.voxora.app.ui.theme

/**
 * **Original Voxora Dark** — the product's primary identity, as raw ARGB values.
 *
 * ## Provenance
 * These are not invented and not "recoloured until they look similar". They are the values of the
 * historical implementation, verified against Git:
 *
 * - `4228f1b` *"feat: Voxora dark gold Material3 theme"* — the original theme: `Gold #D4AF37`,
 *   `GoldDim #B8962E`, `NearBlack #0A0A0B`, `SurfaceDark #141416`, `Card #1C1C1F`, text `#F5F0E6`,
 *   secondary text `#C4BBA8`, error `#E85D5D`.
 * - `f25cc5b` — the same dark values extended with the container/outline ramp and the semantic
 *   status tones (`success #3DDC84`, `warning #E6B422`, `danger #E85D5D`).
 *
 * The one deliberate change from the historical palette is the **explanation/help** role: the
 * historical warm tan `#A79E8C` read as a second gold accent rather than as guidance, so the owner
 * replaced it with a dedicated icy/electric blue, `#7DD3FC`. It is a guidance-only role — never a
 * heading, body, button or status colour — and it clears AA (11.9:1) on the near-black page and
 * (10.2:1) on a card. Because the icy blue is brighter than the warm secondary text, the historical
 * "explanation is dimmer than secondary content" ordering does not apply to this theme; the role is
 * kept distinct by hue and by its dedicated use instead.
 *
 * A later pass (`660f6c9`) replaced the gold `primary` with a Google blue and de-warmed the text;
 * this object restores the verified original. The two roles the historical code did not name —
 * [Neutral] and [Disabled] — are derived from the same warm ramp rather than imported from the
 * light themes, so the dark identity stays whole.
 *
 * ## Why raw values and not `Color`
 * The local harness cannot compile Compose, so keeping the numbers in a plain Kotlin object is what
 * lets the palette — the part with rules worth asserting — stay unit-testable on a JVM.
 * `Theme.kt` is the only place these become a `Color`.
 *
 * Voxora gold is the identity here: it is `primary`, `secondary` **and** the tint of every tonal
 * surface. There is no Google colour language and no Nova colour language in this theme.
 */
internal object OriginalDarkPalette {

    // ---- the gold identity ---------------------------------------------------------------

    /** Voxora gold. The original `primary`. */
    const val Gold: Long = 0xFFD4AF37

    /** Muted gold. The original `secondary`. */
    const val GoldDim: Long = 0xFFB8962E

    /**
     * The gold glow / accent wash behind icons and brand marks: [Gold] at 15 % alpha. This is the
     * dark appearance's value and it is **unchanged** — the light appearance states its own,
     * deliberately different glow, so the two never share a token.
     */
    const val Glow: Long = 0x26D4AF37

    // ---- surfaces ------------------------------------------------------------------------

    /** Near-black page. */
    const val NearBlack: Long = 0xFF0A0A0B

    /** The base dark surface. */
    const val SurfaceDark: Long = 0xFF141416

    /** A card on the dark surface. */
    const val Card: Long = 0xFF1C1C1F

    const val SurfaceContainerLowest: Long = 0xFF0A0A0B
    const val SurfaceContainerLow: Long = 0xFF121214
    const val SurfaceContainer: Long = 0xFF17171A
    const val SurfaceContainerHigh: Long = 0xFF1C1C1F
    const val SurfaceContainerHighest: Long = 0xFF242428

    /** Warm light text on the dark surfaces. */
    const val OnSurface: Long = 0xFFF5F0E6

    /** Muted warm secondary text. */
    const val OnSurfaceVariant: Long = 0xFFC4BBA8

    const val Outline: Long = 0xFF4E4A42
    const val OutlineVariant: Long = 0xFF2E2C28

    // ---- containers ----------------------------------------------------------------------

    const val PrimaryContainer: Long = 0xFF3A2E12
    const val OnPrimaryContainer: Long = 0xFFF6E3A8
    const val SecondaryContainer: Long = 0xFF2A2418
    const val OnSecondaryContainer: Long = 0xFFE3D2A0

    // ---- status --------------------------------------------------------------------------

    /** Stopped or failed. Matches the Live bubble's stop red. */
    const val Error: Long = 0xFFE85D5D
    const val OnError: Long = 0xFFFFFFFF
    const val ErrorContainer: Long = 0xFF4A1F1F
    const val OnErrorContainer: Long = 0xFFFFD9D9

    // ---- semantic roles Material 3 does not model ----------------------------------------

    /** Active narration / a healthy connection. */
    const val Success: Long = 0xFF3DDC84

    /** Paused, ready, preparing — the warm Voxora accent. */
    const val Warning: Long = 0xFFE6B422

    /** Stopped, failed, refused. */
    const val Danger: Long = 0xFFE85D5D

    /**
     * Idle or connecting.
     *
     * The historical code had no neutral role — the usage screen borrowed `colorScheme.outline`
     * (`#4E4A42`), which is a border value and is too dim to read as text. This is the same warm
     * family, lifted until it is legible as a status tone, so the role is honest rather than
     * inherited from a border.
     */
    const val Neutral: Long = 0xFF8E8A7E

    /**
     * Help and explanatory text: the owner's dedicated icy/electric blue.
     *
     * Used **only** for guidance — descriptions under a control, hints, privacy and limitation
     * notes — never for headings, body content, buttons or status. See the class note for why it
     * replaces the historical warm tan.
     */
    const val Explanation: Long = 0xFF7DD3FC

    /** Present but not actionable. Derived from the same warm ramp. */
    const val Disabled: Long = 0xFF6B6558
}
