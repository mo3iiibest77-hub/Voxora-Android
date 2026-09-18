package com.voxora.app.ui

import androidx.activity.compose.BackHandler
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
import com.voxora.app.reader.ReaderScreen
import com.voxora.core.prefs.UserPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Product shell destinations.
 *
 * `HOME` is the product root: a neutral chooser between Live Dub and
 * Voxora Reader. Reader playback is owned by `ReaderController` /
 * `ReaderService`, never by this navigation state, so leaving the Reader
 * destination never stops narration.
 */
private enum class VoxoraScreen { ONBOARDING, HOME, DUB, READER, SETTINGS, USAGE, LOGS }

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
    var screen by remember { mutableStateOf(VoxoraScreen.HOME) }
    val status by DubService.status.collectAsState()

    LaunchedEffect(Unit) {
        val onboardingDone = prefs.onboardingDone.first()
        ready = true
        if (!onboardingDone) screen = VoxoraScreen.ONBOARDING
    }

    if (!ready) return

    // System back returns to the product root. Screens that own their own
    // back behavior (Reader) register later and take priority.
    BackHandler(enabled = screen != VoxoraScreen.HOME && screen != VoxoraScreen.ONBOARDING) {
        screen = when (screen) {
            VoxoraScreen.LOGS, VoxoraScreen.USAGE -> VoxoraScreen.SETTINGS
            else -> VoxoraScreen.HOME
        }
    }

    when (screen) {
        VoxoraScreen.ONBOARDING -> OnboardingScreen(
            onFinished = {
                scope.launch {
                    prefs.setOnboardingDone(true)
                    screen = VoxoraScreen.HOME
                }
            },
            onOpenSettingsForKey = {
                scope.launch {
                    prefs.setOnboardingDone(true)
                    screen = VoxoraScreen.SETTINGS
                }
            },
        )
        VoxoraScreen.SETTINGS -> SettingsScreen(
            onBack = { screen = VoxoraScreen.HOME },
            onRequestOverlayPermission = onRequestOverlayPermission,
            onOpenLogs = { screen = VoxoraScreen.LOGS },
            onOpenUsage = { screen = VoxoraScreen.USAGE },
        )
        VoxoraScreen.USAGE -> ApiUsageScreen(
            onBack = { screen = VoxoraScreen.SETTINGS },
        )
        VoxoraScreen.LOGS -> LogsScreen(
            onBack = { screen = VoxoraScreen.SETTINGS },
        )
        VoxoraScreen.READER -> ReaderScreen(onBack = { screen = VoxoraScreen.HOME })
        VoxoraScreen.DUB -> DubScreen(
            status = status,
            onStart = onStartDub,
            onStop = onStopDub,
            onBack = { screen = VoxoraScreen.HOME },
            onOpenSettings = { screen = VoxoraScreen.SETTINGS },
            onDismissError = onDismissError,
        )
        VoxoraScreen.HOME -> HomeScreen(
            onOpenDub = { screen = VoxoraScreen.DUB },
            onOpenReader = { screen = VoxoraScreen.READER },
            onOpenSettings = { screen = VoxoraScreen.SETTINGS },
        )
    }
}
