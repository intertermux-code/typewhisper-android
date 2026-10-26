package com.typewhisper.android.capture

import kotlin.math.max

/**
 * Stateless linear resampler for mono float PCM.
 *
 * Used when the device cannot capture at [MicAudioSource.TARGET_SAMPLE_RATE_HZ]
 * and the pipeline must still honor the [com.typewhisper.android.engineapi.AudioData]
 * 16 kHz contract. Linear interpolation is adequate for speech at these rates;
 * it is not meant for music-quality conversion.
 */
public object LinearResampler {

    /**
     * Resamples [input] from [fromRateHz] to [toRateHz].
     *
     * @return a new array at the target rate; returns [input] itself when the
     *   rates already match or the input is empty.
     */
    public fun resample(
        input: FloatArray,
        fromRateHz: Int,
        toRateHz: Int,
    ): FloatArray {
        require(fromRateHz > 0) { "fromRateHz must be positive" }
        require(toRateHz > 0) { "toRateHz must be positive" }
        if (fromRateHz == toRateHz || input.isEmpty()) return input

        val ratio = fromRateHz.toDouble() / toRateHz
        val outSize = max(1, (input.size / ratio).toInt())
        return FloatArray(outSize) { i ->
            val pos = i * ratio
            val i0 = pos.toInt().coerceIn(0, input.size - 1)
            val i1 = (i0 + 1).coerceIn(0, input.size - 1)
            val frac = (pos - i0).toFloat()
            input[i0] * (1f - frac) + input[i1] * frac
        }
    }
}
