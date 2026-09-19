package com.voxora.app.reader

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * The Reader library: the imported books, and the one to continue.
 *
 * ## What this surface has to answer without the reader having to remember anything
 *
 * "Which book was I reading, and where was I?" The continue card answers both — it names the book
 * and the chunk — and it is the first thing in the section, because a reader returning after a week
 * should not have to work out which file they imported.
 *
 * Every row states the book's own progress (`Chunk 12 of 210`), its state and how long ago it was
 * read, so the list is a reading history rather than a file list. The book that is currently loaded
 * is marked, and the most recently read one is what the continue card offers.
 *
 * Opening a book is one tap and never re-imports: the document lives in app storage and the saved
 * chunk is restored. Removing a book asks first, and says exactly what is deleted — the library
 * record and Voxora's copy, never the original file.
 */
@Composable
internal fun ReaderLibrarySection(
    books: List<ReaderBook>,
    activeBookId: String?,
    canOpen: Boolean,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingRemoval by remember { mutableStateOf<String?>(null) }
    val ordered = remember(books) { ReaderLibrary.byRecency(books) }
    val continueBook = remember(books) { ReaderLibrary.mostRecent(books)?.takeIf { it.hasResumePoint } }

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
            if (continueBook != null) {
                ContinueCard(
                    book = continueBook,
                    enabled = canOpen,
                    onOpen = { onOpen(continueBook.id) },
                )
            }
            ordered.forEach { book ->
                BookRow(
                    book = book,
                    active = book.id == activeBookId,
                    enabled = canOpen,
                    onOpen = { onOpen(book.id) },
                    onRemove = { pendingRemoval = book.id },
                )
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
 * The "this is the book you were reading" card.
 *
 * It exists so returning to the Reader never requires remembering which file was imported last. It
 * restores state and offers Continue; it deliberately does **not** start narration, because opening
 * the Reader must never make sound.
 */
@Composable
private fun ContinueCard(
    book: ReaderBook,
    enabled: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.primaryContainer,
        border = BorderStroke(1.dp, colors.primary),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_library_continue_title),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onPrimaryContainer,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReaderCoverImage(url = book.metadata?.coverUrl, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    book.metadata?.authorLine?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            ProgressLine(book = book, onContainer = true)
            Button(onClick = onOpen, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.reader_library_continue))
            }
        }
    }
}

@Composable
private fun BookRow(
    book: ReaderBook,
    active: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        // A book whose document copy is gone is not openable, so the row is not clickable: an
        // enabled row that can only fail is worse than a row that plainly cannot be used.
        enabled = enabled && !book.isUnavailable,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (active) colors.primaryContainer else colors.surfaceContainer,
        border = BorderStroke(1.dp, if (active) colors.primary else colors.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderCoverImage(url = book.metadata?.coverUrl, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) colors.onPrimaryContainer else colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                book.metadata?.authorLine?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ProgressLine(book = book, onContainer = active)
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.reader_library_remove),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** The progress bar, the chunk position and the state, in the order the reader reads them. */
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.reader_library_chunk_progress, book.displayChunk, book.chunkCount),
                style = MaterialTheme.typography.labelSmall,
                color = label,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(bookStateLabel(book.state)),
                style = MaterialTheme.typography.labelSmall,
                color = if (book.isCompleted) VoxoraColors.success else label,
            )
        }
        Text(
            text = stringResource(R.string.reader_library_last_read, recencyText(book.lastReadAt)),
            style = MaterialTheme.typography.labelSmall,
            color = label,
        )
    }
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
            onOpen = {},
            onRemove = {},
            onImport = {},
        )
    }
}
