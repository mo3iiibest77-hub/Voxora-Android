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
    private val mutableReaderBubble = MutableStateFlow(true)
    val readerBubble = mutableReaderBubble.asStateFlow()
    private var commandJob: Job? = null
    private var languageJob: Job? = null
    @Volatile private var languageLocale = Locale.ENGLISH

    init {
        runCommand {
            try {
                prefs.migrateReaderLanguage()
                mutableMode.value = ReaderNarrationModes.normalize(prefs.readerMode.first())
                mutableOutputLang.value = prefs.readerOutputLang.first()
                mutableReaderBubble.value = prefs.readerBubble.first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VoxoraLog.w("ReaderVM", "Reader settings load failed: ${e.javaClass.simpleName}")
                mutableSettingsError.value = context.getString(R.string.reader_settings_load_failed)
            } finally {
                mutableReady.value = true
            }
            // Keep the display language and style in step with the persisted selection before
            // the document is restored, so the reading text is never rendered from a stale one.
            controller.setOutputLanguage(mutableOutputLang.value)
            controller.setNarrationMode(mutableMode.value)
            controller.restoreLastDocument()
        }
    }

    fun searchLanguages(query: String, locale: Locale) {
        languageLocale = locale
        languageJob?.cancel()
        languageJob = viewModelScope.launch(Dispatchers.Default) {
            val options = languageOptions(locale, query)
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
        // The controller owns the reading text, so it has to learn about the change too. It
        // keeps one rendering cache per narration mode, which is what stops text produced for
        // the previous style from being shown after the switch.
        controller.setNarrationMode(value)
    }

    fun setOutputLang(language: String) = runCommand {
        if (!canConfigure() || !ReaderLanguages.isValid(language) || language == outputLang.value) return@runCommand
        prefs.setReaderOutputLang(language)
        mutableOutputLang.value = language
        val selected = ReaderLanguages.language(language)
        mutableLanguageLabel.value = selected.displayName(languageLocale)
        mutableLanguageFlag.value = selected.flagEmoji
        // The controller owns the reading text, so it has to learn about the change too. Its
        // cache is keyed by language within a mode, which is what stops text rendered for the
        // previous language (or the other narration style) from being shown after the switch.
        controller.setOutputLanguage(language)
    }

    /**
     * Chunk and segment navigation publish **synchronously** on the caller's thread.
     *
     * The page animation depends on that. `ReaderController.jumpToChunk` / `jumpToSegment`
     * mutate the queue position and publish the new [ReaderState] inside one `synchronized`
     * block, so by the time these return the arriving unit is already the current one and the
     * page can compose it at zero presence. Routing them through the IO command queue made
     * the publish asynchronous, which let the outgoing unit flash back at full opacity before
     * the new one arrived — the page-turn flicker.
     *
     * The navigation path is pure state plus a job cancellation, so Main is safe for it. The
     * genuinely slow work (extraction, narration) still runs on IO.
     */
    fun jumpToChunk(index: Int): Boolean = controller.jumpToChunk(index)

    fun jumpToSegment(index: Int): Boolean = controller.jumpToSegment(index)

    /**
     * Turns the floating bubble on or off.
     *
     * This is a preference rather than a gate, so it is writable at any time and the running
     * service reacts to it immediately. A stop from the bubble itself clears the same preference,
     * which is why the ViewModel reads it back rather than keeping a second copy.
     */
    fun setReaderBubble(enabled: Boolean) = runCommand {
        if (enabled == readerBubble.value) return@runCommand
        prefs.setReaderBubble(enabled)
        mutableReaderBubble.value = enabled
    }

    fun load(uri: Uri) = runCommand { controller.load(uri) }

    fun play() = runCommand {
        // Never start narration before the queue is ready; see ReaderGates.canPlay.
        if (!ReaderGates.canPlay(ready.value, state.value.phase, state.value.total > 0)) return@runCommand
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

    /**
     * Mode and language stay editable while a document is being extracted. They are
     * only preferences and never touch extraction, so freezing them was what made a
     * restored PDF feel locked. Playback keeps its own, stricter gate.
     */
    private fun canConfigure(): Boolean = ReaderGates.canConfigure(ready.value, state.value.phase)

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
