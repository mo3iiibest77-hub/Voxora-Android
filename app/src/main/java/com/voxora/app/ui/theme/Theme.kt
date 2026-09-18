package com.voxora.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
 * Two appearances, one role map. `VoxoraPalette` holds the raw values (and is unit-tested);
 * this file is the only place a value becomes a `Color`, and it is the only file in the app
 * allowed to contain a colour literal. A screen asks for a role — `MaterialTheme.colorScheme.*`
 * or `VoxoraColors.*` — and never for a number.
 *
 * The accents are Google's familiar families used semantically: **blue** for action and selection,
 * **green** for success, **yellow** for warning, **red** for error. Voxora gold survives as the
 * brand accent role ([tertiary]) for a subtle highlight, and in the Live waveform, but it no longer
 * fills cards, backgrounds or explanatory text.
 */

/**
 * Semantic colours Material 3 does not model.
 *
 * `colorScheme` has no success, warning, explanation, neutral or disabled role, so screens were
 * reaching for literal hex values, for `onSurfaceVariant`, or for an alpha on `onSurface`. These
 * are the named roles those values meant:
 *
 * - [success] / [warning] / [danger] are the status tones;
 * - [neutral] is the fourth status tone: idle, connecting, or an expected gap. It is neither good
 *   nor bad, so it must never borrow success or danger;
 * - [explanation] is the one role for **explanatory and help text** — the sentence under a control,
 *   the note about why a figure is unavailable, the description of what a feature does. It is
 *   deliberately less prominent than primary and secondary content;
 * - [disabled] is content that is present but explicitly not actionable.
 *
 * Exposed through a composition local rather than added to `colorScheme` so the roles stay explicit
 * and each appearance resolves them deliberately.
 */
@Immutable
data class VoxoraSemanticColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val neutral: Color,
    val explanation: Color,
    val disabled: Color,
)

private val DarkSemantics = VoxoraSemanticColors(
    success = Color(VoxoraPalette.GreenDark),
    warning = Color(VoxoraPalette.YellowDark),
    danger = Color(VoxoraPalette.RedDark),
    neutral = Color(VoxoraPalette.NeutralDark),
    explanation = Color(VoxoraPalette.ExplanationDark),
    disabled = Color(VoxoraPalette.DisabledDark),
)

private val LightSemantics = VoxoraSemanticColors(
    success = Color(VoxoraPalette.Green),
    warning = Color(VoxoraPalette.Yellow),
    danger = Color(VoxoraPalette.Red),
    neutral = Color(VoxoraPalette.NeutralLight),
    explanation = Color(VoxoraPalette.ExplanationLight),
    disabled = Color(VoxoraPalette.DisabledLight),
)

private val LocalVoxoraSemanticColors = staticCompositionLocalOf { DarkSemantics }

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
}

/**
 * Decorative brand values that are **not** semantic roles.
 *
 * The Live Dub waveform's gold→green gradient is a brand asset, not a state: it reads the same in
 * both appearances and it deliberately differs from the semantic `warning`/`success` tones (it is
 * a decorative sweep, not a claim about status). Naming the pair here keeps the hex out of the
 * composable without pretending the colours carry meaning they do not.
 */
object VoxoraBrand {
    /** The Voxora gold. Decorative only — the brand accent role is `colorScheme.tertiary`. */
    val waveGold = Color(VoxoraPalette.Gold)
    val waveGreen = Color(0xFF3DDC97)
}

/**
 * The dark appearance: near-black surfaces, cool neutrals, and the lifted Google accents.
 *
 * This stays the product's primary visual direction. The warm brown/gold ramp the first pass used
 * is gone — neutrals are cool grey and gold is confined to the `tertiary` brand accent — so
 * explanatory text no longer reads as gold.
 */
