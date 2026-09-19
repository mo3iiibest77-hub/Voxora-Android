package com.voxora.core.gemini

/**
 * The Reader's narration voice, as the two semantic choices the reader actually makes.
 *
 * ## What Gemini does and does not expose
 *
 * The Gemini Live API selects a voice with
 * `speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName`, and the documented prebuilt set is a
 * flat list of **30 names**, each carrying only a *tone* descriptor — `Aoede` is "Breezy", `Charon`
 * is "Informative", `Kore` is "Firm", `Puck` is "Upbeat", and so on. The API publishes **no gender
 * field and no pitch field** for any of them, and there is no supported request parameter that asks
 * for one. This is the honest contract, and the reason this enum exists at all:
 *
 * - The product concept is a *feminine* narrator and a *masculine* narrator.
 * - The API concept is a voice *name*.
 * - [geminiVoiceName] is therefore a **curated perceptual mapping**, not an API-declared fact.
 *
 * Do not "fix" this by claiming the API declares a gender, and do not reach for DSP pitch shifting
 * as the primary mechanism: the model's own voice is the intended instrument and shifting PCM
 * would degrade it. If Google later publishes a real gender/pitch parameter, that parameter belongs
 * here, behind the same two semantic ids, and nothing else in the app has to change.
 *
 * ## The mapping, and why these two voices
 *
 * Both names are in the documented prebuilt list above. The choices are the widely used perceptual
 * pairings for a lighter/higher narrator and a deeper/warmer one:
 *
 * - [FEMALE] → `Aoede` ("Breezy") — the lighter, higher-placed of the two.
 * - [MALE] → `Charon` ("Informative") — the deeper, warmer of the two.
 *
 * The Reader previously hard-coded `Kore` ("Firm"). That constant is gone: a voice is now a stored
 * reader choice, and `Kore` is simply one of the voices a future choice could offer.
 *
 * ## Identity, not display
 *
 * [id] is the persisted, stable identity (what `UserPrefs` stores and what survives a rename of the
 * enum constant). [geminiVoiceName] is what the Gemini setup message receives. The two are
 * deliberately separate: the stored value is Voxora's word, the wire value is Gemini's.
 *
 * Pure JVM — no `android.*` — so the mapping and its normalization are unit-testable.
 */
enum class ReaderVoice(
    /** Stable persisted identity. Never change a value that has shipped. */
    val id: String,
    /** The Gemini prebuilt voice name sent in the session setup message. */
    val geminiVoiceName: String,
) {
    /** A lighter, higher-placed narrator. */
    FEMALE("female", "Aoede"),

    /** A deeper, warmer narrator. */
    MALE("male", "Charon"),
    ;

    companion object {
        /** The choice used when nothing is stored, and what an unknown value normalizes to. */
        val DEFAULT: ReaderVoice = FEMALE

        val all: List<ReaderVoice> = entries.toList()

        /**
         * The voice for a stored id, falling back to [DEFAULT].
         *
         * Unknown, absent and malformed values all normalize rather than throw, exactly as
         * [ReaderNarrationModes.normalize] and [ReaderLanguages.normalize] do, so a value written
         * by a newer build can never stop the Reader from narrating.
         */
        fun normalize(id: String?): ReaderVoice =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT

        fun isValid(id: String): Boolean = entries.any { it.id == id }

        /**
         * The Gemini voice name for a stored id. This is the only call the narration path needs:
         * it accepts the persisted string and always answers with a usable name.
         */
        fun voiceName(id: String?): String = normalize(id).geminiVoiceName
    }
}
