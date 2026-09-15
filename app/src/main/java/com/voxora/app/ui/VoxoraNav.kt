package com.voxora.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.voxora.app.dub.DubService

@Composable
fun VoxoraNav(
    onStartDub: () -> Unit,
    onStopDub: () -> Unit,
) {
    var screen by remember { mutableStateOf("home") }
    val status by DubService.status.collectAsState()

    when (screen) {
        "settings" -> SettingsScreen(onBack = { screen = "home" })
        else -> HomeScreen(
            status = status,
            onStart = onStartDub,
            onStop = onStopDub,
            onOpenSettings = { screen = "settings" },
        )
    }
}
