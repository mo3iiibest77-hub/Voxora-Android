package com.voxora.core.gemini

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The voice contract.
 *
 * The load-bearing assertions are that the two semantic choices map to **different** names, that
 * both names are in the documented Gemini prebuilt set, and that normalization never throws — a
 * stored value written by a newer build must degrade to the default rather than stop narration.
 */
class ReaderVoiceTest {

    /**
     * The 30 prebuilt voice names published in the Gemini speech-generation voice table. Pinned here
     * so a mapping to a name the API does not offer fails the build instead of failing on a device.
     */
    private val documentedVoices = setOf(
        "Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede", "Callirrhoe",
        "Autonoe", "Enceladus", "Iapetus", "Umbriel", "Algieba", "Despina", "Erinome", "Algenib",
        "Rasalgethi", "Laomedeia", "Achernar", "Alnilam", "Schedar", "Gacrux", "Pulcherrima",
        "Achird", "Zubenelgenubi", "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat",
    )

    @Test
    fun everyChoiceMapsToADocumentedGeminiVoice() {
        for (voice in ReaderVoice.all) {
            assertTrue(
                "${voice.name} maps to '${voice.geminiVoiceName}', which is not a documented voice",
                voice.geminiVoiceName in documentedVoices,
            )
        }
    }

    @Test
    fun theTwoChoicesAreDifferentVoices() {
        assertNotEquals(ReaderVoice.FEMALE.geminiVoiceName, ReaderVoice.MALE.geminiVoiceName)
    }

    @Test
    fun thereAreExactlyTheTwoSemanticChoices() {
        assertEquals(listOf(ReaderVoice.FEMALE, ReaderVoice.MALE), ReaderVoice.all)
    }

    @Test
    fun idsAreStableAndDistinct() {
        assertEquals(listOf("female", "male"), ReaderVoice.all.map { it.id })
    }

    @Test
    fun normalizeIsTotalAndFallsBackToTheDefault() {
        assertEquals(ReaderVoice.FEMALE, ReaderVoice.DEFAULT)
        assertEquals(ReaderVoice.FEMALE, ReaderVoice.normalize(null))
        assertEquals(ReaderVoice.FEMALE, ReaderVoice.normalize(""))
        assertEquals(ReaderVoice.FEMALE, ReaderVoice.normalize("soprano"))
        assertEquals(ReaderVoice.MALE, ReaderVoice.normalize("male"))
        assertEquals(ReaderVoice.MALE, ReaderVoice.normalize("MALE"))
    }

    @Test
    fun voiceNameAcceptsTheStoredIdDirectly() {
        assertEquals(ReaderVoice.MALE.geminiVoiceName, ReaderVoice.voiceName(ReaderVoice.MALE.id))
        assertEquals(ReaderVoice.DEFAULT.geminiVoiceName, ReaderVoice.voiceName("not-a-voice"))
    }

    @Test
    fun isValidRejectsAnythingNotStored() {
        assertTrue(ReaderVoice.isValid("female"))
        assertTrue(ReaderVoice.isValid("male"))
        assertFalse(ReaderVoice.isValid("Female"))
        assertFalse(ReaderVoice.isValid(""))
        assertFalse(ReaderVoice.isValid("Aoede"))
    }
}
