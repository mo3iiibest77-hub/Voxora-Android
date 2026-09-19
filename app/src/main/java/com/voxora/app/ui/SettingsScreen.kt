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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxora.app.R
import com.voxora.app.reader.languageOptions
import com.voxora.app.ui.theme.VoxoraColors
import com.voxora.app.ui.theme.VoxoraTheme
import com.voxora.core.i18n.AppLocales
import com.voxora.core.prefs.ThemeMode
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
 * outlined Material 3 cards — so both screens read as one product.
 *
 * The order follows the product's model of Google access: **sign in with Google → the account →
 * the Gemini project → the key → usage**. The account card therefore sits above the manual key
 * field rather than in a separate "Account" section, because a manual key is a fallback for the
 * same job, not a different feature. Every control that existed before is still here: app
 * language, the Google/Gemini hierarchy, the Gemini API key, dubbing language, save, overlay
 * permission and logs.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRequestOverlayPermission: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onOpenUsage: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { UserPrefs(context) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    // The key field starts from *whether* a key is configured and holds only what the user is
    // typing. The stored secret is never read here, so it cannot be shown again — see
    // ApiKeyFieldState for why that is structural rather than a display rule.
    var keyField by remember { mutableStateOf(ApiKeyFieldState.of(configured = false)) }
    var keyNotice by remember { mutableStateOf<Int?>(null) }
    var lang by remember { mutableStateOf("fa") }
    var appLang by remember { mutableStateOf("en") }
    var saved by remember { mutableStateOf(false) }
    // The appearance is a persisted preference, so the control reads the same flow the theme
    // itself is driven by: there is no second copy to fall out of step with the applied theme.
    val themeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.DEFAULT)

    LaunchedEffect(Unit) {
        // Only the fact of configuration crosses into UI state — never the key itself.
        keyField = ApiKeyFieldState.of(configured = prefs.apiKeyConfigured.first())
        lang = prefs.targetLanguage.first()
        appLang = prefs.appLanguage.first()
    }

    fun applyAppLocale(code: String) {
        scope.launch {
            prefs.setAppLanguage(code)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
        }
    }

    SettingsContent(
        keyField = keyField,
        onKeyDraftChange = { keyField = ApiKeyFieldState.edit(keyField, it); keyNotice = null },
        onKeyReplace = { keyField = ApiKeyFieldState.beginReplace(keyField); keyNotice = null },
        onKeyCancel = { keyField = ApiKeyFieldState.cancel(keyField); keyNotice = null },
        onKeySave = {
            // The user's own draft is the only value that can be written; the stored key is never
            // read back and re-saved.
            val draft = keyField.draft.trim()
            if (draft.isNotEmpty()) {
                scope.launch {
                    prefs.setApiKey(draft)
                    keyField = ApiKeyFieldState.saved()
                    keyNotice = R.string.settings_api_saved
                }
            }
        },
        onKeyRemove = {
            scope.launch {
                prefs.clearApiKey()
                keyField = ApiKeyFieldState.removed()
                keyNotice = R.string.settings_api_removed
            }
        },
        keyNotice = keyNotice,
        dubbingLanguage = lang,
        onDubbingLanguageChange = { lang = it; saved = false },
        appLanguage = appLang,
        onAppLanguageChange = { appLang = it; applyAppLocale(it) },
        themeMode = themeMode,
        onThemeModeChange = { mode -> scope.launch { prefs.setThemeMode(mode) } },
        saved = saved,
        onBack = onBack,
        onSave = {
            // The key is deliberately not part of this save: it is a secret with its own explicit
            // Save/Replace/Remove actions, so it is never rewritten as a side effect.
            scope.launch {
                prefs.setTargetLanguage(lang)
                prefs.setAppLanguage(appLang)
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(appLang))
                saved = true
            }
        },
        onOpenAiStudio = { uriHandler.openUri(AI_STUDIO_URL) },
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
        onOpenUsage = onOpenUsage,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    keyField: ApiKeyFieldState,
    onKeyDraftChange: (String) -> Unit,
    onKeyReplace: () -> Unit,
    onKeyCancel: () -> Unit,
    onKeySave: () -> Unit,
    onKeyRemove: () -> Unit,
    keyNotice: Int?,
    dubbingLanguage: String,
    onDubbingLanguageChange: (String) -> Unit,
    appLanguage: String,
    onAppLanguageChange: (String) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    saved: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenUsage: () -> Unit,
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
                // The theme comes first: it is the appearance decision that changes the whole app,
                // and it is persisted the moment it is chosen rather than on Save.
                SettingsRowHeader(
                    icon = Icons.Filled.DarkMode,
                    title = stringResource(R.string.settings_theme_mode),
                    subtitle = stringResource(R.string.settings_theme_help),
                )
                ThemeSelector(mode = themeMode, onSelect = onThemeModeChange)
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

        item(key = "google-header") {
            SettingsSectionHeader(stringResource(R.string.settings_section_gemini))
        }
        item(key = "account") {
            // Sign in with Google comes first, because that is the path the product leads with:
            // the account, then the Gemini project, then the key. The account hierarchy is its own
            // composable so the three levels stay together and the invalidation rules live in one
            // tested model (CloudSelection).
            CloudAccountCard(onOpenUsage = onOpenUsage)
        }
        item(key = "gemini") {
            // The manual key is a first-class fallback, not a lesser mode: it sits directly under
            // the account card, works with no Google sign-in at all, and is never silently bound
            // to the signed-in account.
            //
            // Once a key is saved the secret is gone from the UI for good: the card shows that a
            // key is configured and offers Replace/Remove, and the field is only ever filled from
            // what the user types now. The stored value is never read back.
            SettingsCard {
                SettingsRowHeader(
                    icon = Icons.Filled.Key,
                    title = stringResource(R.string.settings_api_key),
                    subtitle = stringResource(R.string.settings_api_help),
                )
                if (keyField.showsConfigured) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = VoxoraColors.success,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_api_configured),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = VoxoraColors.success,
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_api_configured_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = VoxoraColors.explanation,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onKeyReplace,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_api_replace))
                        }
                        OutlinedButton(
                            onClick = onKeyRemove,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_api_remove))
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = keyField.draft,
                        onValueChange = onKeyDraftChange,
                        label = { Text(stringResource(R.string.settings_api_key_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        // The entry is masked, and the keyboard must not offer to learn it.
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = fieldColors,
                        shape = RoundedCornerShape(14.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onKeySave,
                            enabled = keyField.canSaveDraft,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_api_save),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (keyField.replacing) {
                            OutlinedButton(
                                onClick = onKeyCancel,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    }
                }
                if (keyNotice != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = VoxoraColors.success,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(keyNotice),
                            style = MaterialTheme.typography.bodySmall,
                            color = VoxoraColors.success,
                        )
                    }
                }
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

        item(key = "usage-header") {
            SettingsSectionHeader(stringResource(R.string.settings_section_usage))
        }
        item(key = "usage") {
            SettingsCard {
                SettingsRowHeader(
                    icon = Icons.Filled.DataUsage,
                    title = stringResource(R.string.settings_usage_open),
                    subtitle = stringResource(R.string.settings_usage_help),
                )
                OutlinedButton(
                    onClick = onOpenUsage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DataUsage,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_usage_open))
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
                        color = VoxoraColors.success,
                    )
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
                .background(VoxoraColors.glow),
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
                    color = VoxoraColors.explanation,
                )
            }
        }
    }
}

