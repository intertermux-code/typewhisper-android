package com.typewhisper.android.core

/** Where a transcript is headed; translation output is always normalized as English. */
public enum class TranscriptionTask { Transcribe, Translate }

/** Ported model: one time-bounded piece of a transcript, as the macOS result shape carries it. */
public data class TranscriptionSegment(
    val text: String,
    val start: Double,
    val end: Double,
    val speakerLabel: String? = null,
    val speakerConfidence: Double? = null,
)

/** Ported model: mirrors the macOS `TranscriptionResult` that normalization wraps. */
public data class TranscriptionResult(
    val text: String,
    val detectedLanguage: String?,
    val duration: Double,
    val processingTime: Double,
    val engineUsed: String,
    val segments: List<TranscriptionSegment>,
)

/**
 * The user defaults the normalizer reads. Android's equivalent lives in DataStore
 * behind :storage; :core stays JVM-only, so it asks this interface instead.
 */
public interface NormalizationSettings {

    /** Null when unset or not an int, matching `UserDefaults.object(forKey:) as? Int`. */
    public fun integer(key: String): Int?

    /** Null when the key was never written. */
    public fun boolean(key: String): Boolean?

    public companion object {
        public const val NUMBER_NORMALIZATION_ENABLED: String = "transcriptionNumberNormalizationEnabled"
        public const val NUMBER_NORMALIZATION_MINIMUM_VALUE: String = "transcriptionNumberNormalizationMinimumValue"

        /** Nothing configured: enabled with the default threshold. */
        public val EMPTY: NormalizationSettings = object : NormalizationSettings {
            override fun integer(key: String): Int? = null
            override fun boolean(key: String): Boolean? = null
        }
    }
}
