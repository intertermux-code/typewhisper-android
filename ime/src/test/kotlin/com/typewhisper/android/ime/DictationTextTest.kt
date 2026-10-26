package com.typewhisper.android.ime

import com.typewhisper.android.core.NormalizationSettings
import com.typewhisper.android.core.PostProcessing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictationTextTest {

    private class MapSettings(
        private val integers: Map<String, Int> = emptyMap(),
        private val booleans: Map<String, Boolean> = emptyMap(),
    ) : NormalizationSettings {
        override fun integer(key: String): Int? = integers[key]
        override fun boolean(key: String): Boolean? = booleans[key]
    }

    @Test
    fun autoAndBlankLanguagesLeaveDetectionToTheEngine() {
        assertNull(DictationText.preferredLanguage("auto"))
        assertNull(DictationText.preferredLanguage(""))
        assertNull(DictationText.preferredLanguage(null))
        assertEquals("en-US", DictationText.preferredLanguage("en-US"))
    }

    @Test
    fun storedNormalizationOffKeepsSpokenNumbers() {
        val settings = MapSettings(
            booleans = mapOf(NormalizationSettings.NUMBER_NORMALIZATION_ENABLED to false),
        )
        assertEquals(
            "I have twenty three questions",
            DictationText.finalize("I have twenty three questions", "en", settings, emptyList(), emptyList()),
        )
    }

    @Test
    fun storedThresholdDecidesWhichNumbersBecomeDigits() {
        val low = MapSettings(
            booleans = mapOf(NormalizationSettings.NUMBER_NORMALIZATION_ENABLED to true),
            integers = mapOf(NormalizationSettings.NUMBER_NORMALIZATION_MINIMUM_VALUE to 10),
        )
        assertEquals(
            "I have 23 questions",
            DictationText.finalize("I have twenty three questions", "en", low, emptyList(), emptyList()),
        )

        val high = MapSettings(
            booleans = mapOf(NormalizationSettings.NUMBER_NORMALIZATION_ENABLED to true),
            integers = mapOf(NormalizationSettings.NUMBER_NORMALIZATION_MINIMUM_VALUE to 100),
        )
        assertEquals(
            "I have twenty three questions",
            DictationText.finalize("I have twenty three questions", "en", high, emptyList(), emptyList()),
        )
    }

    @Test
    fun dictionaryRunsBeforeSnippetsAndReplacementTextIsNotRescanned() {
        val out = DictationText.finalize(
            raw = "see the cat",
            language = "en",
            settings = NormalizationSettings.EMPTY,
            dictionary = listOf(PostProcessing.DictionaryEntry("cat", "dog")),
            snippets = listOf(PostProcessing.Snippet("see", "look at the cat")),
        )
        assertEquals("look at the cat the dog", out)
    }
}
