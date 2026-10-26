package com.typewhisper.android.ime

import android.util.Log
import com.typewhisper.android.capture.CaptureException
import com.typewhisper.android.capture.RecordingSession
import com.typewhisper.android.engineapi.EngineException
import com.typewhisper.android.engineapi.EnginePlugin
import com.typewhisper.android.storage.HistoryRepository
import com.typewhisper.android.storage.PreferencesRepository
import com.typewhisper.android.storage.SubstitutionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One dictation per call: record, transcribe, normalize, substitute, hand the text to
 * the host. Room and DataStore work runs off the caller's thread because this executes
 * inside the IME's own process, on the same main looper that draws the panel.
 *
 * [stopRecording] and [cancelRecording] are safe to call from that looper: they only set a
 * flag on the session the capture loop is watching, so the panel can acknowledge a release
 * before this coroutine has even woken up.
 */
internal class DictationController(
    private val preferences: PreferencesRepository,
    private val substitutions: SubstitutionRepository,
    private val history: HistoryRepository,
) {
    interface Host {
        fun render(state: VoiceState)
        fun composePartial(text: String)
        fun deliver(text: String)
        fun fail(reason: String?)
    }

    private val running = AtomicBoolean(false)
    @Volatile
    private var session: RecordingSession? = null

    val busy: Boolean get() = running.get()

    /** Ends capture on the spot and lets the utterance go to the engine. */
    fun stopRecording() {
        session?.stop()
    }

    /** Ends capture and drops the audio: a push-to-talk press too short to be a dictation. */
    fun cancelRecording() {
        session?.cancel()
    }

    suspend fun run(engine: EnginePlugin, host: Host) {
        if (!running.compareAndSet(false, true)) return
        try {
            val recording = RecordingSession(MAX_DURATION_MS)
            session = recording
            Log.i(TAG, "capture started")
            host.render(VoiceState.Listening)
            val audio = withContext(Dispatchers.IO) { recording.record() }
            Log.i(TAG, "capture ended after ${audio.durationMs}ms")
            if (recording.isDiscarded() || audio.samples.isEmpty()) {
                Log.i(TAG, "discarded — the press was not a dictation")
                host.render(VoiceState.Idle)
                return
            }
            host.render(VoiceState.Working)

            val language = DictationText.preferredLanguage(preferences.language.first())
            val result = engine.transcribe(audio, language) { partial ->
                host.composePartial(partial)
            }
            val text = DictationText.finalize(
                raw = result.text,
                language = result.detectedLanguage ?: language,
                settings = preferences.normalizationSettings(),
                dictionary = substitutions.dictionary.first(),
                snippets = substitutions.snippets.first(),
            )
            // Length, never content: the log has to prove a transcription completed without
            // becoming a place where the user's words are stored.
            Log.i(TAG, "transcribed ${text.length} chars from ${audio.durationMs}ms of audio")
            host.render(VoiceState.Idle)
            host.deliver(text)
            withContext(Dispatchers.IO) {
                history.save(
                    text = text,
                    engineId = result.engineId,
                    language = result.detectedLanguage ?: language,
                    durationMs = audio.durationMs,
                )
            }
        } catch (cancelled: CancellationException) {
            host.render(VoiceState.Idle)
            throw cancelled
        } catch (failure: EngineException) {
            host.fail(failure.message)
        } catch (failure: CaptureException) {
            host.fail(failure.message)
        } catch (failure: Exception) {
            host.fail(failure.javaClass.simpleName)
        } finally {
            session = null
            running.set(false)
        }
    }

    private companion object {
        const val TAG = "TwIme"
        const val MAX_DURATION_MS = 30_000L
    }
}
