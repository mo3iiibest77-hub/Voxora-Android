package com.voxora.core.i18n

import com.voxora.core.gemini.ReaderLanguage
import com.voxora.core.gemini.ReaderLanguages
import java.util.Locale

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

    /**
     * Maps any requested locale onto one Voxora actually renders in.
     *
     * Language names are produced by `Locale.getDisplayName(locale)`, which names a
     * language *in* that locale. Passing a raw device locale therefore leaks a language
     * the app never ships: a phone set to Chinese with the Voxora UI falling back to
     * English would label every language in the pickers in Chinese while the rest of the
     * screen stayed English. Voxora ships no Chinese translation, so it must never
     * *describe* languages in Chinese either.
     *
     * The rule is therefore: name a language only in a locale whose UI Voxora can
     * actually display. A requested locale resolves to its shipped equivalent, matched
     * on the full tag first and then on the language subtag (`fa-IR` → `fa`,
     * `es-MX` → `es`), and anything else falls back to [DEFAULT].
     *
     * Pure JVM, and the only place a display locale should be produced from outside input.
     */
    fun resolve(locale: Locale?): Locale {
        val requested = locale ?: return Locale.forLanguageTag(DEFAULT)
        val language = requested.language.lowercase(Locale.ROOT)
        if (language.isEmpty() || language == "und") return Locale.forLanguageTag(DEFAULT)
        val tag = requested.toLanguageTag()
        val code = shipped.firstOrNull { it.equals(tag, ignoreCase = true) }
            ?: shipped.firstOrNull { it.equals(language, ignoreCase = true) }
            ?: DEFAULT
        return Locale.forLanguageTag(code)
    }
}
