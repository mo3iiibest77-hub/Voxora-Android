package com.voxora.app.reader

/**
 * Reader lifecycle phase.
 *
 * Kept in its own file with [ReaderState] and free of `android.*` imports so the UI
 * gating rules in [ReaderGates] stay unit-testable on a plain JVM.
 */
internal enum class ReaderPhase { IDLE, EXTRACTING, READY, CONNECTING, REWRITING, SPEAKING, NEXT, PAUSED, STOPPED, COMPLETE, ERROR }

/**
 * Reader state surfaced to the UI.
 *
 * [text] is the canonical extracted chunk exactly as [ChunkQueue] holds it, and
 * [segments] is the reading text of that chunk in the selected narration language:
 * a unit already narrated in that language shows its rendering, and a unit that has
 * not been narrated in it yet falls back to the extracted source. The extracted
 * document is never overwritten.
 */
internal data class ReaderState(
    val phase: ReaderPhase = ReaderPhase.IDLE,
    val chunk: Int = 0,
    val total: Int = 0,
    val segment: Int = 0,
    val segmentTotal: Int = 0,
    val text: String = "",
    val segments: List<String> = emptyList(),
    val documentName: String = "",
    val error: String? = null,
)
