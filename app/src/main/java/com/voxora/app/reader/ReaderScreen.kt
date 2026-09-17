package com.voxora.app.reader

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxora.app.R

@Composable
fun ReaderScreen(onBack: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val narrationText by viewModel.narrationText.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val settingsError by viewModel.settingsError.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::load)
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.pause()
        }
    }
    val back = { viewModel.pause(); onBack() }
    BackHandler(onBack = back)
    val extracting = state.phase == ReaderPhase.EXTRACTING
    val playing = state.phase in setOf(ReaderPhase.REWRITING, ReaderPhase.SPEAKING, ReaderPhase.NEXT)
    val statusLabel = when (state.phase) {
        ReaderPhase.IDLE -> R.string.reader_idle
        ReaderPhase.EXTRACTING -> R.string.reader_extracting
        ReaderPhase.READY -> R.string.reader_ready
        ReaderPhase.REWRITING -> R.string.reader_rewriting
        ReaderPhase.SPEAKING -> R.string.reader_speaking
        ReaderPhase.NEXT -> R.string.reader_next
        ReaderPhase.PAUSED -> R.string.reader_paused
        ReaderPhase.STOPPED -> R.string.reader_stopped
        ReaderPhase.COMPLETE -> R.string.reader_complete
        ReaderPhase.ERROR -> R.string.reader_error
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = back) { Text(stringResource(R.string.action_back)) }
        Text(stringResource(R.string.reader_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.reader_privacy), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = mode == "simple",
                onClick = { viewModel.setMode("simple") },
                enabled = ready && !playing && !extracting,
                label = { Text(stringResource(R.string.reader_simple)) },
            )
            FilterChip(
                selected = mode == "fluent",
                onClick = { viewModel.setMode("fluent") },
                enabled = ready && !playing && !extracting,
                label = { Text(stringResource(R.string.reader_fluent)) },
            )
        }
        OutlinedButton(
            onClick = { picker.launch(arrayOf("application/pdf", "text/plain")) },
            enabled = ready && !extracting,
        ) { Text(stringResource(R.string.reader_pick)) }
        Text(stringResource(statusLabel), style = MaterialTheme.typography.titleMedium)
        if (extracting || state.phase == ReaderPhase.REWRITING) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.total > 0) {
            Text(stringResource(R.string.reader_progress, state.chunk, state.total))
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { if (playing) viewModel.pause() else viewModel.play() },
                enabled = ready && !extracting && state.total > 0,
            ) { Text(stringResource(if (playing) R.string.reader_pause else R.string.reader_play)) }
            OutlinedButton(onClick = viewModel::stop, enabled = extracting || state.total > 0) {
                Text(stringResource(R.string.action_stop))
            }
        }
        Text(stringResource(R.string.reader_pause_hint), style = MaterialTheme.typography.bodySmall)
        if (state.text.isNotBlank()) Text(state.text, style = MaterialTheme.typography.bodyLarge)
        if (narrationText.isNotBlank()) {
            Text(stringResource(R.string.reader_narrating), style = MaterialTheme.typography.labelMedium)
            Text(narrationText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
