package com.voxora.app.ui

import java.util.Locale

/**
 * What a log severity should *communicate*, as opposed to what it is called.
 *
 * The screen must not pick colours by naming a level inline: that is how a viewer drifts into
 * hardcoded hex and stops matching the rest of the product. The mapping from severity to role
 * lives here, and the composable only asks for the role — the same split as
 * `ReaderStatusVisual` and `UsageStatusVisual`.
 *
 * - [SUCCESS] — ordinary, expected operation (info).
 * - [WARNING] — something recoverable went wrong (warn).
 * - [DANGER] — a failure (error).
 * - [NEUTRAL] — diagnostics that are not a status claim at all (debug), and any severity this
 *   build does not recognise, which must never be dressed up as success.
 *
 * Pure JVM (no `android.*`, no Compose) so the mapping is unit-testable. The level arrives as a
 * name rather than as `VoxoraLog.Level` so this file stays independent of the Android logger and
 * can be tested without it.
 */
internal enum class LogSeverityTone { SUCCESS, WARNING, DANGER, NEUTRAL }

internal object LogSeverity {

    fun tone(level: String): LogSeverityTone = when (level.uppercase(Locale.ROOT)) {
        "INFO" -> LogSeverityTone.SUCCESS
        "WARN" -> LogSeverityTone.WARNING
        "ERROR" -> LogSeverityTone.DANGER
        else -> LogSeverityTone.NEUTRAL
    }
}
