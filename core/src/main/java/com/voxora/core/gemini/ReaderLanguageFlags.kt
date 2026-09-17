package com.voxora.core.gemini

import java.util.Locale

/**
 * Deterministic flag representation for the Reader language catalog.
 *
 * Policy (deliberate, not inferred):
 * - Each language is mapped to **one** representative ISO 3166-1 alpha-2 country
 *   code, chosen as the state most commonly associated with that language. The
 *   map is explicit; nothing is derived from the language subtag, and there is no
 *   guessing from string manipulation.
 * - Pluricentric languages always resolve to the same representative so the UI
 *   stays consistent: English -> GB, Spanish -> ES, Arabic -> SA. These are
 *   representative choices, not claims about where a language is spoken.
 * - Languages without a single associated state use [NEUTRAL] instead of a
 *   potentially misleading national flag.
 *
 * The emoji itself is produced with the Unicode regional-indicator encoding
 * (U+1F1E6..U+1F1FF), so it is always a real flag sequence rather than a
 * hand-typed character that could be mistyped.
 *
 * Pure JVM: no `android.*` APIs, so the mapping stays unit-testable.
 */
object ReaderLanguageFlags {

    /** Neutral marker used when no single country is representative. */
    const val NEUTRAL = "\uD83C\uDF10" // 🌐 globe with meridians

    private const val REGIONAL_INDICATOR_A = 0x1F1E6

    private val countries: Map<String, String> = mapOf(
        "af" to "ZA",
        "ak" to "GH",
        "sq" to "AL",
        "am" to "ET",
        "ar" to "SA",
        "hy" to "AM",
        "as" to "IN",
        "az" to "AZ",
        "be" to "BY",
        "bn" to "BD",
        "bs" to "BA",
        "bg" to "BG",
        "my" to "MM",
        "ceb" to "PH",
        "zh-Hans" to "CN",
        "zh-Hant" to "TW",
        "hr" to "HR",
        "cs" to "CZ",
        "da" to "DK",
        "nl" to "NL",
        "en" to "GB",
        "et" to "EE",
        "fo" to "FO",
        "fil" to "PH",
        "fi" to "FI",
        "fr" to "FR",
        "gl" to "ES",
        "ka" to "GE",
        "de" to "DE",
        "el" to "GR",
        "gu" to "IN",
        "ha" to "NG",
        "he" to "IL",
        "hi" to "IN",
        "hu" to "HU",
        "is" to "IS",
        "id" to "ID",
        "ga" to "IE",
        "it" to "IT",
        "ja" to "JP",
        "kn" to "IN",
        "kk" to "KZ",
        "km" to "KH",
        "rw" to "RW",
        "ko" to "KR",
        "ky" to "KG",
        "lo" to "LA",
        "lv" to "LV",
        "lt" to "LT",
        "mk" to "MK",
        "ms" to "MY",
        "ml" to "IN",
        "mt" to "MT",
        "mi" to "NZ",
        "mr" to "IN",
        "mn" to "MN",
        "ne" to "NP",
        "no" to "NO",
        "or" to "IN",
        "om" to "ET",
        "ps" to "AF",
        "fa" to "IR",
        "pl" to "PL",
        "pt-BR" to "BR",
        "pt-PT" to "PT",
        "pa" to "IN",
        "ro" to "RO",
        "rm" to "CH",
        "ru" to "RU",
        "sr" to "RS",
        "sd" to "PK",
        "si" to "LK",
        "sk" to "SK",
        "sl" to "SI",
        "so" to "SO",
        "st" to "LS",
        "es" to "ES",
        "sw" to "TZ",
        "sv" to "SE",
        "tg" to "TJ",
        "ta" to "IN",
        "te" to "IN",
        "th" to "TH",
        "tn" to "BW",
        "tr" to "TR",
        "tk" to "TM",
        "uk" to "UA",
        "ur" to "PK",
        "uz" to "UZ",
        "vi" to "VN",
        "cy" to "GB",
        "fy" to "NL",
        "wo" to "SN",
        "yo" to "NG",
        "zu" to "ZA",
    )

    /**
     * Languages that intentionally have no national flag: they are spoken across
     * several states and picking one would misrepresent the others.
     */
    private val neutralLanguages: Set<String> = setOf("eu", "ca", "ku", "qu")

    /** The flag for a catalog [code], or [NEUTRAL] when none is representative. */
    fun flagFor(code: String): String {
        val normalized = ReaderLanguages.normalize(code)
        if (normalized in neutralLanguages) return NEUTRAL
        return countries[normalized]?.let(::regionalIndicator) ?: NEUTRAL
    }

    /** True when [code] resolves to a national flag rather than [NEUTRAL]. */
    fun hasNationalFlag(code: String): Boolean = flagFor(code) != NEUTRAL

    /**
     * Encodes an ISO 3166-1 alpha-2 country code as its regional-indicator pair.
     * Returns [NEUTRAL] for anything that is not exactly two ASCII letters.
     */
    fun regionalIndicator(countryCode: String): String {
        val code = countryCode.trim().uppercase(Locale.ROOT)
        if (code.length != 2 || code.any { it !in 'A'..'Z' }) return NEUTRAL
        return buildString(4) {
            for (letter in code) appendCodePoint(REGIONAL_INDICATOR_A + (letter - 'A'))
        }
    }
}
