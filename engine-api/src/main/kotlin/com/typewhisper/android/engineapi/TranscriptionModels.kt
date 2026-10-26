package com.typewhisper.android.engineapi

/**
 * One time-bounded piece of a transcription. Engines that only return plain
 * text leave [segments] empty.
 */
public data class TranscriptSegment(
    /** Transcribed text of this segment. */
    val text: String,
    /** Segment start offset in milliseconds. */
    val startMs: Long,
    /** Segment end offset in milliseconds. */
    val endMs: Long,
)

/**
 * The result of [EnginePlugin.transcribe].
 *
 * @property text the full transcript.
 * @property segments optional time-aligned segments.
 * @property detectedLanguage BCP-47 tag reported by the engine, or null.
 * @property engineId the [EnginePlugin.pluginId] that produced this result.
 */
public data class TranscriptionResult(
    val text: String,
    val segments: List<TranscriptSegment> = emptyList(),
    val detectedLanguage: String? = null,
    val engineId: String,
)
