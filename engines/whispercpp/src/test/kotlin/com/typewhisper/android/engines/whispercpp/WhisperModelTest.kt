package com.typewhisper.android.engines.whispercpp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperModelTest {

    @Test
    fun defaultIsTiny() {
        assertSame(WhisperModel.TINY_Q5_1, WhisperModel.DEFAULT)
    }

    @Test
    fun fromIdResolvesKnownAndUnknown() {
        assertSame(WhisperModel.BASE_Q5_1, WhisperModel.fromId("base-q5_1"))
        assertSame(WhisperModel.TINY_Q5_1, WhisperModel.fromId("tiny-q5_1"))
        assertSame("unknown id falls back to default", WhisperModel.DEFAULT, WhisperModel.fromId("nope"))
        assertSame(WhisperModel.DEFAULT, WhisperModel.fromId(null))
    }

    @Test
    fun urlsAreAbsoluteHfResolveLinks() {
        for (model in WhisperModel.entries) {
            assertTrue(model.url, model.url.startsWith("https://huggingface.co/"))
            assertTrue(model.fileName, model.fileName.endsWith(".bin"))
            assertTrue(model.sizeBytes > 0)
        }
    }
}
