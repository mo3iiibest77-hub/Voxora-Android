package com.voxora.core.prefs

/**
 * How Voxora chooses between its light and dark appearance.
 *
 * This is a user preference, not a device fact: [SYSTEM] follows the device setting and is the
 * default, while [LIGHT] and [DARK] are explicit overrides that survive a device change. The
 * value is stored as its [id] so a stored preference stays readable if the enum is ever reordered.
 *
 * Pure JVM (no `android.*`) so normalization is unit-testable; resolving [SYSTEM] needs the
 * platform and stays in the theme layer.
 */
enum class ThemeMode(val id: String) {
    /** Follow the device's light/dark setting. */
    SYSTEM("system"),

    /** Always light. */
    LIGHT("light"),

    /** Always dark. */
    DARK("dark");

    companion object {
        val DEFAULT: ThemeMode = SYSTEM

        val all: List<ThemeMode> = listOf(SYSTEM, LIGHT, DARK)

        /** An absent, blank or unrecognised value falls back to [DEFAULT] rather than throwing. */
        fun normalize(value: String?): ThemeMode =
            all.firstOrNull { it.id == value?.trim()?.lowercase() } ?: DEFAULT
    }
}
