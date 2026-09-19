package com.voxora.core.usage

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for reading token usage out of a Gemini server message.
 *
 * The Live API only *may* attach `usageMetadata`, and the two Live backends name the output count
 * differently. These tests pin that both spellings are understood, that a missing field stays
 * missing rather than becoming zero, and that a message with nothing recognisable yields nothing.
 */
class GeminiUsageMetadataTest {

    private fun message(vararg pairs: Pair<String, Any?>): JSONObject {
        val usage = JSONObject()
        pairs.forEach { (key, value) -> usage.put(key, value ?: JSONObject.NULL) }
        return JSONObject().put("usageMetadata", usage)
    }

    @Test
    fun geminiLiveNamesTheOutputCountResponseTokenCount() {
        val parsed = GeminiUsageMetadata.fromMessage(
            message("promptTokenCount" to 10, "responseTokenCount" to 25, "totalTokenCount" to 35),
        )
        assertEquals(10, parsed?.promptTokens)
        assertEquals(25, parsed?.responseTokens)
        assertEquals(35, parsed?.totalTokens)
    }

    @Test
    fun vertexLiveNamesTheOutputCountCandidatesTokenCount() {
        val parsed = GeminiUsageMetadata.fromMessage(
            message("promptTokenCount" to 10, "candidatesTokenCount" to 25, "totalTokenCount" to 35),
        )
        assertEquals(25, parsed?.responseTokens)
    }

    @Test
    fun theSnakeCaseSpellingIsAlsoUnderstood() {
        val raw = JSONObject().put(
            "usage_metadata",
            JSONObject().put("promptTokenCount", 7).put("responseTokenCount", 3),
        )
        assertEquals(7, GeminiUsageMetadata.fromMessage(raw)?.promptTokens)
    }

    @Test
    fun aMessageWithNoUsageMetadataYieldsNothing() {
        assertNull(GeminiUsageMetadata.fromMessage(JSONObject().put("serverContent", JSONObject())))
        assertNull(GeminiUsageMetadata.fromMessage(null))
        assertNull(GeminiUsageMetadata.fromMessage(JSONObject()))
    }

    @Test
    fun aUsageObjectWithNoRecognisedFieldYieldsNothing() {
        assertNull(GeminiUsageMetadata.fromMessage(message("somethingElse" to 5)))
    }

    @Test
    fun anAbsentFieldStaysUnknownRatherThanBecomingZero() {
        val parsed = GeminiUsageMetadata.fromMessage(message("promptTokenCount" to 10))
        assertEquals(10, parsed?.promptTokens)
        assertNull("an unreported field must not be reported as 0", parsed?.totalTokens)
        assertNull(parsed?.responseTokens)
        assertNull(parsed?.cachedTokens)
    }

    @Test
    fun aFieldTheServerExplicitlyReportedAsZeroIsKept() {
        val parsed = GeminiUsageMetadata.fromMessage(
            message("promptTokenCount" to 0, "responseTokenCount" to 0, "totalTokenCount" to 0),
        )
        assertEquals(0, parsed?.promptTokens)
        assertTrue("an explicit zero is real data", parsed?.hasAny == true)
    }

    @Test
    fun anExplicitJsonNullIsTreatedAsAbsent() {
        val parsed = GeminiUsageMetadata.fromMessage(
            message("promptTokenCount" to 4, "totalTokenCount" to null),
        )
        assertEquals(4, parsed?.promptTokens)
        assertNull(parsed?.totalTokens)
    }

    @Test
    fun aNumericStringIsAccepted() {
        val parsed = GeminiUsageMetadata.fromMessage(
            message("promptTokenCount" to "12", "totalTokenCount" to "20"),
        )
        assertEquals(12, parsed?.promptTokens)
        assertEquals(20, parsed?.totalTokens)
    }

    @Test
    fun aNonNumericValueIsIgnoredRatherThanGuessed() {
        val parsed = GeminiUsageMetadata.fromMessage(message("promptTokenCount" to 5, "totalTokenCount" to "many"))
        assertEquals(5, parsed?.promptTokens)
        assertNull(parsed?.totalTokens)
    }

    @Test
    fun theRemainingDocumentedFieldsAreRead() {
        val parsed = GeminiUsageMetadata.fromMessage(
            message(
                "promptTokenCount" to 1,
                "cachedContentTokenCount" to 2,
                "thoughtsTokenCount" to 3,
            ),
        )
        assertEquals(2, parsed?.cachedTokens)
        assertEquals(3, parsed?.thoughtsTokens)
        assertFalse(parsed?.hasAny == false)
    }
}
