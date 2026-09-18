package com.voxora.app.reader

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxora.app.R
import com.voxora.app.ui.theme.VoxoraTheme
import com.voxora.core.gemini.ReaderNarrationModes

/**
 * Voxora Reader: document narration with a foreground media service.
 *
 * The screen is organized around the listening workflow — document, reading mode,
 * language, playback, position, source text, then the Gemini narration preview.
 * Playback itself is owned by [ReaderController] / [ReaderService], so leaving this
 * destination never interrupts narration.
 *
 * The reading text and the narration preview are deliberately different surfaces: the
 * reading text is the current chunk rendered in the selected narration language (a unit
 * not narrated in that language yet shows the extracted source), and the narration is
 * the live transcript of the unit being spoken. They are never merged.
 */
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
    val languageLabel by viewModel.languageLabel.collectAsStateWithLifecycle()
    val languageFlag by viewModel.languageFlag.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val settingsError by viewModel.settingsError.collectAsStateWithLifecycle()
    val languages by viewModel.languageOptions.collectAsStateWithLifecycle()
    var languageQuery by remember { mutableStateOf("") }
    var choosingLanguage by remember { mutableStateOf(false) }
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
        languageLabel = languageLabel,
        languageFlag = languageFlag,
        ready = ready,
        settingsError = settingsError,
        onBack = onBack,
        onModeChange = viewModel::setMode,
        onOpenLanguage = { languageQuery = ""; choosingLanguage = true },
        onPick = { picker.launch(arrayOf("application/pdf", "text/plain")) },
        onPlay = viewModel::play,
        onPause = viewModel::pause,
        onStop = viewModel::stop,
        onJumpToChunk = viewModel::jumpToChunk,
        onJumpToSegment = viewModel::jumpToSegment,
        modifier = modifier,
    )
    if (choosingLanguage) {
        ReaderLanguageSheet(
            languages = languages,
            selected = outputLang,
            query = languageQuery,
            onQuery = { languageQuery = it },
            onSelect = { viewModel.setOutputLang(it); choosingLanguage = false },
            onDismiss = { choosingLanguage = false },
        )
    }
}

