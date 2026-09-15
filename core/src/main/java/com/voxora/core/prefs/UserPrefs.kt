package com.voxora.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("voxora_prefs")

class UserPrefs(private val context: Context) {
    private val keyApi = stringPreferencesKey("api_key")
    private val keyLang = stringPreferencesKey("target_lang")

    val apiKey: Flow<String> = context.dataStore.data.map { it[keyApi].orEmpty() }
    val targetLanguage: Flow<String> = context.dataStore.data.map { it[keyLang] ?: "fa" }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[keyApi] = value.trim() }
    }

    suspend fun setTargetLanguage(code: String) {
        context.dataStore.edit { it[keyLang] = code }
    }
}
