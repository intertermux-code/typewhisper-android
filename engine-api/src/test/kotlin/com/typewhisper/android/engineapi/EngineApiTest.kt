package com.typewhisper.android.engineapi

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AudioData contract is the seam everything downstream relies on, so the
 * value semantics are pinned here rather than left to `data class` assumptions.
 */
class AudioDataTest {

    @Test
    fun `equal payloads compare equal despite distinct arrays`() {
        val a = AudioData(floatArrayOf(0f, 0.5f, -0.5f), durationMs = 120)
        val b = AudioData(floatArrayOf(0f, 0.5f, -0.5f), durationMs = 120)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `different samples rate or duration are unequal`() {
        val base = AudioData(floatArrayOf(0f, 1f), durationMs = 100)
        assertNotEquals(base, AudioData(floatArrayOf(0f, 0.5f), durationMs = 100))
        assertNotEquals(base, AudioData(floatArrayOf(0f, 1f), sampleRateHz = 48_000, durationMs = 100))
        assertNotEquals(base, AudioData(floatArrayOf(0f, 1f), durationMs = 101))
    }

    @Test
    fun `sample rate defaults to the sixteen kilohertz contract`() {
        assertEquals(16_000, AudioData(FloatArray(1), durationMs = 0).sampleRateHz)
    }

    @Test
    fun `results carry segments language and engine id`() {
        val result = TranscriptionResult(text = "hello", engineId = "whispercpp")
        assertEquals(emptyList<TranscriptSegment>(), result.segments)
        assertNull(result.detectedLanguage)
        assertEquals("whispercpp", result.engineId)

        val withSegments = result.copy(
            segments = listOf(TranscriptSegment("hello", 0, 480), TranscriptSegment("world", 480, 900)),
            detectedLanguage = "en",
        )
        assertEquals(2, withSegments.segments.size)
        assertEquals(480L, withSegments.segments[1].startMs)
    }
}

/** Engines are consumed through the interface only; defaults must not surprise callers. */
class EngineContractTest {

    private val audio = AudioData(floatArrayOf(0f, 0.1f), durationMs = 40)

    private inner class FakeEngine(
        override val pluginId: String = "fake",
        override val displayName: String = "Fake",
        override val supportsStreaming: Boolean = false,
        override val requiresApiKey: Boolean = false,
    ) : EnginePlugin {
        override suspend fun transcribe(
            audio: AudioData,
            language: String?,
            onPartial: ((String) -> Unit)?,
        ): TranscriptionResult = TranscriptionResult(text = "ok", engineId = pluginId)
    }

    @Test
    fun `an engine with no declared settings reports none`() {
        assertTrue(FakeEngine().requiredSettings().isEmpty())
    }

    @Test
    fun `settings keys default to non secret`() {
        val key = SettingKey(key = "model", label = "Model")
        assertFalse(key.secret)
        assertTrue(SettingKey("api_key", "API key", secret = true).secret)
    }

    @Test
    fun `transcribe works through the interface without a partial callback`() {
        val result = runBlocking { FakeEngine().transcribe(audio, "en") }
        assertEquals("ok", result.text)
        assertEquals("fake", result.engineId)
    }
}

/** Error payloads are shown to users, so they must stay typed and credential-free. */
class EngineExceptionTest {

    @Test
    fun `each failure mode keeps its type and message`() {
        val errors: List<EngineException> = listOf(
            EngineException.Authentication("Rejected API key (HTTP 401)"),
            EngineException.RateLimited("Rate limited (HTTP 429)", retryAfterMs = 5_000),
            EngineException.BadRequest("Request rejected (HTTP 400)"),
            EngineException.ServerError(503, "Engine unavailable (HTTP 503)"),
            EngineException.InvalidResponse("Unparseable transcription payload"),
        )
        assertEquals(5, errors.size)
        assertEquals(EngineException::class.java, errors.first().javaClass.superclass)
        assertEquals(5_000L, (errors[1] as EngineException.RateLimited).retryAfterMs!!)
        assertEquals(503, (errors[3] as EngineException.ServerError).statusCode)
        errors.forEach { assertTrue(it.message!!.isNotEmpty()) }
    }

    @Test
    fun `network failures wrap the transport cause`() {
        val cause = java.io.IOException("connection reset")
        val error = EngineException.Network(cause)
        assertEquals(cause, error.cause)
        assertEquals("Network error: connection reset", error.message)
    }
}
