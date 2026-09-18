package com.voxora.app.reader

/**
 * The Reader's UI gating rules, as a pure-JVM object.
 *
 * Extraction and restoration are the slowest thing the Reader does, and the screen
 * must not look frozen while they run. These rules say exactly what stays available
 * during that window without opening a hole in the safety gates the pipeline relies
 * on:
 *
 * - mode and narration language are only preferences, so they stay editable until
 *   narration actually starts — a preference change never touches the extraction;
 * - the document picker stays available during extraction because [ReaderController]
 *   already serializes loads (`cancelAndJoin` behind a generation bump), so choosing
 *   another file is a supported cancellation, not a race;
 * - playback never starts before the queue is ready, because a transport request
 *   against an empty queue has nothing to narrate;
 * - chunk/segment navigation needs a document, so it stays off until extraction
 *   publishes one;
 * - leaving the screen is never gated.
 *
 * Pure JVM (no `android.*`) so the contract is unit-testable.
 */
internal object ReaderGates {

    /** Phases in which narration is running and the transport owns the Reader. */
    private val NARRATING = setOf(
        ReaderPhase.CONNECTING,
        ReaderPhase.REWRITING,
        ReaderPhase.SPEAKING,
        ReaderPhase.NEXT,
    )

    fun isExtracting(phase: ReaderPhase): Boolean = phase == ReaderPhase.EXTRACTING

    fun isNarrating(phase: ReaderPhase): Boolean = phase in NARRATING

    /** Reading mode and narration language: editable until narration is running. */
    fun canConfigure(ready: Boolean, phase: ReaderPhase): Boolean = ready && !isNarrating(phase)

    /**
     * Choosing a document. Allowed as soon as settings are loaded, including while
     * another extraction is still running, so the Reader can never trap the user in a
     * long restore.
     */
    fun canPickDocument(ready: Boolean): Boolean = ready

    /**
     * The transport button. Playback must never begin before the queue is ready, so
     * extraction blocks it; once a document exists this also covers Pause and replay.
     */
    fun canPlay(ready: Boolean, phase: ReaderPhase, hasDocument: Boolean): Boolean =
        ready && hasDocument && !isExtracting(phase)

    /** Previous/next chunk and tapping a unit: needs a document and no extraction. */
    fun canNavigate(phase: ReaderPhase, hasDocument: Boolean): Boolean =
        hasDocument && !isExtracting(phase)

    /** Stop doubles as "cancel extraction" while a document is being read. */
    fun canStop(phase: ReaderPhase, hasDocument: Boolean): Boolean =
        isExtracting(phase) || hasDocument
}
