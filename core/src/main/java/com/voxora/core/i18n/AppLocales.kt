package com.voxora.core.i18n

import com.voxora.core.gemini.ReaderLanguage
import com.voxora.core.gemini.ReaderLanguages

/**
 * The languages the Voxora *interface* ships in.
 *
 * This is deliberately a smaller set than [ReaderLanguages.all], and the two are not
 * interchangeable:
 *
 * - an **app UI locale** only works if the APK actually packages that translation, so
 *   it is bounded by `resourceConfigurations` in `app/build.gradle.kts`;
 * - a **Gemini output language** (Reader narration, Live Dub) is a model capability
 *   and is not bounded by what the UI is translated into.
 *
 * Both still describe a language the same way: [languages] resolves each shipped code
 * through [ReaderLanguages], so a language is never named or flagged two different
 * ways across the Reader, Settings and the overlay.
 *
 * [shipped] must stay in step with `resourceConfigurations` in
 * `app/build.gradle.kts`; adding a translation means adding the code in both places.
 */
object AppLocales {

    /** Locales with packaged translations, in the order the picker should show them. */
    val shipped: List<String> = listOf("en", "fa", "ar", "es", "fr", "de", "tr")

    fun isShipped(code: String): Boolean = shipped.any { it.equals(code, ignoreCase = true) }

    /**
     * Shipped locales as catalog entries, in declaration order.
     *
     * Codes that are not in [ReaderLanguages] are dropped rather than invented, so the
     * picker can never offer a language the catalog cannot describe.
     */
    val languages: List<ReaderLanguage> get() = shipped.mapNotNull { ReaderLanguages.languageOrNull(it) }

    /** The UI locale to fall back to when a stored preference is missing or unknown. */
    const val DEFAULT: String = "en"
}
