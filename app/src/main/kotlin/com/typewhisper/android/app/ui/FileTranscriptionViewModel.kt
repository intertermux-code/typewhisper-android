package com.typewhisper.android.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.typewhisper.android.app.TypeWhisperApplication
import com.typewhisper.android.app.audio.AudioFileDecoder
import com.typewhisper.android.app.dictation.DictationEngineSource
import com.typewhisper.android.app.dictation.TranscriptPostProcessor
import com.typewhisper.android.app.engine.OnDeviceModels
import com.typewhisper.android.engineapi.EngineException
import com.typewhisper.android.engines.whispercpp.ModelDownloadException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the file-transcription screen stands; the button label and busy states read this. */
enum class FilePhase { Idle, Decoding, Transcribing, Done }

/**
 * Pick an audio file → run it through the engine the settings currently select → hand
 * back the transcript. Nothing is written to history: this is not a dictation, and the
 * history screen is for what the user said, not what they uploaded.
 */
class FileTranscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val container = TypeWhisperApplication.container(application)

    private val _phase = MutableStateFlow(FilePhase.Idle)
    val phase: StateFlow<FilePhase> = _phase.asStateFlow()

    private val _fileName = MutableStateFlow<String?>(null)
    val fileName: StateFlow<String?> = _fileName.asStateFlow()

    private val _transcript = MutableStateFlow<String?>(null)
    val transcript: StateFlow<String?> = _transcript.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun transcribe(uri: Uri) {
        if (_phase.value == FilePhase.Decoding || _phase.value == FilePhase.Transcribing) return
        _fileName.value = displayName(uri)
        _transcript.value = null
        _error.value = null
        _phase.value = FilePhase.Decoding
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val audio = AudioFileDecoder.decode(getApplication(), uri)
                _phase.value = FilePhase.Transcribing
                val outcome = DictationEngineSource.transcribe(getApplication(), audio) { fraction ->
                    // An hour-long file on a phone that has never downloaded the model
                    // still has to download it; the same progress line covers it.
                    _error.value = OnDeviceModels.progressNote(fraction)
                }
                val text = TranscriptPostProcessor.process(
                    getApplication(),
                    outcome.result.text,
                    outcome.result.detectedLanguage,
                )
                _transcript.value = text
                _error.value = outcome.note
                _phase.value = FilePhase.Done
            } catch (e: AudioFileDecoder.Unsupported) {
                _error.value = e.message
                _phase.value = FilePhase.Idle
            } catch (e: ModelDownloadException) {
                _error.value = OnDeviceModels.explain(getApplication(), e)
                _phase.value = FilePhase.Idle
            } catch (e: EngineException) {
                _error.value = e.message
                _phase.value = FilePhase.Idle
            }
        }
    }

    private fun displayName(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { uri.toString() }
}
