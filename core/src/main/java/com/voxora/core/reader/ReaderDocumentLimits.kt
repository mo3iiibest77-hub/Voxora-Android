package com.voxora.core.reader

/**
 * The one supported-size rule for a Reader document.
 *
 * ## Why this is a single object
 *
 * The limit used to be written twice — once in `TextExtractor` (the boundary that actually reads
 * the document) and once in `ReaderDocumentStore` (the boundary that copies it into app storage) —
 * as two independent `20 MB` constants with two independent messages. Two numbers that must agree
 * are one number waiting to disagree, so the value, the rule and the user-facing label all live
 * here and both boundaries consult them.
 *
 * ## Why the label is here too
 *
 * The limit is stated to the reader in a string resource ("… up to 100 MB"), and a resource cannot
 * reference a Kotlin constant. Keeping the number and its label together means a change to one is
 * visible against the other, and [LABEL] is what a test pins so the two cannot drift silently.
 *
 * Pure JVM (no `android.*`) so the boundary is unit-testable without a device.
 */
object ReaderDocumentLimits {

    /** The largest document the Reader will read or copy, in bytes. */
    const val MAX_BYTES: Long = 100L * 1024L * 1024L

    /** The same limit in whole megabytes, for the user-facing sentence and for tests. */
    const val MAX_MEGABYTES: Int = 100

    /** The limit as it is shown to the reader, e.g. `100 MB`. */
    const val LABEL: String = "100 MB"

    /**
     * True when [bytes] is larger than the Reader supports.
     *
     * Exactly [MAX_BYTES] is accepted: the boundary is inclusive, which is what "up to 100 MB"
     * promises. A negative size — an unknown or unavailable length — is not an excess, because a
     * value that could not be read must not be turned into a refusal.
     */
    fun exceeds(bytes: Long): Boolean = bytes > MAX_BYTES
}
