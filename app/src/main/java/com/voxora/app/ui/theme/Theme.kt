package com.voxora.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.voxora.core.prefs.ThemeMode

/**
 * The Voxora design system.
 *
 * ## Two independent themes
 * Voxora ships two complete, separately defined colour systems. Each is a full appearance in its
 * own right, not a variation of the other:
 *
 * | `ThemeMode`        | Palette                 | Identity |
 * |--------------------|-------------------------|----------|
 * | [ThemeMode.ORIGINAL_DARK] | [OriginalDarkPalette] | the original Voxora dark gold theme — the product's primary identity and the default |
 * | [ThemeMode.LIGHT_TEST_2]  | [LightTest2Palette]   | Voxora Light — warm cream, gold leaf, warm brown shadows |
 *
 * **The palettes are independent by construction.** Each lives in its own file, declares every
 * value it uses, and shares no constant or mutable state with the other. A change to the light
 * appearance cannot move a dark value, and a change to the dark appearance cannot move a light one;
 * removing an appearance means deleting its palette file, its scheme block below, its `ThemeMode`
 * entry and its string. The light appearance is never achieved by "lightening" the dark theme's
 * tokens.
 *
 * **A third appearance, the Nova-inspired `LIGHT_TEST_1` candidate, was removed.** Only the two
 * above remain, and a redesign of the dark appearance must never reach the light one.
 *
 * ## Roles, not numbers
 * A screen asks for a role — `MaterialTheme.colorScheme.*` or `VoxoraColors.*` — and never for a
 * number. This file is the only place a raw value becomes a `Color`, and together with the two
 * palette files it is the only place in the app allowed to contain a colour literal.
 *
 * ## Text hierarchy
 * The roles are ordered by purpose, not by luminance: screen title → section title → primary
 * content (`onSurface`) → secondary content (`onSurfaceVariant`) → explanation
 * (`VoxoraColors.explanation`) → status → success/warning/error → disabled. The order is a
 * hierarchy of intent, so it does not promise that each role is dimmer than the one before it —
 * the dark explanation tone is a bright icy blue that is *lighter* than secondary content while
 * still being unmistakably the "aside" voice. No screen should invent a role or use an alpha on
 * `onSurface` to mean "less important".
 */

/**
 * Semantic colours Material 3 does not model.
 *
 * `colorScheme` has no success, warning, neutral, explanation or disabled role, so screens were
 * reaching for literal hex values, for `onSurfaceVariant`, or for an alpha on `onSurface`. These
 * are the named roles those values meant:
 *
 * - [success] / [warning] / [danger] are the status tones;
 * - [neutral] is the fourth status tone: idle, connecting, or an expected gap. It is neither good
 *   nor bad, so it must never borrow success or danger;
 * - [explanation] is the one role for **explanatory and help text** — the sentence under a control,
 *   the note about why a figure is unavailable, the description of what a feature does. It is
 *   deliberately distinct from primary and secondary content, and each theme expresses that
 *   distinction its own way: the dark theme by a cool hue (kept distinct by hue and dedicated use),
 *   the light theme by a quieter grey. It is never used for a heading,
 *   a button label or a status;
 * - [disabled] is content that is present but explicitly not actionable.
 * - [glow] is the one **decorative** role: the accent wash painted behind an icon or a brand mark.
 *   It is theme-resolved like the semantic roles so each appearance states its own — the dark
 *   appearance's gold-at-15 % wash, the light appearance's gold-at-12 % — and a light screen can
 *   never inherit the dark wash. It carries no meaning: never use it for text, a status, or a
 *   surface.
 *
 * Exposed through a composition local rather than added to `colorScheme` so the roles stay explicit
 * and each theme resolves them deliberately.
 */
@Immutable
data class VoxoraSemanticColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val neutral: Color,
    val explanation: Color,
    val disabled: Color,
    val glow: Color,
)

private val OriginalDarkSemantics = VoxoraSemanticColors(
    success = Color(OriginalDarkPalette.Success),
    warning = Color(OriginalDarkPalette.Warning),
    danger = Color(OriginalDarkPalette.Danger),
    neutral = Color(OriginalDarkPalette.Neutral),
    explanation = Color(OriginalDarkPalette.Explanation),
    disabled = Color(OriginalDarkPalette.Disabled),
    glow = Color(OriginalDarkPalette.Glow),
)

private val LightTest2Semantics = VoxoraSemanticColors(
    success = Color(LightTest2Palette.Success),
    warning = Color(LightTest2Palette.Warning),
    danger = Color(LightTest2Palette.Danger),
    neutral = Color(LightTest2Palette.Neutral),
    explanation = Color(LightTest2Palette.Explanation),
    disabled = Color(LightTest2Palette.Disabled),
    glow = Color(LightTest2Palette.Glow),
)

