package com.voxora.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Reader's supported document size.
 *
 * The rule is deliberately inclusive at the boundary — "up to 100 MB" means a 100 MB file is
 * accepted — and the same object supplies the number, the rule and the label the reader is shown,
 * so the constant and the sentence cannot drift apart.
 */
class ReaderDocumentLimitsTest {

    private val megabyte = 1024L * 1024L

    @Test
    fun theLimitIsOneHundredMegabytes() {
        assertEquals(100L * megabyte, ReaderDocumentLimits.MAX_BYTES)
        assertEquals(100, ReaderDocumentLimits.MAX_MEGABYTES)
        assertEquals("100 MB", ReaderDocumentLimits.LABEL)
    }

    @Test
    fun aDocumentJustBelowTheLimitIsAccepted() {
        assertFalse(ReaderDocumentLimits.exceeds(100L * megabyte - 1L))
    }

    @Test
    fun aDocumentExactlyAtTheLimitIsAccepted() {
        assertFalse(ReaderDocumentLimits.exceeds(100L * megabyte))
    }

    @Test
    fun aDocumentJustAboveTheLimitIsRejected() {
        assertTrue(ReaderDocumentLimits.exceeds(100L * megabyte + 1L))
    }

    @Test
    fun thePreviouslySupportedSizeIsNowWellWithinTheLimit() {
        // The old boundary was 20 MB; it must not survive as a second, stricter limit.
        assertFalse(ReaderDocumentLimits.exceeds(20L * megabyte))
        assertFalse(ReaderDocumentLimits.exceeds(20L * megabyte + 1L))
    }

    @Test
    fun anUnknownSizeIsNotTurnedIntoARefusal() {
        // A provider that reports no size gives a negative value here; "we could not measure it"
        // must not read as "it is too large".
        assertFalse(ReaderDocumentLimits.exceeds(-1L))
        assertFalse(ReaderDocumentLimits.exceeds(0L))
    }

    @Test
    fun theLabelMatchesTheConstantRatherThanRestatingIt() {
        assertEquals("${ReaderDocumentLimits.MAX_MEGABYTES} MB", ReaderDocumentLimits.LABEL)
    }
}
