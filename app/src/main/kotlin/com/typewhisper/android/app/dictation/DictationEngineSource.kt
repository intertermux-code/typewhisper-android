package com.typewhisper.android.app.dictation

import android.content.Context
import com.typewhisper.android.app.TypeWhisperApplication
import com.typewhisper.android.app.engine.EngineCatalog
import com.typewhisper.android.app.engine.OnDeviceModels
import com.typewhisper.android.engineapi.AudioData
import com.typewhisper.android.engineapi.EngineException
import com.typewhisper.android.engineapi.EnginePlugin
import com.typewhisper.android.engineapi.TranscriptionResult
import kotlinx.coroutines.flow.first

/** A transcript plus the one line the user should be told about how it was obtained. */
data class DictationOutcome(
    val result: TranscriptionResult,
    /** The language the request was made with, recorded in history alongside the text. */
    val language: String?,
    /** Non-null when the loop had to do something the user did not ask for. */
    val note: String? = null,
)

/**
 * Resolves which engine a dictation runs on, from the stored selection plus the
 * credentials and relay settings that engine needs.
 *
 * Building an engine reads the encrypted store, so every entry point here is a suspend
 * function and callers keep it off the main thread.
 */
object DictationEngineSource {

    /** Shown when a Mac dictation is served by the on-device engine instead. */
    const val MAC_FALLBACK_NOTE: String = "Mac didn't answer — transcribed on the phone instead"

    suspend fun engineId(context: Context): String =
        TypeWhisperApplication.container(context).preferences.engineId.first()

    suspend fun engine(context: Context, id: String): EnginePlugin =
        (EngineCatalog.byId(id) ?: EngineCatalog.default).factory(context)

    /**
     * Transcribes [audio] on the selected engine.
     *
     * When the on-device engine is selected and its model file is not on disk yet, the
     * model is fetched first and its progress reported through [onModelProgress] — the
     * user's first dictation is also the moment the recogniser installs itself, and a
     * silent thirty-megabyte stall would look like a hang.
     *
     * The Mac harness is the only engine with a fallback, because it is the only remote
     * engine the user is expected to lose contact with while the phone still has audio in
     * hand: the Mac sleeps, its server gets switched off, the relay dies. Retrying the same
     * samples locally turns "dictation failed" into a slightly different-quality transcript,
     * which is the whole point of pairing the two. Every other engine's failure is reported
     * as-is — silently rerouting a Groq request to another provider would be dishonest.
     */
    suspend fun transcribe(
        context: Context,
        audio: AudioData,
        onModelProgress: (Float) -> Unit = {},
    ): DictationOutcome {
        val container = TypeWhisperApplication.container(context)
        val language = container.preferences.language.first()
        val id = engineId(context)
        val engine = engine(context, id)
        return try {
            if (id == EngineCatalog.ID_WHISPERCPP) OnDeviceModels.ensureReady(context, onModelProgress)
            DictationOutcome(engine.transcribe(audio, language), language)
        } catch (e: EngineException) {
            if (id != EngineCatalog.ID_MAC || !e.isMacUnavailable()) throw e
            OnDeviceModels.ensureReady(context, onModelProgress)
            DictationOutcome(
                result = EngineCatalog.default.factory(context).transcribe(audio, language),
                language = language,
                note = MAC_FALLBACK_NOTE,
            )
        }
    }

    /**
     * The two Mac states that mean "the phone cannot get work done remotely right now":
     * nothing answering at the socket, and a relay that reached a TypeWhisper whose HTTP
     * server is off. A rejected token is deliberately not one of them — falling back would
     * hide a configuration mistake the user has to fix.
     */
    private fun EngineException.isMacUnavailable(): Boolean = when {
        this is EngineException.Network -> true
        this is EngineException.ServerError ->
            statusCode == 502 || message?.contains("not enabled", ignoreCase = true) == true
        else -> false
    }
}
