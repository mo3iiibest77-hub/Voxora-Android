package com.voxora.app.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxora.app.R
import com.voxora.app.util.VoxoraLog
import com.voxora.core.prefs.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: ReaderController,
) : ViewModel() {
    private val prefs = UserPrefs(context)
    internal val state = controller.state
    internal val narrationText = controller.narrationText
    private val mutableMode = MutableStateFlow("simple")
    val mode = mutableMode.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private val mutableSettingsError = MutableStateFlow<String?>(null)
    val settingsError = mutableSettingsError.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mutableMode.value = prefs.readerMode.first().takeIf { it == "fluent" } ?: "simple"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VoxoraLog.e("ReaderVM", "Could not load Reader settings", e)
                mutableSettingsError.value = context.getString(R.string.reader_settings_load_failed)
            } finally {
                mutableReady.value = true
            }
        }
    }

    fun setMode(value: String) {
        if (value == mode.value || value !in setOf("simple", "fluent")) return
        mutableMode.value = value
        runCommand { controller.stop() }
        saveMode(value)
    }

    fun load(uri: Uri) = runCommand { controller.load(uri) }

    fun play() {
        if (!ready.value) return
        val selectedMode = mode.value
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ReaderService::class.java)
                    .setAction(ReaderService.ACTION_PLAY)
                    .putExtra(ReaderService.EXTRA_MODE, selectedMode),
            )
            mutableSettingsError.value = null
            saveMode(selectedMode)
        } catch (e: Exception) {
            VoxoraLog.e("ReaderVM", "Could not start Reader service", e)
            mutableSettingsError.value = context.getString(R.string.reader_service_start_failed)
        }
    }

    fun pause() = runCommand { controller.pause() }

    fun stop() = runCommand { controller.stop() }

    private fun saveMode(value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                prefs.setReaderSettings("", value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VoxoraLog.e("ReaderVM", "Could not save Reader settings", e)
                mutableSettingsError.value = context.getString(R.string.reader_settings_save_failed)
            }
        }
    }

    private fun runCommand(command: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                command()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VoxoraLog.e("ReaderVM", "Reader command failed", e)
                mutableSettingsError.value = context.getString(R.string.reader_failed_generic)
            }
        }
    }
}
