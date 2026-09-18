package com.voxora.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voxora.core.usage.GeminiUsageLedger
import com.voxora.core.usage.UsageStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Separate DataStore for the observed-usage ledger.
 *
 * It gets its own file rather than sharing `voxora_prefs` because the ledger is written
 * frequently (once per narrated unit) while settings are written rarely. Keeping them apart
 * means a busy narration cannot rewrite — or risk corrupting — the user's settings, and the
 * settings flows do not re-emit on every usage tick.
 *
 * Only the ledger is stored. No key, token, prompt, document text or audio ever reaches this
 * file; see [GeminiUsageLedger] for exactly what is kept.
 */
private val Context.usageStore by preferencesDataStore("voxora_usage")

class UsagePrefs(private val context: Context) : UsageStore {

    private val keyLedger = stringPreferencesKey("usage_ledger")

    /** The stored ledger, or an empty one when nothing has been recorded yet. */
    val ledger: Flow<GeminiUsageLedger> = context.usageStore.data.map { preferences ->
        GeminiUsageLedger.fromJson(preferences[keyLedger])
    }

    override suspend fun load(): GeminiUsageLedger = ledger.first()

    override suspend fun save(ledger: GeminiUsageLedger) {
        context.usageStore.edit { it[keyLedger] = ledger.toJson() }
    }
}
