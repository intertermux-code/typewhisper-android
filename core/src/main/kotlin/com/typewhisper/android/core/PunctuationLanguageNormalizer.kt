package com.typewhisper.android.core

import java.text.Normalizer
import java.util.Locale

/**
 * BCP-47 tags arrive in every shape a model can emit: `en`, `en-US`, `zh_CN`,
 * `DE`. Normalization decisions are made on the primary subtag, so every
 * language-sensitive path funnels through here instead of comparing raw tags.
 */
public object PunctuationLanguageNormalizer {

    private val combiningMarks = Regex("\\p{Mn}")

    /** Lower-cased primary subtag (`de-DE` -> `de`, `zh_CN` -> `zh`), or null when unusable. */
    public fun normalize(language: String?): String? {
        val trimmed = language?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-') ?: return null
        if (trimmed.isEmpty()) return null
        val primary = trimmed.substringBefore('-').trim()
        if (primary.isEmpty() || !primary.all { it.isLetter() }) return null
        return primary
    }

    /**
     * Lower-case plus accent folding, so `Point`, `veintitrés` and `drieëntwintig`
     * match their vocabulary entries. CJK text is returned lower-cased as-is.
     */
    internal fun fold(word: String): String = combiningMarks.replace(
        Normalizer.normalize(word.lowercase(Locale.ROOT), Normalizer.Form.NFD),
        "",
    )
}
