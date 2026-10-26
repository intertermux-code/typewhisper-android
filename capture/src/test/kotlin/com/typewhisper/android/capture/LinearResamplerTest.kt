package com.typewhisper.android.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LinearResamplerTest {

    @Test
    fun `same rate returns the input untouched`() {
        val input = floatArrayOf(1f, 2f, 3f)
        assertSame(input, LinearResampler.resample(input, 16_000, 16_000))
    }

    @Test
    fun `empty input returns empty at any rate`() {
        assertEquals(0, LinearResampler.resample(FloatArray(0), 48_000, 16_000).size)
    }

    @Test
    fun `downsampling by three shortens the buffer by three`() {
        val out = LinearResampler.resample(FloatArray(4_800) { it.toFloat() }, 48_000, 16_000)
        assertEquals(1_600, out.size)
        // Every third source sample lands on the 48k->16k grid with no interpolation.
        assertEquals(0f, out[0], 1e-6f)
        assertEquals(3f, out[1], 1e-6f)
        assertEquals(4_797f, out[1_599], 1e-6f)
    }

    @Test
    fun `downsampling skips every second sample on the grid`() {
        val out = LinearResampler.resample(floatArrayOf(0f, 1f, 2f, 3f), 2, 1)
        assertEquals(2, out.size)
        assertEquals(0f, out[0], 1e-6f)
        assertEquals(2f, out[1], 1e-6f)
    }

    @Test
    fun `interpolates between samples when the grid falls in between`() {
        val out = LinearResampler.resample(floatArrayOf(0f, 1f, 2f, 3f), 3, 2)
        assertEquals(2, out.size)
        // Grid points land at 0 and 1.5 samples of the source.
        assertEquals(0f, out[0], 1e-6f)
        assertEquals(1.5f, out[1], 1e-6f)
    }

    @Test
    fun `a single sample is held across upsampling`() {
        val out = LinearResampler.resample(floatArrayOf(0.5f), 8_000, 16_000)
        assertEquals(2, out.size)
        assertEquals(0.5f, out[0], 1e-6f)
        assertEquals(0.5f, out[1], 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a zero source rate`() {
        LinearResampler.resample(floatArrayOf(1f), 0, 16_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a zero target rate`() {
        LinearResampler.resample(floatArrayOf(1f), 16_000, 0)
    }
}
