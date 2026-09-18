package com.voxora.app.reader

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxora.app.R
import com.voxora.app.ui.theme.VoxoraColors
import com.voxora.app.ui.theme.VoxoraTheme
import com.voxora.core.gemini.ReaderNarrationModes
import com.voxora.core.i18n.AppLocales
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Voxora Reader: document narration with a foreground media service.
 *
 * The screen is organized around the listening workflow — document, reading mode,
 * language, playback, position, the reading page, then the Gemini narration preview.
 * Playback itself is owned by [ReaderController] / [ReaderService], so leaving this
 * destination never interrupts narration.
 *
 * The document is presented as **pages, one chunk at a time** (see [ChunkPage]), not as
 * one long scroll of every chunk: a 210-chunk PDF composes one page, and turning a page
 * goes through the controller's existing `jumpToChunk`. The reading page and the
 * narration preview are deliberately different surfaces: the page is the current chunk
 * rendered in the selected narration language, and the narration is the live transcript
 * of the unit being spoken. They are never merged.
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
    val readerBubble by viewModel.readerBubble.collectAsStateWithLifecycle()
    val languages by viewModel.languageOptions.collectAsStateWithLifecycle()
    var languageQuery by remember { mutableStateOf("") }
    var choosingLanguage by remember { mutableStateOf(false) }
    // Language names are only ever rendered in a locale Voxora actually ships, so a
    // device locale with no Voxora translation cannot label them in another script.
    val locale = AppLocales.resolve(LocalConfiguration.current.locales[0])
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
        readerBubble = readerBubble,
        onBack = onBack,
        onModeChange = viewModel::setMode,
        onOpenLanguage = { languageQuery = ""; choosingLanguage = true },
        onPick = { picker.launch(arrayOf("application/pdf", "text/plain")) },
        onPlay = viewModel::play,
        onPause = viewModel::pause,
        onStop = viewModel::stop,
        onJumpToChunk = viewModel::jumpToChunk,
        onJumpToSegment = viewModel::jumpToSegment,
        onToggleBubble = viewModel::setReaderBubble,
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
    readerBubble: Boolean,
    onBack: () -> Unit,
    onModeChange: (String) -> Unit,
    onOpenLanguage: () -> Unit,
    onPick: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onJumpToChunk: (Int) -> Boolean,
    onJumpToSegment: (Int) -> Boolean,
    onToggleBubble: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Every gate comes from ReaderGates so the screen and the ViewModel can never
    // disagree about what is available, and so the rules stay unit-testable.
    val extracting = ReaderGates.isExtracting(state.phase)
    val playing = ReaderGates.isNarrating(state.phase)
    val configuring = ReaderGates.canConfigure(ready, state.phase)
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
            ReaderTopBar(
                onBack = onBack,
                bubbleEnabled = readerBubble,
                onToggleBubble = { onToggleBubble(!readerBubble) },
            )
        }
        item(key = "document") {
            DocumentCard(
                documentName = state.documentName,
                hasDocument = hasDocument,
                extracting = extracting,
                enabled = ReaderGates.canPickDocument(ready),
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
                ready = ready,
                hasDocument = hasDocument,
                onPlay = onPlay,
                onPause = onPause,
                onStop = onStop,
            )
        }
        val error = state.error ?: settingsError
        if (error != null) {
            item(key = "error") {
                ErrorCard(message = error)
            }
        }
        if (state.segments.isNotEmpty()) {
            item(key = "page") {
                ChunkPage(
                    state = state,
                    languageLabel = languageLabel,
                    languageFlag = languageFlag,
                    onJumpToChunk = onJumpToChunk,
                    onJumpToSegment = onJumpToSegment,
                )
            }
        }
        item(key = "bubble") {
            BubbleCard(
                enabled = readerBubble,
                onToggle = onToggleBubble,
            )
        }
        item(key = "narration") {
            NarrationCard(narrationText = narrationText)
        }
        item(key = "privacy") {
            Text(
                text = stringResource(R.string.reader_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = VoxoraColors.explanation,
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    onBack: () -> Unit,
    bubbleEnabled: Boolean,
    onToggleBubble: () -> Unit,
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
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // The bubble is on by default, like the Live bubble. This toggle is the only place it is
        // turned back on, so the content description names the action rather than the state.
        IconButton(onClick = onToggleBubble) {
            Icon(
                imageVector = Icons.Filled.BubbleChart,
                contentDescription = stringResource(
                    if (bubbleEnabled) R.string.reader_bubble_hide else R.string.reader_bubble_show,
                ),
                tint = if (bubbleEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun DocumentCard(
    documentName: String,
    hasDocument: Boolean,
    extracting: Boolean,
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
                        text = when {
                            // A restored document is being read: say so instead of
                            // claiming there is no document, which read as a frozen screen.
                            extracting && !hasDocument -> stringResource(R.string.reader_document_loading_title)
                            hasDocument -> documentName.ifBlank { stringResource(R.string.reader_document_unknown) }
                            else -> stringResource(R.string.reader_document_none_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (extracting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = colors.primary,
                    trackColor = colors.surfaceContainerHighest,
                )
                Text(
                    text = stringResource(R.string.reader_document_loading_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = VoxoraColors.explanation,
                )
            } else if (!hasDocument) {
                Text(
                    text = stringResource(R.string.reader_document_none_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoxoraColors.explanation,
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

/**
 * Narration-language row.
 *
 * The leading badge is a spoken-word icon rather than the language's flag: a flag is a
 * country, not a voice, and using one as the card's primary icon is exactly the generic
 * treatment this replaces. The flag is still shown, inline and small, next to the name
 * where it helps identify the selection.
 */
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
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.RecordVoiceOver,
                    contentDescription = null,
                    tint = colors.primary,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reader_language_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Decorative: the name beside it is what screen readers should read.
                    Text(
                        text = flag,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
    ready: Boolean,
    hasDocument: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
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
            // Real state, not a timer: `preparing` is true only while the run is actually
            // waiting for its first unit's selected-language and selected-style text, so this
            // says what is happening instead of pretending to show progress.
            if (state.preparing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colors.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.reader_preparing_first),
                        style = MaterialTheme.typography.bodySmall,
                        color = VoxoraColors.explanation,
                    )
                }
            }
            Button(
                onClick = if (playing) onPause else onPlay,
                enabled = ReaderGates.canPlay(ready, state.phase, hasDocument),
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
            OutlinedButton(
                onClick = onStop,
                enabled = ReaderGates.canStop(state.phase, hasDocument),
                modifier = Modifier
                    .fillMaxWidth()
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
            Text(
                text = stringResource(R.string.reader_pause_hint),
                style = MaterialTheme.typography.bodySmall,
                color = VoxoraColors.explanation,
            )
        }
    }
}

/**
 * The background-playback explanation.
 *
 * Narration is kept alive by the foreground service, not by the bubble; the bubble is a
 * quick-access surface over other apps. The card says exactly that, states that it is
 * independent from Live Dub, and carries the same toggle as the top bar so the setting is
 * discoverable without hunting for an icon.
 */
@Composable
private fun BubbleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        imageVector = Icons.Filled.BubbleChart,
                        contentDescription = null,
                        tint = colors.primary,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.reader_bubble_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.reader_bubble_explain),
                style = MaterialTheme.typography.bodySmall,
                color = VoxoraColors.explanation,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.reader_bubble_toggle),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                )
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
        }
    }
}

/** Semantic colour for a phase's status tone; never a literal inside the composable. */
@Composable
private fun statusColor(phase: ReaderPhase): Color = when (ReaderStatusVisual.tone(phase)) {
    ReaderStatusTone.ACTIVE -> VoxoraColors.success
    ReaderStatusTone.READY -> VoxoraColors.warning
    ReaderStatusTone.STOPPED -> VoxoraColors.danger
    ReaderStatusTone.NEUTRAL -> MaterialTheme.colorScheme.outline
}

/**
 * Status light for the current phase.
 *
 * The halo only exists while narration is actually running, so the animation is not
 * merely frozen behind a paused reader — it is not composed at all.
 */
@Composable
private fun StatusDot(
    phase: ReaderPhase,
    modifier: Modifier = Modifier,
) {
    val color = statusColor(phase)
    Box(
        modifier = modifier.size(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (ReaderStatusVisual.pulses(phase)) {
            ActiveHalo(color = color)
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** A slow, low-amplitude breath: alive, not neon. */
@Composable
private fun ActiveHalo(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "reader-active")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ACTIVE_BREATH_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    Box(
        modifier = modifier
            .size(18.dp)
            .graphicsLayer {
                scaleX = 0.85f + 0.35f * breath
                scaleY = 0.85f + 0.35f * breath
                alpha = 0.12f + 0.28f * breath
            }
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * The reading page: exactly one narration unit of the current chunk.
 *
 * The page shows the unit the reader is on, and the horizontal swipe moves to the next or
 * previous unit **inside this chunk**. A swipe never changes the chunk: the rule the gesture
 * uses ([ReaderPager.swipeStep]) is bounded by the chunk's unit count, so it cannot express
 * a chunk move at all. Changing chunks is the explicit Previous/Next controls at the bottom
 * of the page, which call the controller's existing `jumpToChunk`. Chunk navigation and
 * segment navigation are therefore two independent things, and only one of them is a swipe.
 *
 * A release that does not cross the threshold animates the card back to **exactly** zero
 * offset; a release that does cross it animates the current unit out towards the side the
 * finger was already moving, changes the segment index exactly once through
 * `jumpToSegment`, and brings the arriving unit in from the opposite side. The offset is
 * state rather than a one-shot effect, so no stale displacement can survive recomposition,
 * and the turn guard makes rapid repeated swipes resolve one at a time.
 *
 * **Only the text card moves.** The page itself is a fixed frame: the section header, the
 * chunk identity card, the segment position row and the chunk buttons never translate or fade,
 * so a turn reads as the page sliding away rather than the whole screen lurching. The gesture
 * and the transform are therefore attached to [SegmentCard], while the turn state stays here —
 * it is what drives the controller jump and has to outlive the card that is being swapped.
 */
@Composable
private fun ChunkPage(
    state: ReaderState,
    languageLabel: String,
    languageFlag: String,
    onJumpToChunk: (Int) -> Boolean,
    onJumpToSegment: (Int) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val threshold = with(density) { PAGE_TURN_THRESHOLD_DP.dp.toPx() }
    val travel = with(density) { PAGE_ENTER_TRAVEL_DP.dp.toPx() }
    val maxDrag = with(density) { PAGE_MAX_DRAG_DP.dp.toPx() }

    val chunkIndex = state.chunk - 1
    val chunkTotal = state.total
    val segmentIndex = ReaderPager.segmentIndex(state.segment, state.segmentTotal)
    // Derived together with the index so an inconsistent state can never become an invalid
    // list access: no index, no text, no card.
    val segmentText = segmentIndex?.let(state.segments::getOrNull)
    val canNavigate = ReaderGates.canNavigate(state.phase, chunkTotal > 0)
    val narrating = ReaderGates.isNarrating(state.phase)

    var drag by remember { mutableFloatStateOf(0f) }
    var presence by remember { mutableFloatStateOf(1f) }
    var turning by remember { mutableStateOf(false) }
    var forward by remember { mutableStateOf(true) }
    // The settle-back animation, kept so a new gesture cancels it instead of racing it. Two
    // writers on the same offset would otherwise leave the card jittering mid-return.
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // Whenever the displayed unit changes — by a swipe, by the explicit controls, or because
    // the narration advanced on its own — the page must be at rest with no offset left over.
    // Resetting here rather than immediately after the jump is also what makes the swap
    // seamless: the outgoing unit has already receded and faded by the time the arriving one
    // is composed, so it is never flashed back at full opacity.
    LaunchedEffect(state.chunk, state.segment) {
        drag = 0f
        presence = 1f
        turning = false
    }

    /** Returns the card to exactly its resting position, never a partially shifted one. */
    suspend fun settle(from: Float) {
        if (from == 0f) {
            drag = 0f
            return
        }
        animate(from, 0f, animationSpec = tween(durationMillis = PAGE_RETURN_MS)) { value, _ ->
            drag = value
        }
        // The resting offset is exact, not merely where the interpolation happened to stop.
        drag = 0f
    }

    /**
     * A completed swipe: the current unit recedes and fades towards the side the finger was
     * already moving, then the controller is asked for exactly one segment step. The arriving
     * unit is composed by [LaunchedEffect] above at zero offset and slides in from the
     * opposite side.
     */
    fun requestSegmentTurn(step: SegmentStep) {
        if (turning) return
        settleJob?.cancel()
        settleJob = null
        forward = step.turn == PageTurn.NEXT
        turning = true
        val enterFrom = ReaderPager.enterOffset(forward, rtl) * travel
        val released = drag
        scope.launch {
            launch {
                animate(
                    released,
                    -enterFrom,
                    animationSpec = tween(durationMillis = PAGE_EXIT_MS),
                ) { value, _ -> drag = value }
            }
            animate(presence, 0f, animationSpec = tween(durationMillis = PAGE_EXIT_MS)) { value, _ ->
                presence = value
            }
            if (!onJumpToSegment(step.target)) {
                // The controller refused the step, so nothing moved. Bring the page back to
                // rest instead of leaving it faded out and off to the side.
                launch {
                    animate(
                        -enterFrom,
                        0f,
                        animationSpec = tween(durationMillis = PAGE_RETURN_MS),
                    ) { value, _ -> drag = value }
                }
                animate(presence, 1f, animationSpec = tween(durationMillis = PAGE_ENTER_MS)) { value, _ ->
                    presence = value
                }
                drag = 0f
                presence = 1f
                turning = false
            }
        }
    }

    /** An explicit chunk move: the page recedes and the arriving chunk fades in at rest. */
    fun requestChunkTurn(target: Int, next: Boolean) {
        if (turning) return
        settleJob?.cancel()
        settleJob = null
        forward = next
        turning = true
        scope.launch {
            animate(presence, 0f, animationSpec = tween(durationMillis = PAGE_EXIT_MS)) { value, _ ->
                presence = value
            }
            drag = 0f
            if (!onJumpToChunk(target)) {
                animate(presence, 1f, animationSpec = tween(durationMillis = PAGE_ENTER_MS)) { value, _ ->
                    presence = value
                }
                turning = false
            }
        }
    }

    val gesture = Modifier.pointerInput(segmentIndex, state.segmentTotal, rtl, canNavigate) {
        if (!canNavigate) return@pointerInput
        detectHorizontalDragGestures(
            onDragStart = {
                // A new gesture takes over the offset; the return animation must not keep
                // writing to it.
                settleJob?.cancel()
                settleJob = null
            },
            onDragEnd = {
                val released = drag
                drag = 0f
                // Segment-scoped by construction: the rule is bounded by this chunk's unit
                // count, so no gesture can carry the reader into another chunk.
                val step = ReaderPager.swipeStep(
                    current = segmentIndex ?: 0,
                    deltaX = released,
                    rtl = rtl,
                    threshold = threshold,
                    segmentTotal = state.segmentTotal,
                )
                if (step != null) {
                    requestSegmentTurn(step)
                } else {
                    settleJob = scope.launch { settle(released) }
                }
            },
            onDragCancel = {
                val released = drag
                drag = 0f
                settleJob = scope.launch { settle(released) }
            },
        ) { change, delta ->
            if (!turning) {
                change.consume()
                drag = (drag + delta).coerceIn(-maxDrag, maxDrag)
            }
        }
    }

    // The page is a fixed frame. Only the text card below moves: the section header, the chunk
    // identity card, the segment position row and the chunk buttons stay exactly at rest while a
    // turn plays, so a swipe reads as "the page I am reading slid away", not "the screen moved".
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.reader_page_section),
            hint = stringResource(R.string.reader_page_hint),
        )
        ChunkHeader(
            chunk = state.chunk,
            total = chunkTotal,
            segment = state.segment,
            segmentTotal = state.segmentTotal,
            languageFlag = languageFlag,
            languageLabel = languageLabel,
            mode = state.narrationMode,
            preparing = ReaderPageText.isPreparingWholePage(
                phase = state.phase,
                pending = state.pendingSegments,
                unitCount = state.segmentTotal,
            ),
        )
        if (segmentIndex != null && segmentText != null) {
            // Keyed on the unit so the arriving card is composed at zero presence and slides in
            // from the side the turn came from. The turn state itself (drag, presence, the guard)
            // stays owned by the page, because it is what drives the controller jump; only the
            // visual transform and the gesture live here, on the card.
            key(segmentIndex) {
                val enter = remember { Animatable(0f) }
                LaunchedEffect(Unit) { enter.animateTo(1f, tween(durationMillis = PAGE_ENTER_MS)) }

                val dragFade = 1f - (abs(drag) / maxDrag) * 0.5f
                val direction = ReaderPager.enterOffset(forward, rtl)

                SegmentCard(
                    text = segmentText,
                    narrating = narrating,
                    preparing = ReaderPageText.isPreparing(
                        phase = state.phase,
                        pending = state.pendingSegments,
                        index = segmentIndex,
                    ),
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = drag + direction * travel * (1f - enter.value)
                            alpha = (presence * enter.value * dragFade).coerceIn(0f, 1f)
                        }
                        .then(gesture),
                )
            }
        }
        SegmentTurnRow(
            position = state.segment,
            total = state.segmentTotal,
            canPrevious = segmentIndex != null &&
                ReaderPager.target(segmentIndex, PageTurn.PREVIOUS, state.segmentTotal) != null,
            canNext = segmentIndex != null &&
                ReaderPager.target(segmentIndex, PageTurn.NEXT, state.segmentTotal) != null,
            enabled = canNavigate && !turning,
            onPrevious = {
                val index = segmentIndex
                val target = index?.let { ReaderPager.target(it, PageTurn.PREVIOUS, state.segmentTotal) }
                if (target != null) requestSegmentTurn(SegmentStep(PageTurn.PREVIOUS, target))
            },
            onNext = {
                val index = segmentIndex
                val target = index?.let { ReaderPager.target(it, PageTurn.NEXT, state.segmentTotal) }
                if (target != null) requestSegmentTurn(SegmentStep(PageTurn.NEXT, target))
            },
        )
        PageTurnRow(
            canPrevious = ReaderPager.target(chunkIndex, PageTurn.PREVIOUS, chunkTotal) != null,
            canNext = ReaderPager.target(chunkIndex, PageTurn.NEXT, chunkTotal) != null,
            enabled = canNavigate && !turning,
            onPrevious = {
                ReaderPager.target(chunkIndex, PageTurn.PREVIOUS, chunkTotal)
                    ?.let { requestChunkTurn(it, next = false) }
            },
            onNext = {
                ReaderPager.target(chunkIndex, PageTurn.NEXT, chunkTotal)
                    ?.let { requestChunkTurn(it, next = true) }
            },
        )
    }
}

/** Compact page identity: where the reader is, in which language and style, and how far in. */
@Composable
private fun ChunkHeader(
    chunk: Int,
    total: Int,
    segment: Int,
    segmentTotal: Int,
    languageFlag: String,
    languageLabel: String,
    mode: String,
    preparing: Boolean,
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        imageVector = Icons.Filled.RecordVoiceOver,
                        contentDescription = null,
                        tint = colors.primary,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reader_page_chunk, chunk, total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                    if (segmentTotal > 0) {
                        Text(
                            text = stringResource(R.string.reader_segment_progress, segment, segmentTotal),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Decorative: the language name is the meaningful part.
                Text(
                    text = languageFlag,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = languageLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // The page's text is the selected language *and* the selected narration style, so
            // the page says which variant it is showing. A reader who switches style can see
            // that the wording on screen belongs to the new one rather than the old.
            Text(
                text = stringResource(R.string.reader_page_style, stringResource(modeLabelOf(mode))),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            if (preparing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colors.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.reader_page_preparing, languageLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = VoxoraColors.explanation,
                    )
                }
            }
        }
    }
}

/**
 * **Segment** controls: one unit backwards, the position, one unit forwards.
 *
 * The swipe already does this; these exist so the same step is reachable without a gesture
 * and so a screen reader can announce it. They are bounded by the current chunk exactly like
 * the swipe, so they can never change the chunk either.
 *
 * The arrows are auto-mirrored, so "previous" points the way the reader expects in both
 * layout directions. The position sits between them in the layout's own order, which is why
 * it is a formatted sentence rather than a bare "3 / 8" — a bare pair of numbers reorders
 * unpredictably under RTL bidi.
 */
@Composable
private fun SegmentTurnRow(
    position: Int,
    total: Int,
    canPrevious: Boolean,
    canNext: Boolean,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = enabled && canPrevious,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.reader_prev_segment),
                tint = if (enabled && canPrevious) colors.primary else colors.outline,
            )
        }
        Text(
            text = stringResource(R.string.reader_segment_progress, position, total),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
        )
        IconButton(
            onClick = onNext,
            enabled = enabled && canNext,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.reader_next_segment),
                tint = if (enabled && canNext) colors.primary else colors.outline,
            )
        }
    }
}

