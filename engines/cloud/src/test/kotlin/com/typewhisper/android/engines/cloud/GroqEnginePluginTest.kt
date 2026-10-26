package com.typewhisper.android.engines.cloud

import com.typewhisper.android.engineapi.AudioData
import com.typewhisper.android.engineapi.EngineException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The cloud engines are covered against a fake endpoint rather than a live account:
 * what has to stay correct is the request shape and the mapping of HTTP outcomes onto
 * the typed [EngineException]s the UI reads.
 */
class GroqEnginePluginTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun engine(apiKey: String = "test-key", requiresApiKey: Boolean = true) = GroqEnginePlugin(
        apiKey = apiKey,
        baseUrl = server.url("/openai/v1").toString().trimEnd('/'),
        pluginId = "groq",
        displayName = "Groq",
        requiresApiKey = requiresApiKey,
        httpClient = OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS)
            .build(),
    )

    private val audio = AudioData(
        samples = FloatArray(1600) { it / 1600f },
        durationMs = 100L,
    )

    @Test
    fun selfHostedEngineWithoutAUrlIsRefusedBeforeTheNetwork() {
        val unconfigured = GroqEnginePlugin(
            apiKey = "",
            baseUrl = "",
            pluginId = "custom",
            displayName = "Custom endpoint",
            requiresApiKey = false,
        )

        val failure = assertThrows(EngineException.Authentication::class.java) {
            runBlocking { unconfigured.transcribe(audio, language = null) }
        }
        assertTrue(failure.message!!.contains("endpoint"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun sendsMultipartWavWithModelAndLanguage() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody("""{"text":"hello there"}"""))

        val result = engine().transcribe(audio, language = "de")

        assertEquals("hello there", result.text)
        assertEquals("groq", result.engineId)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/openai/v1/audio/transcriptions", request.path)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"file\""))
        assertTrue(body.contains("audio.wav"))
        assertTrue(body.contains("name=\"model\""))
        assertTrue(body.contains(GroqEnginePlugin.DEFAULT_MODEL))
        assertTrue(body.contains("name=\"language\""))
        assertTrue(body.contains("de"))
    }

    @Test
    fun omitsAutoLanguageAndReportsNothingDetected() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody("""{"text":"bonjour"}"""))

        val result = engine().transcribe(audio, language = "auto")

        assertEquals("bonjour", result.text)
        assertEquals(null, result.detectedLanguage)
        assertFalse(server.takeRequest().body.readUtf8().contains("name=\"language\""))
    }

    @Test
    fun rejectedKeyBecomesAuthenticationError() = runBlocking<Unit> {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"Invalid API key"}}"""),
        )

        val failure = assertThrows(EngineException.Authentication::class.java) {
            runBlocking { engine(apiKey = "bad").transcribe(audio, language = null) }
        }
        assertTrue(failure.message!!.contains("Invalid API key"))
    }

    @Test
    fun rateLimitKeepsRetryMessage() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"slow down"}}"""))

        assertThrows(EngineException.RateLimited::class.java) {
            runBlocking { engine().transcribe(audio, language = null) }
        }
    }

    @Test
    fun serverFailureIsTypedWithStatusCode() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))

        val failure = assertThrows(EngineException.ServerError::class.java) {
            runBlocking { engine().transcribe(audio, language = null) }
        }
        assertEquals(503, failure.statusCode)
    }

    @Test
    fun unparseablePayloadIsInvalidResponse() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody("<html>not json</html>"))

        assertThrows(EngineException.InvalidResponse::class.java) {
            runBlocking { engine().transcribe(audio, language = null) }
        }
    }

    @Test
    fun missingKeyIsRefusedBeforeAnyNetworkCall() {
        val failure = assertThrows(EngineException.Authentication::class.java) {
            runBlocking { engine(apiKey = "").transcribe(audio, language = null) }
        }
        assertTrue(failure.message!!.contains("API key"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun keylessEndpointIsAllowedForSelfHostedGateways() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody("""{"text":"from the gateway"}"""))

        val result = engine(apiKey = "", requiresApiKey = false).transcribe(audio, language = null)

        assertEquals("from the gateway", result.text)
        val request = server.takeRequest()
        assertEquals(null, request.getHeader("Authorization"))
    }

    @Test
    fun transportFailureBecomesNetworkError() = runBlocking<Unit> {
        server.shutdown()

        assertThrows(EngineException.Network::class.java) {
            runBlocking { engine().transcribe(audio, language = null) }
        }
    }
}
