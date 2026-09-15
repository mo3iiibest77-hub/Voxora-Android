package com.voxora.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxora.app.util.VoxoraLog

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(VoxoraLog.snapshot()) }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        val listener = {
            entries = VoxoraLog.snapshot()
        }
        VoxoraLog.listener = listener
        onDispose { VoxoraLog.listener = null }
    }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", color = Color(0xFFE6B422))
            }
            Text(
                "Logs (${entries.size})",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFE6B422),
            )
            Spacer(Modifier.padding(40.dp))
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val text = VoxoraLog.asText()
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Voxora logs", text))
                    Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE6B422)),
            ) {
                Text("Copy all")
            }
            OutlinedButton(
                onClick = {
                    val text = VoxoraLog.asText()
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Voxora logs")
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, "Share logs"))
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE6B422)),
            ) {
                Text("Share")
            }
            OutlinedButton(
                onClick = {
                    VoxoraLog.clear()
                    entries = emptyList()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B)),
            ) {
                Text("Clear")
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
                .padding(8.dp),
        ) {
            items(entries) { e ->
                val color = when (e.level) {
                    VoxoraLog.Level.ERROR -> Color(0xFFFF6B6B)
                    VoxoraLog.Level.WARN -> Color(0xFFFFB74D)
                    VoxoraLog.Level.INFO -> Color(0xFF81C784)
                    VoxoraLog.Level.DEBUG -> Color(0xFF90A4AE)
                }
                Text(
                    text = e.formatted(),
                    color = color,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                )
            }
        }
    }
}
