package com.voxora.app.ui.theme

/**
 * The Voxora palette, as raw ARGB values.
 *
 * ## Why the values live here instead of in `Theme.kt`
 * `Theme.kt` needs Compose to turn a value into a `Color`, and the local harness cannot compile
 * Compose. Keeping the numbers in a plain Kotlin object means the palette itself — the part with
 * rules worth asserting, such as "the four accent families are distinct" and "the two appearances
 * really differ" — stays unit-testable on a JVM, exactly like `ReaderStatusVisual` or
 * `UsageStatusVisual` stay testable by not being composables.
 *
 * ## The colour system
 * The accents are **Google's familiar families, used as semantic roles** — blue for action and
 * selection, green for success, yellow for warning, red for error. They are not decoration: a
 * screen asks for the role, never for "the blue one", so the same meaning always looks the same.
 *
 * The neutrals are a clean, cool grey ramp rather than the warm brown/tan ramp the first light
 * theme used, which is what made explanatory text read as gold. Gold itself survives as the
 * brand accent ([Gold] / [GoldInk]) and in the Live waveform, but it no longer fills cards,
 * backgrounds or help text.
 *
 * `Theme.kt` is the only place these become `Color`, and no composable may hold a literal.
 */
internal object VoxoraPalette {

    // ---- accent families -----------------------------------------------------------------

    /**
     * Google blue — primary action and the selected state.
     *
     * The light value is Google's blue 700 rather than the brighter brand blue: the primary is used
     * for icons and selected markers as well as filled buttons, and the brighter blue only reaches
     * 4.05:1 as ink on a light card. This one clears AA in both directions.
     */
    const val Blue: Long = 0xFF1967D2
    const val OnBlue: Long = 0xFFFFFFFF
    const val BlueContainer: Long = 0xFFD3E3FD
    const val OnBlueContainer: Long = 0xFF041E49

    /** Google blue, lifted for a dark surface where the light value would be too dim. */
    const val BlueDark: Long = 0xFF8AB4F8
    const val OnBlueDark: Long = 0xFF062E6F
    const val BlueContainerDark: Long = 0xFF0842A0
    const val OnBlueContainerDark: Long = 0xFFD3E3FD

    /**
     * Google green — success.
     *
     * The light value is Google's deeper green rather than the bright brand green, because these
     * roles are used as *text* (a status label, an unavailable-reason value) on a light card, not
     * only as a dot. The bright green is kept for the dark appearance, where it has the contrast.
     */
    const val Green: Long = 0xFF137333
    const val GreenDark: Long = 0xFF81C995

    /** Google yellow/amber — warning, and the Reader's "paused" tone. */
    const val Yellow: Long = 0xFF8A5200
    const val YellowDark: Long = 0xFFFDD663

    /** Google red — error and danger. */
    const val Red: Long = 0xFFB3261E
    const val RedDark: Long = 0xFFF28B82

    // ---- brand accent (deliberately narrow) ----------------------------------------------

    /**
     * Voxora gold. It survives as the brand accent role, for a subtle highlight or a selected
     * marker — never for a card, a background or help text, which is what the previous light
     * theme got wrong.
     */
    const val Gold: Long = 0xFFE6B422

    /** Gold darkened until it is legible as ink on a light surface. */
    const val GoldInk: Long = 0xFF8A6A16
    const val GoldContainer: Long = 0xFFF6E3A8
    const val OnGoldContainer: Long = 0xFF3A2E12

    // ---- light neutrals ------------------------------------------------------------------

    const val White: Long = 0xFFFFFFFF
    const val OnSurfaceLight: Long = 0xFF1F1F1F
    const val OnSurfaceVariantLight: Long = 0xFF444746
    const val OutlineLight: Long = 0xFF80868B
    const val OutlineVariantLight: Long = 0xFFDADCE0
    const val SurfaceLight: Long = 0xFFFFFFFF
    const val SurfaceLowLight: Long = 0xFFFCFCFD
    const val SurfaceContainerLight: Long = 0xFFF8F9FA
    const val SurfaceHighLight: Long = 0xFFF1F3F4
    const val SurfaceHighestLight: Long = 0xFFE8EAED

    // ---- dark neutrals (the original Voxora direction, de-warmed) ------------------------

    const val NearBlack: Long = 0xFF0A0A0B
    const val SurfaceDark: Long = 0xFF141416
    const val OnSurfaceDark: Long = 0xFFE3E3E3
    const val OnSurfaceVariantDark: Long = 0xFFC4C7C5
    const val OutlineDark: Long = 0xFF8E9195
    const val OutlineVariantDark: Long = 0xFF3C4043
    const val SurfaceLowDark: Long = 0xFF121214
    const val SurfaceContainerDark: Long = 0xFF17171A
    const val SurfaceHighDark: Long = 0xFF1C1C1F
    const val SurfaceHighestDark: Long = 0xFF242428

    // ---- semantic roles Material 3 does not model ----------------------------------------

    /**
     * Help and explanatory text.
     *
     * Deliberately *less* prominent than secondary content in both appearances — lighter than the
     * body grey on a light surface, dimmer than it on a dark one — so a sentence about what a
     * control does can never compete with the content itself.
     */
    const val ExplanationLight: Long = 0xFF5F6368
    const val ExplanationDark: Long = 0xFF9AA0A6

    /** Normal/idle status. Neither good nor bad, so it must not borrow success or danger. */
    const val NeutralLight: Long = 0xFF666B70
    const val NeutralDark: Long = 0xFF8E9195

    /** Disabled content: present, but explicitly not actionable. */
    const val DisabledLight: Long = 0xFF9AA0A6
    const val DisabledDark: Long = 0xFF5F6368

    const val ErrorContainerLight: Long = 0xFFFCE8E6
    const val OnErrorContainerLight: Long = 0xFF410E0B
    const val ErrorContainerDark: Long = 0xFF8C1D18
    const val OnErrorContainerDark: Long = 0xFFF9DEDC
    const val OnErrorDark: Long = 0xFF3B0907
}
