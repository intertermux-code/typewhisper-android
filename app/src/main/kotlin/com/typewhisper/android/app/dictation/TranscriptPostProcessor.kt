package com.typewhisper.android.app.dictation

import android.content.Context
import com.typewhisper.android.app.TypeWhisperApplication
import com.typewhisper.android.core.PostProcessing
import com.typewhisper.android.core.TranscriptionNormalizationService
import kotlinx.coroutines.flow.first

/**
 * The single normalization seam between a raw engine transcript and delivered
 * text. :core is a pure-JVM module, so this is the one call site to change if
 * post-processing needs to be disabled or swapped — the loop never imports
 * :core anywhere else.
 *
 * Suspending because the number-normalization switches and the dictionary/snippet rows
 * are stored data: reading them on the main thread is exactly what the settings screen
 * promises not to do.
 */
object TranscriptPostProcessor {

    suspend fun process(context: Context, raw: String, language: String?): String {
        val container = TypeWhisperApplication.container(context)
        val settings = container.preferences.normalizationSettings()
        val normalized = TranscriptionNormalizationService.normalizeText(
            raw,
            language,
            settings = settings,
        )
        val substitutions = container.substitutions()
        val dictionary = substitutions.dictionary.first()
        val snippets = substitutions.snippets.first()
        return PostProcessing.expandSnippets(
            PostProcessing.applyDictionary(normalized, dictionary),
            snippets,
        )
    }
}
