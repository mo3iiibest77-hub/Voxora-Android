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
 * ## The current redesign — two semantic swaps
 *
 * The owner asked for two **semantic** swaps, not a recolouring pass. Both were applied here and
 * nowhere else; the light appearance states its own values and is deliberately untouched.
 *
 * 1. **The status and explanation roles exchanged their hues.** The green `#3DDC84` and the icy
 *    blue `#7DD3FC` traded places: [Success] is now the icy blue, [Explanation] is now the green.
 *    The role each colour *means* is unchanged — success is still "active narration / a healthy
 *    connection", explanation is still "help and guidance" — only the hue carrying each meaning
 *    moved. `VoxoraBrand.waveGreen #3DDC97` is **not** part of this role and is deliberately left
 *    alone: it is a decorative waveform stop, not a status colour.
 * 2. **The two text roles exchanged their values.** [OnSurface] is now the khaki `#C4BBA8` and
 *    [OnSurfaceVariant] the cream `#F5F0E6`, so primary headings render in the khaki and the
 *    supporting/information text in the cream.
 *
 * ### The consequence, stated plainly
 * After swap 2 the **primary content role is the dimmer of the two** (`#C4BBA8` luminance 0.501
 * against `#F5F0E6` at 0.875). That inverts the usual "primary is the brightest" expectation, and
 * it is intentional: the roles are ordered by **purpose**, not by luminance, which is the rule
 * `AGENTS.md` §3 already states for this theme. A screen must still ask for the role it means —
 * `onSurface` for content, `onSurfaceVariant` for supporting labels — and must never pick between
 * them by which one looks stronger.
 *
 * ### Contrast (WCAG 2.x, measured on the three dark surfaces)
 *
 * | Role | NearBlack `#0A0A0B` | SurfaceDark `#141416` | Card `#1C1C1F` |
 * |------|------|------|------|
 * | `OnSurface` `#C4BBA8` | 10.39 | 9.66 | 8.92 |
 * | `OnSurfaceVariant` `#F5F0E6` | 17.42 | 16.20 | 14.97 |
 * | `Explanation` `#3DDC84` | 11.09 | 10.31 | 9.53 |
 * | `Neutral` `#8E8A7E` | 5.74 | 5.33 | 4.93 |
 *
 * Every content and help role clears AA (4.5) on all three surfaces; `Success` `#7DD3FC` measures
 * 11.87 / 11.04 / 10.20. The ratios are re-derived by `OriginalDarkPaletteTest` rather than trusted
 * from this table.
 *
 * A later pass (`660f6c9`) replaced the gold `primary` with a Google blue and de-warmed the text;
 * this object restores the verified original. The two roles the historical code did not name —
 * [Neutral] and [Disabled] — are derived from the same warm ramp rather than imported from the
 * light theme, so the dark identity stays whole.
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

    /**
     * Primary content and screen titles.
     *
     * The khaki. It is the **dimmer** of the two text roles (luminance 0.501 against
     * [OnSurfaceVariant]'s 0.875) — the roles are ordered by purpose, not by brightness. See the
     * class note.
     */
    const val OnSurface: Long = 0xFFC4BBA8

    /** Supporting content: labels, values, subtitles. The cream, and the brighter text role. */
    const val OnSurfaceVariant: Long = 0xFFF5F0E6

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

    /** Active narration / a healthy connection. The icy blue. */
    const val Success: Long = 0xFF7DD3FC

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
     * Help and explanatory text: the green.
     *
     * Used **only** for guidance — descriptions under a control, hints, privacy and limitation
     * notes — never for headings, body content, buttons or status. It is the hue the status role
     * [Success] used to carry; the two exchanged places in the redesign, and the role each colour
     * means did not change. It is deliberately **not** `VoxoraBrand.waveGreen`, which is a
     * decorative waveform stop and carries no meaning.
     */
    const val Explanation: Long = 0xFF3DDC84

    /** Present but not actionable. Derived from the same warm ramp. */
    const val Disabled: Long = 0xFF6B6558
}
