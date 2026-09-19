package com.voxora.core

/**
 * Shared constants aligned with ParsLiveDub extension protocol.
 *
 * There is deliberately no fixed output-delay constant here. Live Dub measures the pipeline's
 * own latency at run time and corrects drift against it; a hardcoded delay was removed together
 * with the rejected overlay approach.
 */
object GeminiLiveConfig {
    const val WS_PATH =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    const val MODEL = "models/gemini-3.5-live-translate-preview"
    const val INPUT_SAMPLE_RATE = 16_000
    const val OUTPUT_SAMPLE_RATE = 24_000
    const val CHUNK_MS = 60
}
