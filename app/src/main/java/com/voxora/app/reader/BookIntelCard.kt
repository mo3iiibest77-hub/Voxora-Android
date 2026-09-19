package com.voxora.app.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.voxora.app.R
import com.voxora.app.ui.theme.VoxoraColors
import com.voxora.app.ui.theme.VoxoraTheme
import com.voxora.core.gemini.ReaderLanguages
import com.voxora.core.reader.BookIntel
import com.voxora.core.reader.BookIntelFactKind
import com.voxora.core.reader.BookIntelOverview
import com.voxora.core.reader.BookWorkKind
import com.voxora.core.reader.MatchConfidence
import com.voxora.core.reader.MetadataLookupState
import com.voxora.core.reader.ReaderBook
import java.util.Locale

/**
 * "About This Book": what the public catalogues say about the book that was imported.
 *
 * ## The honesty contract, as UI
 *
 * This card shows **only** what a catalogue actually returned, and it is explicit about the three
 * ways that can end:
 *
 * - **identified** — the matched title, cover and every known fact, with the provider named and the
 *   kind of match stated, so the reader can judge how the app knows;
 * - **not identified** — said plainly, with no invented author, date or synopsis, and a way to try
 *   again;
 * - **unavailable** — the network or the catalogue failed, which is a statement about the lookup and
 *   never about the book.
 *
 * A field the catalogue did not provide is simply absent. There is no "Unknown author" row, no
 * placeholder year and no generated summary: an invented fact here would be indistinguishable from
 * a real one, which is exactly the failure this section is designed to avoid.
 *
 * The description is the publisher's text, unedited and untranslated, and it is labelled by what
 * the catalogue's own subjects say the work is — a synopsis for fiction, "about the work" otherwise,
 * and a neutral label when the subjects do not say.
 */
@Composable
internal fun BookIntelCard(
    book: ReaderBook,
    locale: Locale,
    outputLang: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val metadata = book.metadata
    // The generated overview is per output language. It is read here rather than only inside
    // [IdentifiedBook] because a book the catalogues could **not** identify can now have one: its
    // overview is generated from the book's own details and a bounded web search. That text is the
    // only Book Intelligence such a book can have, so showing it is the point of the fallback —
    // hiding it behind the identified branch would have made the whole path invisible.
    val overview = remember(book.overviews, outputLang) { book.overviewFor(outputLang) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(title = stringResource(R.string.reader_book_info_section))
            when {
                metadata != null && book.lookup == MetadataLookupState.FOUND ->
                    IdentifiedBook(book = book, locale = locale, outputLang = outputLang, overview = overview)

                book.lookup == MetadataLookupState.NOT_FOUND ->
                    Unidentified(
                        message = stringResource(R.string.reader_book_info_not_found),
                        onRetry = onRetry,
                        overview = overview,
                    )

                book.lookup == MetadataLookupState.AMBIGUOUS ->
                    Unidentified(
                        message = stringResource(R.string.reader_book_info_ambiguous),
                        onRetry = onRetry,
                        overview = overview,
                    )

                book.lookup == MetadataLookupState.UNAVAILABLE ->
                    Unidentified(
                        message = stringResource(R.string.reader_book_info_unavailable),
                        onRetry = onRetry,
                        overview = overview,
                    )

                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.reader_book_info_identifying),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentifiedBook(
    book: ReaderBook,
    locale: Locale,
    outputLang: String,
    overview: BookIntelOverview?,
) {
    val colors = MaterialTheme.colorScheme
    val metadata = book.metadata ?: return
    // A language is only ever named in a locale Voxora ships, and only when the app's own catalog
    // knows the code — an unrecognised provider code is dropped rather than shown raw.
    val languageName: (String) -> String? = remember(locale) {
        { code -> ReaderLanguages.languageOrNull(code)?.displayName(locale) }
    }
    val facts = remember(metadata, locale) { BookIntel.facts(metadata, languageName) }
    val topics = remember(metadata) { BookIntel.topics(metadata) }
    val description = BookIntel.description(metadata)
    // The catalogue's subject headings are always in the catalogue's own language ("Evolution",
    // "Biology"), so when the overview carries headings rendered in the reading language those win.
    // They live inside the per-language overview, which is why a language switch can never show the
    // previous language's themes: the other language's overview is a different record entirely.
    val generatedThemes = overview?.themes.orEmpty()
    val displayedTopics = generatedThemes.ifEmpty { topics }
    val sourceLanguage = metadata.language
        ?.takeIf { !it.equals(outputLang, ignoreCase = true) }
        ?.let { languageName(it) }
    val matchLabel = when (metadata.confidence) {
        MatchConfidence.ISBN_EXACT -> R.string.reader_book_match_isbn
        MatchConfidence.TITLE_AUTHOR -> R.string.reader_book_match_title_author
        MatchConfidence.TITLE_ONLY -> R.string.reader_book_match_title
        MatchConfidence.NONE -> null
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        ReaderCoverImage(url = metadata.coverUrl, size = 72.dp)
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = metadata.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            metadata.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            if (matchLabel != null) {
                Text(
                    text = stringResource(matchLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = VoxoraColors.explanation,
                )
            }
        }
    }

    if (facts.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            facts.forEach { fact ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(factLabel(fact.kind)),
                        modifier = Modifier.width(96.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = fact.value,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                    )
                }
            }
        }
    }

    // The generated overview comes first: it is the part written in the reader's own language, and
    // it is labelled as generated so it can never be mistaken for something the catalogue said.
    // Absent until a generation has succeeded, which is why its absence is not an error state.
    GeneratedOverviewBlock(overview)

    if (description != null) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                // The label follows what the catalogue's subjects say the work is; it never claims
                // a plot for a book whose subjects do not describe one.
                text = stringResource(
                    if (BookIntel.workKind(metadata) == BookWorkKind.FICTION) {
                        R.string.reader_book_info_synopsis
                    } else {
                        R.string.reader_book_info_description
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
            // Say why this text is not in the reading language, rather than leaving the reader to
            // wonder whether the app failed to translate it.
            if (sourceLanguage != null) {
                Text(
                    text = stringResource(R.string.reader_book_info_source_language, sourceLanguage),
                    style = MaterialTheme.typography.labelSmall,
                    color = VoxoraColors.explanation,
                )
            }
        }
    }

    if (displayedTopics.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.reader_book_info_topics),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = displayedTopics.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
            // Only the catalogue's own subjects need the caveat: they are always in the catalogue's
            // language. Themes rendered from the overview are already in the reading language, so
            // the note is absent exactly when the displayed headings are.
            if (generatedThemes.isEmpty() && sourceLanguage != null) {
                Text(
                    text = stringResource(R.string.reader_book_info_source_language, sourceLanguage),
                    style = MaterialTheme.typography.labelSmall,
                    color = VoxoraColors.explanation,
                )
            }
        }
    }
}

