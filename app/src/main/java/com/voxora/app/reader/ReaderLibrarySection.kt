package com.voxora.app.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.voxora.app.R
import com.voxora.app.ui.theme.VoxoraColors
import com.voxora.app.ui.theme.VoxoraTheme
import com.voxora.core.reader.ReaderBook
import com.voxora.core.reader.ReaderBookState
import com.voxora.core.reader.ReaderLibrary
import com.voxora.core.reader.ReaderSourceType

/** How far the list has to be dragged before it opens or closes, so a stray swipe does nothing. */
private val SWIPE_THRESHOLD = 48.dp

/**
 * The Reader library: every imported book, in one list that opens and closes.
 *
 * ## What this surface has to answer without the reader having to remember anything
 *
 * "Which book was I reading, and where was I?" The section always names one book — the one the
 * reader picked here, or the one the Reader has open, or the most recently read — and shows its real
 * saved chunk and when it was last read. Continue opens exactly that book, restores its persisted
 * chunk and starts narration, so "keep listening" is one press and never a re-synthesis of what was
 * already produced.
 *
 * ## One list, opened vertically
 *
 * There is deliberately **one** list rather than a separate "continue" card plus a list of
 * everything else: two surfaces meant the same book appeared twice and the reader had to work out
 * which one to press. The list is collapsed by default — a compact summary of the selected book and
 * its Continue — and expands **vertically** on a downward drag (or a tap) and collapses on an upward
 * drag. The gesture is vertical because the section is a card in a vertical page; a horizontal
 * gesture would fight the page's own scroll direction and mean nothing here.
 *
 * ## Selecting is not opening
 *
 * Tapping a book selects it and reveals its detail — the progress bar, the chunk of the total, the
 * state and the last-read line — together with Continue and Remove. Nothing is loaded and nothing
 * makes sound until Continue is pressed, so browsing the library can never interrupt what is
 * playing. Removing asks first and says exactly what is deleted: the library record and Voxora's
 * copy, never the original file.
 */
@Composable
internal fun ReaderLibrarySection(
    books: List<ReaderBook>,
    activeBookId: String?,
    canOpen: Boolean,
    onContinue: (String) -> Unit,
    onRemove: (String) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingRemoval by remember { mutableStateOf<String?>(null) }
    // Saved so scrolling the page past the library does not silently close it again.
    var expanded by rememberSaveable { mutableStateOf(false) }
    var chosenId by rememberSaveable { mutableStateOf("") }
    val ordered = remember(books) { ReaderLibrary.byRecency(books) }
    // The book the section speaks for: the one picked here, else the one the Reader has open, else
    // the most recently read. Every fallback is looked up in the current library, so a book that was
    // removed can never be named by a stale selection.
    val selected = remember(ordered, chosenId, activeBookId) {
        ReaderLibrary.find(ordered, chosenId.takeIf { it.isNotEmpty() })
            ?: ReaderLibrary.find(ordered, activeBookId)
            ?: ReaderLibrary.mostRecent(ordered)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(title = stringResource(R.string.reader_library_section))

        if (ordered.isEmpty()) {
            Text(
                text = stringResource(R.string.reader_library_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    LibraryHeader(
                        count = ordered.size,
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        onExpand = { expanded = true },
                        onCollapse = { expanded = false },
                    )
                    AnimatedVisibility(visible = expanded) {
                        Column(
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ordered.forEach { book ->
                                BookEntry(
                                    book = book,
                                    selected = book.id == selected?.id,
                                    active = book.id == activeBookId,
                                    enabled = canOpen,
                                    onSelect = { chosenId = book.id },
                                    onOpen = { onContinue(book.id) },
                                    onRemove = { pendingRemoval = book.id },
                                )
                            }
                        }
                    }
                    if (!expanded && selected != null) {
                        SelectedSummary(
                            book = selected,
                            enabled = canOpen && !selected.isUnavailable,
                            onOpen = { onContinue(selected.id) },
                        )
                    }
                }
            }
        }

        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(imageVector = Icons.Filled.UploadFile, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(text = stringResource(R.string.reader_library_import))
        }
    }

    val removal = pendingRemoval
    if (removal != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.reader_library_remove_title)) },
            text = { Text(stringResource(R.string.reader_library_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                        onRemove(removal)
                    },
                ) {
                    Text(stringResource(R.string.reader_library_remove_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.reader_library_cancel))
                }
            },
        )
    }
}

