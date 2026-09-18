package com.voxora.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.voxora.app.R

/**
 * The Voxora UI font: **Vazirmatn UI, Non-Latin cut**, bundled with the app.
 *
 * Persian has no system font the product can rely on across devices, so the glyphs ship with the
 * APK rather than being fetched at runtime. The *Non-Latin* cut contains the Persian/Arabic glyphs
 * only (verified: no `A`, `a` or digit `0` in the cmap), which is exactly what the product wants —
 * Persian renders in Vazirmatn, while Latin text (product names, URLs, model names, code) falls
 * through to the platform's normal Latin font instead of being forced into an unfamiliar face.
 *
 * Four weights are bundled (Regular/Medium/SemiBold/Bold). Compose synthesises the rarer weights
 * (Light, ExtraBold, Black) from the nearest file, which is enough for a UI that uses at most
 * `SemiBold` and `Bold` in its own styles.
 *
 * Only the font *family* changes here. Sizes, line heights, letter spacing and weights are the
 * Material 3 defaults, so no screen hardcodes a `sp` size and the type scale stays consistent.
 */
internal val VazirmatnUiFamily: FontFamily = FontFamily(
    Font(R.font.vazirmatn_ui_nl_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_ui_nl_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_ui_nl_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_ui_nl_bold, FontWeight.Bold),
)

private val DefaultTypography = Typography()

/**
 * The application type scale: every Material 3 role, with the Voxora Persian font family.
 *
 * Built by copying each default role so the Material type scale is preserved exactly and only the
 * family changes. `LogsScreen` deliberately overrides the family to `FontFamily.Monospace` for log
 * lines, because a timestamp and a bracketed tag are code, not prose.
 */
internal val VoxoraTypography: Typography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(fontFamily = VazirmatnUiFamily),
    displayMedium = DefaultTypography.displayMedium.copy(fontFamily = VazirmatnUiFamily),
    displaySmall = DefaultTypography.displaySmall.copy(fontFamily = VazirmatnUiFamily),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontFamily = VazirmatnUiFamily),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontFamily = VazirmatnUiFamily),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontFamily = VazirmatnUiFamily),
    titleLarge = DefaultTypography.titleLarge.copy(fontFamily = VazirmatnUiFamily),
    titleMedium = DefaultTypography.titleMedium.copy(fontFamily = VazirmatnUiFamily),
    titleSmall = DefaultTypography.titleSmall.copy(fontFamily = VazirmatnUiFamily),
    bodyLarge = DefaultTypography.bodyLarge.copy(fontFamily = VazirmatnUiFamily),
    bodyMedium = DefaultTypography.bodyMedium.copy(fontFamily = VazirmatnUiFamily),
    bodySmall = DefaultTypography.bodySmall.copy(fontFamily = VazirmatnUiFamily),
    labelLarge = DefaultTypography.labelLarge.copy(fontFamily = VazirmatnUiFamily),
    labelMedium = DefaultTypography.labelMedium.copy(fontFamily = VazirmatnUiFamily),
    labelSmall = DefaultTypography.labelSmall.copy(fontFamily = VazirmatnUiFamily),
)
