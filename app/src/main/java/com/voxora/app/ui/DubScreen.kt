package com.voxora.app.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxora.app.R
import com.voxora.app.dub.DubService
import com.voxora.app.dub.DubUiStatus
import com.voxora.app.ui.theme.VoxoraColors
import com.voxora.app.ui.theme.VoxoraTheme
import kotlin.math.sin

/**
 * Live Dub working surface.
 *
 * This is the existing Live Dub start/stop screen, moved out of Home so Home
 * can act as the neutral Voxora product chooser. The Live Dub engine
 * (`dub/` package, `GeminiLiveSession`, `GeminiLiveConfig`) is untouched —
 * this screen only drives the already-public `DubService` start/stop API.
 */
@Composable
fun DubScreen(
    status: DubUiStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val level by DubService.audioLevel.collectAsStateWithLifecycle()
    DubContent(
        status = status,
        level = level,
        onStart = onStart,
        onStop = onStop,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onDismissError = onDismissError,
        modifier = modifier,
    )
}

@Composable
private fun DubContent(
    status: DubUiStatus,
    level: Float,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val isLive = status is DubUiStatus.Live || status is DubUiStatus.Connecting
    val isError = status is DubUiStatus.Error
    val statusLabel = when (status) {
        is DubUiStatus.Idle -> stringResource(R.string.status_idle)
        is DubUiStatus.Connecting -> stringResource(R.string.status_connecting)
        is DubUiStatus.Live -> stringResource(R.string.status_live)
        is DubUiStatus.Error -> stringResource(R.string.status_error)
    }
    val dotColor = when (status) {
        is DubUiStatus.Live -> VoxoraColors.success
        is DubUiStatus.Connecting -> VoxoraColors.warning
        is DubUiStatus.Error -> colors.error
        else -> colors.outline
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // Auto-mirrored so the arrow points the correct way under RTL instead of staying LTR.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = colors.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.dub_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "V",
                        color = colors.primary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.tagline),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceVariant)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = statusLabel,
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (isLive) {
                    Spacer(Modifier.height(20.dp))
                    LiveWaveform(
                        level = level,
                        active = status is DubUiStatus.Live,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    )
                }

                if (isError && status is DubUiStatus.Error) {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.error.copy(alpha = 0.12f))
                            .padding(14.dp),
                    ) {
                        Text(
                            stringResource(R.string.error_banner_title),
                            color = colors.error,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            status.message,
                            color = colors.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onDismissError()
                                    onOpenSettings()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    stringResource(R.string.action_settings),
                                    color = colors.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            Button(
                                onClick = {
                                    onDismissError()
                                    onStart()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary,
                                ),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    stringResource(R.string.action_retry),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.home_title),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.home_subtitle),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                if (isLive) {
                    Button(
                        onClick = onStop,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.error,
                            contentColor = colors.onError,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(stringResource(R.string.action_stop), fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(stringResource(R.string.action_start), fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.phase1_hint),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.latency_hint),
                    color = colors.onSurfaceVariant.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.action_settings), color = colors.primary)
                }
            }
        }
    }
}

@Composable
private fun LiveWaveform(
    level: Float,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val gold = Color(0xFFE6B422)
    val green = Color(0xFF3DDC97)
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    Canvas(modifier = modifier) {
        val bars = 24
        val gap = size.width * 0.012f
        val barW = (size.width - gap * (bars + 1)) / bars
        val base = if (active) level.coerceIn(0.05f, 1f) else 0.12f
        for (i in 0 until bars) {
            val n = ((sin(phase + i * 0.45f) + 1f) / 2f)
            val amp = (0.18f + 0.82f * base * (0.35f + 0.65f * n)).coerceIn(0.1f, 1f)
            val bh = size.height * amp
            val left = gap + i * (barW + gap)
            val top = (size.height - bh) / 2f
            val t = i / (bars - 1f)
            val color = androidx.compose.ui.graphics.lerp(gold, green, t)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color)),
                topLeft = Offset(left, top),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DubContentPreview(modifier: Modifier = Modifier) {
    VoxoraTheme {
        Surface(modifier = modifier) {
            DubContent(
                status = DubUiStatus.Idle,
                level = 0f,
                onStart = {},
                onStop = {},
                onBack = {},
                onOpenSettings = {},
                onDismissError = {},
            )
        }
    }
}
