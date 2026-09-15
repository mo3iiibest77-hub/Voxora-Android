package com.voxora.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxora.app.R
import com.voxora.app.auth.GoogleAuthHelper
import com.voxora.core.prefs.UserPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val LANGS = listOf(
    "fa" to "Persian",
    "en" to "English",
    "ar" to "Arabic",
    "es" to "Spanish",
    "fr" to "French",
    "de" to "German",
    "tr" to "Turkish",
    "ru" to "Russian",
    "zh" to "Chinese",
    "ja" to "Japanese",
    "ko" to "Korean",
    "hi" to "Hindi",
    "pt" to "Portuguese",
    "id" to "Indonesian",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRequestOverlayPermission: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { UserPrefs(context) }
    val auth = remember { GoogleAuthHelper(context, prefs) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var apiKey by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("fa") }
    var expanded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apiKey = prefs.apiKey.first()
        lang = prefs.targetLanguage.first()
        signedIn = prefs.signedIn.first()
        displayName = prefs.displayName.first()
        email = prefs.userEmail.first()
    }

    val colors = MaterialTheme.colorScheme
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outline,
        focusedLabelColor = colors.primary,
        cursorColor = colors.primary,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("← Back", color = colors.primary)
        }
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(20.dp))

        Text(stringResource(R.string.settings_account), color = colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        if (signedIn) {
            Text("$displayName\n$email", color = colors.onBackground, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        auth.signOut()
                        signedIn = false
                        displayName = ""
                        email = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_sign_out), color = colors.primary)
            }
        } else {
            Button(
                onClick = {
                    scope.launch {
                        val r = auth.signIn()
                        if (r.ok) {
                            signedIn = true
                            displayName = r.name
                            email = r.email
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.settings_sign_in))
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.auth_optional_hint), color = colors.onSurfaceVariant, fontSize = 12.sp)
        }

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.settings_api_key), color = colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; saved = false },
            label = { Text(stringResource(R.string.settings_api_key)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = fieldColors,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { uriHandler.openUri("https://aistudio.google.com/apikey") }) {
            Text(stringResource(R.string.settings_open_ai_studio), color = colors.primary)
        }

        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.settings_target_language), color = colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = LANGS.find { it.first == lang }?.second ?: lang,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                LANGS.forEach { (code, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { lang = code; expanded = false; saved = false },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                scope.launch {
                    prefs.setApiKey(apiKey)
                    prefs.setTargetLanguage(lang)
                    saved = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(stringResource(R.string.action_save), fontWeight = FontWeight.SemiBold)
        }
        if (saved) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.settings_saved), color = Color(0xFF3DDC84), fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.settings_api_help), color = colors.onSurfaceVariant, fontSize = 12.sp)

        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.settings_overlay), color = colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_overlay_help), color = colors.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                onRequestOverlayPermission()
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}"),
                )
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(R.string.settings_overlay_open), color = colors.primary)
        }

        Spacer(Modifier.height(28.dp))
        Text("Debug", color = colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenLogs,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("View logs", color = colors.primary)
        }
        Spacer(Modifier.height(24.dp))
    }
}
