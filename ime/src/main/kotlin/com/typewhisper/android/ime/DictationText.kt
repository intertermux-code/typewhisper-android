package com.typewhisper.android.ime

import com.typewhisper.android.core.NormalizationSettings
import com.typewhisper.android.core.PostProcessing
import com.typewhisper.android.core.TranscriptionNormalizationService

/**
 * Everything that turns a raw transcript into the text the user sees, kept free of
 * Android types so it runs in JVM tests.
 */
internal object DictationText {

    fun preferredLanguage(stored: String?): String? =
        stored?.takeIf { it.isNotBlank() && it != AUTO_LANGUAGE }

    fun finalize(
        raw: String,
        language: String?,
        settings: NormalizationSettings,
        dictionary: List<PostProcessing.DictionaryEntry>,
        snippets: List<PostProcessing.Snippet>,
    ): String {
        val normalized = TranscriptionNormalizationService.normalizeText(raw, language, settings = settings)
        val corrected = PostProcessing.applyDictionary(normalized, dictionary)
        return PostProcessing.expandSnippets(corrected, snippets)
    }

    private const val AUTO_LANGUAGE = "auto"
}
