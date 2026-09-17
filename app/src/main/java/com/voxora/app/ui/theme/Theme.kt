package com.voxora.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Gold = Color(0xFFD4AF37)
private val GoldDim = Color(0xFFB8962E)
private val NearBlack = Color(0xFF0A0A0B)
private val SurfaceDark = Color(0xFF141416)
private val Card = Color(0xFF1C1C1F)

/**
 * Warm dark palette. The container and outline roles below are the ones Material 3
 * derives from `primary` for tonal surfaces; without them the baseline scheme
 * would tint Reader's grouped surfaces purple, which is off-brand.
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
    error = Color(0xFFE85D5D),
    onError = Color.White,
    errorContainer = Color(0xFF4A1F1F),
    onErrorContainer = Color(0xFFFFD9D9),
)

@Composable
fun VoxoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