/**
 * The one control that opens and closes the list.
 *
 * It carries the book count so the collapsed card still says how much is behind it, and it is both
 * tappable and draggable: a tap is the accessible, discoverable way in, and the drag is what the
 * section is for. The drag is confined to this row, so the page still scrolls normally everywhere
 * else.
 */
@Composable
private fun LibraryHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "library-chevron",
    )
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    var dragged by remember { mutableStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        dragged += delta
        // Down opens, up closes. The accumulator is cleared whenever it crosses the threshold so a
        // long drag does not flip the state back and forth.
        if (dragged >= threshold) {
            dragged = 0f
            onExpand()
        } else if (dragged <= -threshold) {
            dragged = 0f
            onCollapse()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { dragged = 0f },
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.reader_library_count, count),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
        )
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = stringResource(
                if (expanded) R.string.reader_library_collapse else R.string.reader_library_expand,
            ),
            tint = colors.onSurfaceVariant,
            modifier = Modifier.rotate(chevron),
        )
    }
}

/**
 * The collapsed card: the selected book, its real position, and the one action that matters.
 *
 * Compact on purpose — a reader returning after a week sees a title, a chunk and Continue without
 * opening anything.
 */
@Composable
private fun SelectedSummary(book: ReaderBook, enabled: Boolean, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.reader_library_continue_title),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReaderCoverImage(url = book.metadata?.coverUrl, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BookTitle(book = book, color = colors.onSurface)
                PositionLine(book = book, color = colors.onSurfaceVariant)
            }
        }
        Button(onClick = onOpen, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(openLabel(book)))
        }
    }
}

/**
 * One book in the list.
 *
 * Unselected it is a compact line: the title, the author and where the book stands. Selected it adds
 * the full detail — the progress bar, the chunk of the total, the state, when it was last read — and
 * the actions. Nothing here opens the book: [onSelect] only reveals, and Continue is a separate,
 * deliberate press.
 */
@Composable
private fun BookEntry(
    book: ReaderBook,
    selected: Boolean,
    active: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onSelect,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReaderCoverImage(url = book.metadata?.coverUrl, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BookTitle(
                        book = book,
                        color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                    )
                    if (selected) {
                        book.metadata?.authorLine?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        PositionLine(book = book, color = colors.onSurfaceVariant)
                    }
                }
                if (active) {
                    ActiveChip(onContainer = selected)
                }
            }
            if (selected) {
                ProgressLine(book = book, onContainer = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onOpen,
                        // A book whose document copy is gone is not openable, so it offers no action
                        // that could only fail — the state line already says it is not available.
                        enabled = enabled && !book.isUnavailable,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(openLabel(book)))
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onRemove, enabled = enabled) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.reader_library_remove),
                            tint = colors.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

