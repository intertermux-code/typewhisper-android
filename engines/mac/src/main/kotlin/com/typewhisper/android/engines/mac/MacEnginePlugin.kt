package com.typewhisper.android.engines.mac

import com.typewhisper.android.engineapi.AudioData
import com.typewhisper.android.engineapi.EngineException
import com.typewhisper.android.engineapi.EnginePlugin
import com.typewhisper.android.engineapi.SettingKey
import com.typewhisper.android.engineapi.TranscriptionResult
import com.typewhisper.android.engineapi.WavWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Transcription on the user's Mac, reached through the relay that forwards to
 * TypeWhisper's localhost server. The phone talks to
 * `POST http://<relay>:<port>/v1/transcribe` with the recorded audio as a `file`
 * part and the Mac's API token as a Bearer credential; whatever engine the Mac has
 * configured — including one that needs a key the phone will never see — does the work.
 *
 * Every failure leaves a one-line message for the user rather than a stack trace, and
 * the two states that matter are told apart: nothing answering at the socket
 * ([MacStatus.Unreachable]) versus a Mac that is answering but whose HTTP server is
 * switched off ([MacStatus.ServerNotEnabled]).
 */
public class MacEnginePlugin(
    private val baseUrl: String,
    private val token: String,
    private val engine: String? = null,
    private val model: String? = null,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : EnginePlugin {

    public companion object {
        public const val PLUGIN_ID: String = "mac"
        public const val DEFAULT_RELAY_PORT: Int = 18978
        public const val TRANSCRIBE_PATH: String = "/v1/transcribe"
        public const val STATUS_PATH: String = "/v1/status"

        /** The relay can sit across the network, so a slow hop is normal but a dead one is not. */
        private const val TIMEOUT_SECONDS = 15L

        public fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    override val pluginId: String = PLUGIN_ID
    override val displayName: String = "TypeWhisper on Mac"
    override val supportsStreaming: Boolean = false
    override val requiresApiKey: Boolean = true

    override fun requiredSettings(): List<SettingKey> = listOf(
        SettingKey(key = "relay_url", label = "Mac relay address"),
        SettingKey(key = "api_key", label = "Mac API token", secret = true),
    )

    override suspend fun transcribe(
        audio: AudioData,
        language: String?,
        onPartial: ((String) -> Unit)?,
    ): TranscriptionResult = transcribeRequest(audio, language, targetLanguage = null)

    /**
     * The Mac's whisper-class engines can render the utterance in another language in the
     * same pass: `/v1/transcribe` accepts a `target_language` form field and answers with
     * the translation instead of the transcript. Only the Mac harness exposes this — the
     * phone never invents a translation endpoint the relay does not serve.
     */
    public suspend fun translate(
        audio: AudioData,
        targetLanguage: String,
        sourceLanguage: String? = null,
    ): TranscriptionResult = transcribeRequest(audio, sourceLanguage, targetLanguage)

    private suspend fun transcribeRequest(
        audio: AudioData,
        language: String?,
        targetLanguage: String?,
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) throw EngineException.Authentication(NEEDS_HOST_MESSAGE)
        if (token.isBlank()) throw EngineException.Authentication("Add the Mac API token in settings")
        require(audio.samples.isNotEmpty()) { "Audio is empty" }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "audio.wav",
                WavWriter.encodeMono16(audio.samples, audio.sampleRateHz).toRequestBody("audio/wav".toMediaType()),
            )
            .apply {
                engine?.takeIf { it.isNotBlank() }?.let { addFormDataPart("engine", it) }
                model?.takeIf { it.isNotBlank() }?.let { addFormDataPart("model", it) }
                if (!language.isNullOrBlank() && language != "auto") addFormDataPart("language", language)
                if (!targetLanguage.isNullOrBlank()) addFormDataPart("target_language", targetLanguage)
            }
            .build()

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + TRANSCRIBE_PATH)
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw mapHttpError(response.code, payload)
                val text = payload.fromJsonField("text")
                    ?: throw EngineException.InvalidResponse("The Mac replied without a transcript")
                TranscriptionResult(
                    text = text,
                    detectedLanguage = payload.fromJsonField("language") ?: language?.takeIf { it != "auto" },
                    engineId = pluginId,
                )
            }
        } catch (e: IOException) {
            // EngineException is not an IOException, so typed errors above pass through.
            throw EngineException.Network(e, UNREACHABLE_MESSAGE)
        }
    }

    /**
     * What the settings screen shows after "Test connection". Unlike [transcribe] this
     * never throws: an unreachable Mac is a state to display, not an error to catch.
     */
    public suspend fun status(): MacStatus = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext MacStatus.Unreachable(NEEDS_HOST_MESSAGE)
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + STATUS_PATH)
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .get()
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                when {
                    response.code == 502 -> MacStatus.ServerNotEnabled(extractErrorMessage(payload) ?: RELAY_502_MESSAGE)
                    response.isSuccessful -> MacStatus.Reachable(extractErrorMessage(payload) ?: payload.take(200))
                    else -> MacStatus.ServerNotEnabled("The Mac answered with HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            MacStatus.Unreachable(UNREACHABLE_MESSAGE)
        }
    }

    private fun mapHttpError(statusCode: Int, payload: String): EngineException {
        val detail = extractErrorMessage(payload)
        return when (statusCode) {
            401, 403 -> EngineException.Authentication(
                "The Mac rejected this token${detail?.let { ": $it" }.orEmpty()}",
            )
            // The relay's own answer when TypeWhisper's server is switched off.
            502 -> EngineException.ServerError(statusCode, detail ?: RELAY_502_MESSAGE)
            404 -> EngineException.InvalidResponse("This Mac does not serve /v1/transcribe — check the TypeWhisper version")
            400, 422 -> EngineException.BadRequest("The Mac rejected the audio${detail?.let { ": $it" }.orEmpty()}")
            in 500..599 -> EngineException.ServerError(statusCode, "The Mac failed to transcribe (HTTP $statusCode)")
            else -> EngineException.InvalidResponse("Unexpected HTTP $statusCode from the Mac")
        }
    }

    private fun String.fromJsonField(field: String): String? = try {
        JSONObject(this).optString(field).takeIf { it.isNotBlank() }
    } catch (e: JSONException) {
        null
    }

    /** Accepts both `{"error":{"message":...}}` and a plain-text body from the relay. */
    private fun extractErrorMessage(payload: String): String? {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return null
        val fromJson = try {
            val json = JSONObject(trimmed)
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: json.optString("message").takeIf { it.isNotBlank() }
        } catch (e: JSONException) {
            null
        }
        return fromJson ?: trimmed.take(200)
    }

    public sealed class MacStatus {
        /** Something answered and TypeWhisper's server is serving requests. */
        public data class Reachable(public val detail: String) : MacStatus()

        /** Reachable, but the HTTP server is off in TypeWhisper's settings. */
        public data class ServerNotEnabled(public val detail: String) : MacStatus()

        /** No socket at all: out of reach, asleep, or the relay is not running. */
        public data class Unreachable(public val detail: String) : MacStatus()
    }
}

public const val UNREACHABLE_MESSAGE: String =
    "Mac unreachable — check it is awake, reachable from this phone, and that the relay is running"
public const val NEEDS_HOST_MESSAGE: String =
    "Add the Mac relay address under Mac harness in settings"
public const val RELAY_502_MESSAGE: String =
    "The relay reached the Mac but TypeWhisper's HTTP server is not enabled — turn it on in TypeWhisper settings"
