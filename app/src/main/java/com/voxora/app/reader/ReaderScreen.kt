package com.voxora.app.reader

import android.content.res.Configuration.UI_MODE_NIGHT_YES
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxora.app.R
import com.voxora.app.ui.theme.VoxoraTheme

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val narrationText by viewModel.narrationText.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val outputLang by viewModel.outputLang.collectAsStateWithLifecycle(initialValue = "original")
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val settingsError by viewModel.settingsError.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::load)
    }
    BackHandler(onBack = onBack)
    ReaderContent(
        state = state,
        narrationText = narrationText,
        mode = mode,
        outputLang = outputLang,
        ready = ready,
        settingsError = settingsError,
        onBack = onBack,
        onModeChange = viewModel::setMode,
        onLangChange = viewModel::setOutputLang,
        onPick = { picker.launch(arrayOf("application/pdf", "text/plain")) },
        onPlay = viewModel::play,
        onPause = viewModel::pause,
        onStop = viewModel::stop,
        onJumpToChunk = viewModel::jumpToChunk,
        modifier = modifier,
    )
}

@Composable
private fun ReaderContent(
    state: ReaderState,
    narrationText: String,
    mode: String,
    outputLang: String,
    ready: Boolean,
    settingsError: String?,
    onBack: () -> Unit,
    onModeChange: (String) -> Unit,
    onLangChange: (String) -> Unit,
    onPick: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onJumpToChunk: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extracting = state.phase == ReaderPhase.EXTRACTING
    val playing = state.phase in setOf(ReaderPhase.CONNECTING, ReaderPhase.REWRITING, ReaderPhase.SPEAKING, ReaderPhase.NEXT)
    val statusLabel = when (state.phase) {
        ReaderPhase.IDLE -> R.string.reader_idle
        ReaderPhase.EXTRACTING -> R.string.reader_extracting
        ReaderPhase.READY -> R.string.reader_ready
        ReaderPhase.CONNECTING -> R.string.reader_connecting
        ReaderPhase.REWRITING -> R.string.reader_rewriting
        ReaderPhase.SPEAKING -> R.string.reader_speaking
        ReaderPhase.NEXT -> R.string.reader_next
        ReaderPhase.PAUSED -> R.string.reader_paused
        ReaderPhase.STOPPED -> R.string.reader_stopped
        ReaderPhase.COMPLETE -> R.string.reader_complete
        ReaderPhase.ERROR -> R.string.reader_error
    }
    Column(
        modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        Text(stringResource(R.string.reader_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.reader_privacy), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = mode == "faithful",
                onClick = { onModeChange("faithful") },
                enabled = ready && !playing && !extracting,
                label = { Text(stringResource(R.string.reader_faithful)) },
            )
            FilterChip(
                selected = mode == "fluent",
                onClick = { onModeChange("fluent") },
                enabled = ready && !playing && !extracting,
                label = { Text(stringResource(R.string.reader_fluent)) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = outputLang == "original",
                onClick = { onLangChange("original") },
                enabled = ready && !playing && !extracting,
                label = { Text(stringResource(R.string.reader_lang_original)) },
            )
            FilterChip(
                selected = outputLang == "fa",
                onClick = { onLangChange("fa") },
                enabled = ready && !playing && !extracting,
                label = { Text(stringResource(R.string.reader_lang_fa)) },
            )
            FilterChip(
                selected = outputLang == "en",
                onClick = { onLangChange("en") },
                enabled = ready && !playing && !extracting,
                label = { Text(stringResource(R.string.reader_lang_en)) },
            )
        }
        OutlinedButton(
            onClick = onPick,
            enabled = ready && !extracting,
        ) { Text(stringResource(R.string.reader_pick)) }
        Text(stringResource(statusLabel), style = MaterialTheme.typography.titleMedium)
        if (extracting || state.phase == ReaderPhase.CONNECTING || state.phase == ReaderPhase.REWRITING) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.total > 0) {
            Text(stringResource(R.string.reader_progress, state.chunk, state.total))
        }
        if (state.total > 1 &&
            state.phase in setOf(ReaderPhase.READY, ReaderPhase.PAUSED, ReaderPhase.STOPPED, ReaderPhase.COMPLETE, ReaderPhase.ERROR)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { onJumpToChunk(state.chunk - 2) },
                    enabled = state.chunk > 1,
                ) { Text(stringResource(R.string.reader_prev_chunk)) }
                Text(
                    stringResource(R.string.reader_progress, state.chunk, state.total),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(
                    onClick = { onJumpToChunk(state.chunk) },
                    enabled = state.chunk < state.total,
                ) { Text(stringResource(R.string.reader_next_chunk)) }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = if (playing) onPause else onPlay,
                enabled = ready && !extracting && state.total > 0,
            ) { Text(stringResource(if (playing) R.string.reader_pause else R.string.reader_play)) }
            OutlinedButton(onClick = onStop, enabled = extracting || state.total > 0) {
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

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ReaderScreenPreview(modifier: Modifier = Modifier) {
    VoxoraTheme {
        Surface(modifier = modifier) {
            ReaderContent(
                state = ReaderState(phase = ReaderPhase.READY, chunk = 1, total = 4),
                narrationText = "",
                mode = "faithful",
                outputLang = "original",
                ready = true,
                settingsError = null,
                onBack = {},
                onModeChange = {},
                onLangChange = {},
                onPick = {},
                onPlay = {},
                onPause = {},
                onStop = {},
                onJumpToChunk = {},
            )
        }
    }
}
