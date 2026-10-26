package com.typewhisper.android.app.dictation

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.typewhisper.android.app.TypeWhisperApplication
import com.typewhisper.android.app.engine.EngineCatalog
import com.typewhisper.android.app.engine.OnDeviceModels
import com.typewhisper.android.capture.CaptureException
import com.typewhisper.android.capture.RecordingSession
import com.typewhisper.android.engineapi.EngineException
import com.typewhisper.android.engines.mac.MacEnginePlugin
import com.typewhisper.android.engines.whispercpp.ModelDownloadException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Foreground service owning the whole companion-mode loop.
 *
 * One [RecordingSession] at a time: start → capture up to [MAX_RECORD_MS] of
 * audio (an early [RecordingSession.stop] ends it sooner) → build [AudioData] →
 * engine → normalize → deliver. Every trigger funnels its start/stop/toggle
 * command into [onStartCommand], so the toggle lives in exactly one place.
 *
 * The service foregrounds itself the instant a valid start is accepted, before
 * any recording begins: on Android 12+ a service launched via
 * startForegroundService must call startForeground within seconds, and the
 * `microphone` type is what keeps AudioRecord legal on Android 14+.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var session: RecordingSession? = null
    private var lastModelPercent = -1

    /** A dictation loop owns this service: capture, transcription, delivery, or any of them. */
    private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            DictationControl.ACTION_STOP -> stopRecording(discard = false)
            DictationControl.ACTION_DISCARD -> stopRecording(discard = true)
            DictationControl.ACTION_TOGGLE ->
                if (busy) stopRecording(discard = false) else startRecording()
            DictationControl.ACTION_TRANSLATE -> translateLast()
            else -> startRecording()
        }
        // START_NOT_STICKY: a killed service must not silently resume capturing
        // the microphone — the user has to re-trigger it deliberately.
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (busy) return
        if (!DictationControl.hasMicrophonePermission(this)) {
            fail(MIC_DENIED_MESSAGE)
            return
        }

        ServiceCompat.startForeground(
            this,
            Notifications.ID_ONGOING,
            Notifications.ongoing(this, DictationPhase.Recording),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        DictationControl.setPhase(DictationPhase.Recording)
        busy = true
        lastModelPercent = -1

        val recordingSession = RecordingSession(
            maxDurationMs = MAX_RECORD_MS,
            preferredDevice = TypeWhisperApplication.container(this).micDevice.value,
        )
        session = recordingSession
        Log.i(TAG, "capture started")
        scope.launch { runDictation(recordingSession) }
    }

    /**
     * Requests an early end of capture. The in-flight [runDictation] still runs to
     * transcription, so stop means "finish this utterance now" — unless [discard] is set,
     * which is the push-to-talk gesture's too-short press: its audio is dropped instead.
     *
     * The phase flips here, on the main thread, before the capture loop has even noticed the
     * request: the button has to show the consequence of the tap that made it. Publishing
     * that state only from the end of [runDictation] is what made one tap on stop look
     * ignored, because the stop icon stayed up for the whole of the transcription after the
     * microphone had already gone quiet.
     *
     * A discard leaves the phase alone. There is no wait to acknowledge — capture is already
     * halting, and [finishSession] will be back at [DictationPhase.Idle] within a frame.
     */
    private fun stopRecording(discard: Boolean) {
        val current = session
        if (current == null) {
            // Nothing to end, because the start this release belongs to never arrived: the
            // microphone was refused, or a translate owns the service instead. Let an idle
            // service go, but never interrupt a translate the user asked for.
            if (!busy) stopSelf()
            return
        }
        if (discard) current.cancel() else current.stop()
        if (!discard) {
            DictationControl.setPhase(DictationPhase.Finishing)
            // The notification that carries the Stop action says the tap was taken, so the
            // user has an acknowledgement even when they are not looking at the bubble.
            runCatching {
                NotificationManagerCompat.from(this).notify(
                    Notifications.ID_ONGOING,
                    Notifications.ongoing(this, DictationPhase.Finishing),
                )
            }
        }
        Log.i(TAG, if (discard) "stop: discard" else "stop: accepted")
    }

    /**
     * The Translate action's entry. The service was pulled up from the notification, so
     * it has to foreground itself before doing anything slower than a tap; no microphone
     * is opened — this is the held samples and one HTTP call.
     */
    private fun translateLast() {
        if (busy) return
        val held = LastDictation.fresh()
        if (held == null) {
            fail("That dictation is too old to translate — dictate again and use Translate right after.")
            return
        }
        busy = true
        ServiceCompat.startForeground(
            this,
            Notifications.ID_ONGOING,
            Notifications.ongoing(this, DictationPhase.Idle),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        scope.launch { runTranslate(held) }
    }

    private suspend fun runTranslate(held: LastDictation.Held) {
        val container = TypeWhisperApplication.container(this)
        try {
            val token = withContext(Dispatchers.IO) { container.secrets.secret(EngineCatalog.ID_MAC) }
                ?: return fail(TRANSLATE_NEEDS_MAC_MESSAGE)
            val requestedLanguage = container.preferences.language.first()
            // "auto" tells the Mac nothing about the target; English is the one language
            // every user of a translate feature expects as a fallback.
            val target = requestedLanguage.takeIf { it.isNotBlank() && it != "auto" } ?: "en"
            val mac = MacEnginePlugin(
                baseUrl = container.macBaseUrl().orEmpty(),
                token = token,
            )
            val result = mac.translate(held.audio, targetLanguage = target)
            val text = TranscriptPostProcessor.process(this, result.text, result.detectedLanguage)
            val label = Locale.forLanguageTag(target).getDisplayLanguage(Locale.getDefault())
            TranscriptPoster(this).deliverTranslation(text, label.ifBlank { target })
        } catch (e: EngineException) {
            fail(e.message ?: TRANSLATE_FAILED_MESSAGE)
            Log.w(TAG, "translation failed", e)
        } finally {
            finishSession()
        }
    }

    /** The Translate offer exists only when the Mac is configured; reads the encrypted store. */
    private suspend fun macIsConfigured(): Boolean =
        TypeWhisperApplication.container(this).macBaseUrl() != null &&
            withContext(Dispatchers.IO) {
                runCatching {
                    TypeWhisperApplication.container(this@RecordingService)
                        .secrets.secret(EngineCatalog.ID_MAC)?.isNotBlank() == true
                }.getOrDefault(false)
            }

    private suspend fun runDictation(recordingSession: RecordingSession) {
        try {
            val audio = recordingSession.record()
            Log.i(TAG, "capture ended after ${audio.durationMs}ms")
            // A press the user disowned, and a press that ended before the microphone
            // opened, both mean the same thing downstream: there is nothing to say.
            if (recordingSession.isDiscarded() || audio.samples.isEmpty()) {
                Log.i(TAG, "discarded — the press was not a dictation")
                return
            }
            val outcome = DictationEngineSource.transcribe(this, audio, onModelProgress = ::showModelProgress)
            val text = TranscriptPostProcessor.process(this, outcome.result.text, outcome.result.detectedLanguage)
            // Length, never content: the log has to prove a transcription completed without
            // becoming a place where the user's words are stored.
            Log.i(TAG, "transcribed ${text.length} chars from ${audio.durationMs}ms of audio")
            // History is a convenience record, not part of delivering the text: a
            // database hiccup must never eat the transcript the user is waiting for.
            runCatching {
                TypeWhisperApplication.container(this@RecordingService).history().save(
                    text = text,
                    engineId = outcome.result.engineId,
                    language = outcome.language,
                    durationMs = audio.durationMs,
                )
            }
            TranscriptPoster(this).deliver(text, outcome.note, offerTranslate = macIsConfigured())
            // Only a companion-mode dictation keeps its samples, and only long enough
            // for the Translate action to still make sense on them.
            LastDictation.hold(audio, text)
        } catch (e: CaptureException) {
            // Mic busy or unusable at the HAL: surface it, never crash.
            fail(MIC_BUSY_MESSAGE)
            Log.w(TAG, "capture failed", e)
        } catch (e: SecurityException) {
            fail(MIC_DENIED_MESSAGE)
            Log.w(TAG, "capture denied", e)
        } catch (e: ModelDownloadException) {
            // First dictation on a fresh install fetches the model inside this loop;
            // a blocked download is explained, never traded for a canned transcript.
            fail(OnDeviceModels.explain(this, e))
            Log.w(TAG, "model download failed", e)
        } catch (e: EngineException) {
            // Engine messages are already credential-free and user-safe.
            fail(e.message ?: TRANSCRIPTION_FAILED_MESSAGE)
            Log.w(TAG, "transcription failed", e)
        } finally {
            finishSession()
        }
    }

    private fun finishSession() {
        session = null
        busy = false
        DictationControl.setPhase(DictationPhase.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Repaints the ongoing notification with download progress, once per whole
     * percent so the notify channel is not hammered with near-identical updates.
     */
    private fun showModelProgress(fraction: Float) {
        val percent = (fraction * 100).toInt().coerceIn(0, 100)
        if (percent == lastModelPercent) return
        lastModelPercent = percent
        runCatching {
            NotificationManagerCompat.from(this).notify(
                Notifications.ID_ONGOING,
                Notifications.modelDownload(this, percent),
            )
        }
    }

    private fun fail(message: String) {
        // Guarded: on API 33+ a notify without POST_NOTIFICATIONS throws, and a
        // failure notice must never turn into a second crash.
        runCatching {
            NotificationManagerCompat.from(this).notify(
                Notifications.ID_ALERT,
                Notifications.failure(this, message),
            )
        }
        finishSession()
    }

    override fun onDestroy() {
        DictationControl.setPhase(DictationPhase.Idle)
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "TwRecording"
        const val MAX_RECORD_MS = 10_000L
        const val MIC_DENIED_MESSAGE = "Microphone access is off. Open TypeWhisper to grant it."
        const val MIC_BUSY_MESSAGE = "Microphone unavailable — another app may be using it."
        const val TRANSCRIPTION_FAILED_MESSAGE = "Transcription failed. Try again."
        const val TRANSLATE_NEEDS_MAC_MESSAGE =
            "Translation runs on your Mac — set up TypeWhisper on Mac in Settings to use it."
        const val TRANSLATE_FAILED_MESSAGE = "The Mac could not translate that dictation. Try again."
    }
}
