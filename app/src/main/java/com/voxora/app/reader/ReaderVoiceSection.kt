package com.voxora.app.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.voxora.app.R
import com.voxora.app.ui.theme.VoxoraTheme
import com.voxora.core.gemini.ReaderVoice

/**
 * The narration-voice choice: two semantic cards, exactly like the narration mode.
 *
 * The reader picks **Female** or **Male** — never a raw Gemini voice name and never a technical
 * parameter. The mapping from that choice to a Gemini prebuilt voice lives in
 * `ReaderVoice`, and the card's description states the difference in the terms the choice is
 * actually about (lighter/higher versus deeper/warmer) rather than naming the voice.
 *
 * There is deliberately no pitch or frequency control: the API exposes none, and the Reader does
 * not DSP-shift PCM. Adding a slider would promise a control the product cannot honour.
 */
@Composable
internal fun ReaderVoiceSection(
    selected: ReaderVoice,
    enabled: Boolean,
    onVoiceChange: (ReaderVoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader(title = stringResource(R.string.reader_voice_section))
        VoiceCard(
            title = stringResource(R.string.reader_voice_female),
            description = stringResource(R.string.reader_voice_female_desc),
            selected = selected == ReaderVoice.FEMALE,
            enabled = enabled,
            onClick = { onVoiceChange(ReaderVoice.FEMALE) },
        )
        VoiceCard(
            title = stringResource(R.string.reader_voice_male),
            description = stringResource(R.string.reader_voice_male_desc),
            selected = selected == ReaderVoice.MALE,
            enabled = enabled,
            onClick = { onVoiceChange(ReaderVoice.MALE) },
        )
        ExplanationNote(
            text = stringResource(R.string.reader_voice_explain),
            helpTitle = stringResource(R.string.reader_voice_section),
            helpBody = stringResource(R.string.reader_voice_explain_help),
        )
    }
}

@Composable
private fun VoiceCard(
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

@Preview
@Composable
private fun ReaderVoiceSectionPreview() {
    VoxoraTheme {
        ReaderVoiceSection(
            selected = ReaderVoice.FEMALE,
            enabled = true,
            onVoiceChange = {},
        )
    }
}
