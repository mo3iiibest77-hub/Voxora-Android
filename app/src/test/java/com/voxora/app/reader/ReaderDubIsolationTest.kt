package com.voxora.app.reader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Reader's isolation from Live Dub, asserted against the source rather than promised in prose.
 *
 * "Do not modify Live Dub" is a scope rule, and a scope rule that is only stated is a rule that
 * silently rots. This test reads the Reader's own sources and fails if any of them reaches into the
 * Dub package or the Live session. It is deliberately a source scan: the dependency it forbids is a
 * *textual* one, and there is no runtime behaviour that could stand in for it.
 *
 * The Reader has its own bubble, its own playback, its own session and its own capture-free path
 * precisely so that the two products share nothing but the API key and the language catalog.
 */
class ReaderDubIsolationTest {

    /**
     * Symbols that belong to Live Dub. A Reader file naming any of them is a regression, not a
     * refactor — the two features have separate audio sessions, separate services and separate
     * models by design.
     */
    private val forbidden = listOf(
        "com.voxora.app.dub",
        "DubService",
        "SystemAudioCapture",
        "DubPlayback",
        "GeminiLiveSession",
        "GeminiLiveConfig",
        "FloatingBubbleService",
        "DelayedScreenOverlay",
    )

    /** The one reference that would actually compile a Dub dependency into the Reader. */
    private val IMPORT_DUB = Regex("""^\s*import\s+com\.voxora\.app\.dub""", RegexOption.MULTILINE)

    @Test
    fun noReaderSourceReferencesLiveDub() {
        val offenders = mutableListOf<String>()
        for (file in readerSources()) {
            val text = file.readText()
            // The import check runs on the raw text: an import can never be inside a comment, and
            // this is the reference that would actually compile a dependency in.
            if (IMPORT_DUB.containsMatchIn(text)) offenders += "${file.name} imports the Dub package"
            // The symbol check runs on code only. ReaderService and ReaderBubbleService *describe*
            // the isolation in KDoc ("Mirrors DubService.syncBubble"), and a comment is not a
            // dependency — scanning prose would make this test fail for explaining itself.
            val code = stripComments(text)
            for (symbol in forbidden) {
                if (code.contains(symbol)) offenders += "${file.name} references $symbol"
            }
        }
        assertEquals("Reader must not depend on Live Dub: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun theReaderPackageIsWhereTheFeatureLives() {
        // Guards the scan itself: if the directory ever moved, the test above would pass vacuously.
        val names = readerSources().map { it.name }
        assertTrue("ReaderController.kt should be part of the scan", "ReaderController.kt" in names)
        assertTrue("GeminiReaderSession is core, not app/reader", "GeminiReaderSession.kt" !in names)
        assertTrue(names.size >= 10)
    }

    /**
     * Removes block comments (including KDoc) and line comments, leaving only code.
     *
     * A deliberately simple scanner: it is only used to decide whether a *symbol name* appears in
     * code, so the worst case — a `//` inside a string literal truncating the rest of that line — can
     * only hide a reference on that one line, and the import check on the raw text still catches the
     * only reference that matters.
     */
    private fun stripComments(text: String): String =
        text.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")

    /**
     * Every `.kt` under `app/src/main/java/com/voxora/app/reader`, including `library/`.
     *
     * Two candidate roots because the working directory differs between Gradle (the module
     * directory) and the local pure-JVM harness (the repository root).
     */
    private fun readerSources(): List<File> {
        val roots = listOf(
            File("src/main/java/com/voxora/app/reader"),
            File("app/src/main/java/com/voxora/app/reader"),
        )
        val root = roots.firstOrNull { it.isDirectory }
            ?: error("Reader source directory not found; looked in ${roots.map { it.path }}")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }
}
