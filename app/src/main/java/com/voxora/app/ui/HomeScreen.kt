package com.voxora.app.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxora.app.R
import com.voxora.app.dub.DubUiStatus

@Composable
fun HomeScreen(
    status: DubUiStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit = {},
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
        is DubUiStatus.Live -> Color(0xFF3DDC84)
        is DubUiStatus.Connecting -> Color(0xFFE6B422)
        is DubUiStatus.Error -> colors.error
        else -> Color(0xFF666666)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 24.dp, vertical = 48.dp),
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
                Text("V", color = colors.primary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = colors.primary,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.tagline),
                color = colors.onSurfaceVariant,
                fontSize = 14.sp,
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
                Text(statusLabel, color = colors.onSurface, fontSize = 14.sp)
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
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(status.message, color = colors.onSurface, fontSize = 13.sp)
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
                            Text(stringResource(R.string.action_settings), color = colors.primary, fontSize = 12.sp)
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
                            Text(stringResource(R.string.action_retry), fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.home_title),
                color = colors.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                color = colors.onSurfaceVariant,
                fontSize = 14.sp,
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
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.latency_hint),
                color = colors.onSurfaceVariant.copy(alpha = 0.85f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }

        TextButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.action_settings), color = colors.primary)
        }
    }
}
