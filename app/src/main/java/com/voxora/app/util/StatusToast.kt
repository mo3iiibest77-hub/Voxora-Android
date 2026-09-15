package com.voxora.app.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** Short system-style status messages (ALAD-like), on main thread. */
object StatusToast {
    private val handler = Handler(Looper.getMainLooper())
    private var lastMsg = ""
    private var lastAt = 0L

    fun show(context: Context, message: String) {
        val msg = message.trim()
        if (msg.isEmpty()) return
        val now = System.currentTimeMillis()
        if (msg == lastMsg && now - lastAt < 1200) return
        lastMsg = msg
        lastAt = now
        handler.post {
            try {
                Toast.makeText(context.applicationContext, "Voxora · $msg", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
        }
    }
}
