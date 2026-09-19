package com.voxora.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voxora.core.gemini.ReaderLanguages
import com.voxora.core.gemini.ReaderVoice
import com.voxora.core.usage.ApiKeyMask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("voxora_prefs")

class UserPrefs(private val context: Context) {
    private val keyApi = stringPreferencesKey("api_key")
    private val keyLang = stringPreferencesKey("target_lang")
    private val keyAppLang = stringPreferencesKey("app_lang")
    private val keyOnboarding = booleanPreferencesKey("onboarding_done")
    private val keyReaderEndpoint = stringPreferencesKey("reader_endpoint")
    private val keyReaderMode = stringPreferencesKey("reader_mode")
    private val keyReaderOutputLang = stringPreferencesKey("reader_output_lang")
    private val keyReaderVoice = stringPreferencesKey("reader_voice")
    private val keyLastDocUri = stringPreferencesKey("last_doc_uri")
    private val keyReaderBubble = booleanPreferencesKey("reader_bubble")
    private val keyThemeMode = stringPreferencesKey("theme_mode")

    val readerEndpoint: Flow<String> = context.dataStore.data.map { it[keyReaderEndpoint].orEmpty() }
    val readerMode: Flow<String> = context.dataStore.data.map { it[keyReaderMode] ?: "faithful" }
    val readerOutputLang: Flow<String> =
        context.dataStore.data.map { ReaderLanguages.normalize(it[keyReaderOutputLang]) }

    /**
     * The narrator voice the Reader should use, as a [ReaderVoice] id.
     *
     * A **global Reader preference**, not per-book: the voice is how the product sounds, not what
     * the book is. It normalizes like the narration language does, so an absent or unknown value
     * yields [ReaderVoice.DEFAULT] instead of failing a run.
     */
    val readerVoice: Flow<ReaderVoice> =
        context.dataStore.data.map { ReaderVoice.normalize(it[keyReaderVoice]) }

    suspend fun setReaderVoice(voice: ReaderVoice) {
        context.dataStore.edit { it[keyReaderVoice] = voice.id }
    }

    suspend fun migrateReaderLanguage() {
        context.dataStore.edit {
            it[keyReaderOutputLang] = ReaderLanguages.normalize(it[keyReaderOutputLang])
        }
    }
    val lastDocUri: Flow<String> =
        context.dataStore.data.map { it[keyLastDocUri].orEmpty() }

    /**
     * Whether the Reader's floating bubble may show while narration is active.
     *
     * Defaults to on, matching the Live bubble, which appears whenever dubbing is running. An
     * absent value therefore means "on" rather than "off": the preference only ever records a
     * deliberate choice to hide it.
     */
    val readerBubble: Flow<Boolean> =
        context.dataStore.data.map { it[keyReaderBubble] != false }

    suspend fun setReaderBubble(enabled: Boolean) {
        context.dataStore.edit { it[keyReaderBubble] = enabled }
    }

    /**
     * The user's chosen appearance.
     *
     * Defaults to [ThemeMode.DEFAULT], which is the original Voxora dark theme — the product's
     * primary identity. An unrecognised stored value normalizes to the default instead of throwing,
     * and values written by the previous `system`/`light`/`dark` model are migrated rather than
     * discarded.
     */
    val themeMode: Flow<ThemeMode> =
        context.dataStore.data.map { ThemeMode.normalize(it[keyThemeMode]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[keyThemeMode] = mode.id }
    }

    suspend fun setReaderSettings(endpoint: String, mode: String) {
        context.dataStore.edit {
            it[keyReaderEndpoint] = endpoint.trim()
            it[keyReaderMode] = mode
        }
    }

    suspend fun setReaderOutputLang(lang: String) {
        context.dataStore.edit { it[keyReaderOutputLang] = ReaderLanguages.normalize(lang) }
    }

    suspend fun setLastDocUri(uri: String) {
        context.dataStore.edit { it[keyLastDocUri] = uri }
    }

    suspend fun clearLastDocUri() {
        context.dataStore.edit { it.remove(keyLastDocUri) }
    }

    suspend fun updateReaderDocument(uri: String?, isCurrent: () -> Boolean) {
        context.dataStore.edit {
            if (isCurrent()) {
                if (uri == null) it.remove(keyLastDocUri) else it[keyLastDocUri] = uri
            }
        }
    }

    val apiKey: Flow<String> = context.dataStore.data.map { it[keyApi].orEmpty() }

    /**
     * Whether a usable Gemini API key is stored, **without exposing the key**.
     *
     * This is what the Settings key card reads. It deliberately does not hand the secret to the UI,
     * so the field cannot repopulate itself with the saved value — the same reason the access token
     * is kept out of `CloudAuthState`. The rule is [ApiKeyMask.isConfigured], the one the usage
     * screen and the key probe already use, so "configured" means the same thing everywhere and a
     * placeholder value still counts as absent.
     */
    val apiKeyConfigured: Flow<Boolean> =
        context.dataStore.data.map { ApiKeyMask.isConfigured(it[keyApi]) }

    val targetLanguage: Flow<String> = context.dataStore.data.map { it[keyLang] ?: "fa" }
    /** UI language — default English (international) */
    val appLanguage: Flow<String> = context.dataStore.data.map { it[keyAppLang] ?: "en" }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[keyOnboarding] == true }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[keyApi] = value.trim() }
    }

    /**
     * Removes the stored key.
     *
     * An absent key is not the same as a stored empty string: `remove` is the honest "no key", so
     * `apiKeyConfigured` reports false and every reader of [apiKey] behaves as on a fresh install.
     */
    suspend fun clearApiKey() {
        context.dataStore.edit { it.remove(keyApi) }
    }

    suspend fun setTargetLanguage(code: String) {
        context.dataStore.edit { it[keyLang] = code }
    }

    suspend fun setAppLanguage(code: String) {
        context.dataStore.edit { it[keyAppLang] = code }
    }

    suspend fun setOnboardingDone(done: Boolean = true) {
        context.dataStore.edit { it[keyOnboarding] = done }
    }
}