/**
 * The generated paragraph, labelled as generated.
 *
 * Shared by the identified and unidentified branches because the paragraph is the same kind of thing
 * in both: text Gemini wrote, in the reader's reading language, from evidence rather than from the
 * book. What differs is only the evidence — a catalogue record, or the book's own details and a
 * search — and the help text says both.
 */
@Composable
private fun GeneratedOverviewBlock(overview: BookIntelOverview?) {
    if (overview == null) return
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.reader_book_info_generated),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = overview.text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
        )
        ExplanationNote(
            text = stringResource(R.string.reader_book_info_generated_note),
            helpTitle = stringResource(R.string.reader_book_info_generated),
            helpBody = stringResource(R.string.reader_book_info_generated_help),
        )
    }
}

/**
 * The "could not be identified" branches.
 *
 * A generated overview is shown here too when one exists. It is not a contradiction of the message
 * above it: the message says no **catalogue** identified the book, and the paragraph is labelled as
 * generated text written from a search — two different statements, kept visibly different.
 */
@Composable
private fun Unidentified(message: String, onRetry: () -> Unit, overview: BookIntelOverview?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = VoxoraColors.explanation,
        )
        GeneratedOverviewBlock(overview)
        OutlinedButton(onClick = onRetry) {
            Text(text = stringResource(R.string.reader_book_info_retry))
        }
    }
}

private fun factLabel(kind: BookIntelFactKind): Int = when (kind) {
    BookIntelFactKind.AUTHOR -> R.string.reader_book_fact_author
    BookIntelFactKind.PUBLISHER -> R.string.reader_book_fact_publisher
    BookIntelFactKind.PUBLISHED -> R.string.reader_book_fact_published
    BookIntelFactKind.LANGUAGE -> R.string.reader_book_fact_language
    BookIntelFactKind.PAGES -> R.string.reader_book_fact_pages
    BookIntelFactKind.ISBN -> R.string.reader_book_fact_isbn
    BookIntelFactKind.PROVIDER -> R.string.reader_book_fact_provider
}

@Preview
@Composable
private fun BookIntelCardPreview() {
    VoxoraTheme {
        BookIntelCard(
            book = com.voxora.core.reader.ReaderBook(
                id = "preview",
                localPath = "/preview.pdf",
                title = "The Selfish Gene",
                sourceType = com.voxora.core.reader.ReaderSourceType.PDF,
                chunkCount = 210,
                currentChunk = 72,
                state = com.voxora.core.reader.ReaderBookState.IN_PROGRESS,
                importedAt = 0L,
                lastReadAt = 0L,
                metadata = null,
                lookup = MetadataLookupState.NOT_FOUND,
                signals = null,
            ),
            locale = Locale.ENGLISH,
            outputLang = "en",
            onRetry = {},
        )
    }
}
