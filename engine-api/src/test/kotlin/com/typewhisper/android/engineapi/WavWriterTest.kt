package com.typewhisper.android.engineapi

import org.junit.Assert.assertEquals
import org.junit.Test

class WavWriterTest {

    @Test
    fun headerDescribesMonoPcm16() {
        val wav = WavWriter.encodeMono16(FloatArray(2) { 0f }, sampleRateHz = 16_000)

        assertEquals(48, wav.size)
        assertEquals("RIFF", String(wav.copyOfRange(0, 4)))
        // Chunk size after the "RIFF" id and its own size field: 36 + data bytes.
        assertEquals(40, littleEndianInt(wav, 4))
        assertEquals("WAVE", String(wav.copyOfRange(8, 12)))
        assertEquals("data", String(wav.copyOfRange(36, 40)))
        assertEquals(4, littleEndianInt(wav, 40))
        assertEquals(16_000, littleEndianInt(wav, 24))
    }

    @Test
    fun samplesSaturateInsteadOfWrapping() {
        val wav = WavWriter.encodeMono16(floatArrayOf(2f, -2f), sampleRateHz = 16_000)

        assertEquals(32_767, littleEndianShort(wav, 44))
        assertEquals(-32_767, littleEndianShort(wav, 46))
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun littleEndianShort(bytes: ByteArray, offset: Int): Int {
        val low = bytes[offset].toInt() and 0xff
        val high = bytes[offset + 1].toInt()
        return (high shl 8) or low
    }
}
