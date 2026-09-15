package com.voxora.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.voxora.app.dub.DubService
import com.voxora.core.prefs.UserPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun VoxoraNav(
    onStartDub: () -> Unit,
    onStopDub: () -> Unit,
    onDismissError: () -> Unit = {},
    onRequestOverlayPermission: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { UserPrefs(context) }
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var onboardingDone by remember { mutableStateOf(true) }
    var screen by remember { mutableStateOf("home") }
    val status by DubService.status.collectAsState()

    LaunchedEffect(Unit) {
        onboardingDone = prefs.onboardingDone.first()
        ready = true
        if (!onboardingDone) screen = "onboarding"
    }

    if (!ready) return

    when (screen) {
        "onboarding" -> OnboardingScreen(
            onFinished = {
                scope.launch {
                    prefs.setOnboardingDone(true)
                    onboardingDone = true
                    screen = "home"
                }
            },
            onOpenSettingsForKey = {
                scope.launch {
                    prefs.setOnboardingDone(true)
                    onboardingDone = true
                    screen = "settings"
                }
            },
        )
        "settings" -> SettingsScreen(
            onBack = { screen = "home" },
            onRequestOverlayPermission = onRequestOverlayPermission,
        )
        else -> HomeScreen(
            status = status,
            onStart = onStartDub,
            onStop = onStopDub,
            onOpenSettings = { screen = "settings" },
            onDismissError = onDismissError,
        )
    }
}
