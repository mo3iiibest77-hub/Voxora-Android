package com.voxora.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.voxora.core.prefs.UserPrefs
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class VoxoraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val code = runBlocking { UserPrefs(this@VoxoraApp).appLanguage.first() }
            val tags = code.ifBlank { "en" }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
        } catch (_: Exception) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }
    }
}