private val LocalVoxoraSemanticColors = staticCompositionLocalOf { OriginalDarkSemantics }

/** Accessor for the semantic roles, e.g. `VoxoraColors.explanation`. */
object VoxoraColors {
    /** Active narration, a healthy connection, a completed action. */
    val success: Color
        @Composable @ReadOnlyComposable get() = LocalVoxoraSemanticColors.current.success

    /** Paused, ready, preparing, a limit approaching. */
    val warning: Color
        @Composable @ReadOnlyComposable get() = LocalVoxoraSemanticColors.current.warning

    /** Stopped, failed, refused. */
    val danger: Color
        @Composable @ReadOnlyComposable get() = LocalVoxoraSemanticColors.current.danger

    /** Idle or connecting status, and an expected gap in the data. Never a success or an error. */
    val neutral: Color
        @Composable @ReadOnlyComposable get() = LocalVoxoraSemanticColors.current.neutral

    /**
     * Explanatory and help text. Use this for the sentence under a control or a note about why
     * something is unavailable — not for primary content, headings, buttons or status.
     */
    val explanation: Color
        @Composable @ReadOnlyComposable get() = LocalVoxoraSemanticColors.current.explanation

    /** Content that is present but not actionable. */
    val disabled: Color
        @Composable @ReadOnlyComposable get() = LocalVoxoraSemanticColors.current.disabled

    /**
     * The decorative accent wash behind an icon or a brand mark. Never text, a status or a surface.
     * Each appearance states its own value; a light screen never inherits the dark gold wash.
     */
    val glow: Color
        @Composable @ReadOnlyComposable get() = LocalVoxoraSemanticColors.current.glow
}

/**
 * Decorative brand values that are **not** semantic roles.
 *
 * The Live Dub waveform's gold→green gradient is a brand asset, not a state: it reads the same in
 * every theme and it deliberately differs from the semantic `warning`/`success` tones (it is a
 * decorative sweep, not a claim about status). Naming the pair here keeps the hex out of the
 * composable without pretending the colours carry meaning they do not. These values are unchanged
 * from before this feature so the Live Dub waveform is visually identical.
 */
object VoxoraBrand {
    val waveGold = Color(0xFFE6B422)
    val waveGreen = Color(0xFF3DDC97)
}

/**
 * **Original Voxora Dark** — near-black surfaces, dark charcoal cards, the gold identity, warm
 * light text. This is the product's primary visual direction and the default theme.
 *
 * Every role resolves to a value from [OriginalDarkPalette]; gold is `primary`, `secondary` and the
 * tint of the tonal surface ramp. No Google colour language and no Nova colour language here.
 */
private val OriginalDarkColors = darkColorScheme(
    primary = Color(OriginalDarkPalette.Gold),
    onPrimary = Color(OriginalDarkPalette.NearBlack),
    primaryContainer = Color(OriginalDarkPalette.PrimaryContainer),
    onPrimaryContainer = Color(OriginalDarkPalette.OnPrimaryContainer),
    inversePrimary = Color(OriginalDarkPalette.Gold),
    secondary = Color(OriginalDarkPalette.GoldDim),
    onSecondary = Color(OriginalDarkPalette.NearBlack),
    secondaryContainer = Color(OriginalDarkPalette.SecondaryContainer),
    onSecondaryContainer = Color(OriginalDarkPalette.OnSecondaryContainer),
    // The original theme had no tertiary; the muted gold keeps a derived role on-brand.
    tertiary = Color(OriginalDarkPalette.GoldDim),
    onTertiary = Color(OriginalDarkPalette.NearBlack),
    tertiaryContainer = Color(OriginalDarkPalette.PrimaryContainer),
    onTertiaryContainer = Color(OriginalDarkPalette.OnPrimaryContainer),
    background = Color(OriginalDarkPalette.NearBlack),
    onBackground = Color(OriginalDarkPalette.OnSurface),
    surface = Color(OriginalDarkPalette.SurfaceDark),
    onSurface = Color(OriginalDarkPalette.OnSurface),
    surfaceVariant = Color(OriginalDarkPalette.Card),
    onSurfaceVariant = Color(OriginalDarkPalette.OnSurfaceVariant),
    surfaceTint = Color(OriginalDarkPalette.Gold),
    surfaceBright = Color(OriginalDarkPalette.SurfaceContainerHighest),
    surfaceDim = Color(OriginalDarkPalette.NearBlack),
    surfaceContainerLowest = Color(OriginalDarkPalette.SurfaceContainerLowest),
    surfaceContainerLow = Color(OriginalDarkPalette.SurfaceContainerLow),
    surfaceContainer = Color(OriginalDarkPalette.SurfaceContainer),
    surfaceContainerHigh = Color(OriginalDarkPalette.SurfaceContainerHigh),
    surfaceContainerHighest = Color(OriginalDarkPalette.SurfaceContainerHighest),
    outline = Color(OriginalDarkPalette.Outline),
    outlineVariant = Color(OriginalDarkPalette.OutlineVariant),
    error = Color(OriginalDarkPalette.Error),
    onError = Color(OriginalDarkPalette.OnError),
    errorContainer = Color(OriginalDarkPalette.ErrorContainer),
    onErrorContainer = Color(OriginalDarkPalette.OnErrorContainer),
)

