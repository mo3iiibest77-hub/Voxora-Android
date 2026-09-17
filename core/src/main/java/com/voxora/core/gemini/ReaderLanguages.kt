package com.voxora.core.gemini

import java.util.Locale

data class ReaderLanguage(val code: String, val englishName: String) {
    fun displayName(locale: Locale): String = Locale.forLanguageTag(code)
        .getDisplayName(locale)
        .takeUnless { it.isBlank() || it == code }
        ?: englishName
}

object ReaderLanguages {
    const val DEFAULT = "en"

    val all: List<ReaderLanguage> = listOf(
        ReaderLanguage("af", "Afrikaans"),
        ReaderLanguage("ak", "Akan"),
        ReaderLanguage("sq", "Albanian"),
        ReaderLanguage("am", "Amharic"),
        ReaderLanguage("ar", "Arabic"),
        ReaderLanguage("hy", "Armenian"),
        ReaderLanguage("as", "Assamese"),
        ReaderLanguage("az", "Azerbaijani"),
        ReaderLanguage("eu", "Basque"),
        ReaderLanguage("be", "Belarusian"),
        ReaderLanguage("bn", "Bengali"),
        ReaderLanguage("bs", "Bosnian"),
        ReaderLanguage("bg", "Bulgarian"),
        ReaderLanguage("my", "Burmese"),
        ReaderLanguage("ca", "Catalan"),
        ReaderLanguage("ceb", "Cebuano"),
        ReaderLanguage("zh-Hans", "Chinese (Simplified)"),
        ReaderLanguage("zh-Hant", "Chinese (Traditional)"),
        ReaderLanguage("hr", "Croatian"),
        ReaderLanguage("cs", "Czech"),
        ReaderLanguage("da", "Danish"),
        ReaderLanguage("nl", "Dutch"),
        ReaderLanguage("en", "English"),
        ReaderLanguage("et", "Estonian"),
        ReaderLanguage("fo", "Faroese"),
        ReaderLanguage("fil", "Filipino"),
        ReaderLanguage("fi", "Finnish"),
        ReaderLanguage("fr", "French"),
        ReaderLanguage("gl", "Galician"),
        ReaderLanguage("ka", "Georgian"),
        ReaderLanguage("de", "German"),
        ReaderLanguage("el", "Greek"),
        ReaderLanguage("gu", "Gujarati"),
        ReaderLanguage("ha", "Hausa"),
        ReaderLanguage("he", "Hebrew"),
        ReaderLanguage("hi", "Hindi"),
        ReaderLanguage("hu", "Hungarian"),
        ReaderLanguage("is", "Icelandic"),
        ReaderLanguage("id", "Indonesian"),
        ReaderLanguage("ga", "Irish"),
        ReaderLanguage("it", "Italian"),
        ReaderLanguage("ja", "Japanese"),
        ReaderLanguage("kn", "Kannada"),
        ReaderLanguage("kk", "Kazakh"),
        ReaderLanguage("km", "Khmer"),
        ReaderLanguage("rw", "Kinyarwanda"),
        ReaderLanguage("ko", "Korean"),
        ReaderLanguage("ku", "Kurdish"),
        ReaderLanguage("ky", "Kyrgyz"),
        ReaderLanguage("lo", "Lao"),
        ReaderLanguage("lv", "Latvian"),
        ReaderLanguage("lt", "Lithuanian"),
        ReaderLanguage("mk", "Macedonian"),
        ReaderLanguage("ms", "Malay"),
        ReaderLanguage("ml", "Malayalam"),
        ReaderLanguage("mt", "Maltese"),
        ReaderLanguage("mi", "Maori"),
        ReaderLanguage("mr", "Marathi"),
        ReaderLanguage("mn", "Mongolian"),
        ReaderLanguage("ne", "Nepali"),
        ReaderLanguage("no", "Norwegian"),
        ReaderLanguage("or", "Odia"),
        ReaderLanguage("om", "Oromo"),
        ReaderLanguage("ps", "Pashto"),
        ReaderLanguage("fa", "Persian"),
        ReaderLanguage("pl", "Polish"),
        ReaderLanguage("pt-BR", "Portuguese (Brazil)"),
        ReaderLanguage("pt-PT", "Portuguese (Portugal)"),
        ReaderLanguage("pa", "Punjabi"),
        ReaderLanguage("qu", "Quechua"),
        ReaderLanguage("ro", "Romanian"),
        ReaderLanguage("rm", "Romansh"),
        ReaderLanguage("ru", "Russian"),
        ReaderLanguage("sr", "Serbian"),
        ReaderLanguage("sd", "Sindhi"),
        ReaderLanguage("si", "Sinhala"),
        ReaderLanguage("sk", "Slovak"),
        ReaderLanguage("sl", "Slovenian"),
        ReaderLanguage("so", "Somali"),
        ReaderLanguage("st", "Southern Sotho"),
        ReaderLanguage("es", "Spanish"),
        ReaderLanguage("sw", "Swahili"),
        ReaderLanguage("sv", "Swedish"),
        ReaderLanguage("tg", "Tajik"),
        ReaderLanguage("ta", "Tamil"),
        ReaderLanguage("te", "Telugu"),
        ReaderLanguage("th", "Thai"),
        ReaderLanguage("tn", "Tswana"),
        ReaderLanguage("tr", "Turkish"),
        ReaderLanguage("tk", "Turkmen"),
        ReaderLanguage("uk", "Ukrainian"),
        ReaderLanguage("ur", "Urdu"),
        ReaderLanguage("uz", "Uzbek"),
        ReaderLanguage("vi", "Vietnamese"),
        ReaderLanguage("cy", "Welsh"),
        ReaderLanguage("fy", "Western Frisian"),
        ReaderLanguage("wo", "Wolof"),
        ReaderLanguage("yo", "Yoruba"),
        ReaderLanguage("zu", "Zulu"),
    )

    private val byCode = all.associateBy { it.code }

    fun normalize(code: String?): String {
        if (code.equals("nb", ignoreCase = true)) return "no"
        return all.firstOrNull { it.code.equals(code, ignoreCase = true) }?.code ?: DEFAULT
    }

    fun isValid(code: String): Boolean = code in byCode

    fun language(code: String): ReaderLanguage = byCode.getValue(normalize(code))
}
