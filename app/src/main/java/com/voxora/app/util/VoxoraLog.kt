package com.voxora.app.util

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-app ring-buffer logger.
 * Keeps the last [MAX_ENTRIES] lines so the user can copy/export them
 * without needing adb.
 */
object VoxoraLog {
    private const val MAX_ENTRIES = 800
    private const val TAG = "Voxora"

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val timeMs: Long,
        val level: Level,
        val tag: String,
        val message: String,
    ) {
        /**
         * The entry as one line. Delegates to [LogLineFormat] so the copy-all text, the share
         * text and a single entry copied from the list are all produced by the same rule.
         */
        fun formatted(): String = LogLineFormat.format(timeMs, level.name, tag, message)
    }

    private val entries = CopyOnWriteArrayList<Entry>()

    @Volatile
    var listener: (() -> Unit)? = null

    fun d(tag: String, msg: String) = add(Level.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = add(Level.INFO, tag, msg)
    fun w(tag: String, msg: String) = add(Level.WARN, tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) {
        val full = if (t != null) "$msg | ${t.javaClass.simpleName}: ${t.message}" else msg
        add(Level.ERROR, tag, full)
        if (t != null) Log.e(TAG, "[$tag] $msg", t) else Log.e(TAG, "[$tag] $msg")
    }

    private fun add(level: Level, tag: String, message: String) {
        val e = Entry(System.currentTimeMillis(), level, tag, message)
        entries.add(e)
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
        when (level) {
            Level.DEBUG -> Log.d(TAG, "[$tag] $message")
            Level.INFO -> Log.i(TAG, "[$tag] $message")
            Level.WARN -> Log.w(TAG, "[$tag] $message")
            Level.ERROR -> Log.e(TAG, "[$tag] $message")
        }
        listener?.invoke()
    }

    fun snapshot(): List<Entry> = entries.toList()

    fun clear() {
        entries.clear()
        listener?.invoke()
    }

    fun asText(): String = snapshot().joinToString("\n") { it.formatted() }
}
