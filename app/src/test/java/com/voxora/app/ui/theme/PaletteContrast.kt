package com.voxora.app.ui.theme

/**
 * WCAG 2.x relative luminance and contrast, over raw ARGB values.
 *
 * Kept beside the palette tests so the "content must clear AA" rule is computed the same way for
 * every theme. Pure JVM — no Compose — so it runs in the local harness.
 */
internal object PaletteContrast {

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    /** Relative luminance of a fully opaque ARGB value, in `0.0..1.0`. */
    fun luminance(argb: Long): Double {
        val r = channel(((argb shr 16) and 0xFF).toInt())
        val g = channel(((argb shr 8) and 0xFF).toInt())
        val b = channel((argb and 0xFF).toInt())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** Contrast ratio between two fully opaque ARGB values, in `1.0..21.0`. */
    fun ratio(foreground: Long, background: Long): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** True when every byte is fully opaque (`0xFF` alpha). */
    fun isOpaque(argb: Long): Boolean = ((argb shr 24) and 0xFF) == 0xFFL

    /** WCAG AA for normal-size text. */
    const val AA: Double = 4.5
}