private val DarkColors = darkColorScheme(
    primary = Color(VoxoraPalette.BlueDark),
    onPrimary = Color(VoxoraPalette.OnBlueDark),
    primaryContainer = Color(VoxoraPalette.BlueContainerDark),
    onPrimaryContainer = Color(VoxoraPalette.OnBlueContainerDark),
    inversePrimary = Color(VoxoraPalette.Blue),
    secondary = Color(VoxoraPalette.OnSurfaceVariantDark),
    onSecondary = Color(VoxoraPalette.SurfaceHighDark),
    secondaryContainer = Color(VoxoraPalette.SurfaceHighestDark),
    onSecondaryContainer = Color(VoxoraPalette.OnSurfaceDark),
    // The brand accent. Subtle highlights and selected markers only — never a large surface.
    tertiary = Color(VoxoraPalette.Gold),
    onTertiary = Color(VoxoraPalette.OnGoldContainer),
    tertiaryContainer = Color(VoxoraPalette.OnGoldContainer),
    onTertiaryContainer = Color(VoxoraPalette.GoldContainer),
    background = Color(VoxoraPalette.NearBlack),
    onBackground = Color(VoxoraPalette.OnSurfaceDark),
    surface = Color(VoxoraPalette.SurfaceDark),
    onSurface = Color(VoxoraPalette.OnSurfaceDark),
    surfaceVariant = Color(VoxoraPalette.SurfaceHighDark),
    onSurfaceVariant = Color(VoxoraPalette.OnSurfaceVariantDark),
    surfaceTint = Color(VoxoraPalette.BlueDark),
    surfaceBright = Color(VoxoraPalette.SurfaceHighestDark),
    surfaceDim = Color(VoxoraPalette.NearBlack),
    surfaceContainerLowest = Color(VoxoraPalette.NearBlack),
    surfaceContainerLow = Color(VoxoraPalette.SurfaceLowDark),
    surfaceContainer = Color(VoxoraPalette.SurfaceContainerDark),
    surfaceContainerHigh = Color(VoxoraPalette.SurfaceHighDark),
    surfaceContainerHighest = Color(VoxoraPalette.SurfaceHighestDark),
    outline = Color(VoxoraPalette.OutlineDark),
    outlineVariant = Color(VoxoraPalette.OutlineVariantDark),
    error = Color(VoxoraPalette.RedDark),
    onError = Color(VoxoraPalette.OnErrorDark),
    errorContainer = Color(VoxoraPalette.ErrorContainerDark),
    onErrorContainer = Color(VoxoraPalette.OnErrorContainerDark),
)

/**
 * The light appearance: clean white surfaces with a cool grey ramp.
 *
 * Every role is a real value rather than an inverted dark one, so contrast is deliberate: content
 * sits near-black on white, secondary content is a mid grey, help text is lighter still, and the
 * Google blue carries action and selection. There is no brown and no gold on a large surface.
 */
private val LightColors = lightColorScheme(
    primary = Color(VoxoraPalette.Blue),
    onPrimary = Color(VoxoraPalette.OnBlue),
    primaryContainer = Color(VoxoraPalette.BlueContainer),
    onPrimaryContainer = Color(VoxoraPalette.OnBlueContainer),
    inversePrimary = Color(VoxoraPalette.BlueDark),
    secondary = Color(VoxoraPalette.OnSurfaceVariantLight),
    onSecondary = Color(VoxoraPalette.White),
    secondaryContainer = Color(VoxoraPalette.SurfaceContainerLight),
    onSecondaryContainer = Color(VoxoraPalette.OnSurfaceLight),
    // The brand accent. Deep enough to read as ink, never a surface.
    tertiary = Color(VoxoraPalette.GoldInk),
    onTertiary = Color(VoxoraPalette.White),
    tertiaryContainer = Color(VoxoraPalette.GoldContainer),
    onTertiaryContainer = Color(VoxoraPalette.OnGoldContainer),
    background = Color(VoxoraPalette.White),
    onBackground = Color(VoxoraPalette.OnSurfaceLight),
    surface = Color(VoxoraPalette.SurfaceLight),
    onSurface = Color(VoxoraPalette.OnSurfaceLight),
    surfaceVariant = Color(VoxoraPalette.SurfaceContainerLight),
    onSurfaceVariant = Color(VoxoraPalette.OnSurfaceVariantLight),
    surfaceTint = Color(VoxoraPalette.Blue),
    surfaceBright = Color(VoxoraPalette.White),
    surfaceDim = Color(VoxoraPalette.SurfaceHighestLight),
    surfaceContainerLowest = Color(VoxoraPalette.White),
    surfaceContainerLow = Color(VoxoraPalette.SurfaceLowLight),
    surfaceContainer = Color(VoxoraPalette.SurfaceContainerLight),
    surfaceContainerHigh = Color(VoxoraPalette.SurfaceHighLight),
    surfaceContainerHighest = Color(VoxoraPalette.SurfaceHighestLight),
    outline = Color(VoxoraPalette.OutlineLight),
    outlineVariant = Color(VoxoraPalette.OutlineVariantLight),
    error = Color(VoxoraPalette.Red),
    onError = Color(VoxoraPalette.White),
    errorContainer = Color(VoxoraPalette.ErrorContainerLight),
    onErrorContainer = Color(VoxoraPalette.OnErrorContainerLight),
)

/**
 * Applies the Voxora design system.
 *
 * [mode] is the user's persisted choice: [ThemeMode.SYSTEM] follows the device, and the other two
 * are explicit overrides. `ThemeMode.DEFAULT` is `SYSTEM`, so an install that has never touched the
 * control follows the device rather than defaulting to light. The semantic roles are resolved from
 * the same decision as the colour scheme, so a light screen can never be handed a dark status
 * colour.
 */
@Composable
fun VoxoraTheme(
    mode: ThemeMode = ThemeMode.DEFAULT,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val semantics = if (dark) DarkSemantics else LightSemantics
    CompositionLocalProvider(LocalVoxoraSemanticColors provides semantics) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            content = content,
        )
    }
}
