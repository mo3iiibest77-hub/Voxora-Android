package com.voxora.app.reader

import com.voxora.core.gemini.ReaderLanguages
import java.util.Locale

/**
 * UI-facing language row. The flag is resolved by the core catalog rather than
 * inferred here, so composables never do string gymnastics to pick an emoji.
 */
data class ReaderLanguageOption(
    val code: String,
    val label: String,
    val englishName: String,
    val flagEmoji: String,
    val searchText: String,
) {
    /** English name to show as a secondary line, or null when it adds nothing. */
    val secondaryLabel: String? get() = englishName.takeIf { !it.equals(label, ignoreCase = true) }
}

/**
 * The one selectable-language list for every Gemini *output* language in the app.
 *
 * The Reader's narration picker and the Settings "Dubbing language" picker both call
 * this, so the two can never drift apart in count, order, label or flag: they are the
 * same list over `ReaderLanguages.all`, which is the single language catalog. A third
 * hardcoded list is exactly what this replaces.
 *
 * This is not the app's own UI locale, which is bounded by the translations actually
 * packaged in the APK — see `AppLocales.shipped`.
 */
internal fun languageOptions(locale: Locale, query: String): List<ReaderLanguageOption> {
    val search = query.trim()
    return ReaderLanguages.all.map { language ->
        val label = language.displayName(locale)
        ReaderLanguageOption(
            code = language.code,
            label = label,
            englishName = language.englishName,
            flagEmoji = language.flagEmoji,
            searchText = "$label ${language.englishName} ${language.displayName(Locale.forLanguageTag("fa"))} " +
                "${language.displayName(Locale.forLanguageTag(language.code))} ${language.code}",
        )
    }.filter { it.searchText.contains(search, ignoreCase = true) }
        .sortedBy { it.label.lowercase(locale) }
}
