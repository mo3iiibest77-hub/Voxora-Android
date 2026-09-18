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
 * The Voxora brand palette.
 *
 * Gold is the identity and stays gold in both appearances; the Live green and the stop red are
 * the same colours the floating bubbles and the Live Dub status dot already use. What changes
 * between light and dark is the *value* a role resolves to, never the role itself.
 */
private val Gold = Color(0xFFD4AF37)
private val GoldDim = Color(0xFFB8962E)
private val NearBlack = Color(0xFF0A0A0B)
private val SurfaceDark = Color(0xFF141416)
private val Card = Color(0xFF1C1C1F)

/** Active narration / success. Matches the Live bubble's green. */
private val SuccessDark = Color(0xFF3DDC84)

/** Paused, ready, preparing — the warm Voxora accent. */
private val WarningDark = Color(0xFFE6B422)

/** Stopped or failed. Matches the Live bubble's stop red. */
private val DangerDark = Color(0xFFE85D5D)

/**
 * Deep gold. The brand gold is a bright accent, which is legible as a *fill* on a dark surface but
 * not as text or a thin outline on a light one, so light mode uses a darker member of the same
 * family for the `primary` role and keeps the bright gold for containers.
 */
private val GoldInk = Color(0xFF8A6A16)

/** Light-mode status colours: the same semantics, darkened until they are readable on off-white. */
private val SuccessLight = Color(0xFF1B7F4B)
private val WarningLight = Color(0xFF8A6100)
private val DangerLight = Color(0xFFB3261E)

/**
 * Semantic colours Material 3 does not model.
 *
 * `colorScheme` has no success, warning or explanation role, so screens were reaching for literal
 * hex values or for `onSurfaceVariant`. These are the named roles those values meant:
 *
 * - [success] / [warning] / [danger] are the status tones, matching the Live bubble's palette;
 * - [explanation] is the one role for **explanatory and help text** — the sentences that explain
 *   what a control does, why a figure is unavailable, or what a failure means. It is deliberately
 *   distinct from primary content, headings, buttons, errors and warnings, so a screen asks for
 *   "help text" instead of guessing at an alpha on `onSurface`.
 *
 * Exposed through a composition local rather than added to `colorScheme` so the roles stay explicit
 * and each appearance resolves them deliberately.
 */
@Immutable
data class VoxoraSemanticColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val explanation: Color,
)

private val DarkSemantics = VoxoraSemanticColors(
    success = SuccessDark,
    warning = WarningDark,
    danger = DangerDark,
    explanation = Color(0xFFA79E8C),
)

private val LightSemantics = VoxoraSemanticColors(
    success = SuccessLight,
    warning = WarningLight,
    danger = DangerLight,
    explanation = Color(0xFF6E685B),
)

private val LocalVoxoraSemanticColors = staticCompositionLocalOf { DarkSemantics }

/** Accessor for the semantic roles, e.g. `VoxoraColors.explanation`. */
object VoxoraColors {
    val success: Color
        @Composable @ReadOnlyComposable get() = LocalVoxoraSemanticColors.current.success

    val warning: Color
        @Composable @ReadOnlyComposable get() = LocalVoxoraSemanticColors.current.warning

    val danger: Color
        @Composable @ReadOnlyComposable get() = LocalVoxoraSemanticColors.current.danger

    /**
     * Explanatory and help text. Use this for the sentence under a control or a note about why
     * something is unavailable — not for primary content, headings, buttons or status.
     */
    val explanation: Color
        @Composable @ReadOnlyComposable get() = LocalVoxoraSemanticColors.current.explanation
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
    val waveGold = Color(0xFFE6B422)
    val waveGreen = Color(0xFF3DDC97)
}

/**
 * Warm dark palette. The container and outline roles below are the ones Material 3 derives from
 * `primary` for tonal surfaces; without them the baseline scheme would tint grouped surfaces
 * purple, which is off-brand.
 */
private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = NearBlack,
    primaryContainer = Color(0xFF3A2E12),
    onPrimaryContainer = Color(0xFFF6E3A8),
    secondary = GoldDim,
    onSecondary = NearBlack,
    secondaryContainer = Color(0xFF2A2418),
    onSecondaryContainer = Color(0xFFE3D2A0),
    background = NearBlack,
    onBackground = Color(0xFFF5F0E6),
    surface = SurfaceDark,
    onSurface = Color(0xFFF5F0E6),
    surfaceVariant = Card,
    onSurfaceVariant = Color(0xFFC4BBA8),
    surfaceTint = Gold,
    surfaceContainerLowest = Color(0xFF0A0A0B),
    surfaceContainerLow = Color(0xFF121214),
    surfaceContainer = Color(0xFF17171A),
    surfaceContainerHigh = Color(0xFF1C1C1F),
    surfaceContainerHighest = Color(0xFF242428),
    outline = Color(0xFF4E4A42),
    outlineVariant = Color(0xFF2E2C28),
    error = DangerDark,
    onError = Color.White,
    errorContainer = Color(0xFF4A1F1F),
    onErrorContainer = Color(0xFFFFD9D9),
)

/**
 * Light palette: clean, warm off-white surfaces with a strong hierarchy.
 *
 * Every role is a real value rather than an inverted dark one, so contrast is deliberate: body and
 * heading text sits near-black on off-white, secondary text is a muted warm grey, and the gold
 * identity moves to a deep gold for text and outlines while the bright gold stays available as a
 * container tint. Voxora is not a Google clone — the gold, the warm neutrals and the Live green
 * are the product's own.
 */
private val LightColors = lightColorScheme(
    primary = GoldInk,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E3A8),
    onPrimaryContainer = Color(0xFF3A2E12),
    secondary = Color(0xFF6F6448),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFE6CE),
    onSecondaryContainer = Color(0xFF2A2418),
    background = Color(0xFFFAF9F6),
    onBackground = Color(0xFF1B1A17),
    surface = Color(0xFFFDFCFA),
    onSurface = Color(0xFF1B1A17),
    surfaceVariant = Color(0xFFEDEAE3),
    onSurfaceVariant = Color(0xFF5C574C),
    surfaceTint = GoldInk,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F5F1),
    surfaceContainer = Color(0xFFF2F0EA),
    surfaceContainerHigh = Color(0xFFECE9E2),
    surfaceContainerHighest = Color(0xFFE5E2DA),
    outline = Color(0xFF7E796D),
    outlineVariant = Color(0xFFD5D1C7),
    error = DangerLight,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

/**
 * Applies the Voxora design system.
 *
 * [mode] is the user's persisted choice: [ThemeMode.SYSTEM] follows the device, and the other two
 * are explicit overrides. The semantic roles are resolved from the same decision as the colour
 * scheme, so a light screen can never be handed a dark status colour.
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
