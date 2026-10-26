package com.typewhisper.android.engineapi

/**
 * The single architectural seam of the whole app: everything downstream of
 * "we have mono 16 kHz PCM" is engine-agnostic.
 *
 * Mirrors TypeWhisper (macOS) `AudioData`: 16 kHz mono Float32 samples.
 * Engines must assume [sampleRateHz] is 16_000 unless they explicitly
 * advertise otherwise; [RecordingSession][com.typewhisper.android.capture.RecordingSession]
 * guarantees the contract.
 *
 * @property samples mono PCM samples normalized to [-1, 1].
 * @property sampleRateHz sample rate in Hz; always 16_000 in the M1 pipeline.
 * @property durationMs captured audio duration in milliseconds.
 */
public data class AudioData(
    val samples: FloatArray,
    val sampleRateHz: Int = 16_000,
    val durationMs: Long,
) {
    override fun equals(other: Any?): Boolean =
        other is AudioData &&
            sampleRateHz == other.sampleRateHz &&
            durationMs == other.durationMs &&
            samples.contentEquals(other.samples)

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRateHz
        result = 31 * result + durationMs.hashCode()
        return result
    }
}
