package com.voxora.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("voxora_prefs")

class UserPrefs(private val context: Context) {
    private val keyApi = stringPreferencesKey("api_key")
    private val keyLang = stringPreferencesKey("target_lang")
    private val keyAppLang = stringPreferencesKey("app_lang")
    private val keyOnboarding = booleanPreferencesKey("onboarding_done")
    private val keyEmail = stringPreferencesKey("user_email")
    private val keyDisplayName = stringPreferencesKey("user_display_name")
    private val keySignedIn = booleanPreferencesKey("signed_in")
    private val keyReaderEndpoint = stringPreferencesKey("reader_endpoint")
    private val keyReaderMode = stringPreferencesKey("reader_mode")
    private val keyReaderOutputLang = stringPreferencesKey("reader_output_lang")
    private val keyLastDocUri = stringPreferencesKey("last_doc_uri")

    val readerEndpoint: Flow<String> = context.dataStore.data.map { it[keyReaderEndpoint].orEmpty() }
    val readerMode: Flow<String> = context.dataStore.data.map { it[keyReaderMode] ?: "faithful" }
    val readerOutputLang: Flow<String> =
        context.dataStore.data.map { it[keyReaderOutputLang] ?: "original" }
    val lastDocUri: Flow<String> =
        context.dataStore.data.map { it[keyLastDocUri].orEmpty() }

    suspend fun setReaderSettings(endpoint: String, mode: String) {
        context.dataStore.edit {
            it[keyReaderEndpoint] = endpoint.trim()
            it[keyReaderMode] = mode
        }
    }

    suspend fun setReaderOutputLang(lang: String) {
        context.dataStore.edit { it[keyReaderOutputLang] = lang }
    }

    suspend fun setLastDocUri(uri: String) {
        context.dataStore.edit { it[keyLastDocUri] = uri }
    }

    suspend fun clearLastDocUri() {
        context.dataStore.edit { it.remove(keyLastDocUri) }
    }

    val apiKey: Flow<String> = context.dataStore.data.map { it[keyApi].orEmpty() }
    val targetLanguage: Flow<String> = context.dataStore.data.map { it[keyLang] ?: "fa" }
    /** UI language — default English (international) */
    val appLanguage: Flow<String> = context.dataStore.data.map { it[keyAppLang] ?: "en" }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[keyOnboarding] == true }
    val userEmail: Flow<String> = context.dataStore.data.map { it[keyEmail].orEmpty() }
    val displayName: Flow<String> = context.dataStore.data.map { it[keyDisplayName].orEmpty() }
    val signedIn: Flow<Boolean> = context.dataStore.data.map { it[keySignedIn] == true }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[keyApi] = value.trim() }
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

    suspend fun setAccount(email: String, name: String, signedIn: Boolean) {
        context.dataStore.edit {
            it[keyEmail] = email
            it[keyDisplayName] = name
            it[keySignedIn] = signedIn
        }
    }

    suspend fun clearAccount() {
        context.dataStore.edit {
            it.remove(keyEmail)
            it.remove(keyDisplayName)
            it[keySignedIn] = false
        }
    }
}
