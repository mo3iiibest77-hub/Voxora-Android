package com.voxora.app.reader

import com.voxora.core.gemini.ReaderNarrationModes

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
 * [segments] is the reading text of that chunk for the selected narration variant — the
 * selected language **and** the selected narration style ([narrationMode]): a unit already
 * narrated for that variant shows its rendering, and a unit that has not been narrated for
 * it yet falls back to the extracted source. The extracted document is never overwritten.
 *
 * [pendingSegments] holds the units of [segments] that have no rendering for the selected
 * variant yet, so the UI can show them as being prepared instead of presenting the
 * source language as if it were the selection. It is raw state — whether that temporary
 * treatment is shown at all is [ReaderPageText]'s decision, because it depends on the
 * phase.
 *
 * [preparing] is real state, not a timer: it is true only while a run is genuinely waiting
 * for its first unit's rendering before it may emit sound, so the UI can say what is
 * happening without inventing progress.
 */
internal data class ReaderState(
    val phase: ReaderPhase = ReaderPhase.IDLE,
    val chunk: Int = 0,
    val total: Int = 0,
    val segment: Int = 0,
    val segmentTotal: Int = 0,
    val text: String = "",
    val segments: List<String> = emptyList(),
    val pendingSegments: Set<Int> = emptySet(),
    val documentName: String = "",
    val narrationMode: String = ReaderNarrationModes.DEFAULT,
    val preparing: Boolean = false,
    val error: String? = null,
)