/**
 * **Chunk** controls: the only thing in the Reader that moves between chunks.
 *
 * Kept visually distinct from the segment row above it because the two do different things:
 * the segment row steps inside this chunk, these move the document position. The icons are
 * decorative — each button carries its own visible label, so announcing them twice would be
 * noise.
 */
@Composable
private fun PageTurnRow(
    canPrevious: Boolean,
    canNext: Boolean,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = onPrevious,
            enabled = enabled && canPrevious,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.reader_prev_chunk))
        }
        OutlinedButton(
            onClick = onNext,
            enabled = enabled && canNext,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(stringResource(R.string.reader_next_chunk))
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The narration unit currently on the page.
 *
 * Exactly one of these is composed, because the page shows one unit — the swipe moves to the
 * next one rather than scrolling a list. It is a plain surface rather than a clickable one:
 * there is nothing left to select within a single unit.
 */
@Composable
private fun SegmentCard(
    text: String,
    narrating: Boolean,
    preparing: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.primaryContainer,
        border = BorderStroke(1.dp, colors.primary),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // "Now narrating" is a claim about the audio, so it is only shown while the audio
            // is actually running. A reader who has not pressed Play is still browsing.
            if (narrating) {
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
            if (preparing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = colors.secondary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.reader_segment_preparing),
                        style = MaterialTheme.typography.labelMedium,
                        color = VoxoraColors.explanation,
                    )
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                // A unit still being rendered is presented as provisional rather than as
                // settled selected-language text.
                color = if (preparing) colors.onSurfaceVariant else colors.onPrimaryContainer,
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
                color = if (hasNarration) colors.onSurface else VoxoraColors.explanation,
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
                color = VoxoraColors.explanation,
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

private const val PAGE_ENTER_MS = 240
private const val PAGE_EXIT_MS = 130
/** Settling a sub-threshold drag back to rest. Short, and it always ends at exactly zero. */
private const val PAGE_RETURN_MS = 180
private const val ACTIVE_BREATH_MS = 1500
private const val PAGE_TURN_THRESHOLD_DP = 56
private const val PAGE_ENTER_TRAVEL_DP = 40
private const val PAGE_MAX_DRAG_DP = 150

/** Human label for the narration style, from the one mode contract. */
private fun modeLabelOf(mode: String): Int =
    if (mode == ReaderNarrationModes.FLUENT) R.string.reader_fluent else R.string.reader_faithful

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
                readerBubble = true,
                onBack = {},
                onModeChange = {},
                onOpenLanguage = {},
                onPick = {},
                onPlay = {},
                onPause = {},
                onStop = {},
                onJumpToChunk = { true },
                onJumpToSegment = { true },
                onToggleBubble = {},
            )
        }
    }
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ReaderScreenPreparingPreview(modifier: Modifier = Modifier) {
    VoxoraTheme {
        Surface(modifier = modifier) {
            ReaderContent(
                state = ReaderState(
                    phase = ReaderPhase.SPEAKING,
                    chunk = 12,
                    total = 210,
                    segment = 1,
                    segmentTotal = 3,
                    documentName = "shahnameh.pdf",
                    text = stringResource(R.string.reader_preview_source),
                    segments = listOf(
                        stringResource(R.string.reader_preview_source),
                        stringResource(R.string.reader_preview_next),
                        stringResource(R.string.reader_preview_next),
                    ),
                    pendingSegments = setOf(0, 1, 2),
                ),
                narrationText = "",
                mode = ReaderNarrationModes.FLUENT,
                languageLabel = stringResource(R.string.reader_lang_en),
                languageFlag = "🇬🇧",
                ready = true,
                settingsError = null,
                readerBubble = true,
                onBack = {},
                onModeChange = {},
                onOpenLanguage = {},
                onPick = {},
                onPlay = {},
                onPause = {},
                onStop = {},
                onJumpToChunk = { true },
                onJumpToSegment = { true },
                onToggleBubble = {},
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
                readerBubble = true,
                onBack = {},
                onModeChange = {},
                onOpenLanguage = {},
                onPick = {},
                onPlay = {},
                onPause = {},
                onStop = {},
                onJumpToChunk = { true },
                onJumpToSegment = { true },
                onToggleBubble = {},
            )
        }
    }
}
