package com.typewhisper.android.ime

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import com.typewhisper.android.engineapi.EnginePlugin
import com.typewhisper.android.storage.DictationMode
import com.typewhisper.android.storage.HistoryRepository
import com.typewhisper.android.storage.PreferencesRepository
import com.typewhisper.android.storage.SubstitutionRepository
import com.typewhisper.android.storage.TypeWhisperDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TypeWhisperVoiceIME : InputMethodService(), DictationController.Host, VoiceInputPanel.Listener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var workJob: Job? = null
    private var panel: VoiceInputPanel? = null
    private var database: TypeWhisperDatabase? = null
    private var controller: DictationController? = null

    override fun onCreate() {
        super.onCreate()
        val created = TypeWhisperDatabase.create(this)
        database = created
        val preferences = PreferencesRepository.forContext(this)
        controller = DictationController(
            preferences = preferences,
            substitutions = SubstitutionRepository(created),
            history = HistoryRepository(created),
        )
        scope.launch {
            preferences.dictationMode.collect { mode ->
                val pushToTalk = mode == DictationMode.PushToTalk
                mainHandler.post { panel?.pushToTalk = pushToTalk }
            }
        }
    }

    override fun onCreateInputView(): View {
        val created = VoiceInputPanel.inflate(this)
        panel = created
        created.setListener(this)
        return created.root
    }

    override fun onFinishInput() {
        super.onFinishInput()
        // The field we write into is going away; a dictation left running would finish
        // into whatever the user focuses next, so the whole cycle is cancelled.
        controller?.stopRecording()
        workJob?.cancel()
        workJob = null
    }

    /** Tap mode, and the accessibility click: whichever half of a dictation is owed. */
    override fun onToggle() {
        if (controller?.busy == true) stopAndTranscribe() else startDictation()
    }

    override fun onPressStart() {
        val controller = controller ?: return
        // A press while a dictation is already in flight is ignored rather than queued: the
        // key is showing what the first press is doing, and two utterances in one field is
        // never what a second hold meant.
        if (!controller.busy) startDictation()
    }

    override fun onPressEnd(discard: Boolean) {
        val controller = controller ?: return
        if (controller.busy) {
            if (discard) controller.cancelRecording() else stopAndTranscribe()
            return
        }
        // The release came before the microphone ever opened — the engine was still being
        // resolved, possibly fetching the model. There is no utterance to send, so the
        // dictation this press started is dropped instead of being allowed to run on.
        workJob?.cancel()
        workJob = null
        panel?.render(VoiceState.Idle)
    }

    /**
     * Halts capture and acknowledges the release on the spot. The words have not arrived and
     * the microphone has gone quiet, which is exactly what [VoiceState.Working] says.
     */
    private fun stopAndTranscribe() {
        controller?.stopRecording()
        panel?.render(VoiceState.Working)
    }

    private fun startDictation() {
        val controller = controller ?: return
        if (controller.busy) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            panel?.showError(getString(R.string.error_permission))
            return
        }
        val resolver = ImeEngineSource.resolver
        if (resolver == null) {
            panel?.showError(getString(R.string.error_no_engine))
            return
        }
        workJob = scope.launch {
            val engine = resolveEngine(resolver) ?: return@launch
            controller.run(engine, this@TypeWhisperVoiceIME)
        }
    }

    /**
     * Building the engine can mean fetching the on-device model first; that wait is
     * shown on the status line, and a model that cannot be fetched is reported rather
     * than dictated around. Returns null when the press should simply end.
     */
    private suspend fun resolveEngine(
        resolver: suspend (Context, (Float) -> Unit) -> EnginePlugin,
    ): EnginePlugin? = try {
        resolver(this) { fraction ->
            mainHandler.post {
                panel?.showStatus(getString(R.string.state_model_progress, (fraction * 100).toInt()))
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        mainHandler.post {
            panel?.showError(failure.message ?: getString(R.string.error_generic))
        }
        null
    }

    override fun render(state: VoiceState) {
        mainHandler.post { panel?.render(state) }
    }

    override fun composePartial(text: String) {
        mainHandler.post {
            // Composing, not committing: the in-flight words stay replaceable by the
            // next partial and by the final normalized text.
            currentInputConnection?.setComposingText(text, 1)
        }
    }

    override fun deliver(text: String) {
        mainHandler.post {
            val connection = currentInputConnection
            val accepted = connection != null && connection.setComposingText(text, 1)
            if (accepted) {
                // Finish explicitly: an open composing region tells the host app the
                // text is still being typed, and its own completion logic may rewrite
                // or drop it later.
                connection.finishComposingText()
            } else {
                copyForManualPaste(text)
            }
        }
    }

    override fun fail(reason: String?) {
        mainHandler.post {
            discardComposing()
            val message = if (reason == null) {
                getString(R.string.error_generic)
            } else {
                getString(R.string.error_failed, reason)
            }
            panel?.showError(message)
        }
    }

    private fun discardComposing() {
        // Blank the composing region before finishing so a failed utterance does not
        // leave half a sentence committed in the user's field.
        val connection = currentInputConnection ?: return
        connection.setComposingText("", 1)
        connection.finishComposingText()
    }

    private fun copyForManualPaste(text: String) {
        // Reached only when the focused field refuses composition — a view that declares
        // itself non-editable. The clipboard plus a visible hint is the last honest route.
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.ime_name), text))
        Toast.makeText(this, R.string.copied_toast, Toast.LENGTH_SHORT).show()
    }
}
