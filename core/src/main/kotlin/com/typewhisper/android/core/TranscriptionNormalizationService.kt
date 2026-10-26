package com.typewhisper.android.core

/**
 * Post-processing applied to a transcript before it reaches the user, ported from
 * TypeWhisper (macOS) `TranscriptionNormalizationService`.
 *
 * Two rewrites run, in order: spoken number words to digits, then German clock
 * times to the DIN 5008 colon form. The time rewrite is deliberately independent
 * of the number-normalization switch — turning spoken-number cleanup off must not
 * restore a separator the standard treats as incorrect.
 */
public object TranscriptionNormalizationService {

    public const val DEFAULT_NUMBER_NORMALIZATION_MINIMUM_VALUE: Int = 10

    private val MINIMUM_VALUE_CHOICES = setOf(0, 10, 100)

    public fun numberNormalizationMinimumValue(
        settings: NormalizationSettings = NormalizationSettings.EMPTY,
    ): Int {
        val value = settings.integer(NormalizationSettings.NUMBER_NORMALIZATION_MINIMUM_VALUE)
        return if (value != null && value in MINIMUM_VALUE_CHOICES) value else DEFAULT_NUMBER_NORMALIZATION_MINIMUM_VALUE
    }

    public fun numberNormalizationEnabled(
        override: Boolean? = null,
        settings: NormalizationSettings = NormalizationSettings.EMPTY,
    ): Boolean {
        if (override != null) return override
        val stored = settings.boolean(NormalizationSettings.NUMBER_NORMALIZATION_ENABLED) ?: return true
        return stored
    }

    public fun normalizeText(
        text: String,
        language: String?,
        languageCandidates: List<String> = emptyList(),
        normalizeNumbers: Boolean? = null,
        settings: NormalizationSettings = NormalizationSettings.EMPTY,
    ): String = normalizeText(
        text,
        languages = prioritizedLanguages(language, languageCandidates),
        normalizeNumbers = normalizeNumbers,
        settings = settings,
    )

    public fun normalizeText(
        text: String,
        languages: List<String>,
        normalizeNumbers: Boolean? = null,
        settings: NormalizationSettings = NormalizationSettings.EMPTY,
    ): String {
        // No early return on the number-normalization setting here: the time rewrite
        // runs independently of it, and normalizeNumberWords checks the setting itself.
        val numberNormalized = normalizeNumberWords(text, languages, normalizeNumbers, settings)
        return normalizeTimeNotation(numberNormalized, languages)
    }

    private fun normalizeNumberWords(
        text: String,
        languages: List<String>,
        normalizeNumbers: Boolean?,
        settings: NormalizationSettings,
    ): String {
        if (!numberNormalizationEnabled(normalizeNumbers, settings)) return text

        val minimumValue = numberNormalizationMinimumValue(settings)
        for (language in prioritizedLanguages(null, languages)) {
            val normalized = NumberWordNormalizer.normalize(text, language, minimumValue)
            if (normalized != text) return normalized
        }

        return text
    }

    /**
     * Rewrites German clock times that use a period as the hour/minute separator,
     * such as `20.45 Uhr`, to the colon form `20:45 Uhr` required by DIN 5008.
     *
     * Only a time written directly in front of the word `Uhr` is considered, and only
     * when both groups hold a valid hour and minute. Dates (`19.04.2026`), thousands
     * separators (`20.450`), version numbers and plain decimals therefore stay
     * unchanged, as does anything whose minutes are not spelled with two digits.
     * Words that merely start with `Uhr` (`Uhren`, `Uhrzeit`, `Uhrwerk`) do not count
     * either. Ranges linked by `bis`, `und`, `-` or `–` are rewritten on both sides.
     */
    public fun normalizeTimeNotation(text: String, languages: List<String>): String {
        if (text.isEmpty()) return text
        val isGerman = languages.any { PunctuationLanguageNormalizer.normalize(it) == "de" }
        if (!isGerman) return text
        return GermanTimeNotation.normalize(text)
    }

    public fun normalizeResult(
        text: String,
        detectedLanguage: String?,
        configuredLanguage: String?,
        configuredLanguageCandidates: List<String> = emptyList(),
        duration: Double,
        processingTime: Double,
        engineUsed: String,
        segments: List<TranscriptionSegment>,
        task: TranscriptionTask,
        normalizeNumbers: Boolean? = null,
        settings: NormalizationSettings = NormalizationSettings.EMPTY,
    ): TranscriptionResult {
        val languages = normalizationLanguages(
            task = task,
            detectedLanguage = detectedLanguage,
            configuredLanguage = configuredLanguage,
            configuredLanguageCandidates = configuredLanguageCandidates,
        )
        return TranscriptionResult(
            text = normalizeText(text, languages, normalizeNumbers, settings),
            detectedLanguage = detectedLanguage,
            duration = duration,
            processingTime = processingTime,
            engineUsed = engineUsed,
            segments = segments.map {
                it.copy(text = normalizeText(it.text, languages, normalizeNumbers, settings))
            },
        )
    }

    public fun normalizeResult(
        result: TranscriptionResult,
        configuredLanguage: String?,
        configuredLanguageCandidates: List<String> = emptyList(),
        task: TranscriptionTask,
        normalizeNumbers: Boolean? = null,
        settings: NormalizationSettings = NormalizationSettings.EMPTY,
    ): TranscriptionResult = normalizeResult(
        text = result.text,
        detectedLanguage = result.detectedLanguage,
        configuredLanguage = configuredLanguage,
        configuredLanguageCandidates = configuredLanguageCandidates,
        duration = result.duration,
        processingTime = result.processingTime,
        engineUsed = result.engineUsed,
        segments = result.segments,
        task = task,
        normalizeNumbers = normalizeNumbers,
        settings = settings,
    )

    public fun normalizationLanguages(
        task: TranscriptionTask,
        detectedLanguage: String?,
        configuredLanguage: String?,
        configuredLanguageCandidates: List<String> = emptyList(),
    ): List<String> {
        if (task == TranscriptionTask.Translate) return listOf("en")
        val candidates = listOfNotNull(configuredLanguage) + configuredLanguageCandidates
        return prioritizedLanguages(detectedLanguage, candidates)
    }

    private fun prioritizedLanguages(primary: String?, candidates: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()
        for (rawLanguage in listOfNotNull(primary) + candidates) {
            val normalized = PunctuationLanguageNormalizer.normalize(rawLanguage) ?: continue
            if (seen.add(normalized)) result.add(normalized)
        }
        return result
    }

    /** `HH.MM` before the word `Uhr`, and the same pattern on both ends of a time range. */
    private object GermanTimeNotation {

        private val rangeExpression = Regex(
            """(?<![\d.])((?:[01]?\d)|(?:2[0-3]))\.([0-5]\d)(?=[ \t]*(?:bis|und|-|–)[ \t]*(?:[01]?\d|2[0-3])\.[0-5]\d[ \t]*Uhr\b)""",
            RegexOption.IGNORE_CASE,
        )

        private val expression = Regex(
            """(?<![\d.])((?:[01]?\d)|(?:2[0-3]))\.([0-5]\d)(?=[ \t]*Uhr\b)""",
            RegexOption.IGNORE_CASE,
        )

        fun normalize(text: String): String {
            // The range pattern runs first so the second time is still written with a period when it matches.
            return expression.replace(rangeExpression.replace(text, "$1:$2"), "$1:$2")
        }
    }
}
