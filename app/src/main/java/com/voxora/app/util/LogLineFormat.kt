package com.voxora.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders one log line.
 *
 * The line shape is a technical contract, not product copy: a timestamp, a fixed-width severity,
 * a bracketed tag and the message. Copy-all, share and a single-entry copy must all produce
 * byte-identical text, so the format lives in exactly one place rather than being rebuilt at each
 * call site.
 *
 * Kept free of `android.*` (unlike [VoxoraLog], which mirrors to `android.util.Log`) so the exact
 * shape is unit-testable on a plain JVM. [VoxoraLog.Entry.formatted] delegates here.
 *
 * Pure JVM on purpose.
 */
object LogLineFormat {

    /** `DEBUG`, `INFO `, `WARN `, `ERROR` all occupy the same width so messages line up. */
    private const val LEVEL_WIDTH = 5

    private const val TIME_PATTERN = "HH:mm:ss.SSS"

    /**
     * `HH:mm:ss.SSS LEVEL [tag] message`, with the level padded to a fixed width.
     *
     * [level] is the severity's name rather than an enum so this stays independent of
     * [VoxoraLog]'s type, which is what lets the format be tested without the Android logger.
     * A new [SimpleDateFormat] is built per call because it is not thread-safe and log lines are
     * written from several threads.
     */
    fun format(timeMs: Long, level: String, tag: String, message: String): String {
        val ts = SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date(timeMs))
        return "$ts ${level.padEnd(LEVEL_WIDTH)} [$tag] $message"
    }
}
