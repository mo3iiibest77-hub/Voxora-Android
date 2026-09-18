package com.voxora.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.voxora.app.R
import com.voxora.app.ui.theme.VoxoraColors
import com.voxora.app.ui.theme.VoxoraTheme
import com.voxora.app.util.VoxoraLog

/**
 * In-app log viewer.
 *
 * ## What is localized and what is not
 * The **log lines themselves are developer diagnostics and stay exactly as [VoxoraLog] produced
 * them** — technical English, monospace, never translated, never reordered, never truncated. Only
 * the chrome around them is product UI: the header, the actions, the counter and the empty state
 * are all localized and follow the same Material 3 language as Settings and API usage.
 *
 * ## Why the list is laid out LTR in every language
 * A line is a timestamp, a severity and a bracketed tag followed by a message. Those are technical
 * identifiers, not prose: mirroring `14:02:11.480 INFO  [Reader] …` under a Persian UI would move
 * the timestamp to the far side of the tag and make the list unreadable. The list is therefore an
 * explicit LTR container inside an otherwise fully RTL screen — a directionality decision about
 * one region, not a blanket text hack.
 *
 * ## Why the actions are icons in the header
 * Copy, Share and Clear were three labelled buttons in a row. Persian labels ("اشتراک‌گذاری") are
 * far wider than their English counterparts, so a third of the screen is not enough for a label
 * plus an icon, and the row clipped or wrapped. The actions now live in the header as icon
 * buttons, each carrying its localized label as its accessibility description, so nothing is
 * narrower than the language needs and screen readers still announce words rather than shapes.
 */
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(VoxoraLog.snapshot()) }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        val listener = { entries = VoxoraLog.snapshot() }
        VoxoraLog.listener = listener
        onDispose { VoxoraLog.listener = null }
    }

    // Follow the newest line only while the newest line is what the user is looking at. If they
    // have scrolled up to read an older entry, an incoming line must not yank the list away.
    val followingNewest by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 1
        }
    }
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty() && followingNewest) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }

    LogsContent(
        entries = entries,
        onBack = onBack,
        onCopy = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText(context.getString(R.string.logs_title), VoxoraLog.asText()),
            )
            Toast.makeText(context, context.getString(R.string.logs_copied), Toast.LENGTH_SHORT).show()
        },
        onShare = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.logs_title))
                putExtra(Intent.EXTRA_TEXT, VoxoraLog.asText())
            }
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.logs_share_chooser)),
            )
        },
        onClear = {
            VoxoraLog.clear()
            entries = emptyList()
        },
        listState = listState,
        modifier = modifier,
    )
}

@Composable
private fun LogsContent(
    entries: List<VoxoraLog.Entry>,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val hasEntries = entries.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = colors.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.logs_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            LogAction(
                icon = Icons.Filled.ContentCopy,
                label = stringResource(R.string.logs_copy),
                enabled = hasEntries,
                tint = colors.onSurface,
                onClick = onCopy,
            )
            LogAction(
                icon = Icons.Filled.Share,
                label = stringResource(R.string.logs_share),
                enabled = hasEntries,
                tint = colors.onSurface,
                onClick = onShare,
            )
            LogAction(
                icon = Icons.Filled.DeleteOutline,
                label = stringResource(R.string.logs_clear),
                enabled = hasEntries,
                tint = VoxoraColors.danger,
                onClick = onClear,
            )
        }

        Text(
            text = stringResource(R.string.logs_entry_count, entries.size),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            color = colors.surfaceContainerHigh,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.logs_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            } else {
                // Technical content: left-to-right in every UI language, because a timestamp and
                // a bracketed tag are not prose. The surrounding screen still lays out RTL.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(entries) { entry ->
                            Text(
                                text = entry.formatted(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = severityColor(entry.level),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One header action.
 *
 * The icon is decorative and the label is the accessibility description, so TalkBack announces
 * "اشتراک‌گذاری" rather than an unnamed button. The disabled colour comes from
 * [IconButtonDefaults] so an action that cannot run yet dims the way Material expects instead of
 * keeping full contrast.
 */
@Composable
private fun LogAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint,
            disabledContentColor = tint.copy(alpha = 0.38f),
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Severity colour.
 *
 * Material 3 models no severity role, so these read the product's semantic roles and the theme's
 * text roles instead of literal hex: a failure is the same red as a stopped Reader, a warning is
 * the warm accent, and info/debug are ordinary and dimmed body text.
 */
@Composable
private fun severityColor(level: VoxoraLog.Level): Color = when (level) {
    VoxoraLog.Level.ERROR -> VoxoraColors.danger
    VoxoraLog.Level.WARN -> VoxoraColors.warning
    VoxoraLog.Level.INFO -> MaterialTheme.colorScheme.onSurface
    VoxoraLog.Level.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LogsContentPreview(modifier: Modifier = Modifier) {
    VoxoraTheme {
        Surface(modifier = modifier) {
            LogsContent(
                entries = listOf(
                    VoxoraLog.Entry(0L, VoxoraLog.Level.INFO, "Reader", "Document loaded"),
                    VoxoraLog.Entry(1L, VoxoraLog.Level.WARN, "Reader", "Prefetch allocation failed chunk=3"),
                    VoxoraLog.Entry(2L, VoxoraLog.Level.ERROR, "Reader", "Narration failed: NarrationFailure"),
                ),
                onBack = {},
                onCopy = {},
                onShare = {},
                onClear = {},
                listState = rememberLazyListState(),
            )
        }
    }
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LogsContentEmptyPreview(modifier: Modifier = Modifier) {
    VoxoraTheme {
        Surface(modifier = modifier) {
            LogsContent(
                entries = emptyList(),
                onBack = {},
                onCopy = {},
                onShare = {},
                onClear = {},
                listState = rememberLazyListState(),
            )
        }
    }
}
