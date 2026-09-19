package com.voxora.core.prefs

/**
 * Which of Voxora's two visual themes the user has chosen.
 *
 * The two themes are independent colour systems, not variations of one another:
 *
 * - [ORIGINAL_DARK] — the original Voxora dark gold theme. It is the product's primary identity and
 *   therefore the **default**; an install that has never opened the control starts here.
 * - [LIGHT_TEST_2] — Voxora Light: warm cream surfaces, the darker Voxora Light gold, warm brown
 *   shadows and a gold-at-12 % glow. The light appearance.
 *
 * The light appearance is a complete, independent colour system; it is not an override of the dark
 * theme and not a lightened copy of it. The identifier `LIGHT_TEST_2` is historical — it is the
 * stored preference id, kept so a saved choice keeps working — while the appearance it selects is
 * Voxora Light.
 *
 * **A third appearance, `LIGHT_TEST_1` (the Nova-inspired comparison candidate), was removed.** Its
 * stored id and the older `light` id both migrate to [LIGHT_TEST_2]: someone who chose a light
 * appearance must land on the surviving light appearance, never be silently dropped into dark.
 *
 * The value is stored as its [id] so a stored preference stays readable if the enum is ever
 * reordered. Pure JVM (no `android.*`) so normalization is unit-testable.
 */
enum class ThemeMode(val id: String) {
    /** The original Voxora dark gold theme. The default and the product's primary identity. */
    ORIGINAL_DARK("original_dark"),

    /** Voxora Light: warm cream, gold leaf. */
    LIGHT_TEST_2("light_test_2");

    companion object {
        val DEFAULT: ThemeMode = ORIGINAL_DARK

        val all: List<ThemeMode> = listOf(ORIGINAL_DARK, LIGHT_TEST_2)

        /**
         * Ids this enum no longer declares, which must still resolve to something sensible.
         *
         * The old `system` and `dark` both meant "the dark Voxora look", so they migrate to
         * [ORIGINAL_DARK]. The old `light` meant "the user asked for light", and `light_test_1` was
         * the removed light candidate, so both migrate to [LIGHT_TEST_2] — the surviving light
         * appearance — rather than silently dropping the user into dark.
         */
        private val LEGACY_IDS: Map<String, ThemeMode> = mapOf(
            "system" to ORIGINAL_DARK,
            "dark" to ORIGINAL_DARK,
            "light" to LIGHT_TEST_2,
            "light_test_1" to LIGHT_TEST_2,
        )

        /** An absent, blank or unrecognised value falls back to [DEFAULT] rather than throwing. */
        fun normalize(value: String?): ThemeMode {
            val normalized = value?.trim()?.lowercase().orEmpty()
            all.firstOrNull { it.id == normalized }?.let { return it }
            return LEGACY_IDS[normalized] ?: DEFAULT
        }
    }
}
