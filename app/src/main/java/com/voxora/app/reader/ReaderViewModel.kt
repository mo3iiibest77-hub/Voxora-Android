package com.voxora.app.reader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxora.core.prefs.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val service: ReaderService,
) : ViewModel() {
    private val prefs = UserPrefs(context)
    internal val state = service.state
    private val mutableEndpoint = MutableStateFlow("")
    val endpoint = mutableEndpoint.asStateFlow()
    private val mutableMode = MutableStateFlow("simple")
    val mode = mutableMode.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private val mutableSettingsError = MutableStateFlow<String?>(null)
    val settingsError = mutableSettingsError.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                mutableEndpoint.value = prefs.readerEndpoint.first()
                mutableMode.value = prefs.readerMode.first().takeIf { it == "fluent" } ?: "simple"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableSettingsError.value = "Could not load Reader settings. Enter your endpoint again."
            } finally {
                mutableReady.value = true
            }
        }
    }

    fun setEndpoint(value: String) {
        if (value == endpoint.value) return
        service.stop()
        mutableEndpoint.value = value
    }

    fun setMode(value: String) {
        if (value == mode.value) return
        service.stop()
        mutableMode.value = value
    }

    fun load(uri: Uri) = service.load(uri)

    fun play() {
        val endpoint = endpoint.value.trim()
        val mode = mode.value
        viewModelScope.launch {
            try {
                prefs.setReaderSettings(endpoint, mode)
                mutableSettingsError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableSettingsError.value = "Could not save Reader settings."
            }
        }
        service.play(endpoint, mode)
    }

    fun pause() = service.pause()

    fun stop() = service.stop()

    override fun onCleared() {
        service.close()
        super.onCleared()
    }
}
