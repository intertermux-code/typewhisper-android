package com.typewhisper.android.engines.cloud

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
 * Transcription via any OpenAI-compatible `/audio/transcriptions` endpoint.
 *
 * Defaults target Groq (`whisper-large-v3-turbo`: fastest/cheapest quality
 * transcription), but [baseUrl], [apiKey] and [model] are constructor-injected
 * so the same class serves OpenAI, Deepgram's OpenAI-compatible route, or a
 * self-hosted endpoint later without code changes.
 *
 * HTTP failures are mapped to [EngineException]; [message]s never include the
 * API key or audio bytes.
 */
public class GroqEnginePlugin(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val model: String = DEFAULT_MODEL,
    override val pluginId: String = "groq",
    override val displayName: String = "Groq",
    override val requiresApiKey: Boolean = true,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : EnginePlugin {

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://api.groq.com/openai/v1"
        public const val DEFAULT_MODEL: String = "whisper-large-v3-turbo"
        private const val TRANSCRIPTIONS_PATH = "/audio/transcriptions"

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // long audio needs a generous read window
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    override val supportsStreaming: Boolean = false

    override fun requiredSettings(): List<SettingKey> = listOf(
        SettingKey(key = "api_key", label = "API key", secret = true),
    )

    override suspend fun transcribe(
        audio: AudioData,
        language: String?,
        onPartial: ((String) -> Unit)?,
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        // A self-hosted engine has no address until the user types one; failing here keeps
        // that from reaching OkHttp as a malformed URL.
        if (baseUrl.isBlank()) throw EngineException.Authentication("Add the endpoint URL in settings")
        if (requiresApiKey && apiKey.isBlank()) throw EngineException.Authentication("API key is required")
        require(audio.samples.isNotEmpty()) { "Audio is empty" }

        val wav = WavWriter.encodeMono16(audio.samples, audio.sampleRateHz)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "audio.wav",
                wav.toRequestBody("audio/wav".toMediaType()),
            )
            .addFormDataPart("model", model)
            .apply {
                if (!language.isNullOrBlank() && language != "auto") {
                    addFormDataPart("language", language)
                }
            }
            .build()

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + TRANSCRIPTIONS_PATH)
            // Self-hosted gateways on a trusted network answer without a credential at all.
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
            .post(body)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw mapHttpError(response.code, payload)
                val text = try {
                    JSONObject(payload).optString("text", "")
                } catch (e: JSONException) {
                    throw EngineException.InvalidResponse("Unparseable transcription payload", e)
                }
                TranscriptionResult(
                    text = text,
                    detectedLanguage = language?.takeIf { it != "auto" },
                    engineId = pluginId,
                )
            }
        } catch (e: IOException) {
            // EngineException is not an IOException, so typed errors pass through untouched.
            throw EngineException.Network(e)
        }
    }

    private fun mapHttpError(statusCode: Int, payload: String): EngineException {
        val detail = extractErrorMessage(payload)
        return when (statusCode) {
            401, 403 -> EngineException.Authentication(
                "Rejected API key (HTTP $statusCode)${detail?.let { ": $it" }.orEmpty()}",
            )
            429 -> EngineException.RateLimited(
                "Rate limited (HTTP 429)${detail?.let { ": $it" }.orEmpty()}",
            )
            400, 422 -> EngineException.BadRequest(
                "Request rejected (HTTP $statusCode)${detail?.let { ": $it" }.orEmpty()}",
            )
            in 500..599 -> EngineException.ServerError(
                statusCode,
                "Engine unavailable (HTTP $statusCode)${detail?.let { ": $it" }.orEmpty()}",
            )
            else -> EngineException.InvalidResponse("Unexpected HTTP $statusCode")
        }
    }

    /** Best-effort extraction of OpenAI-style `{"error": {"message": ...}}`. */
    private fun extractErrorMessage(payload: String): String? = try {
        JSONObject(payload).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    } catch (e: JSONException) {
        null
    }
}
