package com.voxora.app.ui

import android.content.Intent
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.voxora.app.R
import com.voxora.app.auth.GoogleAuthHelper
import com.voxora.app.reader.languageOptions
import com.voxora.app.ui.theme.VoxoraTheme
import com.voxora.core.i18n.AppLocales
import com.voxora.core.prefs.UserPrefs
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val AI_STUDIO_URL = "https://aistudio.google.com/apikey"

/**
 * One row of a Settings language picker.
 *
 * There is no language list in this file. Both pickers resolve their entries from the
 * single catalog — the dubbing picker through the very same function the Reader's
 * narration picker uses, and the app-language picker through `AppLocales.shipped` —
 * so count, order, labels and flags cannot drift between screens.
 */
private data class LanguageChoice(val code: String, val flag: String, val label: String)

/** Identical to the Reader's narration picker: same catalog, order, labels and flags. */
private fun geminiLanguageChoices(locale: Locale): List<LanguageChoice> =
    languageOptions(locale, "").map { LanguageChoice(it.code, it.flagEmoji, it.label) }

/**
 * The app's own UI locales — only the translations packaged in the APK, so the user
 * cannot select an interface language Voxora does not actually speak. Named in the
 * language itself, because someone who cannot read the current UI language still has
 * to recognise their own.
 */
private fun appLanguageChoices(): List<LanguageChoice> =
    AppLocales.languages.map { language ->
        LanguageChoice(
            code = language.code,
            flag = language.flagEmoji,
            label = language.displayName(Locale.forLanguageTag(language.code)),
        )
    }

private fun List<LanguageChoice>.labelOf(code: String): String =
    firstOrNull { it.code == code }?.label ?: code

