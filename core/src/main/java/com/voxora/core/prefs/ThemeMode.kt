package com.voxora.core.prefs

/**
 * Which of Voxora's three visual themes the user has chosen.
 *
 * The three themes are independent colour systems, not variations of one another:
 *
 * - [ORIGINAL_DARK] — the original Voxora dark gold theme. It is the product's primary identity and
 *   therefore the **default**; an install that has never opened the control starts here.
 * - [LIGHT_TEST_1] — Voxora Light, Nova-inspired.
 * - [LIGHT_TEST_2] — Nova-style Light.
 *
 * The two light themes exist so the owner can compare them on a device and later keep one. Because
 * they are separate, removing either later is a matter of deleting its palette and its entry here,
 * without touching the dark theme or the other light theme.
 *
 * The value is stored as its [id] so a stored preference stays readable if the enum is ever
 * reordered. Pure JVM (no `android.*`) so normalization is unit-testable.
 */
enum class ThemeMode(val id: String) {
    /** The original Voxora dark gold theme. The default and the product's primary identity. */
    ORIGINAL_DARK("original_dark"),

    /** Voxora Light, Nova-inspired. A comparison candidate. */
    LIGHT_TEST_1("light_test_1"),

    /** Nova-style Light. A comparison candidate. */
    LIGHT_TEST_2("light_test_2");

    companion object {
        val DEFAULT: ThemeMode = ORIGINAL_DARK

        val all: List<ThemeMode> = listOf(ORIGINAL_DARK, LIGHT_TEST_1, LIGHT_TEST_2)

        /**
         * Values written by the previous `system`/`light`/`dark` model.
         *
         * The old `system` and `dark` both meant "the dark Voxora look", so they migrate to
         * [ORIGINAL_DARK]; the old `light` meant "the user asked for light", so it migrates to the
         * first light candidate rather than silently dropping the user into dark.
         */
        private val LEGACY_IDS: Map<String, ThemeMode> = mapOf(
            "system" to ORIGINAL_DARK,
            "dark" to ORIGINAL_DARK,
            "light" to LIGHT_TEST_1,
        )

        /** An absent, blank or unrecognised value falls back to [DEFAULT] rather than throwing. */
        fun normalize(value: String?): ThemeMode {
            val normalized = value?.trim()?.lowercase().orEmpty()
            all.firstOrNull { it.id == normalized }?.let { return it }
            return LEGACY_IDS[normalized] ?: DEFAULT
        }
    }
}
