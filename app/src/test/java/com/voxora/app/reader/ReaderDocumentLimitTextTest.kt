package com.voxora.app.reader

import com.voxora.core.reader.ReaderDocumentLimits
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The limit the reader is actually told about, checked against the one the code enforces.
 *
 * `ReaderDocumentLimitsTest` in `:core` proves the rule; this proves the sentences. The number
 * appears in a string resource (which a Kotlin constant cannot be interpolated into) and in the
 * extraction messages, so a change to the limit that missed one of them would leave the app
 * refusing a file it says it accepts. The check is static — it reads the committed sources —
 * because that is exactly the drift it exists to catch.
 */
class ReaderDocumentLimitTextTest {

    private val label = ReaderDocumentLimits.LABEL

    @Test
    fun theEnglishErrorSaysTheSupportedLimit() {
        val value = stringValue("app/src/main/res/values/strings.xml", "reader_document_failed")
        assertTrue(value, value.contains("up to $label"))
        assertFalse("the previous limit must not survive in user-facing text", value.contains("20 MB"))
    }

    @Test
    fun thePersianErrorSaysTheSupportedLimit() {
        val value = stringValue("app/src/main/res/values-fa/strings.xml", "reader_document_failed")
        assertTrue(value, value.contains("100 مگابایت"))
        assertFalse("the previous limit must not survive in user-facing text", value.contains("20 مگابایت"))
    }

    @Test
    fun theExtractionMessagesCarryTheSharedLabelRatherThanAHandWrittenNumber() {
        val extractor = repoFile("app/src/main/java/com/voxora/app/reader/TextExtractor.kt").readText()
        assertTrue(
            "the extraction message must interpolate the shared label",
            extractor.contains("\${ReaderDocumentLimits.LABEL}"),
        )
        assertFalse("a stale 20 MB literal is still present", extractor.contains("20 MB"))
    }

    @Test
    fun theDocumentStoreNoLongerDeclaresItsOwnLimit() {
        val store = repoFile("app/src/main/java/com/voxora/app/reader/library/ReaderDocumentStore.kt").readText()
        // One rule, consulted by both boundaries — a second constant is the drift this prevents.
        assertFalse(store.contains("MAX_BYTES"))
        assertTrue(store.contains("ReaderDocumentLimits.exceeds"))
    }

    /** The raw value of one `<string name="…">` entry, or a failure when it is missing. */
    private fun stringValue(relativePath: String, name: String): String {
        val matches = repoFile(relativePath).readLines().filter { it.contains("name=\"$name\"") }
        assertEquals("$name must be declared exactly once in $relativePath", 1, matches.size)
        val value = matches.single().substringAfter(">").substringBeforeLast("</string>")
        assertTrue("$name is empty in $relativePath", value.isNotBlank())
        return value
    }

    /**
     * Resolves a repository-relative path from wherever the test JVM happens to start.
     *
     * Gradle runs app unit tests from the module directory and the local harness runs from the
     * repository root; walking up finds the file in both, so the check does not depend on which.
     */
    private fun repoFile(relativePath: String): File {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        throw AssertionError("Could not locate $relativePath from ${File("").absolutePath}")
    }
}
