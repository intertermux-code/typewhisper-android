package com.typewhisper.android.app.dictation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the current dictation has got, in the only three states the UI can act on.
 *
 * [Finishing] exists because capture and transcription are two different waits: the
 * microphone has halted but the words have not arrived yet. A control that shows
 * [Recording] through both is what made a single tap on stop look ignored.
 */
enum class DictationPhase { Idle, Recording, Finishing }

/**
 * The single entry point every trigger (Quick Settings tile, overlay bubble,
 * notification action, in-app button) routes through, plus the authoritative dictation
 * phase the service mirrors back for the UI to observe.
 *
 * All three triggers live in this process, so a plain [StateFlow] is enough to keep the
 * tile and bubble in sync with the service — no binder or broadcast needed. The phase is a
 * *reflection* of the service, written only by the service via [setPhase]; callers never
 * mutate it directly, which is why a tap cannot get the UI and the microphone out of step.
 */
object DictationControl {

    internal const val ACTION_START = "com.typewhisper.android.app.START_DICTATION"
    internal const val ACTION_STOP = "com.typewhisper.android.app.STOP_DICTATION"
    internal const val ACTION_TOGGLE = "com.typewhisper.android.app.TOGGLE_DICTATION"

    /**
     * Ends the dictation and throws its audio away. The push-to-talk gesture needs this: a
     * press too short to be deliberate must not come back as an empty transcript.
     */
    internal const val ACTION_DISCARD = "com.typewhisper.android.app.DISCARD_DICTATION"

    /**
     * Runs the held audio of the last dictation through the Mac again with a target
     * language. Only ever launched from the paste nudge's Translate action.
     */
    internal const val ACTION_TRANSLATE = "com.typewhisper.android.app.TRANSLATE_LAST"

    /**
     * Set when a trigger fires but the microphone permission is missing, so the
     * only way forward is to open the app and grant it. Carried as an activity
     * extra on the failure notification's content intent.
     */
    internal const val EXTRA_OPEN_REASON = "open_reason"
    internal const val REASON_NEEDS_MIC = "needs_mic"

    private val _phase = MutableStateFlow(DictationPhase.Idle)
    val phase: StateFlow<DictationPhase> = _phase.asStateFlow()

    fun hasMicrophonePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun start(context: Context) {
        if (!hasMicrophonePermission(context)) {
            MicPermissionPrompt.notify(context)
            return
        }
        // A QS-tile tap / bubble press can arrive while the app is backgrounded;
        // only startForegroundService survives the background-FCR restriction, and
        // the service calls startForeground immediately so the 5s obligation is met.
        ContextCompat.startForegroundService(context, intent(context, ACTION_START))
    }

    /**
     * Ends the dictation in flight and transcribes it.
     *
     * Deliberately not gated on the phase: in push-to-talk mode a release can arrive before
     * the start it belongs to has been dispatched, and a stop that is dropped here is a
     * microphone that records until the cap runs out. The service decides what to do with it.
     */
    fun stop(context: Context) {
        context.startService(intent(context, ACTION_STOP))
    }

    /** Ends the dictation in flight and throws its audio away. See [stop] for the gating. */
    fun discard(context: Context) {
        context.startService(intent(context, ACTION_DISCARD))
    }

    fun toggle(context: Context) {
        when (_phase.value) {
            DictationPhase.Idle -> start(context)
            DictationPhase.Recording -> stop(context)
            // The microphone is already off and the words are on their way: there is
            // nothing for this tap to do, and starting a second dictation over the top
            // of the first is how a tap ends up looking like it was ignored.
            DictationPhase.Finishing -> Unit
        }
    }

    private fun intent(context: Context, action: String): Intent =
        Intent(context, RecordingService::class.java).setAction(action)

    /** Called only by [RecordingService] to publish its authoritative phase. */
    internal fun setPhase(value: DictationPhase) {
        _phase.value = value
    }
}
