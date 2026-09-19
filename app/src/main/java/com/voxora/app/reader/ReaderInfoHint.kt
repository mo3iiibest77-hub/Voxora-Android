package com.voxora.app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxora.app.R
import com.voxora.app.ui.theme.VoxoraColors

/**
 * The help affordance for the Reader's explanation-role sections.
 *
 * ## Why an icon and a dialog
 *
 * The explanation sentences explain a section in one or two lines, which is the right amount for the
 * page and the wrong amount for a reader who wants to understand the whole behaviour. An info icon
 * beside the sentence opens a short, section-specific explanation without turning the section into a
 * wall of text — and without changing what the sentence itself says, because the sentence is the
 * summary and the dialog is the detail.
 *
 * ## Why it looks like the other card icons
 *
 * It uses the **same visual treatment as the Reader's ordinary section icons** — the decorative
 * accent wash behind the glyph and the primary content colour — rather than a colour of its own.
 * A help affordance that invented its own treatment read as a different kind of thing from the
 * document, language and voice icons beside it; belonging to the same icon language is what makes it
 * read as part of the section rather than as an annotation on it. The glyph stays an information
 * icon, because that is what it means.
 *
 * ## What it deliberately is not
 *
 * It is not a status or an error: nothing here reports a fault, so it never appears on a card whose
 * job is to say something is wrong. It is also not a tooltip: a tooltip is unreachable for a touch
 * user and is not announced reliably, whereas a dialog is focusable, dismissible and readable by a
 * screen reader.
 *
 * ## Accessibility and RTL
 *
 * The button carries a content description naming the section it explains, so it is announced as an
 * action rather than as a decorative glyph, and it keeps Material's own minimum touch target. The
 * layout is a plain [Row], so it mirrors under RTL on its own — the icon sits where the reading
 * direction ends, and no coordinate or direction is hand-set.
 */
@Composable
internal fun HelpIconButton(
    helpTitle: String,
    helpBody: String,
    modifier: Modifier = Modifier,
) {
    var showing by rememberSaveable { mutableStateOf(false) }
    IconButton(
        onClick = { showing = true },
        modifier = modifier,
    ) {
        // The same container the Reader's other section icons use: a circular accent wash with the
        // primary content colour on top. No literal colour and no size of its own — both come from
        // the existing pattern, so the help icon cannot drift away from it.
        Box(
            modifier = Modifier
                .size(HELP_BADGE_DP.dp)
                .clip(CircleShape)
                .background(VoxoraColors.glow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.reader_help_open, helpTitle),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (showing) {
        AlertDialog(
            onDismissRequest = { showing = false },
            title = {
                Text(text = helpTitle, style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Text(text = helpBody, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = { showing = false }) {
                    Text(text = stringResource(R.string.reader_help_close))
                }
            },
        )
    }
}

/**
 * An explanation sentence in the explanation role, with its section's help beside it.
 *
 * The sentence keeps the role and the typography it already had; the icon is added next to it
 * rather than replacing anything, so a section that had no help affordance gains one without
 * changing what it says.
 */
@Composable
internal fun ExplanationNote(
    text: String,
    helpTitle: String,
    helpBody: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = VoxoraColors.explanation,
        )
        HelpIconButton(helpTitle = helpTitle, helpBody = helpBody)
    }
}

/** The icon badge matches the Reader's other section icons; the touch target stays Material's. */
private const val HELP_BADGE_DP = 44
