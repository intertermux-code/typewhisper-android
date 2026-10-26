package com.typewhisper.android.engineapi

/**
 * A user-facing setting an engine needs before it can transcribe
 * (e.g. an API key). Rendered by settings UI; secrets must never be logged.
 *
 * @property key stable storage key, namespaced per engine by the host.
 * @property label human-readable label shown in settings.
 * @property secret true for credentials — mask in UI, store in encrypted storage.
 */
public data class SettingKey(
    val key: String,
    val label: String,
    val secret: Boolean = false,
)

/**
 * Contract every transcription engine implements. Mirrors the shape of
 * TypeWhisper (macOS) `TranscriptionEnginePlugin`: engines are identified,
 * declare capabilities, and transcribe [AudioData].
 *
 * Implementations must be safe to call from any thread; [transcribe] is a
 * suspend function and may block on I/O.
 */
public interface EnginePlugin {
    /** Stable id, e.g. "groq", "openai", "deepgram", "soniox", "whispercpp". */
    val pluginId: String

    /** Human-readable name shown in the engine picker. */
    val displayName: String

    /** True when the engine can deliver incremental results via [onPartial]. */
    val supportsStreaming: Boolean

    /** True when the engine needs a user-supplied credential. */
    val requiresApiKey: Boolean

    /**
     * Transcribes [audio] and returns the result.
     *
     * @param audio 16 kHz mono PCM (see [AudioData]).
     * @param language BCP-47 tag, or null/"auto" for auto-detection.
     * @param onPartial invoked with incremental text only when
     *   [supportsStreaming] is true; may be null.
     * @throws EngineException on any engine-side failure.
     */
    public suspend fun transcribe(
        audio: AudioData,
        language: String?,
        onPartial: ((String) -> Unit)? = null,
    ): TranscriptionResult

    /** Settings the host must collect before this engine can run. */
    public fun requiredSettings(): List<SettingKey> = emptyList()
}
