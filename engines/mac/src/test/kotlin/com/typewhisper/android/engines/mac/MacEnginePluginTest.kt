package com.typewhisper.android.engines.mac

import com.typewhisper.android.engineapi.AudioData
import com.typewhisper.android.engineapi.EngineException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class MacEnginePluginTest {

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

    private fun engine(token: String = "mac-token") = MacEnginePlugin(
        baseUrl = server.url("/").toString(),
        token = token,
        engine = "whispercpp",
        model = "base",
        httpClient = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build(),
    )

    private val audio = AudioData(samples = FloatArray(1600) { it / 1600f }, durationMs = 100L)

    @Test
    fun postsWavToTheTranscribeEndpointWithTheToken() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody("""{"text":"dictated on the mac","language":"de"}"""))

        val result = engine().transcribe(audio, language = "de")

        assertEquals("dictated on the mac", result.text)
        assertEquals("de", result.detectedLanguage)
        assertEquals("mac", result.engineId)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/transcribe", request.path)
        assertEquals("Bearer mac-token", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"file\""))
        assertTrue(body.contains("name=\"engine\""))
        assertTrue(body.contains("whispercpp"))
        assertTrue(body.contains("name=\"model\""))
        assertTrue(body.contains("name=\"language\""))
    }

    @Test
    fun missingTokenIsRefusedWithoutTouchingTheNetwork() {
        val failure = assertThrows(EngineException.Authentication::class.java) {
            runBlocking { engine(token = "").transcribe(audio, language = null) }
        }
        assertTrue(failure.message!!.contains("token"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun relayWithoutAnAddressIsRefusedBeforeTheNetwork() {
        val unconfigured = MacEnginePlugin(
            baseUrl = "",
            token = "mac-token",
            engine = "whispercpp",
            model = "base",
            httpClient = OkHttpClient(),
        )

        val failure = assertThrows(EngineException.Authentication::class.java) {
            runBlocking { unconfigured.transcribe(audio, language = null) }
        }
        assertTrue(failure.message!!.contains("address"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun statusWithoutAnAddressReportsUnreachable() = runBlocking<Unit> {
        val status = MacEnginePlugin(
            baseUrl = "",
            token = "mac-token",
            httpClient = OkHttpClient(),
        ).status()

        assertTrue(status is MacEnginePlugin.MacStatus.Unreachable)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun rejectedTokenIsAuthentication() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad token"}}"""))

        val failure = assertThrows(EngineException.Authentication::class.java) {
            runBlocking { engine().transcribe(audio, language = null) }
        }
        assertTrue(failure.message!!.contains("bad token"))
    }

    @Test
    fun relayBadGatewaySaysTheServerIsNotEnabled() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("TypeWhisper's HTTP server is not enabled"))

        val failure = assertThrows(EngineException.ServerError::class.java) {
            runBlocking { engine().transcribe(audio, language = null) }
        }
        assertTrue(failure.message!!.contains("not enabled"))
    }

    @Test
    fun unansweredSocketIsUnreachableMessage() = runBlocking<Unit> {
        server.shutdown()

        val failure = assertThrows(EngineException.Network::class.java) {
            runBlocking { engine().transcribe(audio, language = null) }
        }
        assertTrue(failure.message!!.contains("Mac unreachable"))
    }

    @Test
    fun responseWithoutTextIsInvalid() {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))

        assertThrows(EngineException.InvalidResponse::class.java) {
            runBlocking { engine().transcribe(audio, language = null) }
        }
    }

    @Test
    fun statusReportsReachableServer() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody("""{"message":"TypeWhisper API ready"}"""))

        val status = engine().status()

        assertTrue(status is MacEnginePlugin.MacStatus.Reachable)
        assertEquals("/v1/status", server.takeRequest().path)
    }

    @Test
    fun statusDistinguishesServerOffFromMacGone() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(502).setBody("server not enabled"))
        assertTrue(engine().status() is MacEnginePlugin.MacStatus.ServerNotEnabled)

        server.shutdown()
        val gone = engine().status()
        assertTrue(gone is MacEnginePlugin.MacStatus.Unreachable)
        assertTrue((gone as MacEnginePlugin.MacStatus.Unreachable).detail.contains("Mac unreachable"))
    }

    @Test
    fun translateSendsTargetLanguageAlongsideTheSource() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody("""{"text":"translated words","language":"en"}"""))

        val result = engine().translate(audio, targetLanguage = "en", sourceLanguage = "ja")

        assertEquals("translated words", result.text)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("name=\"target_language\""))
        assertTrue(body.contains("en"))
        assertTrue(body.contains("name=\"language\""))
        assertTrue(body.contains("ja"))
    }
}
