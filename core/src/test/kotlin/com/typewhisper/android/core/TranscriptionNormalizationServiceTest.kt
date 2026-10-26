package com.typewhisper.android.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Expectations transliterated from the macOS `TranscriptionNormalizationServiceTests`.
 * The UserDefaults the Swift service reads become a [MapBackedSettings] here, which is
 * the same three keys the Android DataStore will hold.
 */
class TranscriptionNormalizationServiceTest {

    private class MapBackedSettings(
        private val integers: Map<String, Int> = emptyMap(),
        private val booleans: Map<String, Boolean> = emptyMap(),
    ) : NormalizationSettings {
        override fun integer(key: String): Int? = integers[key]
        override fun boolean(key: String): Boolean? = booleans[key]
    }

    private fun settings(enabled: Boolean? = null, minimumValue: Int? = null): MapBackedSettings =
        MapBackedSettings(
            integers = listOfNotNull(minimumValue?.let { NormalizationSettings.NUMBER_NORMALIZATION_MINIMUM_VALUE to it }).toMap(),
            booleans = listOfNotNull(enabled?.let { NormalizationSettings.NUMBER_NORMALIZATION_ENABLED to it }).toMap(),
        )

    @Test
    fun defaultOnPreservesSmallNumbersBeforePostProcessing() {
        assertEquals(
            "I have two questions",
            TranscriptionNormalizationService.normalizeText("I have two questions", "en"),
        )
    }

    @Test
    fun dutchLocalePreservesSmallNumbersBeforePostProcessing() {
        assertEquals(
            "ik heb twee vragen",
            TranscriptionNormalizationService.normalizeText("ik heb twee vragen", "nl-NL"),
        )
    }

    @Test
    fun globalOffSkipsNormalization() {
        val result = TranscriptionNormalizationService.normalizeText(
            "I have twenty three questions",
            "en",
            settings = settings(enabled = false),
        )
        assertEquals("I have twenty three questions", result)
    }

    @Test
    fun workflowOverrideOffWinsOverGlobalOn() {
        val result = TranscriptionNormalizationService.normalizeText(
            "I have twenty three questions",
            "en",
            normalizeNumbers = false,
            settings = settings(enabled = true),
        )
        assertEquals("I have twenty three questions", result)
    }

    @Test
    fun laterLanguageCandidateNormalizesWhenConfiguredLanguageDoesNotMatch() {
        val result = TranscriptionNormalizationService.normalizeText(
            "Set the value to twenty three",
            "de",
            languageCandidates = listOf("de", "en"),
        )
        assertEquals("Set the value to 23", result)
    }

    @Test
    fun normalizeResultUsesLaterConfiguredLanguageCandidate() {
        val result = TranscriptionNormalizationService.normalizeResult(
            text = "Set the value to twenty three",
            detectedLanguage = null,
            configuredLanguage = "de",
            configuredLanguageCandidates = listOf("de", "en"),
            duration = 1.0,
            processingTime = 0.1,
            engineUsed = "test",
            segments = listOf(TranscriptionSegment("twenty three", 0.0, 1.0)),
            task = TranscriptionTask.Transcribe,
        )
        assertEquals("Set the value to 23", result.text)
        assertEquals("23", result.segments.first().text)
    }

    @Test
    fun minimumValueDefaultsAndInvalidSettings() {
        assertEquals(10, TranscriptionNormalizationService.numberNormalizationMinimumValue())
        for (value in listOf(0, 10, 100)) {
            assertEquals(value, TranscriptionNormalizationService.numberNormalizationMinimumValue(settings(minimumValue = value)))
        }
        for (value in listOf(-1, 3, 999)) {
            assertEquals(10, TranscriptionNormalizationService.numberNormalizationMinimumValue(settings(minimumValue = value)))
        }
    }

    @Test
    fun alwaysRestoresSmallNumberNormalization() {
        assertEquals(
            "1 of my clients",
            TranscriptionNormalizationService.normalizeText(
                "one of my clients",
                "en",
                settings = settings(minimumValue = 0),
            ),
        )
    }

    @Test
    fun workflowOverrideOnUsesGlobalThresholdEvenWhenGlobalNormalizationIsOff() {
        val stored = settings(enabled = false, minimumValue = 100)
        val text = "three, ten, one hundred"
        assertEquals(text, TranscriptionNormalizationService.normalizeText(text, "en", settings = stored))
        assertEquals(
            "three, ten, 100",
            TranscriptionNormalizationService.normalizeText(text, "en", normalizeNumbers = true, settings = stored),
        )
    }

    @Test
    fun thresholdAppliesConsistentlyToTextAndSegments() {
        val result = TranscriptionNormalizationService.normalizeResult(
            text = "Three, twenty-three, one hundred",
            detectedLanguage = "en",
            configuredLanguage = "en",
            duration = 3.0,
            processingTime = 0.1,
            engineUsed = "test",
            segments = listOf(
                TranscriptionSegment("Three, twenty-three", 0.0, 1.0),
                TranscriptionSegment("one hundred", 1.0, 3.0),
            ),
            task = TranscriptionTask.Transcribe,
            settings = settings(minimumValue = 100),
        )
        assertEquals("Three, twenty-three, 100", result.text)
        assertEquals(listOf("Three, twenty-three", "100"), result.segments.map { it.text })
        assertEquals(listOf(0.0, 1.0), result.segments.map { it.start })
        assertEquals(listOf(1.0, 3.0), result.segments.map { it.end })
    }

    // German time notation (DIN 5008).