/**
 * **Voxora Light — warm cream and gold leaf.** The light appearance: warm parchment surfaces
 * (never pure white), the darker Voxora Light gold as `primary`, warm brown shadows and a
 * gold-at-12 % glow. Every role resolves to a value from [LightTest2Palette]; nothing is inherited
 * from the dark appearance, and it is deliberately not a lightened copy of the dark theme.
 */
private val LightTest2Colors = lightColorScheme(
    primary = Color(LightTest2Palette.Primary),
    onPrimary = Color(LightTest2Palette.OnPrimary),
    primaryContainer = Color(LightTest2Palette.PrimaryContainer),
    onPrimaryContainer = Color(LightTest2Palette.OnPrimaryContainer),
    inversePrimary = Color(LightTest2Palette.Primary),
    secondary = Color(LightTest2Palette.Secondary),
    onSecondary = Color(LightTest2Palette.OnSecondary),
    secondaryContainer = Color(LightTest2Palette.SecondaryContainer),
    onSecondaryContainer = Color(LightTest2Palette.OnSecondaryContainer),
    tertiary = Color(LightTest2Palette.Tertiary),
    onTertiary = Color(LightTest2Palette.OnTertiary),
    tertiaryContainer = Color(LightTest2Palette.TertiaryContainer),
    onTertiaryContainer = Color(LightTest2Palette.OnTertiaryContainer),
    background = Color(LightTest2Palette.Background),
    onBackground = Color(LightTest2Palette.OnSurface),
    surface = Color(LightTest2Palette.Surface),
    onSurface = Color(LightTest2Palette.OnSurface),
    surfaceVariant = Color(LightTest2Palette.SurfaceVariant),
    onSurfaceVariant = Color(LightTest2Palette.OnSurfaceVariant),
    surfaceTint = Color(LightTest2Palette.Primary),
    surfaceBright = Color(LightTest2Palette.SurfaceContainerLowest),
    surfaceDim = Color(LightTest2Palette.SurfaceContainerHighest),
    surfaceContainerLowest = Color(LightTest2Palette.SurfaceContainerLowest),
    surfaceContainerLow = Color(LightTest2Palette.SurfaceContainerLow),
    surfaceContainer = Color(LightTest2Palette.SurfaceContainer),
    surfaceContainerHigh = Color(LightTest2Palette.SurfaceContainerHigh),
    surfaceContainerHighest = Color(LightTest2Palette.SurfaceContainerHighest),
    outline = Color(LightTest2Palette.Outline),
    outlineVariant = Color(LightTest2Palette.OutlineVariant),
    error = Color(LightTest2Palette.Error),
    onError = Color(LightTest2Palette.OnError),
    errorContainer = Color(LightTest2Palette.ErrorContainer),
    onErrorContainer = Color(LightTest2Palette.OnErrorContainer),
)

/**
 * Applies the Voxora design system.
 *
 * [mode] is the user's persisted choice. [ThemeMode.ORIGINAL_DARK] is the default and the product's
 * primary identity, so an install that has never opened the control starts on the original Voxora
 * dark theme. The semantic roles are resolved from the same decision as the colour scheme, so a
 * light screen can never be handed a dark status colour.
 */
@Composable
fun VoxoraTheme(
    mode: ThemeMode = ThemeMode.DEFAULT,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (mode) {
        ThemeMode.ORIGINAL_DARK -> OriginalDarkColors
        ThemeMode.LIGHT_TEST_2 -> LightTest2Colors
    }
    val semantics = when (mode) {
        ThemeMode.ORIGINAL_DARK -> OriginalDarkSemantics
        ThemeMode.LIGHT_TEST_2 -> LightTest2Semantics
    }
    CompositionLocalProvider(LocalVoxoraSemanticColors provides semantics) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
