package com.typewhisper.android.engines.whispercpp

import com.typewhisper.android.engineapi.AudioData
import com.typewhisper.android.engineapi.EngineException
import com.typewhisper.android.engineapi.EnginePlugin
import com.typewhisper.android.engineapi.SettingKey
import com.typewhisper.android.engineapi.TranscriptionResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/**
 * On-device speech recognition through whisper.cpp. This is the product's
 * default engine: no network, no API key, works in airplane mode once a model
 * has been downloaded.
 *
 * The native context is opened lazily and reused for the lifetime of the
 * instance; [release] must be called to free it. All native work is confined to
 * [Dispatchers.Default] so the calling coroutine thread is never blocked in the
 * ggml runtime.
 */
class WhisperCppEnginePlugin(
    /** Directory holding downloaded ggml model files (the app's files dir). */
    private val modelsDir: File,
    /** Model to use; see [WhisperModel.fromId]. */
    private val modelId: String = WhisperModel.DEFAULT.id,
    /** Inference threads; clamped to the core count. */
    requestedThreads: Int = Runtime.getRuntime().availableProcessors(),
) : EnginePlugin {

    override val pluginId: String = ID
    override val displayName: String = "On-device Whisper"
    override val supportsStreaming: Boolean = false
    override val requiresApiKey: Boolean = false

    private val threads: Int = requestedThreads.coerceIn(1, MAX_THREADS)

    @Volatile
    private var ctxPtr: Long = 0L
    private val ctxLock = Any()

    override fun requiredSettings(): List<SettingKey> = listOf(
        SettingKey(key = SETTING_MODEL, label = "Model", secret = false),
    )

    override suspend fun transcribe(
        audio: AudioData,
        language: String?,
        onPartial: ((String) -> Unit)?,
    ): TranscriptionResult {
        validateAudio(audio)

        val whisperLang = WhisperLanguages.toWhisperLanguage(language)
        val ctx = ensureContext()

        // A cancel handle per call lets an aborted transcription leave the
        // context intact for the next utterance; whisper polls abort_callback.
        val cancel = withContext(Dispatchers.Default) {
            WhisperNative.nativeCancelNew()
        }
        val unregister = currentCoroutineContext().job.invokeOnCompletion {
            // Fires on normal completion too; setting the flag is then a no-op.
            if (cancel != 0L) WhisperNative.nativeCancelRequest(cancel)
        }

        try {
            val raw = withContext(Dispatchers.Default) {
                WhisperNative.nativeTranscribe(ctx, audio.samples, threads, whisperLang, cancel)
            }
            val parsed = TranscriptCodec.parse(raw)
            if (parsed.aborted) {
                throw kotlinx.coroutines.CancellationException("transcription cancelled")
            }
            return TranscriptionResult(
                text = parsed.text,
                segments = parsed.segments,
                detectedLanguage = parsed.detectedLanguage,
                engineId = ID,
            )
        } finally {
            unregister.dispose()
            if (cancel != 0L) {
                withContext(Dispatchers.Default) { WhisperNative.nativeCancelFree(cancel) }
            }
        }
    }

    /** Frees the native whisper context. Idempotent; safe to call from any thread. */
    fun release() {
        synchronized(ctxLock) {
            if (ctxPtr != 0L) {
                WhisperNative.nativeRelease(ctxPtr)
                ctxPtr = 0L
            }
        }
    }

    private fun ensureContext(): Long = synchronized(ctxLock) {
        if (ctxPtr != 0L) return ctxPtr
        val model = WhisperModel.fromId(modelId)
        val file = File(modelsDir, model.fileName)
        if (!file.isFile) {
            throw EngineException.InvalidResponse(
                "Whisper model '${model.id}' is not downloaded (expected ${file.absolutePath})",
            )
        }
        val ptr = WhisperNative.nativeInit(file.absolutePath)
        if (ptr == 0L) {
            throw EngineException.InvalidResponse(
                "whisper.cpp failed to load model '${model.id}'",
            )
        }
        ctxPtr = ptr
        ptr
    }

    private fun validateAudio(audio: AudioData) {
        if (audio.samples.isEmpty()) {
            throw EngineException.BadRequest("no audio samples to transcribe")
        }
        if (audio.sampleRateHz != SAMPLE_RATE_HZ) {
            throw EngineException.BadRequest(
                "whisper.cpp expects ${SAMPLE_RATE_HZ}Hz audio, got ${audio.sampleRateHz}Hz",
            )
        }
        // whisper_full chunks internally, but a runaway buffer from a stuck
        // recorder would still exhaust memory; reject absurd lengths up front.
        if (audio.samples.size > MAX_SAMPLES) {
            throw EngineException.BadRequest(
                "audio is longer than the ${MAX_DURATION_SECONDS}s limit for on-device transcription",
            )
        }
    }

    companion object {
        const val ID = "whispercpp"
        const val SETTING_MODEL = "whispercpp_model"
        const val SAMPLE_RATE_HZ = 16_000

        private const val MAX_THREADS = 8
        private const val MAX_DURATION_SECONDS = 3600L
        private const val MAX_SAMPLES = (SAMPLE_RATE_HZ * MAX_DURATION_SECONDS).toInt()
    }
}
