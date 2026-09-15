package com.voxora.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.voxora.core.prefs.UserPrefs
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class VoxoraApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Apply locale off the critical path — never block / crash Application start
        appScope.launch {
            try {
                val code = UserPrefs(this@VoxoraApp).appLanguage.first().ifBlank { "en" }
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
            } catch (_: Exception) {
                try {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                } catch (_: Exception) {
                }
            }
        }
    }
}
