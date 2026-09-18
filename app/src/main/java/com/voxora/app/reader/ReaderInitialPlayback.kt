package com.voxora.app.reader

/**
 * The gate in front of a narration run's **first audible frame**.
 *
 * The Reader's reading text is the Gemini transcript of a narration unit, not a separate
 * translation step, so a unit that has not been narrated yet has no selected-language text to
 * show. That is fine for a unit the reader is still browsing, but it is not fine for the first
 * unit of a run: starting audio there produces the exact sequence the product must never show —
 * source-language text on screen, audio begins, selected-language text appears later.
 *
 * So a run waits for its first unit's rendering before it writes any PCM. The spool already
 * holds that unit's audio by then, and playback starts from the buffered audio, so the text is
 * on screen before the first frame is heard.
 *
 * Three outcomes, and only three:
 * - [Gate.READY] — a rendering exists. This is also the fast path: a unit that was rendered by
 *   an earlier run (resume, replay, a re-read segment) is ready immediately and nothing waits.
 * - [Gate.FAILED] — the unit can no longer produce one: it finished with a blank transcript, it
 *   failed, or the spool completed without it. Waiting would hang, and playing would be the
 *   misleading audio this gate exists to prevent, so the run surfaces its preparation error and
 *   the reader can retry.
 * - [Gate.AWAIT] — the producer is still working on it. Keep waiting.
 *
 * Note what is deliberately *not* here: a "the source language is the selection, so the source
 * counts as translated" shortcut. Voxora does not detect a document's language anywhere, so the
 * existing implementation never treated the extracted source as a valid rendering for a selected
 * language, and inventing that shortcut would let the source be presented as a translation.
 *
 * Pure JVM (no `android.*`) so the decision is unit-testable.
 */
internal object ReaderInitialPlayback {

    /** What a run must do about its first unit before it may emit sound. */
    enum class Gate { READY, AWAIT, FAILED }

    /**
     * The decision for a first unit whose selected-language rendering is [rendering].
     *
     * [failed] is true when the unit can no longer yield one — it is already finished, it
     * failed, or the spool is complete. The rendering is consulted **first**, so a unit that
     * finished successfully is [Gate.READY] rather than being misread as a failure.
     */
    fun gate(rendering: String?, failed: Boolean): Gate = when {
        // A blank transcript is rejected by ReaderDisplayText.record, so it can never be a
        // rendering; treating it as one would put an empty card where text should be.
        !rendering.isNullOrBlank() -> Gate.READY
        failed -> Gate.FAILED
        else -> Gate.AWAIT
    }
}