/** The title, capped so a long one cannot push the rest of a row off the card. */
@Composable
private fun BookTitle(book: ReaderBook, color: Color) {
    Text(
        text = book.title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The book's real saved position: the chunk it will resume from, and what that position means. */
@Composable
private fun PositionLine(book: ReaderBook, color: Color) {
    Text(
        text = stringResource(
            R.string.reader_library_chunk_progress,
            book.displayChunk,
            book.chunkCount,
        ),
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
    Text(
        text = stringResource(bookStateLabel(book.state)),
        style = MaterialTheme.typography.labelSmall,
        color = if (book.isCompleted) VoxoraColors.success else color,
    )
}

/** Marks the book the Reader actually has loaded, so the list says which one is live. */
@Composable
private fun ActiveChip(onContainer: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (onContainer) colors.primary else colors.surfaceContainerHighest,
    ) {
        Text(
            text = stringResource(R.string.reader_library_active),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (onContainer) colors.onPrimary else colors.onSurfaceVariant,
        )
    }
}

/** The progress bar, the chunk position, the state and the last-read line, in reading order. */
@Composable
private fun ProgressLine(book: ReaderBook, onContainer: Boolean) {
    val colors = MaterialTheme.colorScheme
    val label = if (onContainer) colors.onPrimaryContainer else colors.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { book.progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = colors.primary,
            trackColor = colors.surfaceContainerHighest,
        )
        PositionLine(book = book, color = label)
        Text(
            text = stringResource(R.string.reader_library_last_read, recencyText(book.lastReadAt)),
            style = MaterialTheme.typography.labelSmall,
            color = label,
        )
    }
}

/**
 * What the book's action says.
 *
 * "Continue" is only true for a book that was started and not finished; a book never opened says
 * "Start" and a finished one says "Listen again", because a single word for three different
 * situations would be the kind of small untruth this section exists to avoid.
 */
private fun openLabel(book: ReaderBook): Int = when {
    book.isUnavailable -> R.string.reader_library_continue
    book.isCompleted -> R.string.reader_library_listen_again
    book.hasResumePoint -> R.string.reader_library_continue
    else -> R.string.reader_library_start
}

/** The state label. The live transport phase is not persisted, so only these are shown. */
private fun bookStateLabel(state: ReaderBookState): Int = when (state) {
    ReaderBookState.NOT_STARTED -> R.string.reader_library_not_started
    ReaderBookState.IN_PROGRESS -> R.string.reader_library_in_progress
    ReaderBookState.COMPLETED -> R.string.reader_library_completed
    // The copy is gone. Saying so is the honest alternative to offering a Continue that fails.
    ReaderBookState.UNAVAILABLE -> R.string.reader_library_unavailable
}

/** The recency sentence, assembled from the pure rule in [readerRecencyOf]. */
@Composable
private fun recencyText(lastReadAt: Long): String {
    val recency = remember(lastReadAt) { readerRecencyOf(lastReadAt, System.currentTimeMillis()) }
    return when (recency.kind) {
        ReaderRecencyKind.JUST_NOW -> stringResource(R.string.reader_time_just_now)
        ReaderRecencyKind.MINUTES -> stringResource(R.string.reader_time_minutes, recency.count)
        ReaderRecencyKind.HOURS -> stringResource(R.string.reader_time_hours, recency.count)
        ReaderRecencyKind.DAYS -> stringResource(R.string.reader_time_days, recency.count)
        ReaderRecencyKind.WEEKS -> stringResource(R.string.reader_time_weeks, recency.count)
        ReaderRecencyKind.MONTHS -> stringResource(R.string.reader_time_months, recency.count)
    }
}

@Preview
@Composable
private fun ReaderLibrarySectionPreview() {
    VoxoraTheme {
        ReaderLibrarySection(
            books = listOf(
                ReaderBook(
                    id = "a",
                    localPath = "/a.pdf",
                    title = "The Selfish Gene",
                    sourceType = ReaderSourceType.PDF,
                    chunkCount = 200,
                    currentChunk = 72,
                    state = ReaderBookState.IN_PROGRESS,
                    importedAt = 0L,
                    lastReadAt = 0L,
                    metadata = null,
                    lookup = com.voxora.core.reader.MetadataLookupState.NOT_FOUND,
                    signals = null,
                ),
            ),
            activeBookId = "a",
            canOpen = true,
            onContinue = {},
            onRemove = {},
            onImport = {},
        )
    }
}
