package com.voxora.app.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxora.app.R
import com.voxora.app.util.VoxoraLog
import com.voxora.core.gemini.ReaderLanguages
import com.voxora.core.gemini.ReaderNarrationModes
import com.voxora.core.prefs.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
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
    private val mutableMode = MutableStateFlow("faithful")
    val mode = mutableMode.asStateFlow()
    private val mutableOutputLang = MutableStateFlow(ReaderLanguages.DEFAULT)
    val outputLang = mutableOutputLang.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private val mutableSettingsError = MutableStateFlow<String?>(null)
    val settingsError = mutableSettingsError.asStateFlow()
    private val mutableLanguageOptions = MutableStateFlow<List<ReaderLanguageOption>>(emptyList())
    val languageOptions = mutableLanguageOptions.asStateFlow()
    private val mutableLanguageLabel = MutableStateFlow("")
    val languageLabel = mutableLanguageLabel.asStateFlow()
    private val mutableLanguageFlag = MutableStateFlow(ReaderLanguages.language(ReaderLanguages.DEFAULT).flagEmoji)
    val languageFlag = mutableLanguageFlag.asStateFlow()
    private var commandJob: Job? = null
    private var languageJob: Job? = null
    @Volatile private var languageLocale = Locale.ENGLISH

    init {
        runCommand {
            try {
                prefs.migrateReaderLanguage()
                mutableMode.value = ReaderNarrationModes.normalize(prefs.readerMode.first())
                mutableOutputLang.value = prefs.readerOutputLang.first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VoxoraLog.w("ReaderVM", "Reader settings load failed: ${e.javaClass.simpleName}")
                mutableSettingsError.value = context.getString(R.string.reader_settings_load_failed)
            } finally {
                mutableReady.value = true
            }
            controller.restoreLastDocument()
        }
    }

    fun searchLanguages(query: String, locale: Locale) {
        languageLocale = locale
        languageJob?.cancel()
        languageJob = viewModelScope.launch(Dispatchers.Default) {
            val options = readerLanguageOptions(locale, query)
            val selected = ReaderLanguages.language(outputLang.value)
            ensureActive()
            mutableLanguageOptions.value = options
            mutableLanguageLabel.value = selected.displayName(locale)
            mutableLanguageFlag.value = selected.flagEmoji
        }
    }

    fun setMode(value: String) = runCommand {
        if (!canConfigure() || value == mode.value || !ReaderNarrationModes.isValid(value)) return@runCommand
        prefs.setReaderSettings("", value)
        mutableMode.value = value
    }

    fun setOutputLang(language: String) = runCommand {
        if (!canConfigure() || !ReaderLanguages.isValid(language) || language == outputLang.value) return@runCommand
        prefs.setReaderOutputLang(language)
        mutableOutputLang.value = language
        val selected = ReaderLanguages.language(language)
        mutableLanguageLabel.value = selected.displayName(languageLocale)
        mutableLanguageFlag.value = selected.flagEmoji
    }

    fun jumpToChunk(index: Int) = runCommand { controller.jumpToChunk(index) }

    fun jumpToSegment(index: Int) = runCommand { controller.jumpToSegment(index) }

    fun load(uri: Uri) = runCommand { controller.load(uri) }

    fun play() = runCommand {
        if (!ready.value || state.value.total == 0 || state.value.phase == ReaderPhase.EXTRACTING) return@runCommand
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ReaderService::class.java)
                    .setAction(ReaderService.ACTION_PLAY)
                    .putExtra(ReaderService.EXTRA_MODE, mode.value),
            )
            mutableSettingsError.value = null
        } catch (e: Exception) {
            VoxoraLog.w("ReaderVM", "Could not start Reader service: ${e.javaClass.simpleName}")
            mutableSettingsError.value = context.getString(R.string.reader_service_start_failed)
        }
    }

    fun pause() = runCommand { controller.pause() }

    fun stop() = runCommand { controller.stop() }

    private fun canConfigure(): Boolean = ready.value && state.value.phase !in setOf(
        ReaderPhase.EXTRACTING, ReaderPhase.CONNECTING, ReaderPhase.REWRITING, ReaderPhase.SPEAKING, ReaderPhase.NEXT,
    )

    private fun runCommand(command: suspend () -> Unit) {
        val previous = commandJob
        commandJob = viewModelScope.launch(Dispatchers.IO) {
            previous?.join()
            try {
                command()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VoxoraLog.w("ReaderVM", "Reader command failed: ${e.javaClass.simpleName}")
                mutableSettingsError.value = context.getString(R.string.reader_settings_save_failed)
            }
        }
    }
}
