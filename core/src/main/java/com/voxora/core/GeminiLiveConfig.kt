package com.voxora.core

/**
 * Shared constants aligned with ParsLiveDub extension protocol.
 * Phase 1 will implement the WebSocket client against these values.
 */
object GeminiLiveConfig {
    const val WS_PATH =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    const val MODEL = "models/gemini-3.5-live-translate-preview"
    const val INPUT_SAMPLE_RATE = 16_000
    const val OUTPUT_SAMPLE_RATE = 24_000
    const val CHUNK_MS = 60
    const val DEFAULT_DELAY_MS = 2_900
}
