package com.voxora.app.reader

import com.voxora.core.gemini.ReaderLanguages
import java.util.Locale

data class ReaderLanguageOption(val code: String, val label: String, val searchText: String)

internal fun readerLanguageOptions(locale: Locale, query: String): List<ReaderLanguageOption> {
    val search = query.trim()
    return ReaderLanguages.all.map { language ->
        val label = language.displayName(locale)
        ReaderLanguageOption(
            language.code,
            label,
            "$label ${language.englishName} ${language.displayName(Locale.forLanguageTag("fa"))} " +
                "${language.displayName(Locale.forLanguageTag(language.code))} ${language.code}",
        )
    }.filter { it.searchText.contains(search, ignoreCase = true) }
        .sortedBy { it.label.lowercase(locale) }
}