/**
 * Voxora Settings.
 *
 * Laid out with the same visual system as the Reader screen — one keyed
 * `LazyColumn`, a consistent top bar, grouped section headers and rounded,
 * outlined Material 3 cards — so both screens read as one product. Every control
 * that existed before is still here: app language, Gemini API key, dubbing
 * language, save, account sign-in, overlay permission and logs.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRequestOverlayPermission: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { UserPrefs(context) }
    val auth = remember { GoogleAuthHelper(context, prefs) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var apiKey by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("fa") }
    var appLang by remember { mutableStateOf("en") }
    var saved by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apiKey = prefs.apiKey.first()
        lang = prefs.targetLanguage.first()
        appLang = prefs.appLanguage.first()
        signedIn = prefs.signedIn.first()
        displayName = prefs.displayName.first()
        email = prefs.userEmail.first()
    }

    fun applyAppLocale(code: String) {
        scope.launch {
            prefs.setAppLanguage(code)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
        }
    }

    SettingsContent(
        apiKey = apiKey,
        onApiKeyChange = { apiKey = it; saved = false },
        dubbingLanguage = lang,
        onDubbingLanguageChange = { lang = it; saved = false },
        appLanguage = appLang,
        onAppLanguageChange = { appLang = it; applyAppLocale(it) },
        signedIn = signedIn,
        displayName = displayName,
        email = email,
        saved = saved,
        onBack = onBack,
        onSave = {
            scope.launch {
                prefs.setApiKey(apiKey)
                prefs.setTargetLanguage(lang)
                prefs.setAppLanguage(appLang)
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(appLang))
                saved = true
            }
        },
        onOpenAiStudio = { uriHandler.openUri(AI_STUDIO_URL) },
        onSignIn = {
            scope.launch {
                val result = auth.signIn()
                if (result.ok) {
                    signedIn = true
                    displayName = result.name
                    email = result.email
                }
            }
        },
        onSignOut = {
            scope.launch {
                auth.signOut()
                signedIn = false
                displayName = ""
                email = ""
            }
        },
        onOpenOverlay = {
            onRequestOverlayPermission()
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        },
        onOpenLogs = onOpenLogs,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    dubbingLanguage: String,
    onDubbingLanguageChange: (String) -> Unit,
    appLanguage: String,
    onAppLanguageChange: (String) -> Unit,
    signedIn: Boolean,
    displayName: String,
    email: String,
    saved: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    // Resolved once per locale: both pickers read the one catalog, so a language is
    // never labelled or flagged differently here than in the Reader.
    val geminiLanguages = remember(locale) { geminiLanguageChoices(locale) }
    val appLanguages = remember { appLanguageChoices() }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outline,
        focusedLabelColor = colors.primary,
        cursorColor = colors.primary,
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "top-bar") {
            SettingsTopBar(onBack = onBack)
        }

        item(key = "appearance-header") {
            SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
        }
        item(key = "appearance") {
            SettingsCard {
                SettingsRowHeader(
                    icon = Icons.Filled.Language,
                    title = stringResource(R.string.settings_app_language),
                    subtitle = stringResource(R.string.settings_app_language_help),
                )
                SettingsDropdown(
                    label = stringResource(R.string.settings_app_language),
                    value = appLanguages.labelOf(appLanguage),
                    options = appLanguages,
                    onSelect = onAppLanguageChange,
                    colors = fieldColors,
                )
            }
        }

        item(key = "gemini-header") {
            SettingsSectionHeader(stringResource(R.string.settings_section_gemini))
        }
        item(key = "gemini") {
            SettingsCard {
                SettingsRowHeader(
                    icon = Icons.Filled.Key,
                    title = stringResource(R.string.settings_api_key),
                    subtitle = stringResource(R.string.settings_api_help),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text(stringResource(R.string.settings_api_key_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors,
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedButton(
                    onClick = onOpenAiStudio,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_open_ai_studio))
                }
            }
        }

        item(key = "dubbing-header") {
            SettingsSectionHeader(stringResource(R.string.settings_section_dubbing))
        }
        item(key = "dubbing") {
            SettingsCard {
                SettingsRowHeader(
                    icon = Icons.Filled.Translate,
                    title = stringResource(R.string.settings_target_language),
                    subtitle = stringResource(R.string.home_subtitle),
                )
                SettingsDropdown(
                    label = stringResource(R.string.settings_target_language),
                    value = geminiLanguages.labelOf(dubbingLanguage),
                    options = geminiLanguages,
                    onSelect = onDubbingLanguageChange,
                    colors = fieldColors,
                )
            }
        }

        item(key = "save") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.action_save),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (saved) {
                    Text(
                        text = stringResource(R.string.settings_saved),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                    )
                }
            }
        }

        item(key = "account-header") {
            SettingsSectionHeader(stringResource(R.string.settings_account))
        }
        item(key = "account") {
            SettingsCard {
                if (signedIn) {
                    SettingsRowHeader(
                        icon = Icons.Filled.Person,
                        title = displayName.ifBlank { email },
                        subtitle = email.takeIf { it.isNotBlank() && !it.equals(displayName, ignoreCase = true) },
                    )
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_sign_out))
                    }
                } else {
                    SettingsRowHeader(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.settings_sign_in),
                        subtitle = stringResource(R.string.auth_optional_hint),
                    )
                    Button(
                        onClick = onSignIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_sign_in),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        item(key = "overlay-header") {
            SettingsSectionHeader(stringResource(R.string.settings_section_overlay))
        }
        item(key = "overlay") {
            SettingsCard {
                SettingsRowHeader(
                    icon = Icons.Filled.Layers,
                    title = stringResource(R.string.settings_overlay),
                    subtitle = stringResource(R.string.settings_overlay_help),
                )
                OutlinedButton(
                    onClick = onOpenOverlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_overlay_open))
                }
            }
        }

        item(key = "diagnostics-header") {
            SettingsSectionHeader(stringResource(R.string.settings_section_diagnostics))
        }
        item(key = "diagnostics") {
            SettingsCard {
                OutlinedButton(
                    onClick = onOpenLogs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_view_logs))
                }
            }
        }
    }
}

@Composable
private fun SettingsTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsRowHeader(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    label: String,
    value: String,
    options: List<LanguageChoice>,
    onSelect: (String) -> Unit,
    colors: androidx.compose.material3.TextFieldColors,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = colors,
            shape = RoundedCornerShape(14.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { choice ->
                DropdownMenuItem(
                    text = { Text("${choice.flag}  ${choice.label}") },
                    onClick = {
                        expanded = false
                        onSelect(choice.code)
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SettingsContentPreview(modifier: Modifier = Modifier) {
    VoxoraTheme {
        Surface(modifier = modifier) {
            SettingsContent(
                apiKey = "",
                onApiKeyChange = {},
                dubbingLanguage = "fa",
                onDubbingLanguageChange = {},
                appLanguage = "en",
                onAppLanguageChange = {},
                signedIn = false,
                displayName = "",
                email = "",
                saved = false,
                onBack = {},
                onSave = {},
                onOpenAiStudio = {},
                onSignIn = {},
                onSignOut = {},
                onOpenOverlay = {},
                onOpenLogs = {},
            )
        }
    }
}

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SettingsContentSignedInPreview(modifier: Modifier = Modifier) {
    VoxoraTheme {
        Surface(modifier = modifier) {
            SettingsContent(
                apiKey = "AIza…",
                onApiKeyChange = {},
                dubbingLanguage = "en",
                onDubbingLanguageChange = {},
                appLanguage = "fa",
                onAppLanguageChange = {},
                signedIn = true,
                displayName = "Mo3i",
                email = "owner@voxora.app",
                saved = true,
                onBack = {},
                onSave = {},
                onOpenAiStudio = {},
                onSignIn = {},
                onSignOut = {},
                onOpenOverlay = {},
                onOpenLogs = {},
            )
        }
    }
}