    @Test
    fun germanTimeNotationReplacesPeriodWithColon() {
        assertEquals(
            "Wir treffen uns um 20:45 Uhr",
            TranscriptionNormalizationService.normalizeTimeNotation("Wir treffen uns um 20.45 Uhr", listOf("de")),
        )
    }

    @Test
    fun germanTimeNotationAcceptsRegionalCodeAndLetterCase() {
        assertEquals(
            "um 9:30 uhr",
            TranscriptionNormalizationService.normalizeTimeNotation("um 9.30 uhr", listOf("de-DE")),
        )
        assertEquals(
            "09:00 UHR",
            TranscriptionNormalizationService.normalizeTimeNotation("09.00 UHR", listOf("de_DE")),
        )
    }

    @Test
    fun germanTimeNotationLeavesDottedNumbersAlone() {
        listOf(
            "Die Kosten betragen 20.450 Euro",
            "Termin am 19.04.2026",
            "Version 20.45.1 ist da",
            "Termin am 19.20.45 Uhr",
            "Er sagt 20.45. zurück",
        ).forEach { text ->
            assertEquals(text, TranscriptionNormalizationService.normalizeTimeNotation(text, listOf("de")))
        }
    }

    @Test
    fun germanTimeNotationSkipsInvalidTimesAndPartialMinutes() {
        listOf(
            "99.99 Uhr",
            "20.75 Uhr",
            "24.00 Uhr",
            "9.5 Uhr",
            "20.45",
            "Preis: 12.90",
        ).forEach { text ->
            assertEquals(text, TranscriptionNormalizationService.normalizeTimeNotation(text, listOf("de")))
        }
    }

    @Test
    fun timeNotationIsNotAppliedToOtherLanguages() {
        val text = "Meeting um 20.45 Uhr"
        assertEquals(text, TranscriptionNormalizationService.normalizeTimeNotation(text, listOf("en")))
        assertEquals(text, TranscriptionNormalizationService.normalizeTimeNotation(text, emptyList()))
    }

    @Test
    fun timeNotationDoesNotRewriteSpelledOutTimes() {
        assertEquals(
            "Zwanzig Uhr 45",
            TranscriptionNormalizationService.normalizeTimeNotation("Zwanzig Uhr 45", listOf("de")),
        )
    }

    @Test
    fun timeNotationRequiresWordBoundaryAfterUhr() {
        listOf(
            "Der Vortrag beginnt um 12.50 Uhren",
            "Die 20.45 Uhrzeit ist falsch",
            "Sein 9.30 Uhrwerk tickt laut",
        ).forEach { text ->
            assertEquals(text, TranscriptionNormalizationService.normalizeTimeNotation(text, listOf("de")))
        }
        assertEquals(
            "Treffen um 20:45 Uhr.",
            TranscriptionNormalizationService.normalizeTimeNotation("Treffen um 20.45 Uhr.", listOf("de")),
        )
    }

    @Test
    fun timeNotationRewritesTimeRanges() {
        assertEquals(
            "von 9:00 bis 17:00 Uhr",
            TranscriptionNormalizationService.normalizeTimeNotation("von 9.00 bis 17.00 Uhr", listOf("de")),
        )
        assertEquals(
            "zwischen 14:30 und 15:00 Uhr",
            TranscriptionNormalizationService.normalizeTimeNotation("zwischen 14.30 und 15.00 Uhr", listOf("de")),
        )
        assertEquals(
            "Sprechstunde 9:00-17:00 Uhr",
            TranscriptionNormalizationService.normalizeTimeNotation("Sprechstunde 9.00-17.00 Uhr", listOf("de")),
        )
        assertEquals(
            "Sprechstunde 9:00–17:00 Uhr",
            TranscriptionNormalizationService.normalizeTimeNotation("Sprechstunde 9.00–17.00 Uhr", listOf("de")),
        )
        assertEquals(
            "von 9.00 bis 17.00",
            TranscriptionNormalizationService.normalizeTimeNotation("von 9.00 bis 17.00", listOf("de")),
        )
    }

    @Test
    fun timeNotationAppliesThroughNormalizeTextWhenNumberNormalizationIsOff() {
        assertEquals(
            "Wir treffen uns um 20:45 Uhr",
            TranscriptionNormalizationService.normalizeText(
                "Wir treffen uns um 20.45 Uhr",
                "de",
                settings = settings(enabled = false),
            ),
        )
    }

    @Test
    fun timeNotationAppliesToSegmentsAndTextThroughNormalizeResult() {
        val result = TranscriptionNormalizationService.normalizeResult(
            text = "um 20.45 Uhr",
            detectedLanguage = "de",
            configuredLanguage = "de",
            duration = 2.0,
            processingTime = 0.1,
            engineUsed = "test",
            segments = listOf(
                TranscriptionSegment("um 20.45 Uhr", 0.0, 1.0),
                TranscriptionSegment("um 9.30 Uhr", 1.0, 2.0),
            ),
            task = TranscriptionTask.Transcribe,
        )
        assertEquals("um 20:45 Uhr", result.text)
        assertEquals(listOf("um 20:45 Uhr", "um 9:30 Uhr"), result.segments.map { it.text })
    }

    @Test
    fun translateTaskNormalizesAsEnglish() {
        assertEquals(
            listOf("en"),
            TranscriptionNormalizationService.normalizationLanguages(
                task = TranscriptionTask.Translate,
                detectedLanguage = "de",
                configuredLanguage = "de",
            ),
        )
    }
}