/**
 * The theme choice: Original Dark / Voxora Light (the `LIGHT_TEST_2` slot).
 *
 * The options are laid out as visible surfaces rather than a dropdown, because the appearance is
 * the one setting whose effect should be obvious from the Settings screen itself. Each option is a
 * normal selectable surface in a `Row`, so it mirrors correctly under RTL without any custom
 * drawing, and the selected option carries the `selected` semantics a screen reader announces.
 */
@Composable
private fun ThemeSelector(
    mode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThemeMode.all.forEach { option ->
            val selected = option == mode
            Surface(
                onClick = { onSelect(option) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { this.selected = selected },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) colors.primaryContainer else colors.surfaceContainer,
                border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
            ) {
                Text(
                    text = stringResource(themeLabelOf(option)),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun themeLabelOf(mode: ThemeMode): Int = when (mode) {
    ThemeMode.ORIGINAL_DARK -> R.string.settings_theme_original_dark
    ThemeMode.LIGHT_TEST_2 -> R.string.settings_theme_light_test_2
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
                keyField = ApiKeyFieldState.of(configured = false),
                onKeyDraftChange = {},
                onKeyReplace = {},
                onKeyCancel = {},
                onKeySave = {},
                onKeyRemove = {},
                keyNotice = null,
                dubbingLanguage = "fa",
                onDubbingLanguageChange = {},
                appLanguage = "en",
                onAppLanguageChange = {},
                themeMode = ThemeMode.DEFAULT,
                onThemeModeChange = {},
                saved = false,
                onBack = {},
                onSave = {},
                onOpenAiStudio = {},
                onOpenOverlay = {},
                onOpenLogs = {},
                onOpenUsage = {},
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
                // The configured state carries no key text at all — not even a masked sample —
                // so a screenshot of this preview can never leak a credential shape.
                keyField = ApiKeyFieldState.of(configured = true),
                onKeyDraftChange = {},
                onKeyReplace = {},
                onKeyCancel = {},
                onKeySave = {},
                onKeyRemove = {},
                keyNotice = null,
                dubbingLanguage = "en",
                onDubbingLanguageChange = {},
                appLanguage = "fa",
                onAppLanguageChange = {},
                themeMode = ThemeMode.LIGHT_TEST_2,
                onThemeModeChange = {},
                saved = true,
                onBack = {},
                onSave = {},
                onOpenAiStudio = {},
                onOpenOverlay = {},
                onOpenLogs = {},
                onOpenUsage = {},
            )
        }
    }
}
