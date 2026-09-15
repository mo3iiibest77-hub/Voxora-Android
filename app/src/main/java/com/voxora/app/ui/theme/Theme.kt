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

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = NearBlack,
    secondary = GoldDim,
    onSecondary = NearBlack,
    background = NearBlack,
    onBackground = Color(0xFFF5F0E6),
    surface = SurfaceDark,
    onSurface = Color(0xFFF5F0E6),
    surfaceVariant = Card,
    onSurfaceVariant = Color(0xFFC4BBA8),
    error = Color(0xFFE85D5D),
    onError = Color.White,
)

@Composable
fun VoxoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
