package com.typewhisper.android.engineapi

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encodes mono float PCM as 16-bit WAV. Every remote engine — the cloud endpoints and
 * the Mac harness — accepts WAV, so the wire format lives beside [AudioData] and is
 * implemented once rather than per engine module.
 */
public object WavWriter {
    private const val HEADER_BYTES = 44

    /**
     * @param samples mono samples in [-1, 1].
     * @param sampleRateHz e.g. 16_000.
     * @return complete WAV file bytes (44-byte header + PCM16 data).
     */
    public fun encodeMono16(samples: FloatArray, sampleRateHz: Int): ByteArray {
        val dataBytes = samples.size * 2
        val out = ByteBuffer.allocate(HEADER_BYTES + dataBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray())
        out.putInt(36 + dataBytes)
        out.put("WAVE".toByteArray())
        out.put("fmt ".toByteArray())
        out.putInt(16) // PCM format chunk size
        out.putShort(1) // PCM
        out.putShort(1) // mono
        out.putInt(sampleRateHz)
        out.putInt(sampleRateHz * 2) // byte rate: 1ch * 16bit / 8
        out.putShort(2) // block align
        out.putShort(16) // bits per sample
        out.put("data".toByteArray())
        out.putInt(dataBytes)
        for (sample in samples) {
            val pcm = (sample.coerceIn(-1f, 1f) * 32_767f).toInt()
                .coerceIn(-32_768, 32_767)
            out.putShort(pcm.toShort())
        }
        return out.array()
    }
}
