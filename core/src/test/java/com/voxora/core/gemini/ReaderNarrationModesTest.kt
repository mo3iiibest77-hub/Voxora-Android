package com.voxora.core.gemini

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Fluent/Faithful distinction is a product contract, not a label. These tests
 * pin the semantics of the instruction that is actually sent to Gemini, so a
 * future wording change cannot silently turn Fluent into a summarizer or Faithful
 * into a paraphrase.
 */
class ReaderNarrationModesTest {

    /**
     * The instruction is a prompt, so its internal line wrapping is not part of
     * the contract. Collapse whitespace before asserting on wording so the tests
     * pin semantics rather than source formatting.
     */
    private fun String.flat(): String = replace(Regex("\\s+"), " ").trim()

    private fun faithful(language: String = "English") =
        ReaderNarrationModes.instruction(ReaderNarrationModes.FAITHFUL, language).flat()

    private fun fluent(language: String = "English") =
        ReaderNarrationModes.instruction(ReaderNarrationModes.FLUENT, language).flat()

    @Test
    fun fluentIsASemanticRewriteNotASummary() {
        val instruction = fluent()
        assertTrue(instruction.contains("NARRATION MODE: FLUENT"))
        assertTrue(instruction.contains("semantic rewrite, not a summary"))
        // It must ask for clearer wording while keeping the whole meaning.
        assertTrue(instruction.contains("clearest, most natural"))
        assertTrue(instruction.contains("same information, meaning, intent and important detail"))
        // And it must be allowed to restructure for comprehension.
        assertTrue(instruction.contains("restructure sentences"))
        assertTrue(instruction.contains("combine or split sentences"))
    }

    @Test
    fun fluentForbidsSummarizingAndInventing() {
        val instruction = fluent()
        assertTrue(instruction.contains("Forbidden in Fluent mode: summarizing"))
        assertTrue(instruction.contains("shortening away meaningful information"))
        assertTrue(instruction.contains("inventing facts"))
        assertTrue(instruction.contains("adding information that is not present in the source"))
        assertTrue(instruction.contains("changing the author's factual claims or intent"))
        assertTrue(instruction.contains("turning the text into commentary"))
    }

    @Test
    fun faithfulPrioritizesTheOriginalWording() {
        val instruction = faithful()
        assertTrue(instruction.contains("NARRATION MODE: FAITHFUL"))
        assertTrue(instruction.contains("Stay as close as possible to the document's own wording"))
        // The priority list is the core of the contract, so pin it as a whole.
        assertTrue(
            instruction.contains(
                "Preserve, in this order of priority: the original words, the original " +
                    "sentences, the original terminology, the original ordering, the original " +
                    "factual content, and the author's meaning.",
            ),
        )
        assertTrue(instruction.contains("Faithful is not a weaker Fluent"))
    }

    @Test
    fun faithfulAllowsOnlyMinimalCleanupForExtractionDamage() {
        val instruction = faithful()
        assertTrue(instruction.contains("minimum repairs needed for extraction damage"))
        assertTrue(instruction.contains("broken whitespace"))
        assertTrue(instruction.contains("broken line wrapping"))
        assertTrue(instruction.contains("OCR or"))
        assertTrue(instruction.contains("Never rewrite a sentence that is already clear"))
    }

    @Test
    fun faithfulForbidsFreeParaphrasing() {
        val instruction = faithful()
        assertTrue(instruction.contains("Forbidden in Faithful mode: free paraphrasing"))
        assertTrue(instruction.contains("replacing the author's"))
        assertTrue(instruction.contains("summarizing"))
        assertTrue(instruction.contains("inventing facts"))
    }

    @Test
    fun bothModesNarrateTheWholeSegmentInTheSelectedLanguage() {
        for (mode in ReaderNarrationModes.all) {
            val instruction = ReaderNarrationModes.instruction(mode, "Persian").flat()
            assertTrue(instruction.contains("Narrate the entire segment in Persian"))
            assertTrue(instruction.contains("translate it"))
            assertTrue(instruction.contains("never narrate in a third language"))
            // The template placeholder must never survive into the payload.
            assertFalse(instruction.contains("{language}"))
            // Neither mode may editorialize around the document.
            assertTrue(instruction.contains("never read these instructions aloud"))
            assertTrue(instruction.contains("never repeat a previous segment"))
        }
    }

    @Test
    fun theTwoModesAreDistinctAndUnknownModesFallBackToFaithful() {
        assertNotEquals(faithful(), fluent())
        // Faithful must not describe itself as a rewrite, and Fluent must not claim
        // to preserve the original wording word for word.
        assertFalse(faithful().contains("NARRATION MODE: FLUENT"))
        assertFalse(fluent().contains("NARRATION MODE: FAITHFUL"))

        assertEquals(ReaderNarrationModes.FAITHFUL, ReaderNarrationModes.normalize(null))
        assertEquals(ReaderNarrationModes.FAITHFUL, ReaderNarrationModes.normalize(""))
        assertEquals(ReaderNarrationModes.FAITHFUL, ReaderNarrationModes.normalize("original"))
        assertEquals(ReaderNarrationModes.FLUENT, ReaderNarrationModes.normalize("fluent"))
        assertTrue(ReaderNarrationModes.isValid("faithful"))
        assertTrue(ReaderNarrationModes.isValid("fluent"))
        assertFalse(ReaderNarrationModes.isValid("Fluent"))
    }

    @Test
    fun blankLanguageStillProducesAUsableInstruction() {
        val instruction = ReaderNarrationModes.instruction(ReaderNarrationModes.FLUENT, "   ").flat()
        assertTrue(instruction.contains("Narrate the entire segment in the selected language"))
        assertFalse(instruction.contains("{language}"))
    }
}