@Composable
private fun ReaderContent(
    state: ReaderState,
    narrationText: String,
    mode: String,
    languageLabel: String,
    languageFlag: String,
    ready: Boolean,
    settingsError: String?,
    onBack: () -> Unit,
    onModeChange: (String) -> Unit,
    onOpenLanguage: () -> Unit,
    onPick: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onJumpToChunk: (Int) -> Unit,
    onJumpToSegment: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val extracting = state.phase == ReaderPhase.EXTRACTING
    val playing = state.phase in ACTIVE_PHASES
    val configuring = ready && !playing && !extracting
    val hasDocument = state.total > 0
    val progress = progressOf(state)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "top-bar") {
            ReaderTopBar(onBack = onBack)
        }
        item(key = "document") {
            DocumentCard(
                documentName = state.documentName,
                hasDocument = hasDocument,
                enabled = ready && !extracting,
                onPick = onPick,
            )
        }
        item(key = "mode") {
            ModeSection(
                mode = mode,
                enabled = configuring,
                onModeChange = onModeChange,
            )
        }
        item(key = "language") {
            LanguageRow(
                flag = languageFlag,
                label = languageLabel,
                enabled = configuring,
                onClick = onOpenLanguage,
            )
        }
        item(key = "playback") {
            PlaybackCard(
                state = state,
                progress = progress,
                playing = playing,
                extracting = extracting,
                ready = ready,
                hasDocument = hasDocument,
                onPlay = onPlay,
                onPause = onPause,
                onStop = onStop,
                onJumpToChunk = onJumpToChunk,
            )
        }
        val error = state.error ?: settingsError
        if (error != null) {
            item(key = "error") {
                ErrorCard(message = error)
            }
        }
        if (state.segments.isNotEmpty()) {
            item(key = "source-header") {
                SectionHeader(
                    title = stringResource(R.string.reader_display_section, languageLabel),
                    hint = stringResource(R.string.reader_display_hint),
                )
            }
            itemsIndexed(state.segments, key = { index, _ -> "segment-$index" }) { index, text ->
                SegmentCard(
                    text = text,
                    current = index == state.segment - 1,
                    enabled = !extracting,
                    onClick = { onJumpToSegment(index) },
                )
            }
        }
        item(key = "narration") {
            NarrationCard(narrationText = narrationText)
        }
        item(key = "privacy") {
            Text(
                text = stringResource(R.string.reader_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.reader_back),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.reader_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DocumentCard(
    documentName: String,
    hasDocument: Boolean,
    enabled: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = colors.primary,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reader_document_section),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = if (hasDocument) {
                            documentName.ifBlank { stringResource(R.string.reader_document_unknown) }
                        } else {
                            stringResource(R.string.reader_document_none_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!hasDocument) {
                Text(
                    text = stringResource(R.string.reader_document_none_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Button(
                onClick = onPick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Icon(imageVector = Icons.Filled.UploadFile, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(
                        if (hasDocument) R.string.reader_document_change else R.string.reader_pick,
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ModeSection(
    mode: String,
    enabled: Boolean,
    onModeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader(title = stringResource(R.string.reader_mode_section))
        ModeCard(
            title = stringResource(R.string.reader_faithful),
            description = stringResource(R.string.reader_mode_faithful_desc),
            selected = mode == ReaderNarrationModes.FAITHFUL,
            enabled = enabled,
            onClick = { onModeChange(ReaderNarrationModes.FAITHFUL) },
        )
        ModeCard(
            title = stringResource(R.string.reader_fluent),
            description = stringResource(R.string.reader_mode_fluent_desc),
            selected = mode == ReaderNarrationModes.FLUENT,
            enabled = enabled,
            onClick = { onModeChange(ReaderNarrationModes.FLUENT) },
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceContainer,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        colors.onPrimaryContainer.copy(alpha = 0.85f)
                    } else {
                        colors.onSurfaceVariant
                    },
                )
            }
            if (selected) {
                Spacer(Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.reader_mode_selected),
                    tint = colors.primary,
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(
    flag: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Decorative: the language name beside it is what screen readers should read.
            Text(
                text = flag,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reader_language_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.reader_language_change),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaybackCard(
    state: ReaderState,
    progress: Float,
    playing: Boolean,
    extracting: Boolean,
    ready: Boolean,
    hasDocument: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onJumpToChunk: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(phase = state.phase)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(statusLabelOf(state.phase)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.weight(1f))
                if (state.total > 0) {
                    Text(
                        text = stringResource(R.string.reader_progress, state.chunk, state.total),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = colors.primary,
                trackColor = colors.surfaceContainerHighest,
            )
            if (state.segmentTotal > 0) {
                Text(
                    text = stringResource(
                        R.string.reader_segment_progress,
                        state.segment,
                        state.segmentTotal,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Button(
                onClick = if (playing) onPause else onPlay,
                enabled = ready && !extracting && hasDocument,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(if (playing) R.string.reader_pause else R.string.reader_play),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onStop,
                    enabled = extracting || hasDocument,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_stop))
                }
                OutlinedButton(
                    onClick = { onJumpToChunk(state.chunk - 2) },
                    enabled = !extracting && state.chunk > 1,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.reader_prev_chunk))
                }
                OutlinedButton(
                    onClick = { onJumpToChunk(state.chunk) },
                    enabled = !extracting && state.chunk < state.total,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.reader_next_chunk))
                }
            }
            Text(
                text = stringResource(R.string.reader_pause_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusDot(
    phase: ReaderPhase,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val color = when (phase) {
        ReaderPhase.SPEAKING, ReaderPhase.COMPLETE -> colors.primary
        ReaderPhase.CONNECTING, ReaderPhase.REWRITING, ReaderPhase.NEXT -> colors.secondary
        ReaderPhase.ERROR -> colors.error
        ReaderPhase.PAUSED -> colors.onSurfaceVariant
        else -> colors.outline
    }
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun SegmentCard(
    text: String,
    current: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (current) colors.primaryContainer else colors.surfaceContainer,
        border = BorderStroke(1.dp, if (current) colors.primary else colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (current) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.reader_segment_now),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                    )
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (current) colors.onPrimaryContainer else colors.onSurface,
            )
        }
    }
}

@Composable
private fun NarrationCard(
    narrationText: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val hasNarration = narrationText.isNotBlank()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = colors.secondary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.reader_narration_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.secondary,
                )
            }
            Text(
                text = if (hasNarration) {
                    narrationText
                } else {
                    stringResource(R.string.reader_narration_empty)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasNarration) colors.onSurface else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.errorContainer,
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = colors.error,
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.error_banner_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onErrorContainer,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onErrorContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderLanguageSheet(
    languages: List<ReaderLanguageOption>,
    selected: String,
    query: String,
    onQuery: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_choose_language),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                label = { Text(stringResource(R.string.reader_search_language)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (languages.isEmpty()) {
                Text(
                    text = stringResource(R.string.reader_no_languages),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(languages, key = { it.code }) { language ->
                    LanguageSheetRow(
                        language = language,
                        selected = language.code == selected,
                        onSelect = { onSelect(language.code) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSheetRow(
    language: ReaderLanguageOption,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.primaryContainer else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Decorative: the language name beside it is what screen readers should read.
            Text(
                text = language.flagEmoji,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = language.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                )
                language.secondaryLabel?.let { secondary ->
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.reader_mode_selected),
                    tint = colors.primary,
                )
            }
        }
    }
}

private val ACTIVE_PHASES = setOf(
    ReaderPhase.CONNECTING,
    ReaderPhase.REWRITING,
    ReaderPhase.SPEAKING,
    ReaderPhase.NEXT,
)

private fun statusLabelOf(phase: ReaderPhase): Int = when (phase) {
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

/** Overall position across the whole document, not just the current chunk. */
private fun progressOf(state: ReaderState): Float = when {
    state.phase == ReaderPhase.COMPLETE -> 1f
    state.total <= 0 -> 0f
    else -> {
        val withinChunk = if (state.segmentTotal > 1) {
            (state.segment - 1).toFloat() / state.segmentTotal
        } else {
            0f
        }
        (((state.chunk - 1) + withinChunk) / state.total).coerceIn(0f, 1f)
    }
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ReaderScreenPreview(modifier: Modifier = Modifier) {
    VoxoraTheme {
        Surface(modifier = modifier) {
            ReaderContent(
                state = ReaderState(
                    phase = ReaderPhase.SPEAKING,
                    chunk = 2,
                    total = 6,
                    segment = 1,
                    segmentTotal = 2,
                    documentName = "deep-work.pdf",
                    text = stringResource(R.string.reader_preview_source),
                    segments = listOf(
                        stringResource(R.string.reader_preview_source),
                        stringResource(R.string.reader_preview_next),
                    ),
                ),
                narrationText = "",
                mode = ReaderNarrationModes.FAITHFUL,
                languageLabel = stringResource(R.string.reader_lang_en),
                languageFlag = "🇬🇧",
                ready = true,
                settingsError = null,
                onBack = {},
                onModeChange = {},
                onOpenLanguage = {},
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

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ReaderScreenEmptyPreview(modifier: Modifier = Modifier) {
    VoxoraTheme {
        Surface(modifier = modifier) {
            ReaderContent(
                state = ReaderState(phase = ReaderPhase.IDLE),
                narrationText = "",
                mode = ReaderNarrationModes.FLUENT,
                languageLabel = stringResource(R.string.reader_lang_fa),
                languageFlag = "🇮🇷",
                ready = true,
                settingsError = null,
                onBack = {},
                onModeChange = {},
                onOpenLanguage = {},
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
