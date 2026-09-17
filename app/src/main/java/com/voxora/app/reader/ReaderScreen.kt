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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
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
    val outputLang by viewModel.outputLang.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val settingsError by viewModel.settingsError.collectAsStateWithLifecycle()
    val languages by viewModel.languageOptions.collectAsStateWithLifecycle()
    val languageLabel by viewModel.languageLabel.collectAsStateWithLifecycle()
    var languageQuery by remember { mutableStateOf("") }
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(languageQuery, locale, outputLang) {
        viewModel.searchLanguages(languageQuery, locale)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::load)
    }
    BackHandler(onBack = onBack)
    ReaderContent(
        state = state,
        narrationText = narrationText,
        mode = mode,
        outputLang = outputLang,
        languageLabel = languageLabel,
        languages = languages,
        languageQuery = languageQuery,
        onLanguageQuery = { languageQuery = it },
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
        onJumpToSegment = viewModel::jumpToSegment,
        modifier = modifier,
    )
}

@Composable
private fun ReaderContent(
    state: ReaderState,
    narrationText: String,
    mode: String,
    outputLang: String,
    languageLabel: String,
    languages: List<ReaderLanguageOption>,
    languageQuery: String,
    onLanguageQuery: (String) -> Unit,
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
    onJumpToSegment: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extracting = state.phase == ReaderPhase.EXTRACTING
    val playing = state.phase in setOf(ReaderPhase.CONNECTING, ReaderPhase.REWRITING, ReaderPhase.SPEAKING, ReaderPhase.NEXT)
    var choosingLanguage by remember { mutableStateOf(false) }
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
        Text(stringResource(R.string.reader_mode_help), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { onLanguageQuery(""); choosingLanguage = true },
            enabled = ready && !playing && !extracting,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.reader_output_language, languageLabel)) }
        OutlinedButton(onClick = onPick, enabled = ready && !extracting) {
            Text(stringResource(R.string.reader_pick))
        }
        Text(stringResource(statusLabel), style = MaterialTheme.typography.titleMedium)
        if (extracting || state.phase == ReaderPhase.CONNECTING || state.phase == ReaderPhase.REWRITING || state.phase == ReaderPhase.NEXT) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.total > 0) {
            Text(stringResource(R.string.reader_progress, state.chunk, state.total))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onJumpToChunk(state.chunk - 2) },
                    enabled = !extracting && state.chunk > 1,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.reader_prev_chunk)) }
                OutlinedButton(
                    onClick = { onJumpToChunk(state.chunk) },
                    enabled = !extracting && state.chunk < state.total,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.reader_next_chunk)) }
            }
        }
        if (state.segmentTotal > 0) {
            Text(stringResource(R.string.reader_segment_progress, state.segment, state.segmentTotal))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onJumpToSegment(state.segment - 2) },
                    enabled = !extracting && state.segment > 1,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.reader_prev_segment)) }
                OutlinedButton(
                    onClick = { onJumpToSegment(state.segment) },
                    enabled = !extracting && state.segment < state.segmentTotal,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.reader_next_segment)) }
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
        if (state.text.isNotBlank()) {
            Text(stringResource(R.string.reader_source_chunk), style = MaterialTheme.typography.labelMedium)
            state.segments.forEachIndexed { index, text ->
                val selected = index == state.segment - 1
                Surface(
                    onClick = { onJumpToSegment(index) },
                    enabled = !extracting,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = if (selected) 2.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (selected) Text(
                            stringResource(R.string.reader_source_segment),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        if (narrationText.isNotBlank()) {
            Text(stringResource(R.string.reader_narrating), style = MaterialTheme.typography.labelMedium)
            Text(narrationText, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (choosingLanguage && !playing && !extracting) {
        ReaderLanguageDialog(
            languages = languages,
            selected = outputLang,
            query = languageQuery,
            onQuery = onLanguageQuery,
            onSelect = { onLangChange(it); choosingLanguage = false },
            onDismiss = { choosingLanguage = false },
        )
    }
}

@Composable
private fun ReaderLanguageDialog(
    languages: List<ReaderLanguageOption>,
    selected: String,
    query: String,
    onQuery: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.reader_choose_language)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    label = { Text(stringResource(R.string.reader_search_language)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (languages.isEmpty()) Text(stringResource(R.string.reader_no_languages))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(languages, key = { it.code }) { language ->
                        FilterChip(
                            selected = language.code == selected,
                            onClick = { onSelect(language.code) },
                            label = { Text(language.label) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_back)) } },
    )
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ReaderScreenPreview(modifier: Modifier = Modifier) {
    VoxoraTheme {
        Surface(modifier = modifier) {
            ReaderContent(
                state = ReaderState(
                    phase = ReaderPhase.SPEAKING,
                    chunk = 1,
                    total = 4,
                    segment = 1,
                    segmentTotal = 2,
                    text = stringResource(R.string.reader_preview_source) + " " + stringResource(R.string.reader_preview_next),
                    segments = listOf(stringResource(R.string.reader_preview_source), stringResource(R.string.reader_preview_next)),
                ),
                narrationText = "",
                mode = "faithful",
                outputLang = "en",
                languageLabel = stringResource(R.string.reader_lang_en),
                languages = emptyList(),
                languageQuery = "",
                onLanguageQuery = {},
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
                onJumpToSegment = {},
            )
        }
    }
}
