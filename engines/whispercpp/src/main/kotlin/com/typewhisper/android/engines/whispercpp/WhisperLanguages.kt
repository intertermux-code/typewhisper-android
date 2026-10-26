package com.typewhisper.android.engines.whispercpp

/**
 * Maps BCP-47 tags to the language names whisper.cpp's runtime understands.
 *
 * Deliberately narrow: the product only advertises a handful of languages, so a
 * tag outside that set is treated as auto-detect rather than silently sent as a
 * whisper language that may or may not exist in this model build.
 */
internal object WhisperLanguages {

    /** Whisper language identifiers, keyed by the BCP-47 primary subtag. */
    private val SUPPORTED = mapOf(
        "en" to "en",
        "de" to "de",
        "fr" to "fr",
        "es" to "es",
        "nl" to "nl",
        "zh" to "zh",
        "ja" to "ja",
    )

    const val AUTO = "auto"

    /**
     * Reduces a BCP-47 tag to its primary subtag ("zh-Hans-CN" -> "zh").
     * Splits on both separators allowed by the spec and lower-cases, since
     * callers pass tags in mixed case.
     */
    fun primarySubtag(tag: String?): String? {
        val trimmed = tag?.trim()?.lowercase()
        if (trimmed.isNullOrEmpty()) return null
        val sub = trimmed.split('-', '_').firstOrNull { it.isNotEmpty() } ?: return null
        return sub
    }

    /**
     * Returns the whisper language name for [tag], or [AUTO] when the tag is
     * null/blank, explicitly "auto", or a language we do not gate on.
     */
    fun toWhisperLanguage(tag: String?): String {
        val sub = primarySubtag(tag) ?: return AUTO
        if (sub == AUTO) return AUTO
        return SUPPORTED[sub] ?: AUTO
    }
}
