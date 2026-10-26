package com.typewhisper.android.engines.whispercpp

import com.typewhisper.android.engineapi.AudioData
import com.typewhisper.android.engineapi.EngineException
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only the input-validation and model-lookup branches run on the JVM: they throw
 * before any native call, so the .so is never loaded. The actual transcription
 * path needs a device and is covered by the instrumented/manual tests.
 */
class WhisperCppEnginePluginTest {

    private fun plugin(dir: File) = WhisperCppEnginePlugin(modelsDir = dir, modelId = "tiny-q5_1")

    private fun audio(samples: FloatArray, rate: Int = 16_000) =
        AudioData(samples = samples, sampleRateHz = rate, durationMs = 1000L)

    @Test
    fun contractFlags() {
        val p = plugin(File("") /* unused for these flags */)
        assertEquals("whispercpp", p.pluginId)
        assertTrue(!p.requiresApiKey)
        assertTrue(!p.supportsStreaming)
        assertEquals(listOf(WhisperCppEnginePlugin.SETTING_MODEL), p.requiredSettings().map { it.key })
    }

    @Test
    fun emptyAudioIsRejected() {
        val e = assertThrows(EngineException.BadRequest::class.java) {
            runBlocking { plugin(File(System.getProperty("java.io.tmpdir"))).transcribe(audio(FloatArray(0)), "en") }
        }
        assertTrue(e.message!!.contains("no audio"))
    }

    @Test
    fun wrongSampleRateIsRejected() {
        val e = assertThrows(EngineException.BadRequest::class.java) {
            runBlocking {
                plugin(File(System.getProperty("java.io.tmpdir")))
                    .transcribe(audio(FloatArray(16) { 0f }, rate = 48_000), "en")
            }
        }
        assertTrue(e.message!!.contains("48000"))
    }

    @Test
    fun missingModelSurfacesTypedError() {
        val emptyDir = File(System.getProperty("java.io.tmpdir"), "tw-missing-${System.nanoTime()}")
        emptyDir.mkdirs()
        val e = assertThrows(EngineException.InvalidResponse::class.java) {
            runBlocking { plugin(emptyDir).transcribe(audio(FloatArray(16) { 0.1f }), "en") }
        }
        assertTrue(e.message!!.contains("not downloaded"))
        emptyDir.deleteRecursively()
    }
}
